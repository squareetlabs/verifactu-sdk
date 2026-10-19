package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.aeat.events.DatosPropiosEventoType;
import com.squareetlabs.verifactu.aeat.events.DeteccionAnomaliasRegEventoType;
import com.squareetlabs.verifactu.aeat.events.DeteccionAnomaliasRegFacturacionType;
import com.squareetlabs.verifactu.aeat.events.EncadenamientoType;
import com.squareetlabs.verifactu.aeat.events.EventoType;
import com.squareetlabs.verifactu.aeat.events.ExportacionRegEventoPeriodoType;
import com.squareetlabs.verifactu.aeat.events.ExportacionRegFacturacionPeriodoType;
import com.squareetlabs.verifactu.aeat.events.IDFacturaExpedidaHuellaType;
import com.squareetlabs.verifactu.aeat.events.IDFacturaExpedidaType;
import com.squareetlabs.verifactu.aeat.events.LanzamientoProcesoDeteccionAnomaliasRegEventoType;
import com.squareetlabs.verifactu.aeat.events.LanzamientoProcesoDeteccionAnomaliasRegFacturacionType;
import com.squareetlabs.verifactu.aeat.events.PersonaFisicaJuridicaESType;
import com.squareetlabs.verifactu.aeat.events.RegEventoAntType;
import com.squareetlabs.verifactu.aeat.events.RegEventoType;
import com.squareetlabs.verifactu.aeat.events.RegistroEvento;
import com.squareetlabs.verifactu.aeat.events.ResumenEventosType;
import com.squareetlabs.verifactu.aeat.events.SiNoType;
import com.squareetlabs.verifactu.aeat.events.SistemaInformaticoType;
import com.squareetlabs.verifactu.aeat.events.TipoEventoAgrType;
import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.models.Event;
import com.squareetlabs.verifactu.models.EventType;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the event records ("registros de evento") of the event log that a system in "NO VERI*FACTU" mode must
 * keep: each event is built as a {@code RegistroEvento} XML, chained to the previous one with its hash, and signed
 * (XAdES enveloped). Nothing is sent to the AEAT.
 * <p>
 * The software system is described by the {@link VeriFactuConfig} (developer, system id, version and installation
 * number), the same data the billing records carry.
 */
public class EventRecordService {

    private static final DateTimeFormatter EVENT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter AEAT_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String SCHEMA_VERSION = "1.0";
    private static final String HASH_TYPE_SHA256 = "01";
    private static final int MAX_OTHER_DATA = 100;
    private static final int MAX_SUMMARY_TYPES = 20;
    private static final String PLACE_AFTER_EVENT_HASH =
            "/*[local-name()='RegistroEvento']/*[local-name()='Evento']/*[local-name()='HuellaEvento']";

    private final String certPath;
    private final String certPassword;
    private final VeriFactuConfig config;

    public EventRecordService(String certPath, String certPassword, VeriFactuConfig config) {
        this.certPath = certPath;
        this.certPassword = certPassword;
        this.config = config;
    }

    /**
     * Builds, chains and signs the event record.
     *
     * @return on success {@code status = success}, the {@code hash} and {@code hashInput} (store both to verify the
     * chain later), the {@code signedXml} (base64), the {@code generated_at} date used in the hash and the
     * {@code event_type} code; on failure {@code status = error} with a {@code message} and the
     * {@code validation_errors} when the event is invalid.
     */
    public Map<String, Object> generate(Event event) {
        List<String> errors = validate(event);
        if (!errors.isEmpty()) {
            return error("Event record is not valid: " + String.join("; ", errors), errors);
        }
        try {
            // The supplied instant with its own offset, as 1.5.0 did: an event regenerated from its hash input (see
            // HashHelper.generatedAtOf) gives the same text and the same hash. Event's default is already in Madrid.
            ZonedDateTime generatedAt = event.getGeneratedAt().truncatedTo(ChronoUnit.SECONDS);
            String generatedAtText = generatedAt.format(EVENT_TIMESTAMP);
            String previousHash = event.getPrevious() != null ? event.getPrevious().getHash() : null;

            Map<String, String> hash = HashHelper.generateEventHash(config.getDeveloperNif(), config.getSystemId(),
                    config.getSystemVersion(), config.getInstallationNumber(), event.getObligorTaxId(),
                    event.getType().getCode(), previousHash, generatedAtText);

            RegistroEvento record = build(event, generatedAt, hash.get("hash"));
            byte[] signedXml = new SignatureService(certPath, certPassword).signXml(marshal(record), PLACE_AFTER_EVENT_HASH);
            List<String> schemaErrors = schemaErrors(signedXml);
            if (!schemaErrors.isEmpty()) {
                // Never hand back a record that the official schema rejects.
                return error("Event record is not valid against EventosSIF.xsd: " + String.join("; ", schemaErrors), schemaErrors);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("hash", hash.get("hash"));
            response.put("hashInput", hash.get("inputString"));
            response.put("signedXml", Base64.getEncoder().encodeToString(signedXml));
            response.put("generated_at", generatedAtText);
            response.put("event_type", event.getType().getCode());
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "The event record could not be generated: " + e.getMessage());
            return response;
        }
    }

