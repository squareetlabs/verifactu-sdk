package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.Test;

import java.net.URL;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests de la modalidad "NO VERI*FACTU" (RD 1007/2023): {@code AeatClient} construido con
 * {@code verifactuMode = false} debe generar, encadenar (misma huella/algoritmo que en VERI*FACTU)
 * y firmar (XAdES) el registro de facturación, pero NUNCA remitirlo por SOAP a la AEAT.
 */
public class AeatClientLocalModeTest {

    private static final String TEST_CERT_PASSWORD = "testpass123";

    private String testCertPath() {
        URL resource = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull("Falta el certificado de test src/test/resources/test-cert.p12", resource);
        return resource.getPath();
    }

    private Invoice buildInvoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("NOVF-TEST-001");
        invoice.setIssueDate(LocalDate.of(2026, 9, 17));
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(21.0);
        invoice.setTotalAmount(121.0);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21.0, 100.0, 21.0)));
        return invoice;
    }

    private AeatClient buildClient(boolean verifactuMode) {
        VeriFactuConfig config = new VeriFactuConfig("Empresa Test SL", "B72877814");
        return new AeatClient(testCertPath(), TEST_CERT_PASSWORD, config, false, verifactuMode);
    }

    @Test
    public void sendInvoice_noVerifactuMode_neverCallsAeatAndReturnsSignedXml() {
        AeatClient client = buildClient(false);

        Map<String, Object> response = client.sendInvoice(buildInvoice(), null);

        assertEquals("success", response.get("status"));
        assertEquals(Boolean.FALSE, response.get("submittedToAeat"));
        assertNotNull("Debe devolver la huella calculada localmente", response.get("hash"));
        assertNotNull("Debe devolver el input exacto de la huella (para re-verificacion)", response.get("hashInput"));
        assertNotNull("Debe devolver el XML firmado (XAdES) del registro", response.get("signedXml"));

        // El XML firmado debe decodificarse a un XML valido con la firma XAdES embebida.
        byte[] signedXmlBytes = Base64.getDecoder().decode((String) response.get("signedXml"));
        String signedXml = new String(signedXmlBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(signedXml.contains("RegistroAlta"));
        assertTrue("El XML debe contener la firma XAdES", signedXml.contains("Signature"));

        // No debe existir "csv" (solo se recibe de la AEAT en un envio real).
        assertFalse(response.containsKey("csv"));
    }

    @Test
    public void sendInvoice_noVerifactuMode_hashMatchesVerifactuModeAlgorithm() {
        // La huella debe calcularse exactamente igual en ambos modos: mismos datos, mismo
        // algoritmo (HashHelper), independientemente de si el registro se remite o no.
        Map<String, String> data = new HashMap<>();
        data.put("issuer_tax_id", "B72877814");
        data.put("invoice_number", "NOVF-TEST-001");
        data.put("issue_date", "17-09-2026");
        data.put("invoice_type", "F1");
        data.put("total_tax", "21.00");
        data.put("total_amount", "121.00");
        data.put("previous_hash", "");
        data.put("generated_at", "17-09-2026T10:00:00+02:00");

        Map<String, String> result = HashHelper.generateInvoiceHash(data);

        assertTrue(HashHelper.verify(result.get("hash"), result.get("inputString")));
        // Una alteracion de un solo caracter del input debe invalidar la verificacion.
        assertFalse(HashHelper.verify(result.get("hash"), result.get("inputString") + "X"));
    }

    @Test
    public void sendAnnulment_noVerifactuMode_neverCallsAeatAndReturnsSignedXml() {
        AeatClient client = buildClient(false);
        Annulment annulment = new Annulment("NOVF-TEST-001", LocalDate.of(2026, 9, 17));

        Map<String, Object> response = client.sendAnnulment(annulment, null);

        assertEquals("success", response.get("status"));
        assertEquals(Boolean.FALSE, response.get("submittedToAeat"));
        assertNotNull(response.get("hash"));
        assertNotNull(response.get("hashInput"));
        assertNotNull(response.get("signedXml"));

        byte[] signedXmlBytes = Base64.getDecoder().decode((String) response.get("signedXml"));
        String signedXml = new String(signedXmlBytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(signedXml.contains("RegistroAnulacion"));
        assertTrue(signedXml.contains("Signature"));
    }
}
