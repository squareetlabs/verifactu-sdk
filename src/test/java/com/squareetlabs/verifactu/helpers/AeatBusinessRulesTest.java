package com.squareetlabs.verifactu.helpers;

import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Business validations published by the AEAT in "Validaciones y errores" (v1.2.2) and the official
 * hash and QR specifications. Each test names the rule it covers.
 */
public class AeatBusinessRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private static VeriFactuConfig config() {
        VeriFactuConfig config = com.squareetlabs.verifactu.TestConfig.of("Empresa Test SL", "B72877814");
        config.setDeveloperNif("B72877814");
        return config;
    }

    private static Invoice invoice(String regime, String qualification, double rate, double base, double tax) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FCT2600001");
        invoice.setIssueDate(TODAY);
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(base + tax);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown(regime, qualification, rate, base, tax)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        return invoice;
    }

    private static Invoice invoice() {
        return invoice("01", "S1", 21, 100, 21);
    }

    private static ValidationResult check(Invoice invoice) {
        return InvoiceValidator.validate(invoice, config(), TODAY);
    }

    private static void assertError(ValidationResult result, String fragment) {
        assertFalse("Debia ser invalida: " + fragment, result.isValid());
        assertTrue(result.getErrors().toString(), result.getErrors().toString().contains(fragment));
    }

    private static void assertValid(ValidationResult result) {
        assertTrue(result.getErrors().toString(), result.isValid());
    }

    // ─── IDFactura ─────────────────────────────────────────────────

    @Test
    public void issueDateCannotBeBeforeOctober28th2024() {
        Invoice invoice = invoice();
        invoice.setIssueDate(LocalDate.of(2024, 10, 27));
        assertError(check(invoice), "anterior al 28/10/2024");
        invoice.setIssueDate(LocalDate.of(2024, 10, 28));
        assertValid(InvoiceValidator.validate(invoice, config(), LocalDate.of(2024, 11, 5)));
    }

    @Test
    public void invoiceNumberOnlyAllowsPrintableAsciiWithoutQuotesAnglesOrEquals() {
        for (String bad : new String[] {"F\"1", "F'1", "F<1", "F>1", "F=1", "Factura ñ1", "F\t1"}) {
            Invoice invoice = invoice();
            invoice.setInvoiceNumber(bad);
            assertError(check(invoice), "número de factura");
        }
        Invoice ok = invoice();
        ok.setInvoiceNumber("A/2026-1 (bis)");
        assertValid(check(ok));
    }

    // ─── Tipo impositivo, cuota y recargo ──────────────────────────

    @Test
    public void vatRatesAreRestrictedAndSomeAreDated() {
        for (double rate : new double[] {0, 4, 10, 21}) {
            assertValid(check(invoice("01", "S1", rate, 100, rate)));
        }
        assertError(check(invoice("01", "S1", 12, 100, 12)), "Tipo impositivo no admitido para IVA: 12");
        // 5 %, 2 % and 7,5 % only exist in temporary windows (reference = operation date or issue date)
        assertError(check(invoice("01", "S1", 5, 100, 5)), "no admitido para IVA: 5");
        Invoice winter2024 = invoice("01", "S1", 7.5, 100, 7.5);
        winter2024.setIssueDate(LocalDate.of(2024, 11, 5));
        assertValid(InvoiceValidator.validate(winter2024, config(), LocalDate.of(2024, 11, 6)));
        Invoice earlier = invoice("01", "S1", 5, 100, 5);
        earlier.setOperationDate(LocalDate.of(2024, 9, 30));
        earlier.setIssueDate(LocalDate.of(2024, 11, 5));
        assertValid(InvoiceValidator.validate(earlier, config(), LocalDate.of(2024, 11, 6)));
    }

    @Test
    public void quotaMustMatchBaseTimesRateWithinTenEurosAndHaveTheSameSign() {
        assertValid(check(invoice("01", "S1", 21, 100, 29.9))); // 8,90 de diferencia
        assertError(check(invoice("01", "S1", 21, 100, 40)), "cuota repercutida incoherente");
        assertError(check(invoice("01", "S1", 21, 100, -21)), "mismo signo");
        // Rectificativa por diferencias: sin esta comprobacion
        Invoice difference = invoice("01", "S1", 21, 100, -21);
        difference.setInvoiceType(InvoiceType.R1);
        difference.setCorrectionType("I");
        difference.setRectifiedInvoices(Collections.singletonList(new InvoiceReference("F1", TODAY.minusDays(3))));
        assertValid(check(difference));
    }

    @Test
    public void surchargeRatesDependOnTheVatRate() {
        Breakdown ok = new Breakdown("01", "S1", 21, 100, 21);
        ok.setTipoRecargoEquivalencia(5.2);
        ok.setCuotaRecargoEquivalencia(5.2);
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(ok));
        invoice.setTaxAmount(26.2);
        invoice.setTotalAmount(126.2);
        assertValid(check(invoice));

        Breakdown bad = new Breakdown("01", "S1", 21, 100, 21);
        bad.setTipoRecargoEquivalencia(1.4);
        invoice.setBreakdowns(Collections.singletonList(bad));
        assertError(check(invoice), "recargo de equivalencia 1.4");
    }

    // ─── Calificacion, exencion y claves de regimen ────────────────

    @Test
    public void reverseChargeS2NeedsZeroRateAndOnlyFor_F1F3R1toR4() {
        Invoice invoice = invoice("01", "S2", 0, 100, 0);
        assertValid(check(invoice));
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S2", 21, 100, 21)));
        assertError(check(invoice), "S2");
        Invoice f2 = invoice("01", "S2", 0, 100, 0);
        f2.setInvoiceType(InvoiceType.F2);
        f2.setRecipients(Collections.<VeriFactuRecipient>emptyList());
        assertError(check(f2), "S2 solo admite facturas");
    }

    @Test
    public void regime02OnlyAdmitsExemptOperations() {
        assertError(check(invoice("02", "S1", 21, 100, 21)), "ClaveRegimen 02");
        Breakdown exempt = new Breakdown();
        exempt.setClaveRegimen("02");
        exempt.setOperacionExenta("E2");
        exempt.setBaseAmount(100);
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(exempt));
        invoice.setTaxAmount(0);
        invoice.setTotalAmount(100);
        assertValid(check(invoice));
        exempt.setClaveRegimen("01"); // con la clave 01 no valen E2 ni E3
        assertError(check(invoice), "E2 y E3");
    }

    @Test
    public void otherRegimeRules() {
        assertError(check(invoice("08", "S1", 21, 100, 21)), "ClaveRegimen 08 exige");
        assertError(check(invoice("10", "S1", 21, 100, 21)), "ClaveRegimen 10");
        assertError(check(invoice("11", "S1", 10, 100, 10)), "ClaveRegimen 11");
        assertValid(check(invoice("11", "S1", 21, 100, 21)));
        assertError(check(invoice("06", "S1", 21, 100, 21)), "BaseImponibleACoste");
        assertError(check(invoice("07", "S2", 0, 100, 0)), "ClaveRegimen 07");
        assertError(check(invoice("03", "N1", 0, 100, 0)), "ClaveRegimen 03");
        assertError(check(invoice("04", "S1", 21, 100, 21)), "ClaveRegimen 04");
        assertError(check(invoice(null, "S1", 21, 100, 21)), "ClaveRegimen obligatoria");
        assertError(check(invoice("99", "S1", 21, 100, 21)), "ClaveRegimen no válida para IVA");
    }

    @Test
    public void baseImponibleACosteOnlyWithRegime06() {
        Breakdown line = new Breakdown("01", "S1", 21, 100, 21);
        line.setBaseImponibleACoste(100.0);
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(line));
        assertError(check(invoice), "BaseImponibleACoste solo puede informarse con ClaveRegimen 06");
    }

    @Test
    public void igicSkipsTheVatSpecificRules() {
        Breakdown igic = new Breakdown("01", "S1", 7, 100, 7);
        igic.setTax("03");
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(igic));
        invoice.setTaxAmount(7);
        invoice.setTotalAmount(107);
        assertValid(check(invoice));
        igic.setTax("04");
        assertError(check(invoice), "Impuesto desconocido");
    }

    @Test
    public void dateOfOperationRules() {
        Invoice invoice = invoice();
        invoice.setOperationDate(TODAY.plusDays(5));
        assertError(check(invoice), "solo puede ser posterior a hoy con ClaveRegimen 14 o 15");
        invoice.setOperationDate(TODAY.minusYears(21));
        assertError(check(invoice), "entre hace 20 años");
        invoice.setOperationDate(TODAY.minusDays(3)); // anterior a la expedicion: valido
        assertValid(check(invoice));
        invoice.setOperationDate(TODAY.plusDays(1)); // expedicion anterior a la operacion
        assertError(check(invoice), "La fecha de expedición solo puede ser anterior");
    }

    // ─── Destinatarios extranjeros ─────────────────────────────────

    @Test
    public void idOtroRules() {
        Invoice spain = invoice();
        spain.setRecipients(Collections.singletonList(new Recipient("Cliente", "X1234567", "ES", "04")));
        assertError(check(spain), "con país ES");
        spain.setRecipients(Collections.singletonList(new Recipient("Cliente", "X1234567", "ES", "03")));
        assertValid(check(spain));

        Invoice notCensused = invoice();
        notCensused.setRecipients(Collections.singletonList(new Recipient("Cliente", "12345678", "FR", "07")));
        assertError(check(notCensused), "el tipo 07");
        notCensused.setRecipients(Collections.singletonList(new Recipient("Cliente", "12345678", "ES", "07")));
        ValidationResult accepted = check(notCensused);
        assertValid(accepted);
        assertTrue(accepted.getWarnings().toString(), accepted.getWarnings().toString().contains("no censado"));
    }

    @Test
    public void euVatNumberStructureIsChecked() {
        String[][] valid = {{"DE123456789"}, {"FR12345678901"}, {"IT12345678901"}, {"NL123456789B01"},
                {"PT123456789"}, {"XI123456789"}, {"RO123456789"}, {"IE1234567AB"}};
        for (String[] id : valid) {
            Invoice invoice = invoice();
            invoice.setRecipients(Collections.singletonList(new Recipient("Client", id[0], null, "02")));
            assertValid(check(invoice));
        }
        String[] invalid = {"DE12345", "FR", "XX123456789", "IT1234567890A", "RO0123456789", "ES12345678"};
        for (String id : invalid) {
            Invoice invoice = invoice();
            invoice.setRecipients(Collections.singletonList(new Recipient("Client", id, null, "02")));
            assertFalse(id, check(invoice).isValid());
        }
        Invoice gb = invoice();
        gb.setRecipients(Collections.singletonList(new Recipient("Client", "GB123456789", null, "02")));
        assertError(check(gb), "XI");
    }

    @Test
    public void regimes10And14RequireNifRecipients() {
        Invoice foreign = invoice("14", "S1", 21, 100, 21);
        foreign.setOperationDate(TODAY.plusDays(10));
        foreign.setRecipients(Collections.singletonList(new Recipient("Client", "FR12345678901", "FR", "02")));
        assertError(check(foreign), "ClaveRegimen 14");

        Invoice publicBody = invoice("14", "S1", 21, 100, 21);
        publicBody.setOperationDate(TODAY.plusDays(10));
        publicBody.setRecipients(Collections.singletonList(new Recipient("Ayuntamiento", "P2800000A")));
        // P2800000A: NIF de una administracion publica (formato correcto)
        assertFalse(check(publicBody).getErrors().toString().contains("empiece por P, Q, S o V"));
        publicBody.setRecipients(Collections.singletonList(new Recipient("Cliente", "B12345674")));
        assertError(check(publicBody), "empiece por P, Q, S o V");
        publicBody.setOperationDate(null);
        assertError(check(publicBody), "exige la fecha de operación");
    }

    // ─── Subsanacion, tercero ──────────────────────────────────────

    @Test
    public void previousRejectionRequiresSubsanation() {
        Invoice invoice = invoice();
        invoice.setPreviousRejection("X");
        assertError(check(invoice), "solo puede informarse junto con Subsanacion = S");
        invoice.setSubsanation(true);
        assertValid(check(invoice));
        invoice.setPreviousRejection("Z");
        assertError(check(invoice), "RechazoPrevio desconocido");
    }

    @Test
    public void thirdPartyAndRecipientIssuerRules() {
        Invoice invoice = invoice();
        invoice.setIssuedByThirdPartyOrRecipient("T");
        invoice.setThirdPartyName("Gestoria SL");
        invoice.setThirdPartyTaxId("B72877814"); // igual que el emisor
        assertError(check(invoice), "distinto del NIF del emisor");
        invoice.setThirdPartyTaxId("B12345674");
        assertValid(check(invoice));
        Invoice byRecipient = invoice();
        byRecipient.setIssuedByThirdPartyOrRecipient("D");
        assertValid(check(byRecipient));
        byRecipient.setRecipients(Collections.<VeriFactuRecipient>emptyList());
        assertError(check(byRecipient), "emite el destinatario");
    }

    // ─── Anulaciones y configuracion ───────────────────────────────

    @Test
    public void annulmentGeneratorMustDifferFromTheIssuer() {
        Annulment annulment = new Annulment("FCT2600001", TODAY);
        annulment.setGeneratedBy("T");
        annulment.setGeneratorName("Gestoria SL");
        annulment.setGeneratorTaxId("B72877814");
        assertFalse(InvoiceValidator.validate(annulment, config(), TODAY).isValid());
        annulment.setGeneratorTaxId("B12345674");
        assertTrue(InvoiceValidator.validate(annulment, config(), TODAY).isValid());
    }

    @Test
    public void systemIdMustBeTwoUppercaseLettersOrDigits() {
        for (String ok : new String[] {"01", "AB", "A1", "9Z"}) {
            VeriFactuConfig config = config();
            config.setSystemId(ok);
            assertTrue(ok, InvoiceValidator.validate(config).isValid());
        }
        for (String bad : new String[] {"1", "ABC", "ab", "Ñ1", "A-", ""}) {
            VeriFactuConfig config = config();
            config.setSystemId(bad);
            assertFalse(bad, InvoiceValidator.validate(config).isValid());
        }
    }

    // ─── Listas L8A / L8B (hoja "6)Listas" del diseño de registro) ──

    @Test
    public void regimeKeysDependOnTheTax() {
        // L8A (IVA): 01-11, 14, 15, 17, 18, 19, 20. La 21 solo existe para IGIC.
        for (String ok : new String[] {"01", "05", "09", "11", "15", "17", "18", "19", "20"}) {
            assertFalse(ok, check(invoice(ok, "S1", 21, 100, 21)).getErrors().toString().contains("lista L8A"));
        }
        assertError(check(invoice("21", "S1", 21, 100, 21)), "lista L8A");
        assertError(check(invoice("12", "S1", 21, 100, 21)), "ClaveRegimen no válida para IVA");

        Breakdown igic21 = new Breakdown("21", "S1", 7, 100, 7);
        igic21.setTax("03");
        Invoice igic = invoice();
        igic.setBreakdowns(Collections.singletonList(igic21));
        igic.setTaxAmount(7);
        igic.setTotalAmount(107);
        assertValid(check(igic));
        Breakdown igic12 = new Breakdown("12", "S1", 7, 100, 7);
        igic12.setTax("03");
        igic.setBreakdowns(Collections.singletonList(igic12));
        assertError(check(igic), "lista L8B");

        // IPSI: claves 01, 08, 11, 18, 19 y 20; otra clave solo avisa hasta el 31/12/2026.
        Breakdown ipsi = new Breakdown("02", "S1", 4, 100, 4);
        ipsi.setTax("02");
        Invoice ipsiInvoice = invoice();
        ipsiInvoice.setBreakdowns(Collections.singletonList(ipsi));
        ipsiInvoice.setTaxAmount(4);
        ipsiInvoice.setTotalAmount(104);
        ValidationResult result = check(ipsiInvoice);
        assertValid(result);
        assertTrue(result.getWarnings().toString(), result.getWarnings().toString().contains("IPSI"));
    }

    @Test
    public void couponOnlyInR1AndR5() {
        Invoice f1 = invoice();
        f1.setCoupon(true);
        assertError(check(f1), "Cupon solo puede informarse en facturas R1 o R5");
        Invoice r1 = invoice();
        r1.setInvoiceType(InvoiceType.R1);
        r1.setCorrectionType("I");
        r1.setRectifiedInvoices(Collections.singletonList(new InvoiceReference("F1", TODAY.minusDays(2))));
        r1.setCoupon(true);
        assertValid(check(r1));
    }

    // ─── Huella y QR oficiales ─────────────────────────────────────

    /** Vectores del documento oficial "Detalle de las especificaciones técnicas para generación de la huella" (v0.1.2). */
    @Test
    public void officialHashVectors() {
        assertEquals("3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60",
                HashHelper.generateInvoiceHash("89890001K", "12345678/G33", "01-01-2024", "F1", "12.35", "123.45",
                        "", "2024-01-01T19:20:30+01:00"));
        assertEquals("F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97",
                HashHelper.generateInvoiceHash("89890001K", "12345679/G34", "01-01-2024", "F1", "12.35", "123.45",
                        "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60",
                        "2024-01-01T19:20:35+01:00"));
        assertEquals("177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68",
                HashHelper.generateAnnulmentHash("89890001K", "12345679/G34", "01-01-2024",
                        "F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97",
                        "2024-01-01T19:20:40+01:00"));
    }

    @Test
    public void hashTrimsValuesAndIgnoresTrailingZerosOfAmountsOnlyByNormalisingTheInput() {
        String trimmed = HashHelper.generateInvoiceHash("89890001K", "12345678/G33", "01-01-2024", "F1", "12.35",
                "123.45", "", "2024-01-01T19:20:30+01:00");
        String padded = HashHelper.generateInvoiceHash(" 89890001K ", "  12345678/G33  ", "01-01-2024", "F1",
                "12.35", "123.45", "", "2024-01-01T19:20:30+01:00");
        assertEquals(trimmed, padded);
    }

    /** Documento oficial "Características del QR" (v0.5.0), apartados 4 y 5. */
    @Test
    public void officialQrUrls() {
        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("89890001K");
        invoice.setInvoiceNumber("12345678&G33");
        invoice.setIssueDate(LocalDate.of(2024, 1, 1));
        invoice.setTotalAmount(241.4);

        // Ejemplo oficial: el '&' del numero de serie se codifica como %26.
        assertEquals("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=89890001K&numserie=12345678%26G33"
                + "&fecha=01-01-2024&importe=241.40", invoice.getValidationUrl(false));
        assertEquals("https://www2.agenciatributaria.gob.es/wlpl/TIKE-CONT/ValidarQR?nif=89890001K"
                + "&numserie=12345678%26G33&fecha=01-01-2024&importe=241.40", invoice.getValidationUrl(true));
        // Sistemas que no emiten facturas verificables: otro servicio.
        assertEquals("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQRNoVerifactu?nif=89890001K"
                + "&numserie=12345678%26G33&fecha=01-01-2024&importe=241.40", invoice.getValidationUrl(false, false));
        assertEquals("https://www2.agenciatributaria.gob.es/wlpl/TIKE-CONT/ValidarQRNoVerifactu?nif=89890001K"
                + "&numserie=12345678%26G33&fecha=01-01-2024&importe=241.40", invoice.getValidationUrl(true, false));
    }

    // ─── Correcciones de la revision independiente (1.4.2) ───────────────

    private static Invoice igicInvoice(String regime, String qualification, double rate) {
        Breakdown igic = new Breakdown(regime, qualification, rate, 100, rate);
        igic.setTax("03");
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(igic));
        invoice.setTaxAmount(rate);
        invoice.setTotalAmount(100 + rate);
        return invoice;
    }

    @Test
    public void igicRegime11AcceptsItsOwnRatesAndVatRegime11StillNeedsTwentyOnePercent() {
        assertValid(check(igicInvoice("11", "S1", 7)));
        assertError(check(invoice("11", "S1", 10, 100, 10)), "ClaveRegimen 11");
    }

    @Test
    public void igicRegime20NeedsOperationQualificationN2() {
        assertError(check(igicInvoice("20", "S1", 7)), "ClaveRegimen 20");
        Breakdown nonSubject = new Breakdown("20", "N2", 0, 100, 0);
        nonSubject.setTax("03");
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(nonSubject));
        invoice.setTaxAmount(0);
        invoice.setTotalAmount(100);
        assertValid(check(invoice));
    }

    @Test
    public void ipsiRegimeIsAWarningUntilTheEndOf2026AndAnErrorAfterwards() {
        Breakdown ipsi = new Breakdown("", "S1", 10, 100, 10);
        ipsi.setTax("02");
        Invoice invoice = invoice();
        invoice.setBreakdowns(Collections.singletonList(ipsi));
        invoice.setTaxAmount(10);
        invoice.setTotalAmount(110);

        ValidationResult before = InvoiceValidator.validate(invoice, config(), LocalDate.of(2026, 12, 31));
        assertTrue(before.getErrors().toString(), before.isValid());
        assertTrue(before.getWarnings().toString(), before.getWarnings().stream().anyMatch(w -> w.contains("IPSI")));

        invoice.setIssueDate(LocalDate.of(2027, 1, 1));
        assertError(InvoiceValidator.validate(invoice, config(), LocalDate.of(2027, 1, 1)), "ClaveRegimen obligatoria para IPSI");
    }

    @Test
    public void nonFiniteAmountsInSurchargeCostBaseAndCorrectedAmountsAreErrors() {
        Invoice invoice = invoice();
        ((Breakdown) invoice.getBreakdowns().get(0)).setCuotaRecargoEquivalencia(Double.NaN);
        assertError(check(invoice), "CuotaRecargoEquivalencia");

        Invoice corrective = invoice("01", "S1", 21, 100, 21);
        corrective.setInvoiceType(InvoiceType.R1);
        corrective.setCorrectionType("S");
        corrective.setCorrectedBaseAmount(Double.NaN);
        corrective.setCorrectedTaxAmount(21.0);
        assertError(check(corrective), "correctedBaseAmount");
    }
}
