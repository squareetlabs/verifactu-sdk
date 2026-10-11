package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.helpers.InvoiceValidator.ValidationResult;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.Test;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Los ejemplos de la documentacion se validan con el mismo validador y se generan con el mismo
 * cliente que usa el SDK: si un ejemplo deja de ser correcto, este test falla.
 */
public class ExamplesAreValidTest {

    private static VeriFactuConfig config() {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B72877814");
        config.setDeveloperNif("B72877814");
        return config;
    }

    private static Map<String, Invoice> examples() {
        Map<String, Invoice> examples = new LinkedHashMap<>();
        examples.put("standard (F1)", InvoiceTypesExample.standard());
        examples.put("simplified (F2)", InvoiceTypesExample.simplified());
        examples.put("substituteOfSimplified (F3)", InvoiceTypesExample.substituteOfSimplified());
        examples.put("withForeignRecipient", InvoiceTypesExample.withForeignRecipient());
        examples.put("withoutRecipientIdentification (F2 + art. 6.1.d)", InvoiceTypesExample.withoutRecipientIdentification());
        examples.put("reverseCharge (S2)", InvoiceTypesExample.reverseCharge());
        examples.put("resentAfterRejection", InvoiceTypesExample.resentAfterRejection());
        examples.put("rectificativeBySubstitution", InvoiceTypesExample.rectificativeBySubstitution());
        examples.put("rectificativeByDifference", InvoiceTypesExample.rectificativeByDifference());
        return examples;
    }

    @Test
    public void everyInvoiceExampleIsValidWithoutWarnings() {
        for (Map.Entry<String, Invoice> example : examples().entrySet()) {
            ValidationResult result = InvoiceValidator.validate(example.getValue(), config());
            assertTrue(example.getKey() + ": " + result.getErrors(), result.isValid());
            // La F2 sin destinatario no avisa; el resto tampoco debe avisar.
            assertTrue(example.getKey() + ": " + result.getWarnings(), result.getWarnings().isEmpty());
        }
    }

    @Test
    public void everyInvoiceExampleIsGeneratedByTheClient() {
        URL cert = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(cert);
        AeatClient client = new AeatClient(cert.getPath(), "testpass123", config(), false, false);
        for (Map.Entry<String, Invoice> example : examples().entrySet()) {
            Map<String, Object> response = client.sendInvoice(example.getValue(), null);
            assertEquals(example.getKey() + ": " + response, "success", response.get("status"));
            assertNotNull(example.getKey(), response.get("signedXml"));
        }
    }

    @Test
    public void unsupportedF4ExampleIsRejected() {
        ValidationResult result = InvoiceValidator.validate(InvoiceTypesExample.summaryEntryNotSupported(), config());
        assertFalse(result.isValid());
        assertTrue(result.getErrors().toString(), result.getErrors().toString().contains("F4"));
    }

    @Test
    public void validationExampleInvoiceIsValid() {
        assertTrue(InvoiceValidator.validate(InvoiceTypesExample.standard(), config()).isValid());
    }

    @Test
    public void responseHandlingChainsOnlyAcceptedRecords() {
        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("status", "success");
        accepted.put("submittedToAeat", true);
        accepted.put("aeat_status", "Correcto");
        assertTrue(ResponseHandlingExample.isChainable(accepted));

        Map<String, Object> rejected = new LinkedHashMap<>(accepted);
        rejected.put("aeat_status", "Incorrecto");
        assertFalse(ResponseHandlingExample.isChainable(rejected));

        Map<String, Object> local = new LinkedHashMap<>();
        local.put("status", "success");
        local.put("submittedToAeat", false);
        assertTrue(ResponseHandlingExample.isChainable(local));

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", "error");
        assertFalse(ResponseHandlingExample.isChainable(error));
    }
}
