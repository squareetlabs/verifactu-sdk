package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.TestConfig;
import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.Recipient;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AEAT code 2004: the generation instant must be the current date of the AEAT system with a margin of 240 seconds;
 * outside it the record is accepted with errors (verified against the pre-production service: a record generated 20
 * minutes before sending came back as {@code ParcialmenteCorrecto}).
 */
public class GenerationTimestampMarginTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 12, 11, 46, 0, 0, MADRID);

    @Test
    public void marginIs240Seconds() {
        assertEquals(240, InvoiceValidator.GENERATION_TIMESTAMP_MARGIN_SECONDS);
    }

    @Test
    public void withinTheMarginThereIsNoWarning() {
        assertNull(AeatClient.generationTimestampWarning(NOW, NOW));
        assertNull(AeatClient.generationTimestampWarning(NOW.minusSeconds(240), NOW));
        assertNull(AeatClient.generationTimestampWarning(NOW.plusSeconds(240), NOW));
    }

    @Test
    public void anOldGenerationInstantIsReported() {
        // Case verified against the AEAT: generated at 11:26:13, sent at 11:46:02.
        String warning = AeatClient.generationTimestampWarning(NOW.minusMinutes(20), NOW);

        assertNotNull(warning);
        assertTrue(warning, warning.contains("1200 segundos") && warning.contains("240") && warning.contains("2004"));
    }

    @Test
    public void justOutsideTheMarginIsReportedInBothDirections() {
        assertNotNull(AeatClient.generationTimestampWarning(NOW.minusSeconds(241), NOW));
        assertNotNull(AeatClient.generationTimestampWarning(NOW.plusSeconds(241), NOW));
    }

    @Test
    public void theValidatorWarnsAboutAFutureInstantBeyondTheMarginOnly() {
        VeriFactuConfig config = TestConfig.of("Empresa Test SL", "B72877814");
        ZonedDateTime now = ZonedDateTime.now(MADRID);

        assertTrue(warnsAboutTimestamp(InvoiceValidator.validate(invoice(now.plusMinutes(3)), config)) == false);
        assertTrue(warnsAboutTimestamp(InvoiceValidator.validate(invoice(now.plusMinutes(5)), config)));
    }

    private static boolean warnsAboutTimestamp(InvoiceValidator.ValidationResult result) {
        return result.getWarnings().stream().anyMatch(w -> w.contains("generación") && w.contains("240"));
    }

    private static Invoice invoice(ZonedDateTime generated) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("MG-001");
        invoice.setIssueDate(LocalDate.now(MADRID));
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(21.0);
        invoice.setTotalAmount(121.0);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21.0, 100.0, 21.0)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        invoice.setGenerationTimestamp(generated);
        return invoice;
    }
}
