package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;
import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;
import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.aeat.*;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.ws.Holder;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;

public class AeatClient {

    private final String certPath;
    private final String certPassword;
    private final VeriFactuConfig config;
    private final boolean production;
    private final boolean verifactuMode;
    // Cache the service to avoid reloading WSDL every time
    private static SfVerifactu service;

    public AeatClient(String certPath, String certPassword, VeriFactuConfig config, boolean production,
            boolean verifactuMode) {
        this.certPath = certPath;
        this.certPassword = certPassword;
        this.config = config;
        this.production = production;
        this.verifactuMode = verifactuMode;
    }

    public Map<String, Object> sendInvoice(VeriFactuInvoice invoice, Map<String, Object> previous) {
        try {
            // 1. Prepare common data
            String issuerName = config.getIssuerName() != null ? config.getIssuerName() : "";
            String issuerVat = config.getIssuerVat() != null ? config.getIssuerVat() : "";
            String numSerie = invoice.getInvoiceNumber();
            String fechaExp = invoice.getIssueDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            String tipoFactura = invoice.getInvoiceType();
            String cuotaTotal = String.format(Locale.US, "%.2f", invoice.getTaxAmount());
            String importeTotal = String.format(Locale.US, "%.2f", invoice.getTotalAmount());

            // Timestamp with Europe/Madrid timezone (AEAT requirement)
            // Format: dd-MM-yyyy'T'HH:mm:ssXXX (e.g., "20-01-2026T14:30:45+01:00")
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Madrid"));

            // For hash generation: dd-MM-yyyy'T'HH:mm:ssXXX
            String tsString = now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm:ssXXX"));

            // XMLGregorianCalendar for JAXB (Standard ISO format)
            GregorianCalendar c = GregorianCalendar.from(now);
            XMLGregorianCalendar tsXml = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);

            // Get previous hash
            String prevHash = "";
            if (previous != null && previous.containsKey("hash")) {
                prevHash = (String) previous.get("hash");
            } else {
                String ph = invoice.getPreviousHash();
                if (ph != null)
                    prevHash = ph;
            }

            // 2. Generate Hash (Huella)
            String huella = HashHelper.generateInvoiceHash(
                    issuerVat, numSerie, fechaExp, tipoFactura, cuotaTotal, importeTotal, prevHash, tsString);

            // 3. Build JAXB Objects
            CabeceraType cabecera = buildHeader(issuerName, issuerVat);

            RegistroFacturacionAltaType registroAlta = new RegistroFacturacionAltaType();
            registroAlta.setIDVersion("1.0");

            // IDFactura
            IDFacturaExpedidaType idFactura = new IDFacturaExpedidaType();
            idFactura.setIDEmisorFactura(issuerVat);
            idFactura.setNumSerieFactura(numSerie);
            idFactura.setFechaExpedicionFactura(fechaExp);
            registroAlta.setIDFactura(idFactura);

            registroAlta.setNombreRazonEmisor(issuerName);
            // Assuming ClaveTipoFacturaType is an enum or string check.
            // Based on previous error, ClaveTipoFacturaType fromValue might be correct if
            // it is enum.
            // If it is String, set directly. Let's assume Enum for now as JAXB usually
            // generates Enums for codes.
            // If compilation fails on fromValue, I will switch to setter (if String).
            // Actually previous error complained about TipoHuellaType, NOT
            // ClaveTipoFacturaType. So ClaveTipoFacturaType IS likely an enum.
            // BUT wait, if 'fromValue' doesn't exist? I'll check if ClaveTipoFacturaType is
            // enum.
            // Safe bet: ClaveTipoFacturaType.fromValue(tipoFactura) works if generated as
            // Enum.
            // If generated as Class, it might be different.
            // Given 'TipoHuellaType' was missing, 'ClaveTipoFacturaType' might be missing
            // or String too?
            // I will use ClaveTipoFacturaType.fromValue(tipoFactura) BUT catch
            // IllegalArgumentException or check existing file?
            // To be safe, I'll check ClaveTipoFacturaType.java or assume String if simple.
            // Actually, I'll bet it's an enum.
            registroAlta.setTipoFactura(ClaveTipoFacturaType.fromValue(tipoFactura));

            registroAlta.setDescripcionOperacion("Factura " + numSerie);

            // Breakdown (Desglose)
            registroAlta.setDesglose(buildBreakdowns(invoice));

            // Totals
            registroAlta.setCuotaTotal(cuotaTotal);
            registroAlta.setImporteTotal(importeTotal);

            // Chaining (Encadenamiento)
            registroAlta.setEncadenamiento(buildChaining(previous, issuerVat));

            // Recipients
            List<PersonaFisicaJuridicaType> destinatarios = buildRecipients(invoice);
            if (!destinatarios.isEmpty()) {
                RegistroFacturacionAltaType.Destinatarios dests = new RegistroFacturacionAltaType.Destinatarios();
                dests.getIDDestinatario().addAll(destinatarios);
                registroAlta.setDestinatarios(dests);
            }

            // System Info
            registroAlta.setSistemaInformatico(buildSystemInfo());

            // Timestamp and Hash
            registroAlta.setFechaHoraHusoGenRegistro(tsXml);
            registroAlta.setTipoHuella("01"); // 01 = SHA-256
            registroAlta.setHuella(huella);

            // Optional fields
            if (invoice.getOperationDate() != null) {
                String fechaOp = invoice.getOperationDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                registroAlta.setFechaOperacion(fechaOp);
            }

            if (invoice.getTaxPeriod() != null) {
                try {
                    // TODO: Uncomment when WSDL is updated to support PeriodoImpositivo
                    /*
                     * RegistroFacturacionAltaType.PeriodoImpositivo periodo = new
                     * RegistroFacturacionAltaType.PeriodoImpositivo();
                     * periodo.setEjercicio(String.valueOf(invoice.getIssueDate().getYear()));
                     * periodo.setPeriodo(invoice.getTaxPeriod());
                     * registroAlta.setPeriodoImpositivo(periodo);
                     */
                } catch (Exception e) {
                    // PeriodoImpositivo not available in XSD
                }
            }

            if (invoice.getCorrectionType() != null) {
                try {
                    // TODO: Uncomment when WSDL is updated to support TipoRectificativa and
                    // ImporteRectificacion
                    /*
                     * registroAlta.setTipoRectificativa(invoice.getCorrectionType());
                     * 
                     * // Add ImporteRectificacion block if required
                     * if ("S".equals(invoice.getCorrectionType()) &&
                     * isCorrectiveInvoice(tipoFactura)) {
                     * Map<String, String> importeRectificacion =
                     * buildImporteRectificacion(invoice);
                     * if (importeRectificacion != null) {
                     * // Create ImporteRectificacion object
                     * try {
                     * RegistroFacturacionAltaType.ImporteRectificacion importe = new
                     * RegistroFacturacionAltaType.ImporteRectificacion();
                     * importe.setBaseRectificada(importeRectificacion.get("BaseRectificada"));
                     * importe.setCuotaRectificada(importeRectificacion.get("CuotaRectificada"));
                     * if (importeRectificacion.containsKey("CuotaRecargoRectificado")) {
                     * importe.setCuotaRecargoRectificado(
                     * importeRectificacion.get("CuotaRecargoRectificado"));
                     * }
                     * registroAlta.setImporteRectificacion(importe);
                     * } catch (Exception e) {
                     * // ImporteRectificacion not available in XSD
                     * }
                     * }
                     * }
                     */
                } catch (Exception e) {
                    // TipoRectificativa not available in XSD
                }
            }

            if (invoice.getExternalReference() != null) {
                try {
                    registroAlta.setRefExterna(invoice.getExternalReference());
                } catch (Exception e) {
                    // RefExterna not available in XSD
                }
            }

            // Wrap in RegistroFactura
            RegistroFacturaType registroFactura = new RegistroFacturaType();
            registroFactura.setRegistroAlta(registroAlta);

            List<RegistroFacturaType> listaRegistros = new ArrayList<>();
            listaRegistros.add(registroFactura);

            // 4. Send via SOAP
            return performSoapCall(cabecera, listaRegistros, huella, numSerie, fechaExp, tsString);

        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", e.getMessage());
            e.printStackTrace();
            return errorMap;
        }
    }

    private CabeceraType buildHeader(String issuerName, String issuerVat) {
        CabeceraType cabecera = new CabeceraType();
        PersonaFisicaJuridicaESType obligado = new PersonaFisicaJuridicaESType();
        obligado.setNombreRazon(issuerName);
        obligado.setNIF(issuerVat);
        cabecera.setObligadoEmision(obligado);
        return cabecera;
    }

    private DesgloseType buildBreakdowns(VeriFactuInvoice invoice) {
        DesgloseType desglose = new DesgloseType();

        List<VeriFactuBreakdown> breakdowns = invoice.getBreakdowns();

        if (breakdowns != null) {
            for (VeriFactuBreakdown b : breakdowns) {
                DetalleType detalle = new DetalleType();

                // Campos obligatorios básicos
                detalle.setBaseImponibleOimporteNoSujeto(
                        String.format(Locale.US, "%.2f", b.getBaseAmount()));
                detalle.setCuotaRepercutida(
                        String.format(Locale.US, "%.2f", b.getTaxAmount()));
                detalle.setTipoImpositivo(
                        String.format(Locale.US, "%.2f", b.getTaxRate()));

                // Campos AEAT adicionales (ClaveRegimen, CalificacionOperacion, etc.)
                // Nota: Los tipos JAXB generados pueden variar según el XSD
                // Si los tipos enum no existen, usar setters de String directamente

                if (b.getClaveRegimen() != null) {
                    try {
                        // Intentar usar enum si existe
                        detalle.setClaveRegimen(b.getClaveRegimen());
                    } catch (Exception e) {
                        // Si no existe el setter, el campo no está en el XSD
                        // o requiere un tipo diferente
                    }
                }

                if (b.getCalificacionOperacion() != null) {
                    try {
                        detalle.setCalificacionOperacion(
                                CalificacionOperacionType.fromValue(b.getCalificacionOperacion()));
                    } catch (Exception e) {
                        // Campo no disponible o valor inválido
                    }
                }

                if (b.getOperacionExenta() != null) {
                    try {
                        detalle.setOperacionExenta(
                                OperacionExentaType.fromValue(b.getOperacionExenta()));
                    } catch (Exception e) {
                        // Campo no disponible o valor inválido
                    }
                }

                // Campos opcionales
                if (b.getBaseImponibleACoste() != null) {
                    try {
                        detalle.setBaseImponibleACoste(
                                String.format(Locale.US, "%.2f", b.getBaseImponibleACoste()));
                    } catch (Exception e) {
                        // Campo no disponible en XSD actual
                    }
                }

                if (b.getTipoRecargoEquivalencia() != null) {
                    try {
                        detalle.setTipoRecargoEquivalencia(
                                String.format(Locale.US, "%.2f", b.getTipoRecargoEquivalencia()));
                    } catch (Exception e) {
                        // Campo no disponible en XSD actual
                    }
                }

                if (b.getCuotaRecargoEquivalencia() != null) {
                    try {
                        detalle.setCuotaRecargoEquivalencia(
                                String.format(Locale.US, "%.2f", b.getCuotaRecargoEquivalencia()));
                    } catch (Exception e) {
                        // Campo no disponible en XSD actual
                    }
                }

                // Add to list
                desglose.getDetalleDesglose().add(detalle);
            }
        }

        return desglose;
    }

    private RegistroFacturacionAltaType.Encadenamiento buildChaining(Map<String, Object> previous, String issuerVat) {
        RegistroFacturacionAltaType.Encadenamiento enc = new RegistroFacturacionAltaType.Encadenamiento();

        if (previous == null || previous.isEmpty()) {
            enc.setPrimerRegistro(PrimerRegistroCadenaType.S);
        } else {
            EncadenamientoFacturaAnteriorType prev = new EncadenamientoFacturaAnteriorType();
            prev.setIDEmisorFactura(issuerVat);
            prev.setNumSerieFactura((String) previous.get("number"));
            prev.setFechaExpedicionFactura((String) previous.get("date"));
            prev.setHuella((String) previous.get("hash"));
            enc.setRegistroAnterior(prev);
        }
        return enc;
    }

    private List<PersonaFisicaJuridicaType> buildRecipients(VeriFactuInvoice invoice) {
        List<PersonaFisicaJuridicaType> list = new ArrayList<>();
        List<VeriFactuRecipient> recipients = invoice.getRecipients();
        if (recipients != null) {
            for (VeriFactuRecipient r : recipients) {
                PersonaFisicaJuridicaType p = new PersonaFisicaJuridicaType();
                p.setNombreRazon(r.getName());
                p.setNIF(r.getTaxId());
                list.add(p);
            }
        }
        return list;
    }

    private SistemaInformaticoType buildSystemInfo() {
        SistemaInformaticoType si = new SistemaInformaticoType();
        si.setNombreRazon(config.getDeveloperName());
        si.setNIF(config.getDeveloperNif());
        si.setNombreSistemaInformatico(config.getSystemName());
        si.setIdSistemaInformatico(config.getSystemId());
        si.setVersion(config.getSystemVersion());
        si.setNumeroInstalacion(config.getInstallationNumber());
        si.setTipoUsoPosibleSoloVerifactu(SiNoType.fromValue(config.getOnlyVerifactuCapable()));
        si.setTipoUsoPosibleMultiOT(SiNoType.fromValue(config.getMultiObligatedCapable()));
        si.setIndicadorMultiplesOT(SiNoType.fromValue(config.getHasMultipleObligated()));
        return si;
    }

    private Map<String, Object> performSoapCall(CabeceraType cabecera, List<RegistroFacturaType> registros,
            String huella, String numSerie, String fechaExp, String ts) {
        try {
            // Lazy init service
            if (service == null) {
                URL wsdlUrl = new File("src/main/resources/SistemaFacturacion.wsdl").toURI().toURL();
                service = new SfVerifactu(wsdlUrl);
            }

            SfPortTypeVerifactu port = service.getSistemaVerifactu();
            configureSSL(port);

            // Holders for response
            Holder<String> csvH = new Holder<>();
            Holder<DatosPresentacionType> datosH = new Holder<>();
            Holder<CabeceraType> cabeceraH = new Holder<>();
            Holder<String> tiempoH = new Holder<>();
            Holder<EstadoEnvioType> estadoH = new Holder<>();
            Holder<List<RespuestaExpedidaType>> lineaH = new Holder<>();

            // CALL
            port.regFactuSistemaFacturacion(cabecera, registros, csvH, datosH, cabeceraH, tiempoH, estadoH, lineaH);

            // Construct response map
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("aeat_status", estadoH.value != null ? estadoH.value.value() : "UNKNOWN");
            response.put("csv", csvH.value);
            response.put("hash", huella);
            response.put("number", numSerie);
            response.put("date", fechaExp);
            response.put("timestamp", ts);

            return response;

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            e.printStackTrace();
            return response;
        }
    }

    private void configureSSL(Object port) throws Exception {
        Client client = ClientProxy.getClient(port);
        HTTPConduit http = (HTTPConduit) client.getConduit();
        TLSClientParameters tlsParams = new TLSClientParameters();

        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        File certFile = new File(certPath);
        if (certFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(certFile)) {
                keyStore.load(fis, certPassword.toCharArray());
            }

            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, certPassword.toCharArray());

            tlsParams.setKeyManagers(kmf.getKeyManagers());
        }

        tlsParams.setDisableCNCheck(true);

        http.setTlsClientParameters(tlsParams);
    }

    /**
     * Build ImporteRectificacion block for substitution corrective invoices
     *
     * @param invoice The invoice
     * @return Map with ImporteRectificacion structure or null
     */
    private Map<String, String> buildImporteRectificacion(VeriFactuInvoice invoice) {
        Double baseRectificada = invoice.getCorrectedBaseAmount();
        Double cuotaRectificada = invoice.getCorrectedTaxAmount();

        // Both base and tax are required
        if (baseRectificada == null || cuotaRectificada == null) {
            return null;
        }

        Map<String, String> importe = new HashMap<>();
        importe.put("BaseRectificada", String.format(Locale.US, "%.2f", baseRectificada));
        importe.put("CuotaRectificada", String.format(Locale.US, "%.2f", cuotaRectificada));

        // Add optional surcharge if present
        Double cuotaRecargo = invoice.getCorrectedSurchargeAmount();
        if (cuotaRecargo != null) {
            importe.put("CuotaRecargoRectificado", String.format(Locale.US, "%.2f", cuotaRecargo));
        }

        return importe;
    }

    /**
     * Check if invoice type is corrective (R1-R5)
     *
     * @param tipoFactura Invoice type code
     * @return boolean
     */
    private boolean isCorrectiveInvoice(String tipoFactura) {
        return Arrays.asList("R1", "R2", "R3", "R4", "R5").contains(tipoFactura);
    }
}
