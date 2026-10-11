package com.squareetlabs.verifactu.contracts;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Contract that invoice models must implement to be compatible with VeriFactu
 */
public interface VeriFactuInvoice {
    /**
     * Get the invoice number/series
     *
     * @return String
     */
    String getInvoiceNumber();

    /**
     * Get the invoice issue date
     *
     * @return LocalDate
     */
    LocalDate getIssueDate();

    /**
     * Get the invoice type according to AEAT specifications
     * Valid values: F1, F2, F3, F4, R1, R2, R3, R4, R5
     *
     * @return String
     */
    String getInvoiceType();

    /**
     * Get the total amount in euros (including tax)
     *
     * @return double
     */
    double getTotalAmount();

    /**
     * Get the total tax amount in euros
     *
     * @return double
     */
    double getTaxAmount();

    /**
     * Get the customer/recipient name
     *
     * @return String
     */
    String getCustomerName();

    /**
     * Get the customer tax ID (NIF/CIF)
     * Can be null for foreign customers or when using IDOtro
     *
     * @return String or null
     */
    String getCustomerTaxId();

    /**
     * Get the invoice breakdowns (tax details by regime/rate)
     * Must return at least one breakdown
     *
     * @return List<VeriFactuBreakdown>
     */
    List<VeriFactuBreakdown> getBreakdowns();

    /**
     * Get additional recipients (optional, for multiple recipients)
     * Return empty list if not applicable
     *
     * @return List<VeriFactuRecipient>
     */
    List<VeriFactuRecipient> getRecipients();

    /**
     * Get the previous invoice hash for chaining
     * Return null for the first invoice in the chain
     *
     * @return String or null
     */
    String getPreviousHash();

    /**
     * Instant the record was generated ({@code FechaHoraHusoGenRegistro}). Optional: when {@code null}
     * the client uses the current instant at the moment of sending.
     * <p>
     * Persist it before the first send and return the same value on every retry: the hash is computed
     * over it, so resending with the same value yields the same record (same hash, same XML) and the AEAT
     * answers with a duplicate instead of a conflicting record.
     *
     * @return ZonedDateTime or null
     */
    default java.time.ZonedDateTime getGenerationTimestamp() {
        return null;
    }

    /**
     * Get the operation description
     *
     * @return String
     */
    String getOperationDescription();

    /**
     * Get the operation date (if different from issue date)
     *
     * @return LocalDate or null
     */
    LocalDate getOperationDate();

    /**
     * Get the tax period (e.g., "01", "02", "0A")
     *
     * @return String or null
     */
    String getTaxPeriod();

    /**
     * Get the correction type (e.g., "S", "I")
     * Required for corrective invoices (R1-R5)
     *
     * @return String or null
     */
    String getCorrectionType();

    /**
     * Get the external reference (optional)
     *
     * @return String or null
     */
    String getExternalReference();

    /**
     * Get the corrected base amount (for substitution corrective invoices)
     * Required when correction_type = "S"
     * This is the original base amount from the invoice being corrected
     *
     * @return Double or null
     */
    Double getCorrectedBaseAmount();

    /**
     * Get the corrected tax amount (for substitution corrective invoices)
     * Required when correction_type = "S"
     * This is the original tax amount from the invoice being corrected
     *
     * @return Double or null
     */
    Double getCorrectedTaxAmount();

    /**
     * Get the corrected surcharge amount (for substitution corrective invoices)
     * Optional field for AEAT ImporteRectificacion block
     * This is the original surcharge amount from the invoice being corrected
     *
     * @return Double or null
     */
    Double getCorrectedSurchargeAmount();

    /**
     * Whether this invoice record was NOT issued by the "obligado a expedir
     * factura" (issuer) itself, and if so, by whom. Maps to
     * {@code RegistroFacturacionAltaType.EmitidaPorTerceroODestinatario}.
     * <p>
     * Valid values:
     * <ul>
     * <li>{@code null} (default): the issuer expedía la factura por sí mismo.
     * No se rellena.</li>
     * <li>{@code "T"} (Tercero): la factura fue expedida por un tercero (p.
     * ej. Odei actuando en representación de su cliente) en nombre del
     * obligado. Requiere {@link #getThirdPartyName()} y
     * {@link #getThirdPartyTaxId()}.</li>
     * <li>{@code "D"} (Destinatario): autofacturación, la factura fue
     * expedida por el propio destinatario/cliente de la operación.</li>
     * </ul>
     *
     * @return "T", "D" or null
     */
    default String getIssuedByThirdPartyOrRecipient() {
        return null;
    }

