package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceReference;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.models.Recipient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Valida contra los XSD oficiales de la AEAT (los mismos que empaqueta el SDK) el XML real que
 * genera {@link AeatClient}: formatos de fecha, decimales, longitudes y patrones. Un registro que
 * no valide aqui sera rechazado por la AEAT.
 */
public class AeatXsdValidationTest {

    private static Schema schema;

    @BeforeClass
    public static void loadSchema() throws Exception {
        URL xsd = AeatXsdValidationTest.class.getClassLoader().getResource("SuministroInformacion.xsd");
        assertNotNull("Falta SuministroInformacion.xsd en el classpath", xsd);
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schema = factory.newSchema(new StreamSource(xsd.toExternalForm()));
    }

    static String signedXml(Map<String, Object> response) {
        assertEquals(String.valueOf(response), "success", response.get("status"));
        return new String(Base64.getDecoder().decode((String) response.get("signedXml")), StandardCharsets.UTF_8);
    }

    static void assertValid(String label, String xml) {
        try {
            schema.newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            fail(label + " no valida contra el XSD de la AEAT (linea " + e.getLineNumber() + "): " + e.getMessage());
        } catch (Exception e) {
            fail(label + ": " + e);
        }
    }

    private AeatClient client() {
        URL cert = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(cert);
        return new AeatClient(cert.getPath(), "testpass123",
                com.squareetlabs.verifactu.TestConfig.of("Empresa Test SL", "B72877814"), false, false);
    }

    // ─── Utilidades de extraccion del XML generado ─────────────────

    private static List<String> all(String xml, String tag) {
        Matcher m = Pattern.compile("<(?:\\w+:)?" + tag + ">([^<]*)</(?:\\w+:)?" + tag + ">").matcher(xml);
        List<String> values = new ArrayList<>();
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }

    private static String first(String xml, String tag) {
        List<String> values = all(xml, tag);
        return values.isEmpty() ? null : values.get(0);
    }

