package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.models.AnomalyType;
import com.squareetlabs.verifactu.models.Event;
import com.squareetlabs.verifactu.models.EventType;
import com.squareetlabs.verifactu.services.EventRecordService;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The event record: its hash follows the AEAT specification, and the XML that {@link EventRecordService} generates is
 * valid against the official schema (EventosSIF.xsd), is chained, and carries a XAdES enveloped signature inside
 * {@code Evento}.
 */
public class EventRecordTest {

    private static final String PASSWORD = "testpass123";
    private static final String NS = "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/EventosSIF.xsd";
    private static final ZonedDateTime T0 = ZonedDateTime.of(2026, 10, 13, 10, 0, 0, 0, ZoneId.of("Europe/Madrid"));

    private static Schema schema;

    @BeforeClass
    public static void loadSchema() throws Exception {
        URL xsd = EventRecordTest.class.getClassLoader().getResource("EventosSIF.xsd");
        assertNotNull("EventosSIF.xsd is missing from the classpath", xsd);
        schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new StreamSource(xsd.toExternalForm()));
    }

    private EventRecordService service() {
        URL cert = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(cert);
        VeriFactuConfig config = TestConfig.of("Empresa Test SL", "B12345678");
        return new EventRecordService(cert.getPath(), PASSWORD, config);
    }

    private Event event(EventType type) {
        return new Event(type, "B12345678", "Empresa Test SL").setGeneratedAt(T0);
    }

    private static String xmlOf(Map<String, Object> response) {
        assertEquals(String.valueOf(response), "success", response.get("status"));
        return new String(Base64.getDecoder().decode((String) response.get("signedXml")), StandardCharsets.UTF_8);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static void assertValid(String xml) throws Exception {
        schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    // ─── Hash ───────────────────────────────────────────────────────────────────

    @Test
    public void hash_ofTheFirstEvent_followsTheAeatSpecification() {
        Map<String, String> hash = HashHelper.generateEventHash("B72877814", "JV", "1.0", "001", "B12345678", "01", null,
                "2026-10-13T10:00:00+02:00");

        assertEquals("NIF=B72877814&ID=&IdSistemaInformatico=JV&Version=1.0&NumeroInstalacion=001&NIF=B12345678"
                + "&TipoEvento=01&HuellaEvento=&FechaHoraHusoGenEvento=2026-10-13T10:00:00+02:00", hash.get("inputString"));
        // SHA-256 computed independently (Python hashlib) over that exact string.
        assertEquals("0171CCE08B3DE7E810A950F9E3FD022EEBF584D52A545722CD831499B8742C65", hash.get("hash"));
        assertTrue(HashHelper.verify(hash.get("hash"), hash.get("inputString")));
    }

    @Test
    public void hash_ofAChainedEvent_includesThePreviousHash() {
        Map<String, String> hash = HashHelper.generateEventHash("B72877814", "JV", "1.0", "001", "B12345678", "02", "AAAA",
                "2026-10-13T11:00:00+02:00");

        assertTrue(hash.get("inputString").contains("&TipoEvento=02&HuellaEvento=AAAA&FechaHoraHusoGenEvento="));
        assertEquals("96EEAC7E7B982768DCE234874A50192E47CE39975F2B6B20CE299E8FC9150E15", hash.get("hash"));
    }

    // ─── XML, chain and signature ───────────────────────────────────────────────

    @Test
    public void generate_firstEvent_isValidChainedAsFirstAndSigned() throws Exception {
        Map<String, Object> response = service().generate(event(EventType.NO_VERIFACTU_START));
        String xml = xmlOf(response);

        assertValid(xml);
        Document doc = parse(xml);
        assertEquals("S", text(doc, "PrimerEvento"));
        assertEquals("01", text(doc, "TipoEvento"));
        assertEquals("2026-10-13T10:00:00+02:00", text(doc, "FechaHoraHusoGenEvento"));
        assertEquals(response.get("hash"), text(doc, "HuellaEvento"));
        assertEquals("01", text(doc, "TipoHuella"));
        assertTrue(HashHelper.verify((String) response.get("hash"), (String) response.get("hashInput")));
        assertTrue(((String) response.get("hashInput")).contains("&TipoEvento=01&HuellaEvento=&FechaHoraHusoGenEvento="));
    }

    @Test
    public void generate_chainedEvent_carriesThePreviousEvent() throws Exception {
        Map<String, Object> first = service().generate(event(EventType.NO_VERIFACTU_START));
        Event second = event(EventType.NO_VERIFACTU_END).setGeneratedAt(T0.plusHours(1)).setPrevious(
                new Event.Reference(EventType.NO_VERIFACTU_START, T0, (String) first.get("hash")));

        Map<String, Object> response = service().generate(second);
        String xml = xmlOf(response);

        assertValid(xml);
        Document doc = parse(xml);
        Element previous = (Element) doc.getElementsByTagNameNS(NS, "EventoAnterior").item(0);
        assertNotNull(previous);
        assertEquals("01", text(previous, "TipoEvento"));
        assertEquals("2026-10-13T10:00:00+02:00", text(previous, "FechaHoraHusoGenEvento"));
        assertEquals(first.get("hash"), text(previous, "HuellaEvento"));
        assertEquals(0, doc.getElementsByTagNameNS(NS, "PrimerEvento").getLength());
        assertTrue(((String) response.get("hashInput")).contains("&HuellaEvento=" + first.get("hash") + "&"));
        assertFalse(response.get("hash").equals(first.get("hash")));
    }

    @Test
    public void generate_signature_isXadesEnvelopedLastChildOfEventoAndVerifies() throws Exception {
        String xml = xmlOf(service().generate(event(EventType.NO_VERIFACTU_START)));
        Document doc = parse(xml);

        Element evento = (Element) doc.getElementsByTagNameNS(NS, "Evento").item(0);
        Element lastChild = null;
        for (org.w3c.dom.Node node = evento.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                lastChild = (Element) node;
            }
        }
        assertEquals("Signature", lastChild.getLocalName());
        assertEquals(XMLSignature.XMLNS, lastChild.getNamespaceURI());
        assertEquals("The XAdES qualifying properties are part of the signature", 1,
                lastChild.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "QualifyingProperties").getLength());

        NodeList signatures = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertEquals(1, signatures.getLength());
        DOMValidateContext context = new DOMValidateContext(new SignatureKeySelector(), signatures.item(0));
        context.setIdAttributeNS((Element) doc.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "SignedProperties").item(0), null, "Id");
        assertTrue("The signature must verify", XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context));
    }

    @Test
    public void generate_tamperedEvent_failsTheSignature() throws Exception {
        String xml = xmlOf(service().generate(event(EventType.NO_VERIFACTU_START)));
        Document doc = parse(xml.replace("Empresa Test SL", "Otra Empresa SL"));

        NodeList signatures = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        DOMValidateContext context = new DOMValidateContext(new SignatureKeySelector(), signatures.item(0));
        context.setIdAttributeNS((Element) doc.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "SignedProperties").item(0), null, "Id");

        assertFalse("A changed record must not verify", XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context).validate(context));
    }

    // ─── Event types with their own data ────────────────────────────────────────

    @Test
    public void generate_billingAnomalyDetected_isValid() throws Exception {
        Event.AnomalyDetection anomaly = new Event.AnomalyDetection(AnomalyType.INTEGRITY_HASH).setOtherData("Huella no reproducible")
                .setInvoice(new Event.InvoiceReference("B12345678", "FT-1", LocalDate.of(2026, 10, 12), null));

        String xml = xmlOf(service().generate(event(EventType.BILLING_ANOMALY_DETECTED).setDetails(anomaly)));

        assertValid(xml);
        Document doc = parse(xml);
        assertEquals("01", text(doc, "TipoAnomalia"));
        assertEquals("FT-1", text(doc, "NumSerieFactura"));
        assertEquals("12-10-2026", text(doc, "FechaExpedicionFactura"));
    }

    @Test
    public void generate_eventAnomalyDetected_isValid() throws Exception {
        Event.AnomalyDetection anomaly = new Event.AnomalyDetection(AnomalyType.CHAIN_HASH_PREVIOUS_FIELD)
                .setEvent(new Event.Reference(EventType.NO_VERIFACTU_START, T0, repeat('A', 64)));

        assertValid(xmlOf(service().generate(event(EventType.EVENT_ANOMALY_DETECTED).setDetails(anomaly))));
    }

    @Test
    public void generate_processLaunches_areValid() throws Exception {
        Event.ProcessLaunch launch = new Event.ProcessLaunch(new Event.Check(true, 120), new Event.Check(false, null),
                new Event.Check(true, 120), new Event.Check(true, 120));

        assertValid(xmlOf(service().generate(event(EventType.BILLING_ANOMALY_CHECK_LAUNCHED).setDetails(launch))));
        assertValid(xmlOf(service().generate(event(EventType.EVENT_ANOMALY_CHECK_LAUNCHED).setDetails(launch))));
    }

    @Test
    public void generate_exports_areValid() throws Exception {
        Event.InvoiceReference first = new Event.InvoiceReference("B12345678", "FT-1", LocalDate.of(2026, 10, 1), repeat('B', 64));
        Event.InvoiceReference last = new Event.InvoiceReference("B12345678", "FT-9", LocalDate.of(2026, 10, 12), repeat('C', 64));
        Event.BillingExport billing = new Event.BillingExport(T0.minusDays(30), T0, first, last, 9, "189.00", "1089.00", 1, false);
        Event.Reference firstEvent = new Event.Reference(EventType.NO_VERIFACTU_START, T0.minusDays(30), repeat('D', 64));
        Event.Reference lastEvent = new Event.Reference(EventType.EVENTS_SUMMARY, T0.minusHours(1), repeat('E', 64));
        Event.EventExport events = new Event.EventExport(T0.minusDays(30), T0, firstEvent, lastEvent, 12, false);

        assertValid(xmlOf(service().generate(event(EventType.BILLING_RECORDS_EXPORTED).setDetails(billing))));
        assertValid(xmlOf(service().generate(event(EventType.EVENT_RECORDS_EXPORTED).setDetails(events))));
    }

    @Test
    public void generate_summary_isValid() throws Exception {
        Event.Summary summary = new Event.Summary(3, "63.00", "363.00", 0)
                .count(EventType.NO_VERIFACTU_START, 1).count(EventType.BILLING_ANOMALY_DETECTED, 2)
                .setBillingRange(new Event.InvoiceReference("B12345678", "FT-1", LocalDate.of(2026, 10, 1), repeat('B', 64)),
                        new Event.InvoiceReference("B12345678", "FT-3", LocalDate.of(2026, 10, 3), repeat('C', 64)));

        String xml = xmlOf(service().generate(event(EventType.EVENTS_SUMMARY).setDetails(summary).setOtherData("Resumen")));

        assertValid(xml);
        // The event's own TipoEvento, plus each of the two groups of the summary (a TipoEvento that holds a TipoEvento).
        assertEquals(5, parse(xml).getElementsByTagNameNS(NS, "TipoEvento").getLength());
    }

    // ─── Validation ─────────────────────────────────────────────────────────────

    private static void assertRejected(Map<String, Object> response, String fragment) {
        assertEquals(String.valueOf(response), "error", response.get("status"));
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) response.get("validation_errors");
        assertNotNull(String.valueOf(response), errors);
        assertTrue(errors.toString(), errors.toString().contains(fragment));
    }

    @Test
    public void generate_invalidEvents_areRejectedBeforeSigning() {
        assertRejected(service().generate(new Event(EventType.NO_VERIFACTU_START, " ", "Empresa")), "tax id of the obligor");
        assertRejected(service().generate(new Event(EventType.NO_VERIFACTU_START, "B12345678", null)), "name of the obligor");
        assertRejected(service().generate(event(EventType.NO_VERIFACTU_START).setOtherData(repeat('x', 101))), "longer than 100");
        assertRejected(service().generate(event(EventType.NO_VERIFACTU_START).setPrevious(
                new Event.Reference(EventType.NO_VERIFACTU_START, T0, " "))), "previous event");
        assertRejected(service().generate(event(EventType.NO_VERIFACTU_START)
                .setDetails(new Event.AnomalyDetection(AnomalyType.OTHER))), "carries no specific data");
        assertRejected(service().generate(event(EventType.BILLING_ANOMALY_DETECTED)
                .setDetails(new Event.Summary(0, "0.00", "0.00", 0))), "carries AnomalyDetection");
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static String text(org.w3c.dom.Node scope, String localName) {
        NodeList nodes = scope instanceof Document
                ? ((Document) scope).getElementsByTagNameNS(NS, localName)
                : ((Element) scope).getElementsByTagNameNS(NS, localName);
        assertTrue(localName + " is missing", nodes.getLength() > 0);
        return nodes.item(0).getTextContent();
    }

    /** Takes the signer's certificate from the signature itself, as a verifier of a stored record would. */
    private static final class SignatureKeySelector extends javax.xml.crypto.KeySelector {
        @Override
        public javax.xml.crypto.KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, javax.xml.crypto.AlgorithmMethod method,
                                        javax.xml.crypto.XMLCryptoContext context) {
            for (Object content : keyInfo.getContent()) {
                if (content instanceof X509Data) {
                    for (Object item : ((X509Data) content).getContent()) {
                        if (item instanceof X509Certificate) {
                            final java.security.PublicKey key = ((X509Certificate) item).getPublicKey();
                            return () -> key;
                        }
                    }
                }
            }
            return () -> null;
        }
    }
}
