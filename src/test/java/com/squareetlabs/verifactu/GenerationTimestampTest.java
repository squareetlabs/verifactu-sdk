package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.Recipient;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.Test;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * La fecha de generación del registro ({@code FechaHoraHusoGenRegistro}) puede fijarla quien llama: un
 * reenvío con el mismo valor reproduce el mismo registro (misma huella y mismo XML).
 */
public class GenerationTimestampTest {

    private static final ZonedDateTime FIXED = ZonedDateTime.of(2026, 10, 11, 12, 11, 1, 0, ZoneId.of("Europe/Madrid"));

    private AeatClient client() {
        URL resource = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(resource);
        return new AeatClient(resource.getPath(), "testpass123", new VeriFactuConfig("Empresa Test SL", "B72877814"), false, false);
    }

    private Invoice invoice(ZonedDateTime generated) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("TS-001");
        invoice.setIssueDate(LocalDate.of(2026, 10, 11));
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(21.0);
        invoice.setTotalAmount(121.0);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21.0, 100.0, 21.0)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        invoice.setGenerationTimestamp(generated);
        return invoice;
    }

    private String xml(Map<String, Object> response) {
        return new String(Base64.getDecoder().decode((String) response.get("signedXml")), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void invoice_usesTheSuppliedTimestampInHashInputAndXml() {
        Map<String, Object> response = client().sendInvoice(invoice(FIXED), null);

        assertEquals("success", response.get("status"));
        assertTrue((String) response.get("hashInput"), ((String) response.get("hashInput")).contains("FechaHoraHusoGenRegistro=2026-10-11T12:11:01+02:00"));
        assertTrue(xml(response).contains("2026-10-11T12:11:01+02:00"));
    }

    @Test
    public void invoice_resendWithTheSameTimestampReproducesTheSameHash() {
        Map<String, Object> first = client().sendInvoice(invoice(FIXED), null);
        Map<String, Object> second = client().sendInvoice(invoice(FIXED), null);

        assertEquals(first.get("hash"), second.get("hash"));
        assertEquals(first.get("hashInput"), second.get("hashInput"));
    }

    @Test
    public void invoice_aDifferentTimestampChangesTheHash() {
        Map<String, Object> first = client().sendInvoice(invoice(FIXED), null);
        Map<String, Object> second = client().sendInvoice(invoice(FIXED.plusSeconds(1)), null);

        assertNotEquals(first.get("hash"), second.get("hash"));
    }

    @Test
    public void invoice_timestampInAnotherZoneIsExpressedInMadrid() {
        ZonedDateTime utc = FIXED.withZoneSameInstant(ZoneId.of("UTC"));
        Map<String, Object> response = client().sendInvoice(invoice(utc), null);

        assertTrue(((String) response.get("hashInput")).contains("FechaHoraHusoGenRegistro=2026-10-11T12:11:01+02:00"));
    }

    @Test
    public void invoice_fractionalSecondsAreDropped() {
        Map<String, Object> response = client().sendInvoice(invoice(FIXED.plusNanos(987_000_000)), null);
        Map<String, Object> plain = client().sendInvoice(invoice(FIXED), null);

        assertEquals(plain.get("hash"), response.get("hash"));
    }

    @Test
    public void invoice_withoutTimestampUsesTheCurrentInstant() {
        Map<String, Object> response = client().sendInvoice(invoice(null), null);

        assertTrue(((String) response.get("hashInput")).matches("(?s).*FechaHoraHusoGenRegistro=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}.*"));
    }

    @Test
    public void annulment_usesTheSuppliedTimestampAndReproducesTheHash() {
        Annulment a = new Annulment("TS-001", LocalDate.of(2026, 10, 11));
        a.setGenerationTimestamp(FIXED);
        Annulment b = new Annulment("TS-001", LocalDate.of(2026, 10, 11));
        b.setGenerationTimestamp(FIXED);

        Map<String, Object> first = client().sendAnnulment(a, null);
        Map<String, Object> second = client().sendAnnulment(b, null);

        assertEquals("success", first.get("status"));
        assertTrue(((String) first.get("hashInput")).contains("FechaHoraHusoGenRegistro=2026-10-11T12:11:01+02:00"));
        assertEquals(first.get("hash"), second.get("hash"));
    }

    @Test
    public void validator_warnsWhenTheTimestampIsInTheFuture() {
        VeriFactuConfig config = new VeriFactuConfig("Empresa Test SL", "B72877814");
        Invoice future = invoice(ZonedDateTime.now(ZoneId.of("Europe/Madrid")).plusHours(2));
        future.setIssueDate(LocalDate.now(ZoneId.of("Europe/Madrid")));

        InvoiceValidator.ValidationResult result = InvoiceValidator.validate(future, config);

        assertTrue(result.isValid());
        assertTrue(result.getWarnings().toString(), result.getWarnings().stream().anyMatch(w -> w.contains("generación")));
    }

    @Test
    public void validator_acceptsAPastTimestampWithoutWarnings() {
        VeriFactuConfig config = new VeriFactuConfig("Empresa Test SL", "B72877814");
        Invoice past = invoice(ZonedDateTime.now(ZoneId.of("Europe/Madrid")).minusDays(2));
        past.setIssueDate(LocalDate.now(ZoneId.of("Europe/Madrid")).minusDays(2));

        assertTrue(InvoiceValidator.validate(past, config).getWarnings().stream().noneMatch(w -> w.contains("generación")));
    }
}