    private static final String ISO_TS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}";
    private static final String AMOUNT = "-?\\d{1,12}\\.\\d{2}";

    /** Reglas de la AEAT que el XSD no expresa y que provocaron rechazos reales. */
    private static void assertAeatRules(String label, String xml) {
        // FechaHoraHusoGenRegistro: ISO 8601 con huso y sin fraccion de segundo (error 1244).
        String ts = first(xml, "FechaHoraHusoGenRegistro");
        assertNotNull(label, ts);
        assertTrue(label + ": FechaHoraHusoGenRegistro " + ts, ts.matches(ISO_TS));
        // Fechas de expedicion: dd-MM-yyyy.
        for (String tag : Arrays.asList("FechaExpedicionFactura", "FechaExpedicionFacturaAnulada", "FechaOperacion")) {
            for (String v : all(xml, tag)) {
                assertTrue(label + ": " + tag + " " + v, v.matches("\\d{2}-\\d{2}-\\d{4}"));
            }
        }
        // Importes: punto decimal, dos decimales, sin exponente ni "-0.00".
        for (String tag : Arrays.asList("CuotaTotal", "ImporteTotal", "BaseImponibleOimporteNoSujeto", "CuotaRepercutida",
                "TipoImpositivo", "BaseImponibleACoste", "TipoRecargoEquivalencia", "CuotaRecargoEquivalencia",
                "BaseRectificada", "CuotaRectificada", "CuotaRecargoRectificado")) {
            for (String v : all(xml, tag)) {
                assertTrue(label + ": " + tag + " " + v, v.matches(AMOUNT));
                assertTrue(label + ": " + tag + " no puede ser -0.00", !v.equals("-0.00"));
            }
        }
        // Huella: 64 hex en mayuscula.
        for (String v : all(xml, "Huella")) {
            assertTrue(label + ": Huella " + v, v.matches("[0-9A-F]{64}"));
        }
        // Operaciones exentas y no sujetas no llevan tipo ni cuota (solo base).
        Matcher detalle = Pattern.compile("<(?:\\w+:)?DetalleDesglose>(.*?)</(?:\\w+:)?DetalleDesglose>", Pattern.DOTALL).matcher(xml);
        while (detalle.find()) {
            String d = detalle.group(1);
            String calificacion = first(d, "CalificacionOperacion");
            boolean exento = first(d, "OperacionExenta") != null;
            boolean noSujeta = calificacion != null && calificacion.startsWith("N");
            if (exento || noSujeta) {
                assertNull(label + ": exenta/no sujeta sin TipoImpositivo", first(d, "TipoImpositivo"));
                assertNull(label + ": exenta/no sujeta sin CuotaRepercutida", first(d, "CuotaRepercutida"));
            }
        }
    }

    /** La AEAT recalcula la huella con los valores del XML: tienen que dar la huella enviada. */
    private static void assertHashMatchesXml(String label, String xml, Map<String, Object> response, boolean isInvoice) {
        String own = all(xml, "Huella").get(all(xml, "Huella").size() - 1);
        assertEquals(label + ": la huella del XML es la devuelta", response.get("hash"), own);
        String previous = xml.contains("RegistroAnterior") ? all(xml, "Huella").get(0) : "";
        String recomputed;
        if (isInvoice) {
            recomputed = HashHelper.generateInvoiceHash(first(xml, "IDEmisorFactura"), first(xml, "NumSerieFactura"),
                    first(xml, "FechaExpedicionFactura"), first(xml, "TipoFactura"), first(xml, "CuotaTotal"),
                    first(xml, "ImporteTotal"), previous, first(xml, "FechaHoraHusoGenRegistro"));
        } else {
            recomputed = HashHelper.generateAnnulmentHash(first(xml, "IDEmisorFacturaAnulada"),
                    first(xml, "NumSerieFacturaAnulada"), first(xml, "FechaExpedicionFacturaAnulada"), previous,
                    first(xml, "FechaHoraHusoGenRegistro"));
        }
        assertEquals(label + ": la huella recalculada desde el XML", own, recomputed);
    }

    private void check(String label, Map<String, Object> response, boolean isInvoice) {
        String xml = signedXml(response);
        assertValid(label, xml);
        assertAeatRules(label, xml);
        assertHashMatchesXml(label, xml, response, isInvoice);
    }

    private static Map<String, Object> previous(String number, String date, String hash) {
        Map<String, Object> previous = new HashMap<>();
        previous.put("number", number);
        previous.put("date", date);
        previous.put("hash", hash);
        return previous;
    }

    private static Invoice invoice(String number, InvoiceType type, double tax, double total, VeriFactuBreakdown... lines) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(number);
        invoice.setIssueDate(LocalDate.of(2026, 10, 11));
        invoice.setInvoiceType(type);
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(total);
        invoice.setBreakdowns(Arrays.asList(lines));
        // F1, F3 and R1-R4 identify the recipient; F2 and R5 never do (AEAT validations).
        if (type != InvoiceType.F2 && type != InvoiceType.R5) {
            invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        }
        return invoice;
    }

    private static Breakdown line(double rate, double base, double tax) {
        return new Breakdown("01", "S1", rate, base, tax);
    }

    private static final String PREV_HASH = "8C8DCEFB120522E0C71BC19902F44D5334FF6C98E74F0E3AC1D1E5A30C2EA836";

    // ─── Matriz de casos ───────────────────────────────────────────

    @Test
    public void f1_withRecipientAndChain() {
        Invoice invoice = invoice("FCT2600002", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        check("F1 con destinatario y cadena",
                client().sendInvoice(invoice, previous("FCT2600001", "10-10-2026", PREV_HASH)), true);
    }

    @Test
    public void f2_withoutRecipient() {
        check("F2 sin destinatario",
                client().sendInvoice(invoice("FCT2600003", InvoiceType.F2, 2.1, 12.1, line(21, 10, 2.1)), null), true);
    }

    @Test
    public void severalVatRates() {
        check("Varios tipos de IVA", client().sendInvoice(invoice("FCT2600004", InvoiceType.F1, 6.6, 56.6,
                line(21, 20, 4.2), line(10, 20, 2), line(4, 10, 0.4)), null), true);
    }

    @Test
    public void zeroVatRate() {
        check("IVA 0%", client().sendInvoice(invoice("FCT2600005", InvoiceType.F1, 0, 50, line(0, 50, 0)), null), true);
    }

    @Test
    public void exemptOperation() {
        Breakdown exempt = new Breakdown();
        exempt.setClaveRegimen("01");
        exempt.setOperacionExenta("E1");
        exempt.setBaseAmount(100);
        check("Operacion exenta", client().sendInvoice(invoice("FCT2600006", InvoiceType.F1, 0, 100, exempt), null), true);
    }

    @Test
    public void notSubjectOperation() {
        Breakdown notSubject = new Breakdown();
        notSubject.setClaveRegimen("01");
        notSubject.setCalificacionOperacion("N1");
        notSubject.setBaseAmount(100);
        check("Operacion no sujeta", client().sendInvoice(invoice("FCT2600007", InvoiceType.F1, 0, 100, notSubject), null), true);
    }

    @Test
    public void correctiveBySubstitution() {
        Invoice invoice = invoice("RCT2600001", InvoiceType.R1, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setCorrectionType("S");
        invoice.setCorrectedBaseAmount(20.0);
        invoice.setCorrectedTaxAmount(4.2);
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        check("Rectificativa por sustitucion", client().sendInvoice(invoice, null), true);
    }

    @Test
    public void correctiveByDifferenceWithNegativeAmounts() {
        Invoice invoice = invoice("RCT2600002", InvoiceType.R1, -4.2, -24.2, line(21, -20, -4.2));
        invoice.setCorrectionType("I");
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        check("Rectificativa por diferencias (negativa)", client().sendInvoice(invoice, null), true);
    }

    @Test
    public void specialCharactersInNames() {
        VeriFactuConfig config = com.squareetlabs.verifactu.TestConfig.of("Hijos & \"Cia\" <S.L.> Ñandú Ç", "B72877814");
        AeatClient client = new AeatClient(getClass().getClassLoader().getResource("test-cert.p12").getPath(),
                "testpass123", config, false, false);
        check("Caracteres especiales", client.sendInvoice(invoice("FCT2600008", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2)), null), true);
    }

    @Test
    public void largeAndRoundedAmounts() {
        check("Importes grandes y redondeo", client().sendInvoice(
                invoice("FCT2600009", InvoiceType.F1, 20999999.99, 120999999.99, line(21, 99999999.99, 20999999.99)), null), true);
        check("Redondeo", client().sendInvoice(invoice("FCT2600010", InvoiceType.F1, 0.01, 1.01, line(21, 1.005, 0.0105)), null), true);
    }

    @Test
    public void negativeZeroIsNotSent() {
        check("Cero negativo", client().sendInvoice(
                invoice("FCT2600011", InvoiceType.F1, -0.0, -0.001, line(0, -0.001, -0.0)), null), true);
    }

    @Test
    public void annulment_withChain() {
        check("Anulacion encadenada", client().sendAnnulment(new Annulment("FCT2600001", LocalDate.of(2026, 10, 11)),
                previous("FCT2600001", "11-10-2026", PREV_HASH)), false);
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FCT2600001");
        invoice.setIssueDate(LocalDate.of(2026, 10, 11));
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(4.2);
        invoice.setTotalAmount(24.2);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21.0, 20.0, 4.2)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        return invoice;
    }

    @Test
    public void invoice_basic_validatesAgainstXsd() {
        assertValid("Alta basica", signedXml(client().sendInvoice(invoice(), null)));
    }

    @Test
    public void annulment_basic_validatesAgainstXsd() {
        assertValid("Anulacion basica",
                signedXml(client().sendAnnulment(new Annulment("FCT2600001", LocalDate.of(2026, 10, 11)), null)));
    }

    // ─── Reglas de formato y de datos ──────────────────────────────

    @Test
    public void operationDescription_isUsedWhenProvided() {
        Invoice invoice = invoice("FCT2600012", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setOperationDescription("Certificacion de obra n.5");
        String xml = signedXml(client().sendInvoice(invoice, null));
        assertEquals("Certificacion de obra n.5", first(xml, "DescripcionOperacion"));
    }

    @Test
    public void operationDescription_defaultsAndIsTruncatedTo500() {
        String defaultXml = signedXml(client().sendInvoice(
                invoice("FCT2600013", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2)), null));
        assertEquals("Factura FCT2600013", first(defaultXml, "DescripcionOperacion"));

        Invoice invoice = invoice("FCT2600014", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longText.append('x');
        }
        invoice.setOperationDescription(longText.toString());
        String xml = signedXml(client().sendInvoice(invoice, null));
        assertEquals(500, first(xml, "DescripcionOperacion").length());
        assertValid("Descripcion larga", xml);
    }

    @Test
    public void unknownQualificationCode_failsWithClearMessage() {
        Breakdown bad = new Breakdown("01", "ZZ", 21, 20, 4.2);
        Map<String, Object> response = client().sendInvoice(
                invoice("FCT2600015", InvoiceType.F1, 4.2, 24.2, bad), null);
        assertEquals("error", response.get("status"));
        assertTrue(String.valueOf(response.get("message")), String.valueOf(response.get("message")).contains("ZZ"));
    }

    @Test
    public void substitutionWithoutCorrectedAmounts_failsWithClearMessage() {
        Invoice invoice = invoice("RCT2600003", InvoiceType.R1, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setCorrectionType("S");
        Map<String, Object> response = client().sendInvoice(invoice, null);
        assertEquals("error", response.get("status"));
        assertTrue(String.valueOf(response.get("message")).contains("correctedBaseAmount"));
    }

    @Test
    public void reverseChargeOperation_carriesZeroRateAndZeroQuota() {
        Breakdown reverseCharge = new Breakdown("01", "S2", 0, 100, 0);
        Map<String, Object> response = client().sendInvoice(invoice("FCT2600016", InvoiceType.F1, 0, 100, reverseCharge), null);
        check("Inversion del sujeto pasivo", response, true);
        String xml = signedXml(response);
        // S2: the AEAT requires TipoImpositivo = 0 and CuotaRepercutida = 0 (they cannot be missing).
        assertEquals("0.00", first(xml, "TipoImpositivo"));
        assertEquals("0.00", first(xml, "CuotaRepercutida"));
    }

    @Test
    public void validationUrl_encodesParametersAndFormatsAmount() {
        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B72877814");
        invoice.setInvoiceNumber("A/2026 1&x");
        invoice.setIssueDate(LocalDate.of(2026, 10, 11));
        invoice.setTotalAmount(24.2);

        assertEquals("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=B72877814"
                + "&numserie=A%2F2026%201%26x&fecha=11-10-2026&importe=24.20", invoice.getValidationUrl(false));

        invoice.setTotalAmount(-0.001);
        assertTrue(invoice.getValidationUrl(true).endsWith("&importe=0.00"));
    }

    // ─── Funciones del SDK: referencias, IDOtro, art. 6.1.d y validacion previa ─

    @Test
    public void corrective_carriesTheRectifiedInvoice() {
        Invoice invoice = invoice("RCT2600004", InvoiceType.R1, -4.2, -24.2, line(21, -20, -4.2));
        invoice.setCorrectionType("I");
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        invoice.setRectifiedInvoices(Collections.singletonList(new InvoiceReference("FCT2600001", LocalDate.of(2026, 10, 1))));
        Map<String, Object> response = client().sendInvoice(invoice, null);
        check("Rectificativa con factura rectificada", response, true);

        String xml = signedXml(response);
        assertEquals("FCT2600001", first(xml, "NumSerieFactura") == null ? null : all(xml, "NumSerieFactura").get(1));
        assertEquals("01-10-2026", all(xml, "FechaExpedicionFactura").get(1));
        assertEquals("B72877814", all(xml, "IDEmisorFactura").get(1));
        assertTrue(xml.contains("IDFacturaRectificada"));
    }

    @Test
    public void f3_carriesTheSubstitutedInvoices() {
        Invoice invoice = invoice("FCT2600017", InvoiceType.F3, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        invoice.setSubstitutedInvoices(Arrays.<com.squareetlabs.verifactu.contracts.VeriFactuInvoiceReference>asList(
                new InvoiceReference("FS2600001", LocalDate.of(2026, 10, 2)),
                new InvoiceReference("B12345674", "FS2600002", LocalDate.of(2026, 10, 3))));
        Map<String, Object> response = client().sendInvoice(invoice, null);
        check("F3 con facturas sustituidas", response, true);
        assertTrue(signedXml(response).contains("IDFacturaSustituida"));
    }

    @Test
    public void foreignRecipient_isSentAsIdOtro() {
        Invoice invoice = invoice("FCT2600018", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setRecipients(Arrays.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>asList(
                new Recipient("Client SARL", "FR12345678901", "FR", "02"),
                new Recipient("John Smith", "P1234567", "GB", "03")));
        Map<String, Object> response = client().sendInvoice(invoice, null);
        check("Destinatarios extranjeros", response, true);

        String xml = signedXml(response);
        assertEquals(Arrays.asList("FR", "GB"), all(xml, "CodigoPais"));
        assertEquals(Arrays.asList("02", "03"), all(xml, "IDType"));
        assertEquals(Arrays.asList("FR12345678901", "P1234567"), all(xml, "ID"));
        assertTrue("Un destinatario extranjero no lleva NIF", all(xml, "NIF").stream().noneMatch(v -> v.startsWith("FR")));
    }

    @Test
    public void withoutRecipientIdentification_art61d() {
        Invoice invoice = invoice("FCT2600019", InvoiceType.F2, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setWithoutRecipientIdentification(true);
        Map<String, Object> response = client().sendInvoice(invoice, null);
        check("F2 sin identificacion del destinatario", response, true);
        assertEquals("S", first(signedXml(response), "FacturaSinIdentifDestinatarioArt61d"));
    }

    @Test
    public void qualifiedSimplifiedAndMacrodato() {
        Invoice qualified = invoice("FCT2600021", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        qualified.setQualifiedSimplified(true);
        Map<String, Object> response = client().sendInvoice(qualified, null);
        check("Simplificada cualificada", response, true);
        assertEquals("S", first(signedXml(response), "FacturaSimplificadaArt7273"));

        Invoice macro = invoice("FCT2600022", InvoiceType.F1, 21_000_000, 121_000_000, line(21, 100_000_000, 21_000_000));
        Map<String, Object> macroResponse = client().sendInvoice(macro, null);
        check("Macrodato", macroResponse, true);
        assertEquals("S", first(signedXml(macroResponse), "Macrodato"));
        assertNull(first(signedXml(client().sendInvoice(
                invoice("FCT2600023", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2)), null)), "Macrodato"));
    }

    @Test
    public void subsanationAndRejectionRetry_carryTheAeatFlags() {
        Invoice rejectedBefore = invoice("FCT2600024", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        rejectedBefore.setSubsanation(true);
        rejectedBefore.setPreviousRejection("X"); // alta por rechazo
        Map<String, Object> response = client().sendInvoice(rejectedBefore, null);
        check("Alta por rechazo", response, true);
        assertEquals("S", first(signedXml(response), "Subsanacion"));
        assertEquals("X", first(signedXml(response), "RechazoPrevio"));

        Invoice plainSubsanation = invoice("FCT2600025", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        plainSubsanation.setSubsanation(true);
        Map<String, Object> plain = client().sendInvoice(plainSubsanation, null);
        check("Alta de subsanacion", plain, true);
        assertEquals("S", first(signedXml(plain), "Subsanacion"));
        assertNull(first(signedXml(plain), "RechazoPrevio"));

        Invoice invalid = invoice("FCT2600026", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        invalid.setPreviousRejection("X"); // sin Subsanacion = S
        assertEquals("error", client().sendInvoice(invalid, null).get("status"));
    }

    @Test
    public void coupon_isSentOnR1() {
        Invoice invoice = invoice("RCT2600005", InvoiceType.R1, -4.2, -24.2, line(21, -20, -4.2));
        invoice.setCorrectionType("I");
        invoice.setCoupon(true);
        Map<String, Object> response = client().sendInvoice(invoice, null);
        check("Rectificativa con cupon", response, true);
        assertEquals("S", first(signedXml(response), "Cupon"));
    }

    @Test
    public void taxCode_igicIsSentAsImpuesto() {
        Breakdown igic = new Breakdown("01", "S1", 7, 100, 7);
        igic.setTax("03");
        Map<String, Object> response = client().sendInvoice(invoice("FCT2600027", InvoiceType.F1, 7, 107, igic), null);
        check("IGIC", response, true);
        assertEquals("03", first(signedXml(response), "Impuesto"));
    }

    @Test
    public void invalidInvoice_isRefusedBeforeSigningOrSending() {
        Invoice invoice = invoice("FCT2600020", InvoiceType.F1, 4.2, 24.2, line(21, 20, 4.2));
        invoice.setIssueDate(LocalDate.now().plusDays(3));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente", "B12345678")));

        Map<String, Object> response = client().sendInvoice(invoice, null);

        assertEquals("error", response.get("status"));
        assertTrue(String.valueOf(response.get("message")).startsWith("Validacion local:"));
        assertTrue(String.valueOf(response.get("message")).contains("posterior a hoy"));
        assertTrue(String.valueOf(response.get("message")).contains("NIF inválido"));
        assertEquals(2, ((java.util.List<?>) response.get("validation_errors")).size());
        org.junit.Assert.assertFalse("No debe firmarse ni devolver huella", response.containsKey("signedXml"));
        org.junit.Assert.assertFalse(response.containsKey("hash"));
    }

    @Test
    public void invalidAnnulment_isRefusedBeforeSigningOrSending() {
        Map<String, Object> response = client().sendAnnulment(new Annulment("FCT2600001", LocalDate.now().plusDays(3)), null);
        assertEquals("error", response.get("status"));
        assertTrue(String.valueOf(response.get("message")).contains("posterior a hoy"));
        org.junit.Assert.assertFalse(response.containsKey("signedXml"));
    }
}
