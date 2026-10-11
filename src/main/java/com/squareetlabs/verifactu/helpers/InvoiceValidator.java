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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Local validator for the data sent to the AEAT (invoices, annulments and the issuer / system
 * configuration). It checks what the AEAT is known to reject, so that a bad record fails fast with
 * a readable message instead of coming back as a vague rejection.
 * <p>
 * Findings are split in two lists:
 * <ul>
 * <li><b>errors</b>: the AEAT will reject the record (or the SDK cannot represent it). The result
 * is not valid and {@link com.squareetlabs.verifactu.services.AeatClient} refuses to send it.</li>
 * <li><b>warnings</b>: suspicious data the AEAT may reject depending on the case. They never make
 * the result invalid.</li>
 * </ul>
 * Negative amounts are legal (corrective invoices by difference carry them): they are never an
 * error by themselves.
 */
public class InvoiceValidator {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // Enumerations of SuministroInformacion.xsd.
    private static final Set<String> INVOICE_TYPES = set("F1", "F2", "F3", "R1", "R2", "R3", "R4", "R5");
    private static final Set<String> CORRECTIVE_TYPES = set("R1", "R2", "R3", "R4", "R5");
    private static final Set<String> QUALIFICATIONS = set("S1", "S2", "N1", "N2");
    private static final Set<String> EXEMPTIONS = set("E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8");
    private static final Set<String> REGIMES = set("01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
            "11", "14", "15", "17", "18", "19", "20", "21");
    private static final Set<String> CORRECTION_TYPES = set("S", "I");
    private static final Set<String> THIRD_PARTY = set("D", "T");
    private static final Set<String> YES_NO = set("S", "N");
    private static final Set<String> FOREIGN_ID_TYPES = set("02", "03", "04", "05", "06", "07");

    private static final Pattern HASH = Pattern.compile("[0-9A-Fa-f]{64}");
    /** Spanish special NIFs (K, L, M): accepted by format, their check character is not verified. */
    private static final Pattern SPECIAL_NIF = Pattern.compile("^[KLM][0-9]{7}[0-9A-Z]$");

    private static final int MAX_BREAKDOWNS = 12;
    private static final double MAX_AMOUNT = 999_999_999_999.99;
    private static final double TOLERANCE = 0.02;

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

        // Required fields.
        String number = invoice.getInvoiceNumber();
        if (isBlank(number)) {
            f.error("Número de factura obligatorio");
        } else if (number.length() > 60) {
            f.error("Número de factura demasiado largo (máximo 60 caracteres): " + number.length());
        }

        LocalDate issueDate = invoice.getIssueDate();
        if (issueDate == null) {
            f.error("Fecha de emisión obligatoria");
        } else if (issueDate.isAfter(today)) {
            f.error("La fecha de emisión no puede ser posterior a hoy: " + issueDate);
        }

        String type = invoice.getInvoiceType();
        if (isBlank(type)) {
            f.error("Tipo de factura obligatorio");
        } else if (!INVOICE_TYPES.contains(type)) {
            f.error("Tipo de factura no admitido por la AEAT: " + type
                    + " (válidos: F1, F2, F3, R1, R2, R3, R4, R5)");
        }
        boolean corrective = type != null && CORRECTIVE_TYPES.contains(type);

        // Issuer.
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

        // Amounts.
        checkAmount(f, "Importe total", invoice.getTotalAmount());
        checkAmount(f, "Cuota total", invoice.getTaxAmount());
        if (!corrective && (invoice.getTotalAmount() < 0 || invoice.getTaxAmount() < 0)) {
            f.warning("Importe negativo en una factura que no es rectificativa: revisa si debería serlo");
        }

        checkBreakdowns(f, invoice, type);
        checkCorrection(f, invoice, type, corrective);
        checkRecipients(f, invoice, type);
        checkOptionalFields(f, invoice);

