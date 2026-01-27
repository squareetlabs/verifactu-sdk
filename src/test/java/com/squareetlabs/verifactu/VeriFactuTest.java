package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;
import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.Test;
import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VeriFactuTest {

    @Test
    public void testInvoiceHashGeneration() {
        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678"); // Not in interface but needed for hash, assume added to logic usage
        invoice.setInvoiceNumber("F001");
        invoice.setIssueDate(LocalDate.of(2024, 1, 1));
        invoice.setInvoiceType("F1");
        invoice.setTaxAmount(21.00);
        invoice.setTotalAmount(121.00);
        invoice.setPreviousHash("");

        // Manual Hash Data construction to test Helper
        Map<String, String> data = new HashMap<>();
        data.put("issuer_tax_id", "B12345678");
        data.put("invoice_number", "F001");
        data.put("issue_date", "01-01-2024");
        data.put("invoice_type", "F1");
        data.put("total_tax", "21.00");
        data.put("total_amount", "121.00");
        data.put("previous_hash", "");
        data.put("generated_at", "2024-01-01T12:00:00+00:00");

        Map<String, String> result = HashHelper.generateInvoiceHash(data);
        assertNotNull(result.get("hash"));
        System.out.println("Hash: " + result.get("hash"));
        System.out.println("Input: " + result.get("inputString"));
    }

    @Test
    public void testClientProcess() {
        VeriFactuConfig config = new VeriFactuConfig("Test Company", "B12345678");
        AeatClient client = new AeatClient("/path/to/cert", "pass", config, false, true);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType("F1");
        invoice.setTaxAmount(21.00);
        invoice.setTotalAmount(121.00);
        // Note: Invoice interface doesn't have setIssuerTaxId, AeatClient takes it from
        // Config

        Breakdown breakdown = new Breakdown("01", "S1", 21.0, 100.0, 21.0);
        invoice.setBreakdowns(Collections.singletonList(breakdown));

        Map<String, Object> response = client.sendInvoice(invoice, null);

        // Expect error because certificate file is missing and no real connection
        // possible
        assertEquals("error", response.get("status"));
        System.out.println("Client Response Message: " + response.get("message"));
        assertNotNull(response.get("message"));
    }
}