    /**
     * Get the name of the third party that issued the invoice on behalf of
     * the obligado (required when {@link #getIssuedByThirdPartyOrRecipient()}
     * is {@code "T"}). Maps to {@code RegistroFacturacionAltaType.Tercero.NombreRazon}.
     *
     * @return String or null
     */
    default String getThirdPartyName() {
        return null;
    }

    /**
     * Get the tax ID (NIF) of the third party that issued the invoice on
     * behalf of the obligado (required when
     * {@link #getIssuedByThirdPartyOrRecipient()} is {@code "T"}). Maps to
     * {@code RegistroFacturacionAltaType.Tercero.NIF}.
     *
     * @return String or null
     */
    default String getThirdPartyTaxId() {
        return null;
    }

    /**
     * Get the per-invoice override for {@code SistemaInformatico.IndicadorMultiplesOT}.
     * <p>
     * The AEAT requires this flag to be computed automatically for each
     * record, based on whether the system currently manages more than one
     * "facturación" for the client that owns this specific invoice - it must
     * never be a value fixed by the developer or the end user. In a
     * multi-tenant SaaS backend this typically differs per client/tenant, so
     * it cannot be a single static configuration value.
     * <p>
     * Return {@code true}/{@code false} to explicitly set it for this
     * invoice, or {@code null} to fall back to
     * {@link com.squareetlabs.verifactu.services.VeriFactuConfig#getHasMultipleObligated()}.
     *
     * @return Boolean or null
     */
    default Boolean getMultipleObligatedIndicator() {
        return null;
    }

    /**
     * Invoices rectified by this one ({@code FacturasRectificadas}). Recommended for every
     * corrective invoice (R1-R5): it tells the AEAT which invoice is being corrected.
     */
    default List<VeriFactuInvoiceReference> getRectifiedInvoices() {
        return Collections.emptyList();
    }

    /**
     * Simplified invoices replaced by this one ({@code FacturasSustituidas}), for type F3.
     */
    default List<VeriFactuInvoiceReference> getSubstitutedInvoices() {
        return Collections.emptyList();
    }

    /**
     * {@code true} for a complete invoice issued without identifying the recipient
     * (art. 6.1.d RD 1619/2012, {@code FacturaSinIdentifDestinatarioArt61d = S}). It replaces the
     * recipients, which must then be empty.
     */
    default boolean isWithoutRecipientIdentification() {
        return false;
    }

    /**
     * {@code true} for a correction ("subsanación") record of an invoice already generated or sent
     * ({@code Subsanacion = S}): same invoice key, complete and correct data, when no corrective
     * invoice is required (see the AEAT table of admissible operations).
     */
    default boolean isSubsanation() {
        return false;
    }

    /**
     * {@code RechazoPrevio} of the record: {@code null}/{@code "N"} (default), {@code "X"} when the
     * previous record of the same invoice was rejected and therefore does not exist at the AEAT
     * ("alta por rechazo": {@code Subsanacion = S} and {@code RechazoPrevio = X}), or {@code "S"}
     * when a previous correction of a record already registered was rejected.
     */
    default String getPreviousRejection() {
        return null;
    }

    /**
     * {@code true} for a "qualified simplified invoice" (art. 7.2 and 7.3 RD 1619/2012,
     * {@code FacturaSimplificadaArt7273 = S}). Only for F1, F3 and R1-R4.
     */
    default boolean isQualifiedSimplified() {
        return false;
    }

    /**
     * {@code true} when the base was reduced by coupons, bonuses or discounts and only the original
     * of the invoice is issued ({@code Cupon = S}). Only for R1 and R5.
     */
    default boolean isCoupon() {
        return false;
    }
}
