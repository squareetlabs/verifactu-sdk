package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.helpers.NifValidator;
import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.Breakdown;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for VeriFactu AEAT compliance.
 */
public class VeriFactuComplianceTest {

    /**
     * Test hash generation matches AEAT example from official documentation.
     * Example from: https://github.com/mdiago/VeriFactu
     */
    @Test
    public void testHashGenerationMatchesAEATExample() {
        // Ejemplo de la documentación AEAT
        String hash = HashHelper.generateInvoiceHash(
                "B72877814", // IDEmisorFactura
                "GITHUB-EJ-003", // NumSerieFactura
                "04-11-2024", // FechaExpedicionFactura
                "F1", // TipoFactura
                "21.40", // CuotaTotal
                "131.40", // ImporteTotal
                "8C8DCEFB120522E0C71BC19902F44D5334FF6C98E74F0E3AC1D1E5A30C2EA836", // Huella anterior
                "04-11-2024T12:36:39+01:00" // FechaHoraHusoGenRegistro
        );

        // Hash esperado según ejemplo AEAT
        assertEquals(
                "4EECCE4DD48C0539665385D61D451BA921B7160CA6FEF46CD3C2E2BC5C778E14",
                hash);
    }

    /**
     * Test QR URL generation according to AEAT specifications.
     */
    @Test
    public void testQRUrlGeneration() {
        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B72877814");
        invoice.setInvoiceNumber("GITHUB-EJ-004");
        invoice.setIssueDate(LocalDate.of(2024, 11, 4));
        invoice.setTotalAmount(131.40);

        String url = invoice.getValidationUrl(false); // false = test environment

        assertNotNull(url);
        assertTrue(url.startsWith("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR"));
        assertTrue(url.contains("nif=B72877814"));
        assertTrue(url.contains("numserie=GITHUB-EJ-004"));
        assertTrue(url.contains("fecha=04-11-2024"));
        assertTrue(url.contains("importe=131.40"));
    }

    /**
     * Test QR image generation.
     */
    @Test
    public void testQRImageGeneration() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B72877814");
        invoice.setInvoiceNumber("TEST-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setTotalAmount(100.00);

        byte[] qr = invoice.getValidationQR(false, 200);

        assertNotNull(qr);
        assertTrue(qr.length > 0);

        // Verificar que es PNG válido (magic number)
        assertEquals((byte) 0x89, qr[0]);
        assertEquals((byte) 'P', qr[1]);
        assertEquals((byte) 'N', qr[2]);
        assertEquals((byte) 'G', qr[3]);
    }

    /**
     * Test InvoiceType enum integration.
     */
    @Test
    public void testInvoiceTypeEnum() {
        Invoice invoice = new Invoice();

        // Usar enum (recomendado)
        invoice.setInvoiceType(InvoiceType.F1);

        assertEquals("F1", invoice.getInvoiceType());
        assertEquals(InvoiceType.F1, invoice.getInvoiceTypeEnum());
        assertEquals("Factura", invoice.getInvoiceTypeEnum().getDescription());
    }

    /**
     * Test InvoiceType backward compatibility with String.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testInvoiceTypeBackwardCompatibility() {
        Invoice invoice = new Invoice();

        // Usar String (legacy, deprecated)
        invoice.setInvoiceType("R1");

        assertEquals("R1", invoice.getInvoiceType());
        assertEquals(InvoiceType.R1, invoice.getInvoiceTypeEnum());
    }

    /**
     * Test NIF validation with valid NIFs.
     */
    @Test
    public void testValidNIF() {
        assertTrue(NifValidator.isValid("12345678Z"));
        assertTrue(NifValidator.isValid("00000000T"));
        assertEquals("NIF", NifValidator.getIdType("12345678Z"));
    }

    /**
     * Test NIF validation with invalid NIFs.
     */
    @Test
    public void testInvalidNIF() {
        assertFalse(NifValidator.isValid("12345678A")); // letra incorrecta
        assertFalse(NifValidator.isValid("1234567Z")); // muy corto
        assertFalse(NifValidator.isValid("123456789Z")); // muy largo
        assertFalse(NifValidator.isValid("ABCDEFGHZ")); // no numérico
    }

    /**
     * Test CIF validation with valid CIFs.
     */
    @Test
    public void testValidCIF() {
        assertTrue(NifValidator.isValid("B72877814"));
        assertTrue(NifValidator.isValid("A12345674"));
        assertEquals("CIF", NifValidator.getIdType("B72877814"));
    }

    /**
     * Test CIF validation with invalid CIFs.
     */
    @Test
    public void testInvalidCIF() {
        assertFalse(NifValidator.isValid("B72877815")); // dígito incorrecto
        assertFalse(NifValidator.isValid("X72877814")); // letra inicial incorrecta
    }

    /**
     * Test NIE validation.
     */
    @Test
    public void testValidNIE() {
        assertTrue(NifValidator.isValid("X1234567L"));
        assertTrue(NifValidator.isValid("Y1234567Z"));
        assertTrue(NifValidator.isValid("Z1234567R"));
        assertEquals("NIE", NifValidator.getIdType("X1234567L"));
    }

    /**
     * Test invoice validation - valid invoice.
     */
    @Test
    public void testInvoiceValidation_Valid() {
        Invoice invoice = createValidInvoice();

        InvoiceValidator.ValidationResult result = InvoiceValidator.validate(invoice);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    /**
     * Test invoice validation - missing required fields.
     */
    @Test
    public void testInvoiceValidation_MissingFields() {
        Invoice invoice = new Invoice();
        // No establecer campos obligatorios

        InvoiceValidator.ValidationResult result = InvoiceValidator.validate(invoice);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrorMessage().contains("obligatorio"));
    }

    /**
     * Test invoice validation - invalid NIF.
     */
    @Test
    public void testInvoiceValidation_InvalidNIF() {
        Invoice invoice = createValidInvoice();
        invoice.setIssuerTaxId("B12345678"); // NIF inválido

        InvoiceValidator.ValidationResult result = InvoiceValidator.validate(invoice);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("NIF emisor inválido"));
    }

    /**
     * Test invoice validation - amount coherence.
     */
    @Test
    public void testInvoiceValidation_AmountCoherence() {
        Invoice invoice = createValidInvoice();
        invoice.setTotalAmount(999.99); // No coincide con desglose

        InvoiceValidator.ValidationResult result = InvoiceValidator.validate(invoice);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("incoherente"));
    }

    /**
     * Helper method to create a valid invoice for testing.
     */
    private Invoice createValidInvoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("TEST-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setIssuerTaxId("B72877814");
        invoice.setCustomerName("Cliente Test");
        invoice.setCustomerTaxId("12345678Z");

        // Crear desglose
        Breakdown breakdown = new Breakdown("01", "S1", 21.0, 100.0, 21.0);
        invoice.setBreakdowns(Collections.singletonList(breakdown));

        // Importes coherentes con desglose
        invoice.setTaxAmount(21.0);
        invoice.setTotalAmount(121.0);

        return invoice;
    }
}
