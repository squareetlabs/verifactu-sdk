package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

/**
 * Test for ImporteRectificacion block in AeatClient
 */
public class ImporteRectificacionTest {

    @Test
    public void testBuildImporteRectificacionWithAllFields() throws Exception {
        // Create a corrective invoice by substitution with corrected amounts
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F-2025-300-R1");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("S");
        invoice.setCorrectedBaseAmount(100.00);
        invoice.setCorrectedTaxAmount(21.00);
        invoice.setCorrectedSurchargeAmount(5.20);
        invoice.setTotalAmount(150.00);
        invoice.setTaxAmount(31.50);
        invoice.setIssuerTaxId("B12345678");

        // Create breakdown
        Breakdown breakdown = new Breakdown("01", "S1", 21.0, 150.0, 31.50);
        invoice.setBreakdowns(Arrays.asList(breakdown));

        // Use reflection to access private method
        VeriFactuConfig config = new VeriFactuConfig("Test Company", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Method method = AeatClient.class.getDeclaredMethod("buildImporteRectificacion",
                com.squareetlabs.verifactu.contracts.VeriFactuInvoice.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) method.invoke(client, invoice);

        // Assert the block structure
        assertNotNull(result);
        assertTrue(result.containsKey("BaseRectificada"));
        assertTrue(result.containsKey("CuotaRectificada"));
        assertTrue(result.containsKey("CuotaRecargoRectificado"));

        assertEquals("100.00", result.get("BaseRectificada"));
        assertEquals("21.00", result.get("CuotaRectificada"));
        assertEquals("5.20", result.get("CuotaRecargoRectificado"));
    }

    @Test
    public void testReturnsNullWhenCorrectedAmountsAreMissing() throws Exception {
        // Create invoice without corrected amounts
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F-2025-400-R1");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("S");
        invoice.setCorrectedBaseAmount(null);
        invoice.setCorrectedTaxAmount(null);

        VeriFactuConfig config = new VeriFactuConfig("Test Company", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Method method = AeatClient.class.getDeclaredMethod("buildImporteRectificacion",
                com.squareetlabs.verifactu.contracts.VeriFactuInvoice.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) method.invoke(client, invoice);

        assertNull(result);
    }

    @Test
    public void testOmitsSurchargeWhenNull() throws Exception {
        // Create invoice with corrected amounts but no surcharge
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F-2025-500-R1");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("S");
        invoice.setCorrectedBaseAmount(100.00);
        invoice.setCorrectedTaxAmount(21.00);
        invoice.setCorrectedSurchargeAmount(null);

        VeriFactuConfig config = new VeriFactuConfig("Test Company", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Method method = AeatClient.class.getDeclaredMethod("buildImporteRectificacion",
                com.squareetlabs.verifactu.contracts.VeriFactuInvoice.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) method.invoke(client, invoice);

        assertNotNull(result);
        assertTrue(result.containsKey("BaseRectificada"));
        assertTrue(result.containsKey("CuotaRectificada"));
        assertFalse(result.containsKey("CuotaRecargoRectificado"));
    }

    @Test
    public void testIdentifiesCorrectiveInvoiceTypes() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Test Company", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Method method = AeatClient.class.getDeclaredMethod("isCorrectiveInvoice", String.class);
        method.setAccessible(true);

        // Test corrective types
        assertTrue((Boolean) method.invoke(client, "R1"));
        assertTrue((Boolean) method.invoke(client, "R2"));
        assertTrue((Boolean) method.invoke(client, "R3"));
        assertTrue((Boolean) method.invoke(client, "R4"));
        assertTrue((Boolean) method.invoke(client, "R5"));

        // Test non-corrective types
        assertFalse((Boolean) method.invoke(client, "F1"));
        assertFalse((Boolean) method.invoke(client, "F2"));
        assertFalse((Boolean) method.invoke(client, "F3"));
    }
}
