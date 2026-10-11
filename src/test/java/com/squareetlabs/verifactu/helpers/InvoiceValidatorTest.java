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
        VeriFactuConfig config = com.squareetlabs.verifactu.TestConfig.of("Empresa Test SL", "B72877814");
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
        VeriFactuConfig bad = com.squareetlabs.verifactu.TestConfig.of("Empresa Test SL", "CIF0000");
        ValidationResult result = InvoiceValidator.validate(invoice(), bad, TODAY);
        assertError(result, "NIF emisor inválido: CIF0000");
    }

    @Test
    public void specialSpanishNifsAreAccepted() {
        VeriFactuConfig k = com.squareetlabs.verifactu.TestConfig.of("Persona", "K1234567L");
        assertTrue(InvoiceValidator.validate(invoice(), k, TODAY).isValid());
    }

    @Test
    public void issuerNameRequiredWhenValidatingWithConfig() {
        VeriFactuConfig noName = com.squareetlabs.verifactu.TestConfig.of(null, "B72877814");
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
        assertError(check(invoice), "ClaveRegimen no válida para IVA (lista L8A): 99");
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
    public void totalsMismatchOnlyWarnsAndHasTenEuroMargin() {
        Invoice invoice = invoice();
        invoice.setTotalAmount(33.0); // 8,80 de diferencia: dentro del margen
        assertTrue(check(invoice).getWarnings().toString(), check(invoice).getWarnings().isEmpty());

        invoice.setTotalAmount(999.99);
        ValidationResult result = check(invoice);
        assertTrue(result.getErrors().toString(), result.isValid());
        assertWarning(result, "Importe total incoherente");

        Invoice tax = invoice();
        tax.setTaxAmount(40);
        assertWarning(check(tax), "Cuota total incoherente");
    }

    @Test
    public void totalsAreNotCheckedForRegimes03050608And09() {
        for (String regime : new String[] {"03", "05", "09"}) {
            Invoice invoice = invoice();
            invoice.setBreakdowns(Collections.singletonList(new Breakdown(regime, "S1", 21, 20, 4.2)));
            invoice.setTotalAmount(999.99);
            assertTrue(regime, check(invoice).getWarnings().isEmpty());
        }
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
    public void substitutedInvoicesAreOptionalInF3AndForbiddenElsewhere() {
        Invoice f3 = invoice();
        f3.setInvoiceType(InvoiceType.F3);
        assertTrue(check(f3).getErrors().toString(), check(f3).isValid());
        f3.setSubstitutedInvoices(Collections.singletonList(new InvoiceReference("FS2600001", TODAY)));
        assertTrue(check(f3).getErrors().toString(), check(f3).isValid());

        Invoice f1 = invoice();
        f1.setSubstitutedInvoices(Collections.singletonList(new InvoiceReference("FS2600001", TODAY)));
        assertError(check(f1), "solo pueden informarse en facturas F3");
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
    public void recipientsAreMandatoryInF1F3R1toR4AndForbiddenInF2R5() {
        for (InvoiceType type : new InvoiceType[] {InvoiceType.F1, InvoiceType.F3}) {
            Invoice invoice = invoice();
            invoice.setInvoiceType(type);
            invoice.setSubstitutedInvoices(type == InvoiceType.F3
                    ? Collections.singletonList(new InvoiceReference("FS2600001", TODAY)) : Collections.emptyList());
            invoice.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
            assertError(check(invoice), "debe identificar al destinatario");
        }
        Invoice r1 = invoice();
        r1.setInvoiceType(InvoiceType.R1);
        r1.setCorrectionType("I");
        r1.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
        assertError(check(r1), "debe identificar al destinatario");

        Invoice f2 = invoice();
        f2.setInvoiceType(InvoiceType.F2);
        assertError(check(f2), "no puede llevar destinatarios");
        f2.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
        assertTrue(check(f2).getErrors().toString(), check(f2).isValid());
    }

    @Test
    public void art61dFlagOnlyInF2AndR5() {
        Invoice f2 = invoice();
        f2.setInvoiceType(InvoiceType.F2);
        f2.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
        f2.setWithoutRecipientIdentification(true);
        ValidationResult ok = check(f2);
        assertTrue(ok.getErrors().toString(), ok.isValid());
        assertTrue(ok.getWarnings().toString(), ok.getWarnings().isEmpty());

        Invoice f1 = invoice();
        f1.setWithoutRecipientIdentification(true);
        assertError(check(f1), "solo puede informarse en facturas F2 o R5");
    }

    @Test
    public void art61dWithRecipientsInF2IsRejected() {
        Invoice invoice = invoice();
        invoice.setInvoiceType(InvoiceType.F2);
        invoice.setWithoutRecipientIdentification(true);
        assertError(check(invoice), "no puede llevar destinatarios");
    }

    @Test
    public void simplifiedInvoiceLimitAndQualifiedFlag() {
        Invoice big = invoice();
        big.setInvoiceType(InvoiceType.F2);
        big.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
        big.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21, 3000, 630)));
        big.setTaxAmount(630);
        big.setTotalAmount(3630);
        assertError(check(big), "no puede superar 3.000,00");
        big.setWithoutRecipientIdentification(true); // el limite no se aplica con el art. 6.1.d
        assertTrue(check(big).getErrors().toString(), check(big).isValid());

        Invoice qualified = invoice();
        qualified.setQualifiedSimplified(true);
        assertTrue(check(qualified).getErrors().toString(), check(qualified).isValid());
        Invoice f2 = invoice();
        f2.setInvoiceType(InvoiceType.F2);
        f2.setRecipients(Collections.<com.squareetlabs.verifactu.contracts.VeriFactuRecipient>emptyList());
        f2.setQualifiedSimplified(true);
        assertError(check(f2), "FacturaSimplificadaArt7273 solo puede informarse");
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

        VeriFactuConfig bad = com.squareetlabs.verifactu.TestConfig.of("", "CIF0000");
        bad.setDeveloperNif("123");
        bad.setSystemId("ABC");
        bad.setSystemName(String.join("", Collections.nCopies(31, "N")));
        bad.setRepresentativeVat("B72877814");
        ValidationResult result = InvoiceValidator.validate(bad);
        assertError(result, "NIF emisor inválido");
        assertError(result, "Razón social del emisor obligatorio");
        assertError(result, "NIF del desarrollador inválido");
        assertError(result, "debe tener exactamente dos posiciones");
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
