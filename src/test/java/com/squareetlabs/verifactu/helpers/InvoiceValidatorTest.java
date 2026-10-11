package com.squareetlabs.verifactu.helpers;

import com.squareetlabs.verifactu.helpers.InvoiceValidator.ValidationResult;
import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceReference;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.Recipient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InvoiceValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private static VeriFactuConfig config() {
        VeriFactuConfig config = new VeriFactuConfig("Empresa Test SL", "B72877814");
        config.setDeveloperNif("B72877814");
        return config;
    }

    private static Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FCT2600001");
        invoice.setIssueDate(TODAY);
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(4.2);
        invoice.setTotalAmount(24.2);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21, 20, 4.2)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        return invoice;
    }

    private static ValidationResult check(Invoice invoice) {
        return InvoiceValidator.validate(invoice, config(), TODAY);
    }

    private static void assertError(ValidationResult result, String fragment) {
        assertFalse("Debia ser invalida: " + fragment, result.isValid());
        assertTrue(result.getErrors().toString(), result.getErrors().toString().contains(fragment));
    }

    private static void assertWarning(ValidationResult result, String fragment) {
        assertTrue(result.getErrors().toString(), result.isValid());
        assertTrue(result.getWarnings().toString(), result.getWarnings().toString().contains(fragment));
    }

    // ─── Factura valida ────────────────────────────────────────────

    @Test
    public void validInvoice_hasNoErrorsNorWarnings() {
        ValidationResult result = check(invoice());
        assertTrue(result.getErrors().toString(), result.isValid());
        assertTrue(result.getWarnings().toString(), result.getWarnings().isEmpty());
    }

    @Test
    public void negativeAmountsAreAllowedInCorrectiveInvoices() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("I");
        invoice.setTaxAmount(-4.2);
        invoice.setTotalAmount(-24.2);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21, -20, -4.2)));
        invoice.setRectifiedInvoices(Collections.singletonList(new InvoiceReference("FCT2600000", TODAY)));

        ValidationResult result = check(invoice);

        assertTrue(result.getErrors().toString(), result.isValid());
        assertTrue(result.getWarnings().toString(), result.getWarnings().isEmpty());
    }

    @Test
    public void negativeAmountsInANonCorrectiveInvoiceOnlyWarn() {
        Invoice invoice = invoice();
        invoice.setTaxAmount(-4.2);
        invoice.setTotalAmount(-24.2);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21, -20, -4.2)));
        assertWarning(check(invoice), "no es rectificativa");
    }

    // ─── Campos obligatorios y limites ─────────────────────────────

    @Test
    public void missingRequiredFields() {
        ValidationResult result = InvoiceValidator.validate(new Invoice(), config(), TODAY);
        assertError(result, "Número de factura obligatorio");
        assertError(result, "Fecha de emisión obligatoria");
        assertError(result, "Tipo de factura obligatorio");
        assertError(result, "Desglose obligatorio");
    }

    @Test
    public void invoiceNumberTooLong() {
        Invoice invoice = invoice();
        invoice.setInvoiceNumber(String.join("", Collections.nCopies(61, "X")));
        assertError(check(invoice), "demasiado largo");
    }

    @Test
    public void issueDateInTheFutureIsRejected() {
        Invoice invoice = invoice();
        invoice.setIssueDate(TODAY.plusDays(1));
        assertError(check(invoice), "posterior a hoy");
    }

    @Test
    public void unsupportedInvoiceTypeF4IsRejected() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.F4);
        assertError(check(invoice), "no admitido por la AEAT: F4");
    }

    @Test
    public void invalidIssuerNif() {
        VeriFactuConfig bad = new VeriFactuConfig("Empresa Test SL", "CIF0000");
        ValidationResult result = InvoiceValidator.validate(invoice(), bad, TODAY);
        assertError(result, "NIF emisor inválido: CIF0000");
    }

    @Test
    public void specialSpanishNifsAreAccepted() {
        VeriFactuConfig k = new VeriFactuConfig("Persona", "K1234567L");
        assertTrue(InvoiceValidator.validate(invoice(), k, TODAY).isValid());
    }

    @Test
    public void issuerNameRequiredWhenValidatingWithConfig() {
        VeriFactuConfig noName = new VeriFactuConfig(null, "B72877814");
        assertError(InvoiceValidator.validate(invoice(), noName, TODAY), "Razón social del emisor obligatoria");
    }

    @Test
    public void nonFiniteAndOversizedAmounts() {
        Invoice invoice = invoice();
        invoice.setTotalAmount(Double.NaN);
        assertError(check(invoice), "no es un número válido");

        Invoice big = invoice();
        big.setTotalAmount(1e13);
        assertError(check(big), "12 dígitos");
    }

    // ─── Desglose ──────────────────────────────────────────────────

    @Test
    public void tooManyBreakdownLines() {
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.nCopies(13, new Breakdown("01", "S1", 21, 20, 4.2)));
        assertError(check(invoice), "Demasiadas líneas de desglose");
    }

    @Test
    public void breakdownCodesMustBeKnownAndExclusive() {
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "ZZ", 21, 20, 4.2)));
        assertError(check(invoice), "CalificacionOperacion desconocida: ZZ");

        Breakdown both = new Breakdown("01", "S1", 21, 20, 4.2);
        both.setOperacionExenta("E1");
        invoice.setBreakdowns(Collections.singletonList(both));
        assertError(check(invoice), "exactamente una");

        Breakdown none = new Breakdown("01", null, 21, 20, 4.2);
        invoice.setBreakdowns(Collections.singletonList(none));
        assertError(check(invoice), "exactamente una");

        invoice.setBreakdowns(Collections.singletonList(new Breakdown("99", "S1", 21, 20, 4.2)));
        assertError(check(invoice), "ClaveRegimen desconocida: 99");
    }

    @Test
    public void exemptAndNotSubjectLinesDoNotAddTax() {
        Breakdown exempt = new Breakdown();
        exempt.setClaveRegimen("01");
        exempt.setOperacionExenta("E1");
        exempt.setBaseAmount(100);
        Invoice invoice = invoice();
        invoice.setTaxAmount(0);
        invoice.setTotalAmount(100);
        invoice.setBreakdowns(Collections.singletonList(exempt));
        assertTrue(check(invoice).getErrors().toString(), check(invoice).isValid());
    }

    @Test
    public void totalsMustMatchTheBreakdown() {
        Invoice invoice = invoice();
        invoice.setTotalAmount(999.99);
        assertError(check(invoice), "Total incoherente");

        Invoice tax = invoice();
        tax.setTaxAmount(9);
        assertError(check(tax), "Cuota total incoherente");
    }

    @Test
    public void surchargeCountsInTheTotals() {
        Breakdown line = new Breakdown("01", "S1", 21, 100, 21);
        line.setTipoRecargoEquivalencia(5.2);
        line.setCuotaRecargoEquivalencia(5.2);
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(line));
        invoice.setTaxAmount(26.2);
        invoice.setTotalAmount(126.2);
        assertTrue(check(invoice).getErrors().toString(), check(invoice).isValid());
    }

    // ─── Rectificativas ────────────────────────────────────────────

    @Test
    public void correctiveInvoiceNeedsCorrectionType() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.R1);
        assertError(check(invoice), "tipo de rectificación");
        invoice.setCorrectionType("X");
        assertError(check(invoice), "desconocido: X");
    }

    @Test
    public void substitutionNeedsCorrectedAmountsAndWarnsWithoutRectifiedInvoice() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("S");
        assertError(check(invoice), "correctedBaseAmount");

        invoice.setCorrectedBaseAmount(20.0);
        invoice.setCorrectedTaxAmount(4.2);
        assertWarning(check(invoice), "indicar la factura que rectifica");

        invoice.setRectifiedInvoices(Collections.singletonList(new InvoiceReference("FCT2600000", TODAY)));
        assertTrue(check(invoice).getWarnings().toString(), check(invoice).getWarnings().isEmpty());
    }

    @Test
    public void referencesNeedNumberAndDate() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("I");
        invoice.setRectifiedInvoices(Collections.singletonList(new InvoiceReference(null, null)));
        ValidationResult result = check(invoice);
        assertError(result, "número de factura obligatorio");
        assertError(result, "fecha de expedición obligatoria");
    }

    @Test
    public void f3NeedsSubstitutedInvoices() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.F3);
        assertError(check(invoice), "debe indicar las facturas que sustituye");
        invoice.setSubstitutedInvoices(Collections.singletonList(new InvoiceReference("FS2600001", TODAY)));
        assertTrue(check(invoice).getErrors().toString(), check(invoice).isValid());
    }

    // ─── Destinatarios ─────────────────────────────────────────────

    @Test
    public void recipientNifMustBeValid() {
        Invoice invoice = invoice();
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente", "B12345678")));
        assertError(check(invoice), "NIF inválido: B12345678");
    }

    @Test
    public void foreignRecipientIsValidatedAsIdOtro() {
        Invoice invoice = invoice();
        invoice.setRecipients(Collections.singletonList(new Recipient("Client SARL", "FR12345678901", "FR", "02")));
        assertTrue(check(invoice).getErrors().toString(), check(invoice).isValid());

        invoice.setRecipients(Collections.singletonList(new Recipient("Client", "X", "ZZ", "02")));
        assertError(check(invoice), "código de país desconocido: ZZ");

        invoice.setRecipients(Collections.singletonList(new Recipient("Client", "X", "FR", "99")));
        assertError(check(invoice), "tipo de documento desconocido: 99");

        invoice.setRecipients(Collections.singletonList(new Recipient("Client", "X", "FR", null)));
        assertError(check(invoice), "tipo de documento obligatorio");

        invoice.setRecipients(Collections.singletonList(
                new Recipient("Client", String.join("", Collections.nCopies(21, "9")), "FR", "03")));
        assertError(check(invoice), "demasiado largo");
    }

    @Test
    public void recipientNameIsRequired() {
        Invoice invoice = invoice();
        invoice.setRecipients(Collections.singletonList(new Recipient(" ", "B12345674")));
        assertError(check(invoice), "razón social obligatoria");
    }

    @Test
    public void f1WithoutRecipientWarnsAndArt61dResolvesIt() {
        Invoice invoice = invoice();
        invoice.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
        assertWarning(check(invoice), "art. 6.1.d");

        invoice.setWithoutRecipientIdentification(true);
        ValidationResult result = check(invoice);
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().toString(), result.getWarnings().isEmpty());
    }

    @Test
    public void art61dCannotCarryRecipients() {
        Invoice invoice = invoice();
        invoice.setWithoutRecipientIdentification(true);
        assertError(check(invoice), "no puede llevar destinatarios");
    }

    @Test
    public void simplifiedInvoiceWithRecipientWarns() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.F2);
        assertWarning(check(invoice), "simplificada");
    }

    // ─── Otros campos ──────────────────────────────────────────────

    @Test
    public void thirdPartyAndPreviousHashAndReference() {
        Invoice invoice = invoice();
        invoice.setIssuedByThirdPartyOrRecipient("T");
        assertError(check(invoice), "thirdPartyName");

        Invoice hash = invoice();
        hash.setPreviousHash("no-es-hex");
        assertError(check(hash), "64 caracteres hexadecimales");

        Invoice ref = invoice();
        ref.setExternalReference(String.join("", Collections.nCopies(61, "R")));
        assertError(check(ref), "referencia externa");
    }

    // ─── Anulaciones ───────────────────────────────────────────────

    @Test
    public void annulment() {
        assertTrue(InvoiceValidator.validate(new Annulment("FCT2600001", TODAY), config(), TODAY).isValid());

        ValidationResult bad = InvoiceValidator.validate(new Annulment(" ", null), config(), TODAY);
        assertError(bad, "Número de factura obligatorio");
        assertError(bad, "Fecha de emisión obligatoria");

        assertError(InvoiceValidator.validate(new Annulment("FCT2600001", TODAY.plusDays(2)), config(), TODAY),
                "posterior a hoy");

        Annulment byThirdParty = new Annulment("FCT2600001", TODAY);
        byThirdParty.setGeneratedBy("T");
        assertError(InvoiceValidator.validate(byThirdParty, config(), TODAY), "generatorName");
        byThirdParty.setGeneratedBy("Q");
        assertError(InvoiceValidator.validate(byThirdParty, config(), TODAY), "GeneradoPor desconocido: Q");
    }

    // ─── Configuracion ─────────────────────────────────────────────

    @Test
    public void configuration() {
        assertTrue(InvoiceValidator.validate(config()).getErrors().toString(), InvoiceValidator.validate(config()).isValid());

        VeriFactuConfig bad = new VeriFactuConfig("", "CIF0000");
        bad.setDeveloperNif("123");
        bad.setSystemId("ABC");
        bad.setSystemName(String.join("", Collections.nCopies(31, "N")));
        bad.setRepresentativeVat("B72877814");
        ValidationResult result = InvoiceValidator.validate(bad);
        assertError(result, "NIF emisor inválido");
        assertError(result, "Razón social del emisor obligatorio");
        assertError(result, "NIF del desarrollador inválido");
        assertError(result, "Identificador del sistema informático demasiado largo");
        assertError(result, "Nombre del sistema informático demasiado largo");
        assertError(result, "representante necesita razón social y NIF");

        assertError(InvoiceValidator.validate((VeriFactuConfig) null), "Configuración obligatoria");
    }

    @Test
    public void resultListsAreImmutableAndSummaryIsOneLine() {
        ValidationResult result = InvoiceValidator.validate(new Invoice(), config(), TODAY);
        assertFalse(result.getErrorSummary().contains("\n"));
        try {
            result.getErrors().add("x");
            org.junit.Assert.fail("La lista debe ser inmutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(UnsupportedOperationException.class, expected.getClass());
        }
    }

    @Test
    public void legacyValidateKeepsTakingTheIssuerFromTheInvoice() {
        Invoice invoice = invoice();
        invoice.setIssuerTaxId("B72877814");
        assertTrue(InvoiceValidator.validate(invoice).getErrors().toString(), InvoiceValidator.validate(invoice).isValid());

        invoice.setIssuerTaxId("B12345678");
        assertError(InvoiceValidator.validate(invoice), "NIF emisor inválido");

    }
}