    private static Map<String, Object> error(String message, List<String> validationErrors) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("validation_errors", validationErrors);
        return response;
    }

    /** Validates the signed record against the official schema, which the SDK packages. */
    private static List<String> schemaErrors(byte[] xml) {
        List<String> errors = new ArrayList<>();
        try {
            URL xsd = EventRecordService.class.getClassLoader().getResource("EventosSIF.xsd");
            Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(xsd);
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    List<String> validate(Event event) {
        List<String> errors = new ArrayList<>();
        if (event == null || event.getType() == null) {
            errors.add("The event type is required");
            return errors;
        }
        if (isBlank(event.getObligorTaxId())) {
            errors.add("The tax id of the obligor is required");
        }
        if (isBlank(event.getObligorName())) {
            errors.add("The name of the obligor is required");
        }
        if (event.getGeneratedAt() == null) {
            errors.add("The generation date is required");
        }
        if (tooLong(event.getOtherData(), MAX_OTHER_DATA)) {
            errors.add("The other data of the event are longer than " + MAX_OTHER_DATA + " characters");
        }
        if (isBlank(config.getDeveloperName())) {
            errors.add("The developer name is required");
        }
        if (isBlank(config.getDeveloperNif()) || isBlank(config.getSystemId()) || isBlank(config.getSystemVersion())
                || isBlank(config.getInstallationNumber())) {
            errors.add("The developer tax id, the system id, the version and the installation number are required");
        }
        Event.Reference previous = event.getPrevious();
        if (previous != null && (previous.getType() == null || previous.getGeneratedAt() == null
                || isBlank(previous.getHash()))) {
            errors.add("The previous event needs its type, generation date and hash");
        }
        Class<?> expected = detailsClassOf(event.getType());
        Object details = event.getDetails();
        if (expected == null && details != null) {
            errors.add("An event of type " + event.getType().getCode() + " carries no specific data");
        } else if (expected != null && details != null && !expected.isInstance(details)) {
            errors.add("An event of type " + event.getType().getCode() + " carries " + expected.getSimpleName());
        }
        if (details instanceof Event.AnomalyDetection) {
            Event.AnomalyDetection anomaly = (Event.AnomalyDetection) details;
            if (anomaly.getAnomalyType() == null) {
                errors.add("The anomaly type is required");
            }
            if (tooLong(anomaly.getOtherData(), MAX_OTHER_DATA)) {
                errors.add("The other data of the anomaly are longer than " + MAX_OTHER_DATA + " characters");
            }
        }
        if (details instanceof Event.Summary && ((Event.Summary) details).getCounts().size() > MAX_SUMMARY_TYPES) {
            errors.add("A summary counts up to " + MAX_SUMMARY_TYPES + " event types");
        }
        return errors;
    }

    private static Class<?> detailsClassOf(EventType type) {
        switch (type) {
            case BILLING_ANOMALY_CHECK_LAUNCHED:
            case EVENT_ANOMALY_CHECK_LAUNCHED:
                return Event.ProcessLaunch.class;
            case BILLING_ANOMALY_DETECTED:
            case EVENT_ANOMALY_DETECTED:
                return Event.AnomalyDetection.class;
            case BILLING_RECORDS_EXPORTED:
                return Event.BillingExport.class;
            case EVENT_RECORDS_EXPORTED:
                return Event.EventExport.class;
            case EVENTS_SUMMARY:
                return Event.Summary.class;
            default:
                return null;
        }
    }

    private RegistroEvento build(Event event, ZonedDateTime generatedAt, String hash) throws Exception {
        EventoType evento = new EventoType();
        evento.setSistemaInformatico(buildSystemInfo());

        PersonaFisicaJuridicaESType obligor = new PersonaFisicaJuridicaESType();
        obligor.setNombreRazon(event.getObligorName().trim());
        obligor.setNIF(event.getObligorTaxId().trim());
        evento.setObligadoEmision(obligor);

        evento.setFechaHoraHusoGenEvento(calendar(generatedAt));
        evento.setTipoEvento(event.getType().getCode());
        evento.setDatosPropiosEvento(buildDetails(event));
        evento.setOtrosDatosEvento(trimOrNull(event.getOtherData()));

        EncadenamientoType chaining = new EncadenamientoType();
        if (event.getPrevious() == null) {
            chaining.setPrimerEvento("S");
        } else {
            RegEventoAntType previous = new RegEventoAntType();
            previous.setTipoEvento(event.getPrevious().getType().getCode());
            previous.setFechaHoraHusoGenEvento(calendar(event.getPrevious().getGeneratedAt()));
            previous.setHuellaEvento(event.getPrevious().getHash().trim());
            chaining.setEventoAnterior(previous);
        }
        evento.setEncadenamiento(chaining);
        evento.setTipoHuella(HASH_TYPE_SHA256);
        evento.setHuellaEvento(hash);

        RegistroEvento record = new RegistroEvento();
        record.setIDVersion(SCHEMA_VERSION);
        record.setEvento(evento);
        return record;
    }

    private SistemaInformaticoType buildSystemInfo() {
        SistemaInformaticoType system = new SistemaInformaticoType();
        system.setNombreRazon(config.getDeveloperName());
        system.setNIF(config.getDeveloperNif());
        system.setNombreSistemaInformatico(config.getSystemName());
        system.setIdSistemaInformatico(config.getSystemId());
        system.setVersion(config.getSystemVersion());
        system.setNumeroInstalacion(config.getInstallationNumber());
        system.setTipoUsoPosibleSoloVerifactu(SiNoType.fromValue(config.getOnlyVerifactuCapable()));
        system.setTipoUsoPosibleMultiOT(SiNoType.fromValue(config.getMultiObligatedCapable()));
        system.setIndicadorMultiplesOT(SiNoType.fromValue(config.getHasMultipleObligated()));
        return system;
    }

    private DatosPropiosEventoType buildDetails(Event event) throws Exception {
        Object details = event.getDetails();
        if (details == null) {
            return null;
        }
        DatosPropiosEventoType data = new DatosPropiosEventoType();
        switch (event.getType()) {
            case BILLING_ANOMALY_CHECK_LAUNCHED: {
                Event.ProcessLaunch launch = (Event.ProcessLaunch) details;
                LanzamientoProcesoDeteccionAnomaliasRegFacturacionType type =
                        new LanzamientoProcesoDeteccionAnomaliasRegFacturacionType();
                type.setRealizadoProcesoSobreIntegridadHuellasRegFacturacion(yesNo(launch.getHashes()));
                type.setNumeroDeRegistrosFacturacionProcesadosSobreIntegridadHuellas(processed(launch.getHashes()));
                type.setRealizadoProcesoSobreIntegridadFirmasRegFacturacion(yesNo(launch.getSignatures()));
                type.setNumeroDeRegistrosFacturacionProcesadosSobreIntegridadFirmas(processed(launch.getSignatures()));
                type.setRealizadoProcesoSobreTrazabilidadCadenaRegFacturacion(yesNo(launch.getChain()));
                type.setNumeroDeRegistrosFacturacionProcesadosSobreTrazabilidadCadena(processed(launch.getChain()));
                type.setRealizadoProcesoSobreTrazabilidadFechasRegFacturacion(yesNo(launch.getDates()));
                type.setNumeroDeRegistrosFacturacionProcesadosSobreTrazabilidadFechas(processed(launch.getDates()));
                data.setLanzamientoProcesoDeteccionAnomaliasRegFacturacion(type);
                break;
            }
            case EVENT_ANOMALY_CHECK_LAUNCHED: {
                Event.ProcessLaunch launch = (Event.ProcessLaunch) details;
                LanzamientoProcesoDeteccionAnomaliasRegEventoType type =
                        new LanzamientoProcesoDeteccionAnomaliasRegEventoType();
                type.setRealizadoProcesoSobreIntegridadHuellasRegEvento(yesNo(launch.getHashes()));
                type.setNumeroDeRegistrosEventoProcesadosSobreIntegridadHuellas(processed(launch.getHashes()));
                type.setRealizadoProcesoSobreIntegridadFirmasRegEvento(yesNo(launch.getSignatures()));
                type.setNumeroDeRegistrosEventoProcesadosSobreIntegridadFirmas(processed(launch.getSignatures()));
                type.setRealizadoProcesoSobreTrazabilidadCadenaRegEvento(yesNo(launch.getChain()));
                type.setNumeroDeRegistrosEventoProcesadosSobreTrazabilidadCadena(processed(launch.getChain()));
                type.setRealizadoProcesoSobreTrazabilidadFechasRegEvento(yesNo(launch.getDates()));
                type.setNumeroDeRegistrosEventoProcesadosSobreTrazabilidadFechas(processed(launch.getDates()));
                data.setLanzamientoProcesoDeteccionAnomaliasRegEvento(type);
                break;
            }
            case BILLING_ANOMALY_DETECTED: {
                Event.AnomalyDetection anomaly = (Event.AnomalyDetection) details;
                DeteccionAnomaliasRegFacturacionType type = new DeteccionAnomaliasRegFacturacionType();
                type.setTipoAnomalia(anomaly.getAnomalyType().getCode());
                type.setOtrosDatosAnomalia(trimOrNull(anomaly.getOtherData()));
                if (anomaly.getInvoice() != null) {
                    type.setRegistroFacturacionAnomalo(invoiceId(anomaly.getInvoice()));
                }
                data.setDeteccionAnomaliasRegFacturacion(type);
                break;
            }
            case EVENT_ANOMALY_DETECTED: {
                Event.AnomalyDetection anomaly = (Event.AnomalyDetection) details;
                DeteccionAnomaliasRegEventoType type = new DeteccionAnomaliasRegEventoType();
                type.setTipoAnomalia(anomaly.getAnomalyType().getCode());
                type.setOtrosDatosAnomalia(trimOrNull(anomaly.getOtherData()));
                if (anomaly.getEvent() != null) {
                    type.setRegEventoAnomalo(eventRef(anomaly.getEvent()));
                }
                data.setDeteccionAnomaliasRegEvento(type);
                break;
            }
            case BILLING_RECORDS_EXPORTED: {
                Event.BillingExport export = (Event.BillingExport) details;
                ExportacionRegFacturacionPeriodoType type = new ExportacionRegFacturacionPeriodoType();
                type.setFechaHoraHusoInicioPeriodoExport(calendar(export.getFrom()));
                type.setFechaHoraHusoFinPeriodoExport(calendar(export.getTo()));
                type.setRegistroFacturacionInicialPeriodo(invoiceIdWithHash(export.getFirst()));
                type.setRegistroFacturacionFinalPeriodo(invoiceIdWithHash(export.getLast()));
                type.setNumeroDeRegistrosFacturacionAltaExportados(String.valueOf(export.getRegistrations()));
                type.setSumaCuotaTotalAlta(export.getTotalTax());
                type.setSumaImporteTotalAlta(export.getTotalAmount());
                type.setNumeroDeRegistrosFacturacionAnulacionExportados(String.valueOf(export.getAnnulments()));
                type.setRegistrosFacturacionExportadosDejanDeConservarse(yesNo(export.isNoLongerKept()));
                data.setExportacionRegFacturacionPeriodo(type);
                break;
            }
            case EVENT_RECORDS_EXPORTED: {
                Event.EventExport export = (Event.EventExport) details;
                ExportacionRegEventoPeriodoType type = new ExportacionRegEventoPeriodoType();
                type.setFechaHoraHusoInicioPeriodoExport(calendar(export.getFrom()));
                type.setFechaHoraHusoFinPeriodoExport(calendar(export.getTo()));
                type.setRegistroEventoInicialPeriodo(eventRef(export.getFirst()));
                type.setRegistroEventoFinalPeriodo(eventRef(export.getLast()));
                type.setNumeroDeRegEventoExportados(String.valueOf(export.getEvents()));
                type.setRegEventoExportadosDejanDeConservarse(yesNo(export.isNoLongerKept()));
                data.setExportacionRegEventoPeriodo(type);
                break;
            }
            case EVENTS_SUMMARY: {
                Event.Summary summary = (Event.Summary) details;
                ResumenEventosType type = new ResumenEventosType();
                for (Event.EventCount count : summary.getCounts()) {
                    TipoEventoAgrType group = new TipoEventoAgrType();
                    group.setTipoEvento(count.getType().getCode());
                    group.setNumeroDeEventos(String.valueOf(count.getNumber()));
                    type.getTipoEvento().add(group);
                }
                if (summary.getFirst() != null) {
                    type.setRegistroFacturacionInicialPeriodo(invoiceIdWithHash(summary.getFirst()));
                }
                if (summary.getLast() != null) {
                    type.setRegistroFacturacionFinalPeriodo(invoiceIdWithHash(summary.getLast()));
                }
                type.setNumeroDeRegistrosFacturacionAltaGenerados(String.valueOf(summary.getRegistrations()));
                type.setSumaCuotaTotalAlta(summary.getTotalTax());
                type.setSumaImporteTotalAlta(summary.getTotalAmount());
                type.setNumeroDeRegistrosFacturacionAnulacionGenerados(String.valueOf(summary.getAnnulments()));
                data.setResumenEventos(type);
                break;
            }
            default:
                return null;
        }
        return data;
    }

    private static IDFacturaExpedidaType invoiceId(Event.InvoiceReference reference) {
        IDFacturaExpedidaType id = new IDFacturaExpedidaType();
        id.setIDEmisorFactura(reference.getIssuerTaxId());
        id.setNumSerieFactura(reference.getNumber());
        id.setFechaExpedicionFactura(date(reference.getDate()));
        return id;
    }

    private static IDFacturaExpedidaHuellaType invoiceIdWithHash(Event.InvoiceReference reference) {
        IDFacturaExpedidaHuellaType id = new IDFacturaExpedidaHuellaType();
        id.setIDEmisorFactura(reference.getIssuerTaxId());
        id.setNumSerieFactura(reference.getNumber());
        id.setFechaExpedicionFactura(date(reference.getDate()));
        id.setHuella(reference.getHash());
        return id;
    }

    private static RegEventoType eventRef(Event.Reference reference) throws Exception {
        RegEventoType ref = new RegEventoType();
        ref.setTipoEvento(reference.getType().getCode());
        ref.setFechaHoraHusoEvento(calendar(reference.getGeneratedAt()));
        ref.setHuellaEvento(reference.getHash());
        return ref;
    }

    private static String date(LocalDate date) {
        return date.format(AEAT_DATE);
    }

    private static SiNoType yesNo(Event.Check check) {
        return yesNo(check.isPerformed());
    }

    private static SiNoType yesNo(boolean value) {
        return value ? SiNoType.S : SiNoType.N;
    }

    private static String processed(Event.Check check) {
        return check.getProcessed() == null ? null : String.valueOf(check.getProcessed());
    }

    private static XMLGregorianCalendar calendar(ZonedDateTime time) throws Exception {
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(time.truncatedTo(ChronoUnit.SECONDS).format(EVENT_TIMESTAMP));
    }

    private static byte[] marshal(RegistroEvento record) throws Exception {
        Marshaller marshaller = JAXBContext.newInstance(RegistroEvento.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        marshaller.marshal(record, out);
        return out.toByteArray();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean tooLong(String value, int max) {
        return value != null && value.trim().length() > max;
    }

    private static String trimOrNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
