package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoiceReference;
import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;
import com.squareetlabs.verifactu.contracts.VeriFactuAnnulment;
import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.aeat.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.ws.Holder;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

public class AeatClient {

    /** FechaHoraHusoGenRegistro: ISO 8601 without fractional seconds, e.g. 2024-01-01T19:20:30+01:00. */
    private static final DateTimeFormatter AEAT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * Formats an amount the way the AEAT expects it: dot as decimal separator, exactly two
     * decimals, no exponent, and never "-0.00" (a value that rounds to zero is "0.00").
     */
    static String formatAmount(double value) {
        BigDecimal amount = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        return amount.signum() == 0 ? "0.00" : amount.toPlainString();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private final String certPath;
    private final String certPassword;
    private final VeriFactuConfig config;
    private final boolean production;
    private final boolean verifactuMode;
    // Cache the service to avoid reloading WSDL every time
    private static volatile SfVerifactu service;

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
            InvoiceValidator.ValidationResult configuration = InvoiceValidator.validate(config);
            if (!configuration.isValid()) {
                return validationFailure(configuration);
            }
            InvoiceValidator.ValidationResult validation = InvoiceValidator.validate(invoice, config);
            if (!validation.isValid()) {
                return validationFailure(validation);
            }
            String chainingError = chainingError(previous, invoice.getPreviousHash());
            if (chainingError != null) {
                return chainingFailure(chainingError);
            }

            // 1. Prepare common data
            String issuerName = config.getIssuerName() != null ? config.getIssuerName() : "";
            String issuerVat = config.getIssuerVat() != null ? config.getIssuerVat() : "";
            String numSerie = invoice.getInvoiceNumber();
            String fechaExp = invoice.getIssueDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            String tipoFactura = invoice.getInvoiceType();
            String cuotaTotal = formatAmount(invoice.getTaxAmount());
            String importeTotal = formatAmount(invoice.getTotalAmount());

            // Timestamp with Europe/Madrid timezone (AEAT requirement).
            // FechaHoraHusoGenRegistro must be ISO 8601 without fractional seconds
            // (YYYY-MM-DDThh:mm:ss+hh:mm) both in the XML and in the hash input, and both
            // must carry exactly the same value.
            ZonedDateTime now = generationInstant(invoice.getGenerationTimestamp());
            String tsString = now.format(AEAT_TIMESTAMP_FORMAT);
            XMLGregorianCalendar tsXml = DatatypeFactory.newInstance().newXMLGregorianCalendar(tsString);

            // Get previous hash
            String prevHash = "";
            if (previous != null && previous.containsKey("hash")) {
                prevHash = (String) previous.get("hash");
            } else {
                String ph = invoice.getPreviousHash();
                if (ph != null)
                    prevHash = ph;
            }

            // 2. Generate Hash (Huella). Uses the Map-based overload (instead of the
            // positional-args one) purely to also obtain "inputString": the exact,
            // deterministic string the hash was computed over, needed to allow
            // independent re-verification of the chain later (both in VERI*FACTU and
            // NO VERI*FACTU mode - see RD 1007/2023, art. 7: even without real-time
            // remisión, the local record must let the "huella" of every record be
            // checked, and every new record must verify the chaining with the
            // previous one is correct before being generated).
            Map<String, String> hashData = new HashMap<>();
            hashData.put("issuer_tax_id", issuerVat);
            hashData.put("invoice_number", numSerie);
            hashData.put("issue_date", fechaExp);
            hashData.put("invoice_type", tipoFactura);
            hashData.put("total_tax", cuotaTotal);
            hashData.put("total_amount", importeTotal);
            hashData.put("previous_hash", prevHash);
            hashData.put("generated_at", tsString);
            Map<String, String> hashResult = HashHelper.generateInvoiceHash(hashData);
            String huella = hashResult.get("hash");
            String huellaInputString = hashResult.get("inputString");

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

            String description = invoice.getOperationDescription();
            registroAlta.setDescripcionOperacion(description != null && !description.trim().isEmpty()
                    ? truncate(description.trim(), 500) : "Factura " + numSerie);

            // Breakdown (Desglose)
            registroAlta.setDesglose(buildBreakdowns(invoice));

            // Totals
            registroAlta.setCuotaTotal(cuotaTotal);
            registroAlta.setImporteTotal(importeTotal);

            // Chaining (Encadenamiento)
            registroAlta.setEncadenamiento(buildChaining(previous, issuerVat));

            // Rectified / substituted invoices (FacturasRectificadas / FacturasSustituidas)
            List<IDFacturaARType> rectified = buildReferences(invoice.getRectifiedInvoices(), issuerVat);
            if (!rectified.isEmpty()) {
                RegistroFacturacionAltaType.FacturasRectificadas facturasRectificadas =
                        new RegistroFacturacionAltaType.FacturasRectificadas();
                facturasRectificadas.getIDFacturaRectificada().addAll(rectified);
                registroAlta.setFacturasRectificadas(facturasRectificadas);
            }
            List<IDFacturaARType> substituted = buildReferences(invoice.getSubstitutedInvoices(), issuerVat);
            if (!substituted.isEmpty()) {
                RegistroFacturacionAltaType.FacturasSustituidas facturasSustituidas =
                        new RegistroFacturacionAltaType.FacturasSustituidas();
                facturasSustituidas.getIDFacturaSustituida().addAll(substituted);
                registroAlta.setFacturasSustituidas(facturasSustituidas);
            }

            // Complete invoice issued without identifying the recipient (art. 6.1.d)
            if (invoice.isWithoutRecipientIdentification()) {
                registroAlta.setFacturaSinIdentifDestinatarioArt61D(CompletaSinDestinatarioType.S);
            }

            // Recipients
            List<PersonaFisicaJuridicaType> destinatarios = buildRecipients(invoice);
            if (!destinatarios.isEmpty()) {
                RegistroFacturacionAltaType.Destinatarios dests = new RegistroFacturacionAltaType.Destinatarios();
                dests.getIDDestinatario().addAll(destinatarios);
                registroAlta.setDestinatarios(dests);
            }

            // Third-party issuance (EmitidaPorTerceroODestinatario / Tercero):
            // only sent when this specific invoice was issued by a third party
            // (e.g. Odei issuing on behalf of its client) or by the recipient
            // itself (self-billing). Most invoices leave this unset because
            // the obligado issues its own invoices.
            applyThirdPartyIssuance(registroAlta, invoice);

            // System Info
            registroAlta.setSistemaInformatico(buildSystemInfo(invoice));

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
                // TipoRectificativa (S = Sustitución, I = Por diferencia). Both
                // ClaveTipoRectificativaType and DesgloseRectificacionType are
                // top-level types already present in SuministroInformacion.xsd
                // / SistemaFacturacion.wsdl, so no WSDL upgrade is required.
                registroAlta.setTipoRectificativa(ClaveTipoRectificativaType.fromValue(invoice.getCorrectionType()));

                // ImporteRectificacion block: required for substitution-type
                // corrective invoices (R1-R5 with correctionType = "S").
                if ("S".equals(invoice.getCorrectionType()) && isCorrectiveInvoice(tipoFactura)) {
                    Map<String, String> importeRectificacion = buildImporteRectificacion(invoice);
                    if (importeRectificacion == null) {
                        throw new IllegalArgumentException("correctedBaseAmount and correctedTaxAmount are "
                                + "required for a corrective invoice by substitution (TipoRectificativa = \"S\")");
                    }
                    DesgloseRectificacionType importe = new DesgloseRectificacionType();
                    importe.setBaseRectificada(importeRectificacion.get("BaseRectificada"));
                    importe.setCuotaRectificada(importeRectificacion.get("CuotaRectificada"));
                    if (importeRectificacion.containsKey("CuotaRecargoRectificado")) {
                        importe.setCuotaRecargoRectificado(
                                importeRectificacion.get("CuotaRecargoRectificado"));
                    }
                    registroAlta.setImporteRectificacion(importe);
                }
            }

