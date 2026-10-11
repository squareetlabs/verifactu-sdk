package com.squareetlabs.verifactu.helpers;

import com.squareetlabs.verifactu.contracts.VeriFactuAnnulment;
import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoiceReference;
import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Local validator for the data sent to the AEAT (invoices, annulments and the issuer / system
 * configuration). It applies the business validations published by the AEAT in the document
 * <em>Validaciones y errores</em> (Sistemas Informáticos de Facturación y Sistemas VERI*FACTU,
 * v1.2.2) so that a bad record fails fast with a readable message instead of coming back as a
 * rejection.
 * <p>
 * Findings are split in two lists:
 * <ul>
 * <li><b>errors</b>: the AEAT rejects the record ("errores no admisibles") or the SDK cannot
 * represent it. The result is not valid and
 * {@link com.squareetlabs.verifactu.services.AeatClient} refuses to send it.</li>
 * <li><b>warnings</b>: data the AEAT accepts but flags ("aceptado con errores"), or suspicious
 * data. They never make the result invalid.</li>
 * </ul>
 * Negative amounts are legal (corrective invoices by difference carry them): they are never an
 * error by themselves. The validator cannot check what only the AEAT knows: that a NIF is
 * registered in its census or that the issuer authorised the representative.
 */
public class InvoiceValidator {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // Enumerations of SuministroInformacion.xsd.
    private static final Set<String> INVOICE_TYPES = set("F1", "F2", "F3", "R1", "R2", "R3", "R4", "R5");
    private static final Set<String> CORRECTIVE_TYPES = set("R1", "R2", "R3", "R4", "R5");
    /** Types that identify the recipient (Destinatarios mandatory). */
    private static final Set<String> RECIPIENT_TYPES = set("F1", "F3", "R1", "R2", "R3", "R4");
    private static final Set<String> QUALIFICATIONS = set("S1", "S2", "N1", "N2");
    private static final Set<String> EXEMPTIONS = set("E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8");
    /** L8A: ClaveRegimen when the tax is IVA (or not informed). */
    private static final Set<String> REGIMES_IVA = set("01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "14", "15", "17", "18", "19", "20");
    /** L8B plus 20 (IPSI) and 21 (simplified regime): ClaveRegimen when the tax is IGIC. */
    private static final Set<String> REGIMES_IGIC = set("01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "14", "15", "17", "18", "19", "20", "21");
    /** ClaveRegimen admitted when the tax is IPSI. */
    private static final Set<String> REGIMES_IPSI = set("01", "08", "11", "18", "19", "20");
    /** Regimes for which the AEAT does not check the total amounts. */
    private static final Set<String> TOTALS_EXEMPT_REGIMES = set("03", "05", "06", "08", "09");
    private static final Set<String> TAXES = set("01", "02", "03", "05");
    private static final Set<String> CORRECTION_TYPES = set("S", "I");
    private static final Set<String> THIRD_PARTY = set("D", "T");
    private static final Set<String> PREVIOUS_REJECTION = set("N", "S", "X");
    private static final Set<String> YES_NO = set("S", "N");
    private static final Set<String> FOREIGN_ID_TYPES = set("02", "03", "04", "05", "06", "07");

    private static final Pattern HASH = Pattern.compile("[0-9A-Fa-f]{64}");
    /** Spanish special NIFs (K, L, M): accepted by format, their check character is not verified. */
    private static final Pattern SPECIAL_NIF = Pattern.compile("^[KLM][0-9]{7}[0-9A-Z]$");
    private static final Pattern SYSTEM_ID = Pattern.compile("^[A-Z0-9]{2}$");

    /** Structure of the intra-community VAT numbers (without the country prefix). */
    private static final Map<String, Pattern> VAT_ID = new HashMap<>();

    static {
        vat("DE", "\\d{9}");
        vat("AT", "[A-Z0-9]{9}");
        vat("BE", "\\d{10}");
        vat("CY", "[A-Z0-9]{9}");
        vat("CZ", "\\d{8,10}");
        vat("HR", "\\d{11}");
        vat("DK", "\\d{8}");
        vat("SK", "\\d{10}");
        vat("SI", "\\d{8}");
        vat("EE", "\\d{9}");
        vat("FI", "\\d{8}");
        vat("FR", "[A-Z0-9]{11}");
        vat("EL", "\\d{9}");
        vat("GB", "[A-Z0-9]{5}|[A-Z0-9]{9}|[A-Z0-9]{12}");
        vat("XI", "[A-Z0-9]{5}|[A-Z0-9]{9}|[A-Z0-9]{12}");
        vat("NL", "[A-Z0-9]{12}");
        vat("HU", "\\d{8}");
        vat("IT", "\\d{11}");
        vat("IE", "[A-Z0-9]{8,9}");
        vat("LV", "\\d{11}");
        vat("LT", "\\d{9}|\\d{12}");
        vat("LU", "\\d{8}");
        vat("MT", "\\d{8}");
        vat("PL", "\\d{10}");
        vat("PT", "\\d{9}");
        vat("SE", "\\d{12}");
        vat("BG", "\\d{9,10}");
        vat("RO", "[1-9]\\d{1,9}");
    }

    private static final LocalDate FIRST_VALID_ISSUE_DATE = LocalDate.of(2024, 10, 28);
    private static final int MAX_BREAKDOWNS = 12;
    private static final double MAX_AMOUNT = 999_999_999_999.99;
    /** The AEAT admits a margin of 10.00 euros in the amount checks. */
    private static final double MARGIN = 10.00;
    private static final double SIMPLIFIED_LIMIT = 3000.00;

    private InvoiceValidator() {
    }

    // ─── Invoices ──────────────────────────────────────────────────

    /**
     * Validates an invoice taking the issuer from the invoice itself
     * ({@code Invoice#getIssuerTaxId}/{@code getIssuerName}), when available.
     */
    public static ValidationResult validate(VeriFactuInvoice invoice) {
        return validateInvoice(invoice, issuerTaxIdOf(invoice), issuerNameOf(invoice), false,
                LocalDate.now(MADRID));
    }

    /**
     * Validates an invoice as {@link com.squareetlabs.verifactu.services.AeatClient} will send it:
     * the issuer (obligado) is the one in the configuration.
     */
    public static ValidationResult validate(VeriFactuInvoice invoice, VeriFactuConfig config) {
        return validate(invoice, config, LocalDate.now(MADRID));
    }

    static ValidationResult validate(VeriFactuInvoice invoice, VeriFactuConfig config, LocalDate today) {
        return validateInvoice(invoice, config != null ? config.getIssuerVat() : null,
                config != null ? config.getIssuerName() : null, true, today);
    }

    private static ValidationResult validateInvoice(VeriFactuInvoice invoice, String issuerTaxId,
            String issuerName, boolean issuerNameRequired, LocalDate today) {
        Findings f = new Findings();

        String number = invoice.getInvoiceNumber();
        checkInvoiceNumber(f, number);

        LocalDate issueDate = invoice.getIssueDate();
        if (issueDate == null) {
            f.error("Fecha de emisión obligatoria");
        } else if (issueDate.isAfter(today)) {
            f.error("La fecha de emisión no puede ser posterior a hoy: " + issueDate);
        } else if (issueDate.isBefore(FIRST_VALID_ISSUE_DATE)) {
            f.error("La fecha de emisión no puede ser anterior al 28/10/2024 (entrada en vigor de la "
                    + "orden ministerial de VERI*FACTU): " + issueDate);
        }

        String type = invoice.getInvoiceType();
        if (isBlank(type)) {
            f.error("Tipo de factura obligatorio");
        } else if (!INVOICE_TYPES.contains(type)) {
            f.error("Tipo de factura no admitido por la AEAT: " + type
                    + " (válidos: F1, F2, F3, R1, R2, R3, R4, R5)");
        }
        boolean corrective = type != null && CORRECTIVE_TYPES.contains(type);

        if (isBlank(issuerTaxId)) {
            f.error("NIF emisor obligatorio");
        } else if (!isValidSpanishId(issuerTaxId)) {
            f.error("NIF emisor inválido: " + issuerTaxId);
        }
        if (issuerNameRequired && isBlank(issuerName)) {
            f.error("Razón social del emisor obligatoria");
        } else if (issuerName != null && issuerName.length() > 120) {
            f.error("Razón social del emisor demasiado larga (máximo 120 caracteres)");
        }

        checkAmount(f, "Importe total", invoice.getTotalAmount());
        checkAmount(f, "Cuota total", invoice.getTaxAmount());
        if (!corrective && (invoice.getTotalAmount() < 0 || invoice.getTaxAmount() < 0)) {
            f.warning("Importe negativo en una factura que no es rectificativa: revisa si debería serlo");
        }

        LocalDate reference = invoice.getOperationDate() != null ? invoice.getOperationDate() : issueDate;
        Set<String> regimes = regimesOf(invoice);

        checkBreakdowns(f, invoice, type, reference, regimes);
        checkCorrection(f, invoice, type, corrective);
        checkRecipients(f, invoice, type, regimes, reference);
        checkDates(f, invoice, issueDate, today, regimes);
        checkOptionalFields(f, invoice, issuerTaxId);

        return f.result();
    }

    private static void checkInvoiceNumber(Findings f, String number) {
        if (isBlank(number)) {
            f.error("Número de factura obligatorio");
            return;
        }
        if (number.trim().length() > 60) {
            f.error("Número de factura demasiado largo (máximo 60 caracteres): " + number.trim().length());
        }
        for (char c : number.trim().toCharArray()) {
            if (c < 32 || c > 126) {
                f.error("El número de factura solo admite caracteres ASCII imprimibles (32 a 126): '" + c + "'");
                break;
            }
            if (c == '"' || c == '\'' || c == '<' || c == '>' || c == '=') {
                f.error("El número de factura no admite los caracteres \" ' < > =: '" + c + "'");
                break;
            }
        }
    }

    private static void checkBreakdowns(Findings f, VeriFactuInvoice invoice, String type, LocalDate reference,
            Set<String> regimes) {
        List<VeriFactuBreakdown> lines = invoice.getBreakdowns();
        if (lines == null || lines.isEmpty()) {
            f.error("Desglose obligatorio: la factura necesita al menos una línea de desglose");
            return;
        }
        if (lines.size() > MAX_BREAKDOWNS) {
            f.error("Demasiadas líneas de desglose (máximo " + MAX_BREAKDOWNS + "): " + lines.size());
        }

        boolean byDifferenceOrR2R3 = "I".equals(invoice.getCorrectionType()) || "R2".equals(type)
                || "R3".equals(type);
        double base = 0;
        double tax = 0;
        double simplifiedSum = 0;
        for (int i = 0; i < lines.size(); i++) {
            VeriFactuBreakdown b = lines.get(i);
            String label = "Desglose " + (i + 1) + ": ";
            if (b == null) {
                f.error(label + "línea vacía");
                continue;
            }
            checkAmount(f, label + "base imponible", b.getBaseAmount());

            String taxCode = b.getTax();
            if (!isBlank(taxCode) && !TAXES.contains(taxCode)) {
                f.error(label + "Impuesto desconocido: " + taxCode + " (válidos: 01 IVA, 02 IPSI, 03 IGIC, 05 otros)");
            }
            boolean iva = isBlank(taxCode) || "01".equals(taxCode);
            boolean ivaOrIgic = iva || "03".equals(taxCode);

            String qualification = b.getCalificacionOperacion();
            String exemption = b.getOperacionExenta();
            boolean hasQualification = !isBlank(qualification);
            boolean hasExemption = !isBlank(exemption);
            if (hasQualification == hasExemption) {
                f.error(label + "debe indicar exactamente una de CalificacionOperacion (S1, S2, N1, N2) "
                        + "u OperacionExenta (E1-E8)");
            }
            if (hasQualification && !QUALIFICATIONS.contains(qualification)) {
                f.error(label + "CalificacionOperacion desconocida: " + qualification
                        + " (válidas: S1, S2, N1, N2)");
            }
            if (hasExemption && !EXEMPTIONS.contains(exemption)) {
                f.error(label + "OperacionExenta desconocida: " + exemption + " (válidas: E1-E8)");
            }
            if (hasExemption && ("E7".equals(exemption) || "E8".equals(exemption)) && !"03".equals(taxCode)) {
                f.error(label + "OperacionExenta " + exemption + " solo existe para IGIC (Impuesto 03)");
            }

            String regime = b.getClaveRegimen();
            if (isBlank(regime)) {
                if (iva || "03".equals(taxCode)) {
                    f.error(label + "ClaveRegimen obligatoria");
                } else if ("02".equals(taxCode)) {
                    f.warning(label + "ClaveRegimen obligatoria para IPSI: la AEAT acepta el registro con "
                            + "errores hasta el 31/12/2026 y lo rechaza desde el 01/01/2027");
                }
            } else if ("05".equals(taxCode)) {
                f.error(label + "ClaveRegimen solo puede informarse con Impuesto 01, 02 o 03");
            } else if (iva && !REGIMES_IVA.contains(regime)) {
                f.error(label + "ClaveRegimen no válida para IVA (lista L8A): " + regime);
            } else if ("03".equals(taxCode) && !REGIMES_IGIC.contains(regime)) {
                f.error(label + "ClaveRegimen no válida para IGIC (lista L8B): " + regime);
            } else if ("02".equals(taxCode) && !REGIMES_IPSI.contains(regime)) {
                f.warning(label + "ClaveRegimen no válida para IPSI (admitidas 01, 08, 11, 18, 19 y 20): "
                        + regime + "; la AEAT acepta el registro con errores hasta el 31/12/2026 y lo rechaza "
                        + "desde el 01/01/2027");
            }

            boolean baseOnly = hasExemption || (hasQualification && qualification.startsWith("N"));
            boolean s1 = "S1".equals(qualification);
            boolean s2 = "S2".equals(qualification);

            if (s2) {
                if (!RECIPIENT_TYPES.contains(type)) {
                    f.error(label + "CalificacionOperacion S2 solo admite facturas F1, F3, R1, R2, R3 y R4");
                }
                if (b.getTaxRate() != 0 || b.getTaxAmount() != 0) {
                    f.error(label + "con S2 (inversión del sujeto pasivo) el tipo impositivo y la cuota "
                            + "repercutida deben ser 0");
                }
            }
            if (baseOnly && (b.getTaxRate() != 0 || b.getTaxAmount() != 0
                    || b.getTipoRecargoEquivalencia() != null || b.getCuotaRecargoEquivalencia() != null)) {
                f.warning(label + "las operaciones exentas y no sujetas no llevan tipo impositivo, cuota ni "
                        + "recargo: se omitirán");
            }

            if (s1) {
                checkStandardLine(f, label, b, iva, reference, byDifferenceOrR2R3);
            }
            if (b.getBaseImponibleACoste() != null && iva && !"06".equals(regime)) {
                f.error(label + "BaseImponibleACoste solo puede informarse con ClaveRegimen 06");
            }
            if (ivaOrIgic) {
                checkRegime(f, label, b, regime, type, hasExemption, qualification, exemption);
            }

            tax += s1 ? b.getTaxAmount() : 0;
            tax += !baseOnly && b.getCuotaRecargoEquivalencia() != null ? b.getCuotaRecargoEquivalencia() : 0;
            base += b.getBaseAmount();
            simplifiedSum += b.getBaseAmount() + (s1 ? b.getTaxAmount() : 0);
        }

        // Totals: the AEAT only warns ("aceptado con errores") and admits a margin of 10.00 euros.
        if (Collections.disjoint(regimes, TOTALS_EXEMPT_REGIMES)) {
            if (Math.abs(base + tax - invoice.getTotalAmount()) > MARGIN) {
                f.warning(String.format(Locale.US,
                        "Importe total incoherente: calculado=%.2f (base=%.2f + cuota=%.2f), declarado=%.2f "
                                + "(la AEAT admite un margen de 10,00 € y acepta el registro con errores)",
                        base + tax, base, tax, invoice.getTotalAmount()));
            }
            if (Math.abs(tax - invoice.getTaxAmount()) > MARGIN) {
                f.warning(String.format(Locale.US,
                        "Cuota total incoherente: calculada=%.2f, declarada=%.2f (la AEAT admite un margen de "
                                + "10,00 € y acepta el registro con errores)", tax, invoice.getTaxAmount()));
            }
        }

        if ("F2".equals(type) && !invoice.isWithoutRecipientIdentification()
                && simplifiedSum > SIMPLIFIED_LIMIT + MARGIN) {
            f.error(String.format(Locale.US, "Una factura simplificada (F2) no puede superar 3.000,00 € "
                    + "(base + cuota = %.2f); la AEAT admite un margen de 10,00 €", simplifiedSum));
        }
    }

    /** Validations of an S1 (subject and not exempt) line. */
    private static void checkStandardLine(Findings f, String label, VeriFactuBreakdown b, boolean iva,
            LocalDate reference, boolean byDifferenceOrR2R3) {
        double rate = b.getTaxRate();
        if (Double.isNaN(rate) || rate < 0 || rate > 100) {
            f.error(label + "Tipo impositivo debe estar entre 0 y 100: " + rate);
            return;
        }
        checkAmount(f, label + "cuota repercutida", b.getTaxAmount());

        if (iva) {
            if (!isAllowedVatRate(rate, reference)) {
                f.error(label + "Tipo impositivo no admitido para IVA: " + trimRate(rate)
                        + " (válidos: 0, 4, 10 y 21; 5 entre 01/07/2022 y 30/09/2024; 2 y 7,5 entre 01/10/2024 "
                        + "y 31/12/2024)");
            }
            checkSurcharge(f, label, b, rate, reference);
        }

        if (!byDifferenceOrR2R3) {
            double baseForQuota = b.getBaseImponibleACoste() != null ? b.getBaseImponibleACoste() : b.getBaseAmount();
            double expected = baseForQuota * rate / 100;
            if (Math.abs(b.getTaxAmount() - expected) > MARGIN) {
                f.error(String.format(Locale.US, "%scuota repercutida incoherente con la base y el tipo: "
                        + "esperada=%.2f (%.2f x %s %%), declarada=%.2f (la AEAT admite 10,00 €)",
                        label, expected, baseForQuota, trimRate(rate), b.getTaxAmount()));
            }
            if (b.getTaxAmount() != 0 && baseForQuota != 0
                    && Math.signum(b.getTaxAmount()) != Math.signum(baseForQuota)) {
                f.error(label + "la cuota repercutida y la base deben tener el mismo signo");
            }
        }
    }

    private static boolean isAllowedVatRate(double rate, LocalDate ref) {
        if (rate == 0 || rate == 4 || rate == 10 || rate == 21) {
            return true;
        }
        if (ref == null) {
            return false;
        }
        if (rate == 5) {
            return !ref.isBefore(LocalDate.of(2022, 7, 1)) && !ref.isAfter(LocalDate.of(2024, 9, 30));
        }
        if (rate == 2 || rate == 7.5) {
            return !ref.isBefore(LocalDate.of(2024, 10, 1)) && !ref.isAfter(LocalDate.of(2024, 12, 31));
        }
        return false;
    }

    private static void checkSurcharge(Findings f, String label, VeriFactuBreakdown b, double rate, LocalDate ref) {
        Double surcharge = b.getTipoRecargoEquivalencia();
        if (surcharge == null) {
            return;
        }
        Set<Double> allowed = new HashSet<>();
        if (rate == 21) {
            allowed.addAll(Arrays.asList(5.2, 1.75));
        } else if (rate == 10) {
            allowed.add(1.4);
        } else if (rate == 7.5) {
            allowed.add(1.0);
        } else if (rate == 5) {
            if (ref != null && !ref.isAfter(LocalDate.of(2022, 12, 31))) {
                allowed.add(0.5);
            } else {
                allowed.add(0.62);
            }
        } else if (rate == 4) {
            allowed.add(0.5);
        } else if (rate == 2) {
            allowed.add(0.26);
        } else if (rate == 0) {
            allowed.add(0.0);
        }
        if (!allowed.contains(surcharge)) {
            f.error(label + "Tipo de recargo de equivalencia " + trimRate(surcharge)
                    + " no admitido para el tipo impositivo " + trimRate(rate) + " (admitidos: " + allowed + ")");
        }
    }

    /** Rules that depend on the {@code ClaveRegimen} (IVA and IGIC). */
    private static void checkRegime(Findings f, String label, VeriFactuBreakdown b, String regime, String type,
            boolean exempt, String qualification, String exemption) {
        if (isBlank(regime)) {
            return;
        }
        boolean s1 = "S1".equals(qualification);
        boolean s2 = "S2".equals(qualification);
        switch (regime) {
            case "01":
                if (exempt && ("E2".equals(exemption) || "E3".equals(exemption))) {
                    f.error(label + "con ClaveRegimen 01 no pueden usarse las exenciones E2 y E3");
                }
                break;
            case "02":
                if (!exempt) {
                    f.error(label + "ClaveRegimen 02 (exportación) solo admite OperacionExenta");
                }
                break;
            case "03":
                if (!isBlank(qualification) && !s1) {
                    f.error(label + "ClaveRegimen 03 (REBU) solo admite CalificacionOperacion S1");
                }
                break;
            case "04":
                if (!exempt && !s2) {
                    f.error(label + "ClaveRegimen 04 (oro de inversión) solo admite S2 u OperacionExenta");
                }
                break;
            case "06":
                if ("F2".equals(type) || "F3".equals(type) || "R5".equals(type)) {
                    f.error(label + "ClaveRegimen 06 no admite facturas F2, F3 ni R5");
                }
                if (b.getBaseImponibleACoste() == null) {
                    f.error(label + "ClaveRegimen 06 exige BaseImponibleACoste");
                }
                break;
            case "07":
                if (s2 || "N1".equals(qualification) || "N2".equals(qualification)) {
                    f.error(label + "ClaveRegimen 07 (criterio de caja) no admite S2, N1 ni N2");
                }
                if (exempt && set("E2", "E3", "E4", "E5").contains(exemption)) {
                    f.error(label + "ClaveRegimen 07 (criterio de caja) no admite las exenciones E2, E3, E4 ni E5");
                }
                break;
            case "08":
                if (!"N2".equals(qualification)) {
                    f.error(label + "ClaveRegimen 08 exige CalificacionOperacion N2");
                }
                break;
            case "10":
                if (!"N1".equals(qualification)) {
                    f.error(label + "ClaveRegimen 10 (cobro por cuenta de terceros) exige CalificacionOperacion N1");
                }
                if (!"F1".equals(type)) {
                    f.error(label + "ClaveRegimen 10 solo admite facturas F1");
                }
                break;
            case "11":
                if (s1 && b.getTaxRate() != 21) {
                    f.error(label + "ClaveRegimen 11 (arrendamiento de local) solo admite el tipo impositivo 21");
                }
                break;
            case "14":
                if (!set("F1", "R1", "R2", "R3", "R4").contains(type)) {
                    f.error(label + "ClaveRegimen 14 solo admite facturas F1, R1, R2, R3 y R4");
                }
                break;
            default:
                break;
        }
    }

    private static Set<String> regimesOf(VeriFactuInvoice invoice) {
        Set<String> regimes = new HashSet<>();
        if (invoice.getBreakdowns() != null) {
            for (VeriFactuBreakdown b : invoice.getBreakdowns()) {
                if (b != null && !isBlank(b.getClaveRegimen())) {
                    regimes.add(b.getClaveRegimen());
                }
            }
        }
        return regimes;
    }

    private static void checkCorrection(Findings f, VeriFactuInvoice invoice, String type, boolean corrective) {
        String correctionType = invoice.getCorrectionType();
        List<VeriFactuInvoiceReference> rectified = invoice.getRectifiedInvoices();
        List<VeriFactuInvoiceReference> substituted = invoice.getSubstitutedInvoices();
        boolean hasRectified = rectified != null && !rectified.isEmpty();
        boolean hasSubstituted = substituted != null && !substituted.isEmpty();

        if (corrective) {
            if (isBlank(correctionType)) {
                f.error("Una factura rectificativa (" + type + ") necesita el tipo de rectificación: "
                        + "S (por sustitución) o I (por diferencias)");
            } else if (!CORRECTION_TYPES.contains(correctionType)) {
                f.error("Tipo de rectificación desconocido: " + correctionType + " (válidos: S, I)");
            } else if ("S".equals(correctionType)
                    && (invoice.getCorrectedBaseAmount() == null || invoice.getCorrectedTaxAmount() == null)) {
                f.error("La rectificativa por sustitución necesita correctedBaseAmount y correctedTaxAmount");
            }
            if (!hasRectified) {
                f.warning("Una rectificativa debería indicar la factura que rectifica (getRectifiedInvoices); "
                        + "solo puede omitirse en los rappels y casos autorizados por la AEAT");
            }
        } else {
            if (!isBlank(correctionType)) {
                f.error("El tipo de rectificación solo puede informarse en facturas R1 a R5: " + correctionType);
            }
            if (hasRectified) {
                f.error("Las facturas rectificadas solo pueden informarse en facturas R1 a R5");
            }
        }
        if (hasSubstituted && !"F3".equals(type)) {
            f.error("Las facturas sustituidas solo pueden informarse en facturas F3");
        }
        checkReferences(f, "Factura rectificada", rectified);
        checkReferences(f, "Factura sustituida", substituted);
    }

    private static void checkReferences(Findings f, String label, List<VeriFactuInvoiceReference> references) {
        if (references == null) {
            return;
        }
        if (references.size() > 1000) {
            f.error(label + ": demasiadas referencias (máximo 1000)");
        }
        for (int i = 0; i < references.size(); i++) {
            VeriFactuInvoiceReference ref = references.get(i);
            String prefix = label + " " + (i + 1) + ": ";
            if (ref == null || isBlank(ref.getInvoiceNumber())) {
                f.error(prefix + "número de factura obligatorio");
            } else if (ref.getInvoiceNumber().length() > 60) {
                f.error(prefix + "número de factura demasiado largo (máximo 60 caracteres)");
            }
            if (ref == null || ref.getIssueDate() == null) {
                f.error(prefix + "fecha de expedición obligatoria");
            }
            if (ref != null && !isBlank(ref.getIssuerTaxId()) && !isValidSpanishId(ref.getIssuerTaxId())) {
                f.error(prefix + "NIF del emisor inválido: " + ref.getIssuerTaxId());
            }
        }
    }

    private static void checkRecipients(Findings f, VeriFactuInvoice invoice, String type, Set<String> regimes,
            LocalDate reference) {
        List<VeriFactuRecipient> recipients = invoice.getRecipients() != null
                ? invoice.getRecipients() : Collections.<VeriFactuRecipient>emptyList();

        // Destinatarios: mandatory in F1, F3 and R1-R4; not allowed in F2 and R5.
        if (RECIPIENT_TYPES.contains(type) && recipients.isEmpty()) {
            f.error("Una factura " + type + " debe identificar al destinatario (al menos uno): la AEAT rechaza "
                    + "el registro sin él. Si no se puede identificar, emite una factura simplificada (F2) "
                    + "o marca la F2 como emitida sin identificación del destinatario (art. 6.1.d)");
        }
        if (("F2".equals(type) || "R5".equals(type)) && !recipients.isEmpty()) {
            f.error("Una factura " + type + " no puede llevar destinatarios: la AEAT rechaza el registro");
        }

        for (int i = 0; i < recipients.size(); i++) {
            VeriFactuRecipient r = recipients.get(i);
            String label = "Destinatario " + (i + 1) + ": ";
            if (r == null) {
                f.error(label + "destinatario vacío");
                continue;
            }
            if (isBlank(r.getName())) {
                f.error(label + "razón social obligatoria");
            } else if (r.getName().length() > 120) {
                f.error(label + "razón social demasiado larga (máximo 120 caracteres)");
            }
            if (isForeign(r)) {
                checkForeignRecipient(f, label, r, type, reference);
            } else if (isBlank(r.getTaxId())) {
                f.error(label + "NIF obligatorio");
            } else if (!isValidSpanishId(r.getTaxId())) {
                f.error(label + "NIF inválido: " + r.getTaxId()
                        + " (para un identificador extranjero indica país y tipo de documento)");
            }
        }

        if (!isBlank(invoice.getCustomerTaxId()) && recipients.isEmpty()
                && !isValidSpanishId(invoice.getCustomerTaxId())) {
            f.error("NIF cliente inválido: " + invoice.getCustomerTaxId());
        }

        // Regime-specific recipient rules.
        if (regimes.contains("10")) {
            for (VeriFactuRecipient r : recipients) {
                if (r != null && isForeign(r)) {
                    f.error("ClaveRegimen 10: todos los destinatarios deben identificarse mediante NIF");
                    break;
                }
            }
        }
        if (regimes.contains("14")) {
            for (VeriFactuRecipient r : recipients) {
                if (r != null && (isForeign(r) || isBlank(r.getTaxId())
                        || "PQSV".indexOf(r.getTaxId().trim().toUpperCase().charAt(0)) < 0)) {
                    f.error("ClaveRegimen 14: los destinatarios deben identificarse mediante NIF que empiece "
                            + "por P, Q, S o V");
                    break;
                }
            }
        }

        // Flags tied to the invoice type.
        if (invoice.isWithoutRecipientIdentification() && !("F2".equals(type) || "R5".equals(type))) {
            f.error("FacturaSinIdentifDestinatarioArt61d solo puede informarse en facturas F2 o R5 (art. 6.1.d "
                    + "RD 1619/2012); en " + type + " el destinatario es obligatorio");
        }
        if (invoice.isCoupon() && !("R1".equals(type) || "R5".equals(type))) {
            f.error("Cupon solo puede informarse en facturas R1 o R5");
        }
        if (invoice.isQualifiedSimplified() && !RECIPIENT_TYPES.contains(type)) {
            f.error("FacturaSimplificadaArt7273 solo puede informarse en facturas F1, F3, R1, R2, R3 y R4");
        }
    }

    private static boolean isForeign(VeriFactuRecipient r) {
        boolean foreignCountry = !isBlank(r.getCountryCode()) && !"ES".equalsIgnoreCase(r.getCountryCode().trim());
        return foreignCountry || !isBlank(r.getIdType());
    }

    private static void checkForeignRecipient(Findings f, String label, VeriFactuRecipient r, String type,
            LocalDate reference) {
        String idType = r.getIdType();
        if (isBlank(idType)) {
            f.error(label + "tipo de documento obligatorio para un identificador extranjero (02-07)");
        } else if (!FOREIGN_ID_TYPES.contains(idType)) {
            f.error(label + "tipo de documento desconocido: " + idType + " (válidos: 02 a 07)");
        }

        String country = isBlank(r.getCountryCode()) ? null : r.getCountryCode().trim().toUpperCase();
        if (country == null) {
            // Without a country the AEAT only accepts a NIF-IVA (02), whose prefix carries the country.
            if (!"02".equals(idType)) {
                f.error(label + "país obligatorio para este tipo de documento");
            }
        } else if (!isCountry(country)) {
            f.error(label + "código de país desconocido: " + r.getCountryCode() + " (ISO 3166-1 alfa-2)");
        } else if ("ES".equals(country) && !("03".equals(idType) || "07".equals(idType))) {
            f.error(label + "con país ES el tipo de documento solo puede ser 03 (pasaporte) o 07 (no censado)");
        }
        if ("07".equals(idType) && country != null && !"ES".equals(country)) {
            f.error(label + "el tipo 07 (no censado) exige país ES");
        }
        if ("07".equals(idType)) {
            f.warning(label + "un NIF informado como 07 (no censado) se acepta con errores y debe subsanarse "
                    + "cuando se cense");
        }

        if (isBlank(r.getTaxId())) {
            f.error(label + "identificador obligatorio");
        } else if (r.getTaxId().length() > 20) {
            f.error(label + "identificador demasiado largo (máximo 20 caracteres)");
        } else if ("02".equals(idType)) {
            if (!RECIPIENT_TYPES.contains(type)) {
                f.error(label + "un NIF-IVA solo es válido en facturas F1, F3, R1, R2, R3 y R4");
            }
            String vatError = vatIdError(r.getTaxId(), reference);
            if (vatError != null) {
                f.error(label + vatError);
            }
        }
    }

    /** {@code null} if the value has the structure of a member state VAT number. */
    static String vatIdError(String id, LocalDate reference) {
        String value = id.trim();
        if (value.length() < 3) {
            return "el NIF-IVA debe empezar por el código de país (por ejemplo FR12345678901)";
        }
        String prefix = value.substring(0, 2);
        Pattern pattern = VAT_ID.get(prefix);
        if (pattern == null) {
            return "NIF-IVA con prefijo de país desconocido: " + prefix;
        }
        if (!pattern.matcher(value.substring(2)).matches()) {
            return "el NIF-IVA " + value + " no tiene la estructura de " + prefix;
        }
        if (reference != null && "GB".equals(prefix) && !reference.isBefore(LocalDate.of(2021, 2, 1))) {
            return "desde el 01/02/2021 el NIF-IVA del Reino Unido (Irlanda del Norte) empieza por XI, no por GB";
        }
        return null;
    }

    private static boolean isCountry(String code) {
        try {
            com.squareetlabs.verifactu.aeat.CountryType2.fromValue(code.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void checkDates(Findings f, VeriFactuInvoice invoice, LocalDate issueDate, LocalDate today,
            Set<String> regimes) {
        LocalDate operation = invoice.getOperationDate();
        boolean pending = regimes.contains("14") || regimes.contains("15");
        if (operation != null) {
            if (operation.isBefore(today.minusYears(20)) || operation.getYear() > today.getYear() + 1) {
                f.error("La fecha de operación debe estar entre hace 20 años y el año siguiente: " + operation);
            }
            if (operation.isAfter(today) && !pending) {
                f.error("La fecha de operación solo puede ser posterior a hoy con ClaveRegimen 14 o 15: " + operation);
            }
            if (issueDate != null && issueDate.isBefore(operation) && !pending) {
                f.error("La fecha de expedición solo puede ser anterior a la de operación con ClaveRegimen 14 o 15");
            }
        }
        if (regimes.contains("14")) {
            if (operation == null) {
                f.error("ClaveRegimen 14 exige la fecha de operación");
            } else if (issueDate != null && !operation.isAfter(issueDate)) {
                f.error("Con ClaveRegimen 14 la fecha de operación debe ser posterior a la de expedición");
            }
        }
    }

    private static void checkOptionalFields(Findings f, VeriFactuInvoice invoice, String issuerTaxId) {
        if (invoice.getOperationDescription() != null && invoice.getOperationDescription().length() > 500) {
            f.warning("La descripción de la operación supera 500 caracteres y se truncará");
        }
        if (invoice.getExternalReference() != null && invoice.getExternalReference().length() > 60) {
            f.error("La referencia externa no puede superar 60 caracteres");
        }
        checkPreviousHash(f, invoice.getPreviousHash());

        // Subsanacion / RechazoPrevio.
        String rejection = invoice.getPreviousRejection();
        if (!isBlank(rejection)) {
            if (!PREVIOUS_REJECTION.contains(rejection)) {
                f.error("RechazoPrevio desconocido: " + rejection + " (válidos: N, S, X)");
            } else if (("X".equals(rejection) || "S".equals(rejection)) && !invoice.isSubsanation()) {
                f.error("RechazoPrevio " + rejection + " solo puede informarse junto con Subsanacion = S");
            }
        }

        String thirdParty = invoice.getIssuedByThirdPartyOrRecipient();
        if (!isBlank(thirdParty)) {
            if (!THIRD_PARTY.contains(thirdParty)) {
                f.error("EmitidaPorTerceroODestinatario desconocido: " + thirdParty + " (válidos: T, D)");
            } else if ("T".equals(thirdParty)) {
                if (isBlank(invoice.getThirdPartyName()) || isBlank(invoice.getThirdPartyTaxId())) {
                    f.error("thirdPartyName y thirdPartyTaxId son obligatorios cuando la emite un tercero (T)");
                } else if (!isValidSpanishId(invoice.getThirdPartyTaxId())) {
                    f.error("NIF del tercero inválido: " + invoice.getThirdPartyTaxId());
                } else if (issuerTaxId != null
                        && issuerTaxId.trim().equalsIgnoreCase(invoice.getThirdPartyTaxId().trim())) {
                    f.error("El NIF del tercero debe ser distinto del NIF del emisor de la factura");
                }
            } else if ("D".equals(thirdParty)
                    && (invoice.getRecipients() == null || invoice.getRecipients().isEmpty())) {
                f.error("Si la factura la emite el destinatario (D) hay que informar los destinatarios");
            }
        }
    }

    private static void checkPreviousHash(Findings f, String hash) {
        if (isBlank(hash)) {
            return;
        }
        if (!HASH.matcher(hash.trim()).matches()) {
            f.error("La huella anterior debe tener 64 caracteres hexadecimales");
        } else if (!hash.trim().equals(hash.trim().toUpperCase())) {
            f.warning("La huella anterior debe ir en mayúsculas: la AEAT acepta el registro con errores");
        }
    }

    // ─── Annulments ────────────────────────────────────────────────

    /** Validates an annulment as {@link com.squareetlabs.verifactu.services.AeatClient} will send it. */
    public static ValidationResult validate(VeriFactuAnnulment annulment, VeriFactuConfig config) {
        return validate(annulment, config, LocalDate.now(MADRID));
    }

    static ValidationResult validate(VeriFactuAnnulment annulment, VeriFactuConfig config, LocalDate today) {
        Findings f = new Findings();

        checkInvoiceNumber(f, annulment.getInvoiceNumber());

        if (annulment.getIssueDate() == null) {
            f.error("Fecha de emisión obligatoria");
        } else if (annulment.getIssueDate().isAfter(today)) {
            f.error("La fecha de emisión no puede ser posterior a hoy: " + annulment.getIssueDate());
        }

        String issuerTaxId = config != null ? config.getIssuerVat() : null;
        if (isBlank(issuerTaxId)) {
            f.error("NIF emisor obligatorio");
        } else if (!isValidSpanishId(issuerTaxId)) {
            f.error("NIF emisor inválido: " + issuerTaxId);
        }

        checkPreviousHash(f, annulment.getPreviousHash());
        if (annulment.getExternalReference() != null && annulment.getExternalReference().length() > 60) {
            f.error("La referencia externa no puede superar 60 caracteres");
        }

        String generatedBy = annulment.getGeneratedBy();
        if (!isBlank(generatedBy)) {
            if (!set("E", "D", "T").contains(generatedBy)) {
                f.error("GeneradoPor desconocido: " + generatedBy + " (válidos: E, D, T)");
            } else if (isBlank(annulment.getGeneratorName()) || isBlank(annulment.getGeneratorTaxId())) {
                f.error("generatorName y generatorTaxId son obligatorios cuando se informa generatedBy");
            } else if (!isValidSpanishId(annulment.getGeneratorTaxId())) {
                f.error("NIF del generador inválido: " + annulment.getGeneratorTaxId());
            } else if (issuerTaxId != null
                    && issuerTaxId.trim().equalsIgnoreCase(annulment.getGeneratorTaxId().trim())) {
                f.error("El NIF del generador debe ser distinto del NIF del emisor");
            }
        }
        return f.result();
    }

    // ─── Configuration ─────────────────────────────────────────────

    /** Validates the issuer, representative and computer-system data of the configuration. */
    public static ValidationResult validate(VeriFactuConfig config) {
        Findings f = new Findings();
        if (config == null) {
            f.error("Configuración obligatoria");
            return f.result();
        }

        if (isBlank(config.getIssuerVat())) {
            f.error("NIF emisor obligatorio");
        } else if (!isValidSpanishId(config.getIssuerVat())) {
            f.error("NIF emisor inválido: " + config.getIssuerVat());
        }
        checkRequiredText(f, "Razón social del emisor", config.getIssuerName(), 120);

        if (config.hasRepresentative()) {
            checkRequiredText(f, "Razón social del representante", config.getRepresentativeName(), 120);
            if (isBlank(config.getRepresentativeVat())) {
                f.error("NIF del representante obligatorio");
            } else if (!isValidSpanishId(config.getRepresentativeVat())) {
                f.error("NIF del representante inválido: " + config.getRepresentativeVat());
            }
        } else if (!isBlank(config.getRepresentativeName()) || !isBlank(config.getRepresentativeVat())) {
            f.error("El representante necesita razón social y NIF");
        }

        checkRequiredText(f, "Razón social del desarrollador", config.getDeveloperName(), 120);
        if (isBlank(config.getDeveloperNif())) {
            f.error("NIF del desarrollador obligatorio");
        } else if (!isValidSpanishId(config.getDeveloperNif())) {
            f.error("NIF del desarrollador inválido: " + config.getDeveloperNif());
        }
        checkRequiredText(f, "Nombre del sistema informático", config.getSystemName(), 30);
        if (isBlank(config.getSystemId())) {
            f.error("Identificador del sistema informático obligatorio");
        } else if (!SYSTEM_ID.matcher(config.getSystemId()).matches()) {
            f.error("El identificador del sistema informático debe tener exactamente dos posiciones, cada "
                    + "una letra mayúscula (sin Ñ) o dígito: " + config.getSystemId());
        }
        checkRequiredText(f, "Versión del sistema informático", config.getSystemVersion(), 50);
        checkRequiredText(f, "Número de instalación", config.getInstallationNumber(), 100);
        for (String[] flag : new String[][] {
                {"TipoUsoPosibleSoloVerifactu", config.getOnlyVerifactuCapable()},
                {"TipoUsoPosibleMultiOT", config.getMultiObligatedCapable()},
                {"IndicadorMultiplesOT", config.getHasMultipleObligated()}}) {
            if (flag[1] == null || !YES_NO.contains(flag[1])) {
                f.error(flag[0] + " debe ser S o N: " + flag[1]);
            }
        }
        return f.result();
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private static void vat(String country, String pattern) {
        VAT_ID.put(country, Pattern.compile(pattern));
    }

    private static void checkRequiredText(Findings f, String label, String value, int max) {
        if (isBlank(value)) {
            f.error(label + " obligatorio");
        } else if (value.length() > max) {
            f.error(label + " demasiado largo (máximo " + max + " caracteres)");
        }
    }

    private static void checkAmount(Findings f, String label, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            f.error(label + " no es un número válido");
        } else if (Math.abs(value) > MAX_AMOUNT) {
            f.error(label + " supera el máximo de 12 dígitos enteros: " + value);
        }
    }

    private static String trimRate(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static boolean isValidSpanishId(String id) {
        if (isBlank(id)) {
            return false;
        }
        String normalized = id.trim().toUpperCase();
        return NifValidator.isValid(normalized) || SPECIAL_NIF.matcher(normalized).matches();
    }

    private static String issuerTaxIdOf(VeriFactuInvoice invoice) {
        return invoice instanceof com.squareetlabs.verifactu.models.Invoice
                ? ((com.squareetlabs.verifactu.models.Invoice) invoice).getIssuerTaxId() : null;
    }

    private static String issuerNameOf(VeriFactuInvoice invoice) {
        return invoice instanceof com.squareetlabs.verifactu.models.Invoice
                ? ((com.squareetlabs.verifactu.models.Invoice) invoice).getIssuerName() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private static final class Findings {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        void error(String message) {
            errors.add(message);
        }

        void warning(String message) {
            warnings.add(message);
        }

        ValidationResult result() {
            return new ValidationResult(errors.isEmpty(), errors, warnings);
        }
    }

    /**
     * Result of a validation: {@link #getErrors()} make it invalid, {@link #getWarnings()} do not.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors) {
            this(valid, errors, Collections.<String>emptyList());
        }

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getErrorMessage() {
            if (valid) {
                return "Factura válida";
            }
            return "Errores de validación:\n- " + String.join("\n- ", errors);
        }

        /** The errors on a single line, for log and response messages. */
        public String getErrorSummary() {
            return String.join("; ", errors);
        }

        @Override
        public String toString() {
            return getErrorMessage();
        }
    }
}