        return f.result();
    }

    private static void checkBreakdowns(Findings f, VeriFactuInvoice invoice, String type) {
        List<VeriFactuBreakdown> lines = invoice.getBreakdowns();
        if (lines == null || lines.isEmpty()) {
            f.error("Desglose obligatorio: la factura necesita al menos una línea de desglose");
            return;
        }
        if (lines.size() > MAX_BREAKDOWNS) {
            f.error("Demasiadas líneas de desglose (máximo " + MAX_BREAKDOWNS + "): " + lines.size());
        }

        double base = 0;
        double tax = 0;
        for (int i = 0; i < lines.size(); i++) {
            VeriFactuBreakdown b = lines.get(i);
            String label = "Desglose " + (i + 1) + ": ";
            if (b == null) {
                f.error(label + "línea vacía");
                continue;
            }
            checkAmount(f, label + "base imponible", b.getBaseAmount());

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
            if (!isBlank(b.getClaveRegimen()) && !REGIMES.contains(b.getClaveRegimen())) {
                f.error(label + "ClaveRegimen desconocida: " + b.getClaveRegimen());
            }

            boolean baseOnly = hasExemption || (hasQualification
                    && (qualification.startsWith("N") || "S2".equals(qualification)));
            if (!baseOnly) {
                checkAmount(f, label + "cuota repercutida", b.getTaxAmount());
                if (b.getTaxRate() < 0 || b.getTaxRate() > 100 || Double.isNaN(b.getTaxRate())) {
                    f.error(label + "Tipo impositivo debe estar entre 0 y 100: " + b.getTaxRate());
                }
                tax += b.getTaxAmount();
            }
            double surcharge = b.getCuotaRecargoEquivalencia() != null ? b.getCuotaRecargoEquivalencia() : 0;
            tax += surcharge;
            base += b.getBaseAmount();
        }

        // Coherence with the declared totals (ImporteTotal = bases + cuotas + recargos).
        double tolerance = TOLERANCE + 0.01 * lines.size();
        if (Math.abs(base + tax - invoice.getTotalAmount()) > tolerance) {
            f.error(String.format(java.util.Locale.US,
                    "Total incoherente: calculado=%.2f (base=%.2f + cuota=%.2f), declarado=%.2f",
                    base + tax, base, tax, invoice.getTotalAmount()));
        }
        if (Math.abs(tax - invoice.getTaxAmount()) > tolerance) {
            f.error(String.format(java.util.Locale.US,
                    "Cuota total incoherente: calculada=%.2f, declarada=%.2f", tax, invoice.getTaxAmount()));
        }
    }

    private static void checkCorrection(Findings f, VeriFactuInvoice invoice, String type, boolean corrective) {
        String correctionType = invoice.getCorrectionType();
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
            if (invoice.getRectifiedInvoices() == null || invoice.getRectifiedInvoices().isEmpty()) {
                f.warning("Una rectificativa debería indicar la factura que rectifica (getRectifiedInvoices)");
            }
        } else if (!isBlank(correctionType)) {
            f.warning("Tipo de rectificación informado en una factura que no es rectificativa: " + correctionType);
        }
        checkReferences(f, "Factura rectificada", invoice.getRectifiedInvoices());
        checkReferences(f, "Factura sustituida", invoice.getSubstitutedInvoices());
        if ("F3".equals(type)
                && (invoice.getSubstitutedInvoices() == null || invoice.getSubstitutedInvoices().isEmpty())) {
            f.error("Una factura F3 (sustitución de simplificadas) debe indicar las facturas que sustituye");
        }
        if (!"F3".equals(type) && invoice.getSubstitutedInvoices() != null
                && !invoice.getSubstitutedInvoices().isEmpty()) {
            f.warning("Facturas sustituidas informadas en una factura que no es F3");
        }
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

    private static void checkRecipients(Findings f, VeriFactuInvoice invoice, String type) {
        List<VeriFactuRecipient> recipients = invoice.getRecipients() != null
                ? invoice.getRecipients() : Collections.<VeriFactuRecipient>emptyList();

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
                checkForeignRecipient(f, label, r);
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

        boolean withoutIdentification = invoice.isWithoutRecipientIdentification();
        boolean needsRecipient = "F1".equals(type) || "F3".equals(type) || "R1".equals(type)
                || "R2".equals(type) || "R3".equals(type) || "R4".equals(type);
        if (withoutIdentification && !recipients.isEmpty()) {
            f.error("Una factura sin identificación del destinatario (art. 6.1.d) no puede llevar destinatarios");
        }
        if (needsRecipient && recipients.isEmpty() && !withoutIdentification) {
            f.warning("Una factura " + type + " normalmente identifica al destinatario (NIF o identificador "
                    + "extranjero); sin él la AEAT puede rechazarla. Si no se identifica, márcala como "
                    + "emitida sin identificación del destinatario (art. 6.1.d)");
        }
        if (("F2".equals(type) || "R5".equals(type)) && !recipients.isEmpty()) {
            f.warning("Una factura " + type + " (simplificada) normalmente no lleva destinatario");
        }
    }

    private static boolean isForeign(VeriFactuRecipient r) {
        boolean foreignCountry = !isBlank(r.getCountryCode()) && !"ES".equalsIgnoreCase(r.getCountryCode().trim());
        return foreignCountry || !isBlank(r.getIdType());
    }

    private static void checkForeignRecipient(Findings f, String label, VeriFactuRecipient r) {
        if (isBlank(r.getIdType())) {
            f.error(label + "tipo de documento obligatorio para un identificador extranjero (02-07)");
        } else if (!FOREIGN_ID_TYPES.contains(r.getIdType())) {
            f.error(label + "tipo de documento desconocido: " + r.getIdType() + " (válidos: 02 a 07)");
        }
        if (isBlank(r.getCountryCode())) {
            // Without a country the AEAT only accepts a NIF-IVA (02), whose prefix carries the country.
            if (!"02".equals(r.getIdType())) {
                f.error(label + "país obligatorio para este tipo de documento");
            }
        } else if (!isCountry(r.getCountryCode())) {
            f.error(label + "código de país desconocido: " + r.getCountryCode() + " (ISO 3166-1 alfa-2)");
        }
        if (isBlank(r.getTaxId())) {
            f.error(label + "identificador obligatorio");
        } else if (r.getTaxId().length() > 20) {
            f.error(label + "identificador demasiado largo (máximo 20 caracteres)");
        }
    }

    private static boolean isCountry(String code) {
        try {
            com.squareetlabs.verifactu.aeat.CountryType2.fromValue(code.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void checkOptionalFields(Findings f, VeriFactuInvoice invoice) {
        if (invoice.getOperationDescription() != null && invoice.getOperationDescription().length() > 500) {
            f.warning("La descripción de la operación supera 500 caracteres y se truncará");
        }
        if (invoice.getExternalReference() != null && invoice.getExternalReference().length() > 60) {
            f.error("La referencia externa no puede superar 60 caracteres");
        }
        if (!isBlank(invoice.getPreviousHash()) && !HASH.matcher(invoice.getPreviousHash()).matches()) {
            f.error("La huella anterior debe tener 64 caracteres hexadecimales");
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
                }
            }
        }
    }

    // ─── Annulments ────────────────────────────────────────────────

    /** Validates an annulment as {@link com.squareetlabs.verifactu.services.AeatClient} will send it. */
    public static ValidationResult validate(VeriFactuAnnulment annulment, VeriFactuConfig config) {
        return validate(annulment, config, LocalDate.now(MADRID));
    }

    static ValidationResult validate(VeriFactuAnnulment annulment, VeriFactuConfig config, LocalDate today) {
        Findings f = new Findings();

        String number = annulment.getInvoiceNumber();
        if (isBlank(number)) {
            f.error("Número de factura obligatorio");
        } else if (number.length() > 60) {
            f.error("Número de factura demasiado largo (máximo 60 caracteres): " + number.length());
        }

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

        if (!isBlank(annulment.getPreviousHash()) && !HASH.matcher(annulment.getPreviousHash()).matches()) {
            f.error("La huella anterior debe tener 64 caracteres hexadecimales");
        }
        if (annulment.getExternalReference() != null && annulment.getExternalReference().length() > 60) {
            f.error("La referencia externa no puede superar 60 caracteres");
        }

        String generatedBy = annulment.getGeneratedBy();
        if (!isBlank(generatedBy)) {
            if (!set("E", "D", "T").contains(generatedBy)) {
                f.error("GeneradoPor desconocido: " + generatedBy + " (válidos: E, D, T)");
            } else if (!"E".equals(generatedBy)) {
                if (isBlank(annulment.getGeneratorName()) || isBlank(annulment.getGeneratorTaxId())) {
                    f.error("generatorName y generatorTaxId son obligatorios cuando generatedBy es T o D");
                } else if (!isValidSpanishId(annulment.getGeneratorTaxId())) {
                    f.error("NIF del generador inválido: " + annulment.getGeneratorTaxId());
                }
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
        checkRequiredText(f, "Identificador del sistema informático", config.getSystemId(), 2);
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