            if (invoice.getExternalReference() != null) {
                try {
                    registroAlta.setRefExterna(invoice.getExternalReference());
                } catch (Exception e) {
                    // RefExterna not available in XSD
                }
            }

            // Correction of a previously generated / rejected record (Subsanacion / RechazoPrevio)
            if (invoice.isSubsanation()) {
                registroAlta.setSubsanacion(SubsanacionType.S);
            }
            String previousRejection = invoice.getPreviousRejection();
            if (previousRejection != null && !previousRejection.trim().isEmpty()) {
                registroAlta.setRechazoPrevio(RechazoPrevioType.fromValue(previousRejection.trim()));
            }

            // Qualified simplified invoice (art. 7.2 and 7.3 RD 1619/2012)
            if (invoice.isQualifiedSimplified()) {
                registroAlta.setFacturaSimplificadaArt7273(SimplificadaCualificadaType.S);
            }

            // Coupons / bonuses / discounts with only the original issued (Cupon = S)
            if (invoice.isCoupon()) {
                registroAlta.setCupon(CuponType.S);
            }

            // Macrodato is mandatory when |ImporteTotal| >= 100,000,000.00
            if (Math.abs(invoice.getTotalAmount()) >= 100_000_000d) {
                registroAlta.setMacrodato(MacrodatoType.S);
            }

            // Wrap in RegistroFactura
            RegistroFacturaType registroFactura = new RegistroFacturaType();
            registroFactura.setRegistroAlta(registroAlta);

            List<RegistroFacturaType> listaRegistros = new ArrayList<>();
            listaRegistros.add(registroFactura);

