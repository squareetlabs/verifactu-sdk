package com.squareetlabs.verifactu;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Correcciones de la revision independiente: encadenamiento coherente y configuracion validada en el cliente. */
public class ClientReviewFixesTest {

    private AeatClient client(VeriFactuConfig config) {
        URL resource = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(resource);
        return new AeatClient(resource.getPath(), "testpass123", config, false, false);
    }

    private AeatClient client() {
        return client(TestConfig.of("Empresa Test SL", "B72877814"));
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("RV-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(21.0);
        invoice.setTotalAmount(121.0);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21.0, 100.0, 21.0)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        return invoice;
    }

    private Map<String, Object> previous() {
        Map<String, Object> previous = new HashMap<>();
        previous.put("hash", "A".repeat(64));
        previous.put("number", "RV-000");
        previous.put("date", "10-10-2026");
        return previous;
    }

    @Test
    public void previousHashWithoutThePreviousRecordIsRejectedInsteadOfBuildingAContradictoryRecord() {
        Invoice invoice = invoice();
        invoice.setPreviousHash("B".repeat(64));

        Map<String, Object> response = client().sendInvoice(invoice, null);

        assertEquals("error", response.get("status"));
        assertTrue(response.get("message").toString(), response.get("message").toString().contains("previousHash"));
        assertNotNull(response.get("validation_errors"));
    }

    @Test
    public void previousWithoutHashNumberOrDateIsRejected() {
        for (String missing : new String[] {"hash", "number", "date"}) {
            Map<String, Object> previous = previous();
            previous.remove(missing);

            Map<String, Object> response = client().sendInvoice(invoice(), previous);

            assertEquals(missing, "error", response.get("status"));
            assertTrue(response.get("message").toString(), response.get("message").toString().contains("'" + missing + "'"));
        }
    }

    @Test
    public void aFailedResponseIsNotAValidPrevious() {
        Map<String, Object> failed = new HashMap<>();
        failed.put("status", "error");
        failed.put("message", "Connection reset");

        assertEquals("error", client().sendInvoice(invoice(), failed).get("status"));
    }

    @Test
    public void aCompletePreviousChainsNormally() {
        Map<String, Object> response = client().sendInvoice(invoice(), previous());

        assertEquals("success", response.get("status"));
        assertTrue(((String) response.get("hashInput")).contains("Huella=" + "A".repeat(64)));
    }

    @Test
    public void annulmentWithPreviousHashButNoPreviousRecordIsRejected() {
        Annulment annulment = new Annulment("RV-001", LocalDate.now());
        annulment.setPreviousHash("B".repeat(64));

        assertEquals("error", client().sendAnnulment(annulment, null).get("status"));
    }

    @Test
    public void theClientValidatesTheConfigurationBeforeSending() {
        VeriFactuConfig config = new VeriFactuConfig("Empresa Test SL", "B72877814"); // NIF de desarrollador por defecto invalido

        Map<String, Object> response = client(config).sendInvoice(invoice(), null);

        assertEquals("error", response.get("status"));
        assertTrue(response.get("message").toString(), response.get("message").toString().contains("desarrollador"));
    }

    @Test
    public void aBlankRecipientCountryDoesNotBreakTheXmlGeneration() {
        Invoice invoice = invoice();
        Recipient foreign = new Recipient("Cliente Extranjero SL", "FR12345678901", " ", "02");
        invoice.setRecipients(Collections.singletonList(foreign));

        Map<String, Object> response = client().sendInvoice(invoice, null);

        assertTrue(response.toString(), response.get("message") == null
                || !response.get("message").toString().contains("No enum constant"));
    }
}
