package com.squareetlabs.verifactu.contracts;

import java.time.LocalDate;
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
}