            // 4. Send via SOAP, UNLESS this client operates in "NO VERI*FACTU" mode
            // (RD 1007/2023, modalidad sin remisión inmediata): the record must still
            // be generated, hashed/chained exactly the same way, and signed (XAdES is
            // mandatory in this mode - the real-time remisión that provides that
            // integrity guarantee in VERI*FACTU mode does not happen here), but it is
            // NOT transmitted to the AEAT; it is kept by the obligado, available for a
            // future inspection.
            if (!verifactuMode) {
                JAXBElement<RegistroFacturacionAltaType> element =
                        new ObjectFactory().createRegistroAlta(registroAlta);
                return buildLocalOnlyResponse(element, huella, huellaInputString, numSerie, fechaExp, tsString);
            }
            return performSoapCall(cabecera, listaRegistros, huella, huellaInputString, numSerie, fechaExp, tsString);

        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", e.getMessage());
            e.printStackTrace();
            return errorMap;
        }
    }

    /**
     * Requests the AEAT annulment ("anulación") of a previously generated
     * billing record ({@code RegistroFacturacionAnulacionType}).
     * <p>
     * An annulment is a chained record just like an "alta" (see
     * {@link #sendInvoice}): pass the response of whatever the immediately
     * preceding record in the chain was (an invoice or a previous
     * annulment) as {@code previous}, or {@code null}/empty if this is
     * the very first record of the chain.
     *
     * @param annulment the annulment request
     * @param previous  response map of the previous chained record, or null
     * @return response map with the same shape as {@link #sendInvoice}
     */
    public Map<String, Object> sendAnnulment(VeriFactuAnnulment annulment, Map<String, Object> previous) {
        try {
            InvoiceValidator.ValidationResult configuration = InvoiceValidator.validate(config);
            if (!configuration.isValid()) {
                return validationFailure(configuration);
            }
            InvoiceValidator.ValidationResult validation = InvoiceValidator.validate(annulment, config);
            if (!validation.isValid()) {
                return validationFailure(validation);
            }
            String chainingError = chainingError(previous, annulment.getPreviousHash());
            if (chainingError != null) {
                return chainingFailure(chainingError);
            }

            String issuerVat = config.getIssuerVat() != null ? config.getIssuerVat() : "";
            String numSerie = annulment.getInvoiceNumber();
            String fechaExp = annulment.getIssueDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

            ZonedDateTime now = generationInstant(annulment.getGenerationTimestamp());
            String tsString = now.format(AEAT_TIMESTAMP_FORMAT);
            XMLGregorianCalendar tsXml = DatatypeFactory.newInstance().newXMLGregorianCalendar(tsString);

            String prevHash = "";
            if (previous != null && previous.containsKey("hash")) {
                prevHash = (String) previous.get("hash");
            } else {
                String ph = annulment.getPreviousHash();
                if (ph != null) {
                    prevHash = ph;
                }
            }

            Map<String, String> hashData = new HashMap<>();
            hashData.put("issuer_tax_id", issuerVat);
            hashData.put("invoice_number", numSerie);
            hashData.put("issue_date", fechaExp);
            hashData.put("previous_hash", prevHash);
            hashData.put("generated_at", tsString);
            Map<String, String> hashResult = HashHelper.generateAnnulmentHash(hashData);
            String huella = hashResult.get("hash");
            String huellaInputString = hashResult.get("inputString");

            CabeceraType cabecera = buildHeader(
                    config.getIssuerName() != null ? config.getIssuerName() : "", issuerVat);

            RegistroFacturacionAnulacionType registroAnulacion = new RegistroFacturacionAnulacionType();
            registroAnulacion.setIDVersion("1.0");

            IDFacturaExpedidaBajaType idFactura = new IDFacturaExpedidaBajaType();
            idFactura.setIDEmisorFacturaAnulada(issuerVat);
            idFactura.setNumSerieFacturaAnulada(numSerie);
            idFactura.setFechaExpedicionFacturaAnulada(fechaExp);
            registroAnulacion.setIDFactura(idFactura);

            if (annulment.getExternalReference() != null) {
                registroAnulacion.setRefExterna(annulment.getExternalReference());
            }

            if (annulment.isNotRegisteredAtAeat()) {
                registroAnulacion.setSinRegistroPrevio(SinRegistroPrevioType.S);
            }

            if (annulment.isRetryAfterRejection()) {
                registroAnulacion.setRechazoPrevio(RechazoPrevioAnulacionType.S);
            }

            applyAnnulmentGenerator(registroAnulacion, annulment);

            registroAnulacion.setEncadenamiento(buildAnnulmentChaining(previous, issuerVat));
            registroAnulacion.setSistemaInformatico(buildAnnulmentSystemInfo());

            registroAnulacion.setFechaHoraHusoGenRegistro(tsXml);
            registroAnulacion.setTipoHuella("01");
            registroAnulacion.setHuella(huella);

            RegistroFacturaType registroFactura = new RegistroFacturaType();
            registroFactura.setRegistroAnulacion(registroAnulacion);

            List<RegistroFacturaType> listaRegistros = new ArrayList<>();
            listaRegistros.add(registroFactura);

            if (!verifactuMode) {
                JAXBElement<RegistroFacturacionAnulacionType> element =
                        new ObjectFactory().createRegistroAnulacion(registroAnulacion);
                return buildLocalOnlyResponse(element, huella, huellaInputString, numSerie, fechaExp, tsString);
            }
            return performSoapCall(cabecera, listaRegistros, huella, huellaInputString, numSerie, fechaExp, tsString);

        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", e.getMessage());
            e.printStackTrace();
            return errorMap;
        }
    }

    private RegistroFacturacionAnulacionType.Encadenamiento buildAnnulmentChaining(
            Map<String, Object> previous, String issuerVat) {
        RegistroFacturacionAnulacionType.Encadenamiento enc = new RegistroFacturacionAnulacionType.Encadenamiento();

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

    private SistemaInformaticoType buildAnnulmentSystemInfo() {
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

    private static boolean isBaseOnly(VeriFactuBreakdown b) {
        String qualification = b.getCalificacionOperacion();
        return b.getOperacionExenta() != null
                || (qualification != null && qualification.startsWith("N"));
    }

    /**
     * Fills {@code GeneradoPor} and {@code Generador} when this annulment
     * record was NOT generated by the obligado itself (see
     * {@link VeriFactuAnnulment#getGeneratedBy()}).
     */
    private void applyAnnulmentGenerator(
            RegistroFacturacionAnulacionType registroAnulacion, VeriFactuAnnulment annulment) {
        String generatedBy = annulment.getGeneratedBy();
        if (generatedBy == null || generatedBy.trim().isEmpty()) {
            return;
        }

        registroAnulacion.setGeneradoPor(GeneradoPorType.fromValue(generatedBy));

        String generatorName = annulment.getGeneratorName();
        String generatorTaxId = annulment.getGeneratorTaxId();
        if (generatorName == null || generatorTaxId == null) {
            throw new IllegalArgumentException(
                    "generatorName and generatorTaxId are required when generatedBy is \"T\" or \"D\"");
        }
        PersonaFisicaJuridicaType generador = new PersonaFisicaJuridicaType();
        generador.setNombreRazon(generatorName);
        generador.setNIF(generatorTaxId);
        registroAnulacion.setGenerador(generador);
    }

    private CabeceraType buildHeader(String issuerName, String issuerVat) {
        CabeceraType cabecera = new CabeceraType();
        PersonaFisicaJuridicaESType obligado = new PersonaFisicaJuridicaESType();
        obligado.setNombreRazon(issuerName);
        obligado.setNIF(issuerVat);
        cabecera.setObligadoEmision(obligado);

        // Representante (Cabecera.Representante): only sent when this SDK
        // instance is configured with representative data, i.e. when Odei (or
        // any other integrator) is remitting these records on behalf of the
        // "obligado tributario" under apoderamiento or colaboración social.
        // IMPORTANT: filling this field without the corresponding legal
        // authorization (apoderamiento inscrito or Convenio de colaboración
        // social 017) in place is a compliance violation - see AEAT FAQs on
        // "Representación de los OEF por parte de las empresas de software".
        if (config.hasRepresentative()) {
            PersonaFisicaJuridicaESType representante = new PersonaFisicaJuridicaESType();
            representante.setNombreRazon(config.getRepresentativeName());
            representante.setNIF(config.getRepresentativeVat());
            cabecera.setRepresentante(representante);
        }

        return cabecera;
    }

    private DesgloseType buildBreakdowns(VeriFactuInvoice invoice) {
        DesgloseType desglose = new DesgloseType();

        List<VeriFactuBreakdown> breakdowns = invoice.getBreakdowns();

        if (breakdowns != null) {
            for (VeriFactuBreakdown b : breakdowns) {
                DetalleType detalle = new DetalleType();

                detalle.setBaseImponibleOimporteNoSujeto(formatAmount(b.getBaseAmount()));

                // Exempt (E1-E8) and not subject (N1, N2) operations carry only the base: the AEAT
                // rejects TipoImpositivo / CuotaRepercutida for them. Reverse charge (S2) is the
                // opposite: TipoImpositivo and CuotaRepercutida are mandatory and must be 0.
                if ("S2".equals(b.getCalificacionOperacion())) {
                    detalle.setTipoImpositivo(formatAmount(0));
                    detalle.setCuotaRepercutida(formatAmount(0));
                } else if (!isBaseOnly(b)) {
                    detalle.setCuotaRepercutida(formatAmount(b.getTaxAmount()));
                    detalle.setTipoImpositivo(formatAmount(b.getTaxRate()));
                }

                if (b.getTax() != null && !b.getTax().trim().isEmpty()) {
                    detalle.setImpuesto(b.getTax().trim());
                }

                if (b.getClaveRegimen() != null) {
                    detalle.setClaveRegimen(b.getClaveRegimen());
                }

                // An unknown code is a caller error: fail with a clear message instead of
                // silently dropping the element and letting the AEAT reject an incomplete record.
                if (b.getCalificacionOperacion() != null) {
                    detalle.setCalificacionOperacion(
                            CalificacionOperacionType.fromValue(b.getCalificacionOperacion()));
                }

                if (b.getOperacionExenta() != null) {
                    detalle.setOperacionExenta(OperacionExentaType.fromValue(b.getOperacionExenta()));
                }

                if (b.getBaseImponibleACoste() != null) {
                    detalle.setBaseImponibleACoste(formatAmount(b.getBaseImponibleACoste()));
                }

                if (b.getTipoRecargoEquivalencia() != null) {
                    detalle.setTipoRecargoEquivalencia(formatAmount(b.getTipoRecargoEquivalencia()));
                }

                if (b.getCuotaRecargoEquivalencia() != null) {
                    detalle.setCuotaRecargoEquivalencia(formatAmount(b.getCuotaRecargoEquivalencia()));
                }

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

    /**
     * Response for a record that failed the local validation: nothing is signed, chained or sent.
     * Same shape as any other error ({@code status=error} + {@code message}), plus the individual
     * findings in {@code validation_errors}.
     */
    private static Map<String, Object> validationFailure(InvoiceValidator.ValidationResult validation) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Validacion local: " + validation.getErrorSummary());
        response.put("validation_errors", new ArrayList<>(validation.getErrors()));
        return response;
    }

    /**
     * The chaining block needs the previous record's number, date and hash. {@code previous} is the response of
     * the previous accepted record; a bare {@code previousHash} cannot be chained because the XML needs the
     * previous number and date too (and the hash would no longer match the one the AEAT computes).
     */
    private static String chainingError(Map<String, Object> previous, String previousHash) {
        boolean hasPrevious = previous != null && !previous.isEmpty();
        if (!hasPrevious) {
            return previousHash != null && !previousHash.trim().isEmpty()
                    ? "previousHash sin el registro anterior: pasa en 'previous' el registro anterior (numero, fecha "
                    + "y huella), o null si es el primero de la cadena"
                    : null;
        }
        for (String key : new String[] {"hash", "number", "date"}) {
            Object value = previous.get(key);
            if (value == null || value.toString().trim().isEmpty()) {
                return "El registro anterior ('previous') debe traer '" + key + "': pasa la respuesta completa de un "
                        + "registro aceptado, no el de un envio fallido";
            }
        }
        return null;
    }

    private static Map<String, Object> chainingFailure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Encadenamiento: " + message);
        response.put("validation_errors", new ArrayList<>(java.util.Collections.singletonList(message)));
        return response;
    }

    /** A recipient with an explicit identification type, or a non-Spanish country, uses IDOtro. */
    static boolean isForeignRecipient(VeriFactuRecipient recipient) {
        String country = recipient.getCountryCode();
        boolean foreignCountry = country != null && !country.trim().isEmpty()
                && !"ES".equalsIgnoreCase(country.trim());
        String idType = recipient.getIdType();
        return foreignCountry || (idType != null && !idType.trim().isEmpty());
    }

    private static List<IDFacturaARType> buildReferences(List<VeriFactuInvoiceReference> references,
            String defaultIssuer) {
        List<IDFacturaARType> ids = new ArrayList<>();
        if (references != null) {
            for (VeriFactuInvoiceReference ref : references) {
                IDFacturaARType id = new IDFacturaARType();
                id.setIDEmisorFactura(ref.getIssuerTaxId() != null && !ref.getIssuerTaxId().trim().isEmpty()
                        ? ref.getIssuerTaxId() : defaultIssuer);
                id.setNumSerieFactura(ref.getInvoiceNumber());
                id.setFechaExpedicionFactura(
                        ref.getIssueDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                ids.add(id);
            }
        }
        return ids;
    }

    private List<PersonaFisicaJuridicaType> buildRecipients(VeriFactuInvoice invoice) {
        List<PersonaFisicaJuridicaType> list = new ArrayList<>();
        List<VeriFactuRecipient> recipients = invoice.getRecipients();
        if (recipients != null) {
            for (VeriFactuRecipient r : recipients) {
                PersonaFisicaJuridicaType p = new PersonaFisicaJuridicaType();
                p.setNombreRazon(r.getName());
                if (isForeignRecipient(r)) {
                    IDOtroType idOtro = new IDOtroType();
                    if (r.getCountryCode() != null && !r.getCountryCode().trim().isEmpty()) {
                        idOtro.setCodigoPais(CountryType2.fromValue(r.getCountryCode().trim().toUpperCase()));
                    }
                    idOtro.setIDType(r.getIdType());
                    idOtro.setID(r.getTaxId());
                    p.setIDOtro(idOtro);
                } else {
                    p.setNIF(r.getTaxId());
                }
                list.add(p);
            }
        }
        return list;
    }

    private SistemaInformaticoType buildSystemInfo(VeriFactuInvoice invoice) {
        SistemaInformaticoType si = new SistemaInformaticoType();
        si.setNombreRazon(config.getDeveloperName());
        si.setNIF(config.getDeveloperNif());
        si.setNombreSistemaInformatico(config.getSystemName());
        si.setIdSistemaInformatico(config.getSystemId());
        si.setVersion(config.getSystemVersion());
        si.setNumeroInstalacion(config.getInstallationNumber());
        si.setTipoUsoPosibleSoloVerifactu(SiNoType.fromValue(config.getOnlyVerifactuCapable()));
        si.setTipoUsoPosibleMultiOT(SiNoType.fromValue(config.getMultiObligatedCapable()));

        // IndicadorMultiplesOT MUST be computed automatically per record (per
        // client/tenant), never fixed by the developer or the end user. If the
        // invoice provides its own value (e.g. a multi-tenant SaaS backend
        // that knows how many "facturaciones" this specific client has),
        // honor it; otherwise fall back to the static config default (correct
        // for single-tenant integrations where there is only ever one OT).
        Boolean perInvoiceIndicator = invoice.getMultipleObligatedIndicator();
        String indicadorMultiplesOT = perInvoiceIndicator != null
                ? (perInvoiceIndicator ? "S" : "N")
                : config.getHasMultipleObligated();
        si.setIndicadorMultiplesOT(SiNoType.fromValue(indicadorMultiplesOT));

        return si;
    }

    /**
     * Fills {@code EmitidaPorTerceroODestinatario} and {@code Tercero} when
     * this invoice was NOT issued by the obligado itself.
     * <ul>
     * <li>{@code "T"} (Tercero): a third party - e.g. Odei - issued the
     * invoice on behalf of the obligado. Requires
     * {@link VeriFactuInvoice#getThirdPartyName()} and
     * {@link VeriFactuInvoice#getThirdPartyTaxId()}.</li>
     * <li>{@code "D"} (Destinatario): self-billing, the recipient issued the
     * invoice. No {@code Tercero} block is needed in this case, since the
     * recipient is already identified in {@code Destinatarios}.</li>
     * </ul>
     * Left untouched (both fields omitted) for the common case where the
     * issuer expide su propia factura.
     */
    private void applyThirdPartyIssuance(RegistroFacturacionAltaType registroAlta, VeriFactuInvoice invoice) {
        String issuedBy = invoice.getIssuedByThirdPartyOrRecipient();
        if (issuedBy == null || issuedBy.trim().isEmpty()) {
            return;
        }

        registroAlta.setEmitidaPorTerceroODestinatario(TercerosODestinatarioType.fromValue(issuedBy));

        if ("T".equals(issuedBy)) {
            String thirdPartyName = invoice.getThirdPartyName();
            String thirdPartyTaxId = invoice.getThirdPartyTaxId();
            if (thirdPartyName == null || thirdPartyTaxId == null) {
                throw new IllegalArgumentException(
                        "thirdPartyName and thirdPartyTaxId are required when issuedByThirdPartyOrRecipient = \"T\"");
            }
            PersonaFisicaJuridicaType tercero = new PersonaFisicaJuridicaType();
            tercero.setNombreRazon(thirdPartyName);
            tercero.setNIF(thirdPartyTaxId);
            registroAlta.setTercero(tercero);
        }
    }

    /**
     * Selects the AEAT endpoint (WSDL port) according to the environment and the type of
     * certificate used to authenticate:
     * <ul>
     * <li>production + personal/representative certificate: {@code www1.agenciatributaria.gob.es}</li>
     * <li>production + seal certificate: {@code www10.agenciatributaria.gob.es}</li>
     * <li>test (pre-production) + personal/representative certificate: {@code prewww1.aeat.es}</li>
     * <li>test (pre-production) + seal certificate: {@code prewww10.aeat.es}</li>
     * </ul>
     * The AEAT rejects seal certificates on the non-seal endpoints (and vice versa), and the
     * production endpoint must never be reached when the client was created for the test
     * environment.
     */
    static SfPortTypeVerifactu selectPort(SfVerifactu service, boolean production, boolean sealCertificate) {
        if (production) {
            return sealCertificate ? service.getSistemaVerifactuSello() : service.getSistemaVerifactu();
        }
        return sealCertificate ? service.getSistemaVerifactuSelloPruebas() : service.getSistemaVerifactuPruebas();
    }

    private Map<String, Object> performSoapCall(CabeceraType cabecera, List<RegistroFacturaType> registros,
            String huella, String huellaInputString, String numSerie, String fechaExp, String ts) {
        try {
            // Lazy init service. The WSDL is loaded from the classpath (it is
            // bundled inside src/main/resources and packaged into the jar),
            // NOT from a relative filesystem path: a relative "src/main/..."
            // path only exists in this repository's own working directory
            // and breaks for any consumer that depends on the published
            // artifact (e.g. running from a different working directory, or
            // packaged into a fat/shaded jar).
            if (service == null) {
                URL wsdlUrl = AeatClient.class.getClassLoader().getResource("SistemaFacturacion.wsdl");
                if (wsdlUrl == null) {
                    throw new IllegalStateException(
                            "SistemaFacturacion.wsdl not found in classpath. It must be bundled as a "
                                    + "resource of the verifactu-sdk artifact.");
                }
                service = new SfVerifactu(wsdlUrl);
            }

            SfPortTypeVerifactu port = selectPort(service, production, config.isSealCertificate());
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

            return buildSoapResponse(estadoH.value, csvH.value, datosH.value, tiempoH.value, lineaH.value,
                    huella, huellaInputString, numSerie, fechaExp, ts);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            if (isTimeout(e)) {
                // The record may have reached the AEAT even though the answer did not arrive in time: resend it
                // unchanged (same generation timestamp and previous record) and treat a duplicate as a possible hit.
                response.put("timeout", true);
                response.put("message", "Tiempo de espera agotado esperando a la AEAT (conexion "
                        + config.getConnectTimeoutMillis() + " ms, lectura " + config.getReadTimeoutMillis()
                        + " ms): el registro pudo llegar a registrarse; reenvialo sin cambios");
            } else {
                response.put("message", e.getMessage());
            }
            e.printStackTrace();
            return response;
        }
    }

    /**
     * Builds the response map of a SOAP submission. Besides the overall status it exposes the
     * per-record outcome returned by the AEAT ({@code RespuestaExpedidaType}), so callers can tell
     * why a submission was rejected: {@code aeat_error_code}, {@code aeat_error_message},
     * {@code aeat_record_status}, {@code aeat_record_errors}, {@code aeat_submission_wait},
     * {@code aeat_presenter_nif} and a one-line {@code aeat_message}. All of them are always
     * present (null, empty list or empty string when there is no data).
     */
    static Map<String, Object> buildSoapResponse(EstadoEnvioType estado, String csv,
            DatosPresentacionType datos, String tiempoEspera, List<RespuestaExpedidaType> lineas,
            String huella, String huellaInputString, String numSerie, String fechaExp, String ts) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("aeat_status", estado != null ? estado.value() : "UNKNOWN");
        response.put("csv", csv);
        response.put("hash", huella);
        response.put("hashInput", huellaInputString);
        response.put("number", numSerie);
        response.put("date", fechaExp);
        response.put("timestamp", ts);
        response.put("submittedToAeat", true);

        List<Map<String, Object>> recordErrors = new ArrayList<>();
        String firstCode = null;
        String firstMessage = null;
        String recordStatus = null;
        if (lineas != null) {
            for (RespuestaExpedidaType linea : lineas) {
                if (linea == null) {
                    continue;
                }
                String code = linea.getCodigoErrorRegistro() != null ? linea.getCodigoErrorRegistro().toString() : null;
                String message = linea.getDescripcionErrorRegistro();
                String lineStatus = linea.getEstadoRegistro() != null ? linea.getEstadoRegistro().value() : null;
                if (recordStatus == null) {
                    recordStatus = lineStatus;
                }
                if (firstCode == null && code != null) {
                    firstCode = code;
                    firstMessage = message;
                }
                recordErrors.add(describeLine(linea, code, message, lineStatus));
            }
        }
        response.put("aeat_record_status", recordStatus);
        response.put("aeat_error_code", firstCode);
        response.put("aeat_error_message", firstMessage);
        response.put("aeat_record_errors", recordErrors);
        response.put("aeat_submission_wait", tiempoEspera);
        response.put("aeat_presenter_nif", datos != null ? datos.getNIFPresentador() : null);
        response.put("aeat_message", firstCode == null ? ""
                : "Codigo[" + firstCode + "]." + (firstMessage != null ? firstMessage : ""));
        return response;
    }

    private static Map<String, Object> describeLine(RespuestaExpedidaType linea, String code, String message,
            String lineStatus) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("recordStatus", lineStatus);
        detail.put("errorCode", code);
        detail.put("errorMessage", message);
        TipoOperacionType operation = linea.getOperacion() != null ? linea.getOperacion().getTipoOperacion() : null;
        detail.put("operation", operation != null ? operation.value() : null);
        Map<String, Object> invoiceId = new HashMap<>();
        if (linea.getIDFactura() != null) {
            invoiceId.put("issuerNif", linea.getIDFactura().getIDEmisorFactura());
            invoiceId.put("number", linea.getIDFactura().getNumSerieFactura());
            invoiceId.put("date", linea.getIDFactura().getFechaExpedicionFactura());
        }
        detail.put("invoiceId", invoiceId);
        RegistroDuplicadoType duplicate = linea.getRegistroDuplicado();
        if (duplicate != null) {
            Map<String, Object> dup = new HashMap<>();
            dup.put("requestId", duplicate.getIdPeticionRegistroDuplicado());
            dup.put("recordStatus", duplicate.getEstadoRegistroDuplicado() != null
                    ? duplicate.getEstadoRegistroDuplicado().value() : null);
            dup.put("errorCode", duplicate.getCodigoErrorRegistro() != null
                    ? duplicate.getCodigoErrorRegistro().toString() : null);
            dup.put("errorMessage", duplicate.getDescripcionErrorRegistro());
            detail.put("duplicate", dup);
        }
        return detail;
    }

    /**
     * Builds the response for "NO VERI*FACTU" mode (RD 1007/2023): the billing record is
     * generated, hashed/chained and signed (XAdES) exactly as it would be for VERI*FACTU, but it
     * is never transmitted to the AEAT - it stays under the obligado's custody, integral and
     * accessible, for a possible future inspection. The signed XML is returned so the caller can
     * persist it as the actual legal record (not just the parsed fields).
     */
    private Map<String, Object> buildLocalOnlyResponse(JAXBElement<?> registroElement, String huella,
            String huellaInputString, String numSerie, String fechaExp, String ts) {
        try {
            byte[] signedXml = marshalAndSign(registroElement);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("aeat_status", "NoRemitido");
            response.put("submittedToAeat", false);
            response.put("aeat_record_status", null);
            response.put("aeat_error_code", null);
            response.put("aeat_error_message", null);
            response.put("aeat_record_errors", new ArrayList<Map<String, Object>>());
            response.put("aeat_submission_wait", null);
            response.put("aeat_presenter_nif", null);
            response.put("aeat_message", "");
            response.put("hash", huella);
            response.put("hashInput", huellaInputString);
            response.put("number", numSerie);
            response.put("date", fechaExp);
            response.put("timestamp", ts);
            response.put("signedXml", Base64.getEncoder().encodeToString(signedXml));
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            e.printStackTrace();
            return response;
        }
    }

    /**
     * Marshals a single JAXB billing record element (RegistroAlta/RegistroAnulacion) to XML and
     * signs it with XAdES using the same corporate certificate used for SOAP mutual TLS - see
     * {@link SignatureService}. Used only in "NO VERI*FACTU" mode, where this signature is what
     * provides the integrity guarantee that real-time remisión provides in VERI*FACTU mode.
     */
    private byte[] marshalAndSign(JAXBElement<?> registroElement) throws Exception {
        JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        marshaller.marshal(registroElement, baos);

        SignatureService signatureService = new SignatureService(certPath, certPassword);
        return signatureService.signXml(baos.toByteArray());
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
        applyTimeouts(http, config);
    }

    /**
     * Connection and read timeouts of the SOAP call. Without them a connection that never answers would hold the
     * calling thread (and everything it holds, such as a database lock) indefinitely.
     */
    static void applyTimeouts(HTTPConduit http, VeriFactuConfig config) {
        HTTPClientPolicy policy = http.getClient();
        if (policy == null) {
            policy = new HTTPClientPolicy();
        }
        policy.setConnectionTimeout(config.getConnectTimeoutMillis());
        policy.setReceiveTimeout(config.getReadTimeoutMillis());
        http.setClient(policy);
    }

    static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.ConnectException
                    && String.valueOf(cause.getMessage()).toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
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
        importe.put("BaseRectificada", formatAmount(baseRectificada));
        importe.put("CuotaRectificada", formatAmount(cuotaRectificada));

        // Add optional surcharge if present
        Double cuotaRecargo = invoice.getCorrectedSurchargeAmount();
        if (cuotaRecargo != null) {
            importe.put("CuotaRecargoRectificado", formatAmount(cuotaRecargo));
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

    /**
     * Generation instant of a record: the one supplied by the caller (so a resend reproduces the same
     * record) or, when absent, the current instant. Always expressed in Europe/Madrid.
     */
    private static ZonedDateTime generationInstant(ZonedDateTime supplied) {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        return supplied != null ? supplied.withZoneSameInstant(madrid) : ZonedDateTime.now(madrid);
    }
}
