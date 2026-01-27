package com.squareetlabs.verifactu.contracts;

/**
 * Contract for additional invoice recipients
 * Used when an invoice has multiple recipients
 */
public interface VeriFactuRecipient {
    /**
     * Get the recipient name
     *
     * @return String
     */
    String getName();

    /**
     * Get the recipient tax ID (NIF/CIF)
     * Can be null for foreign recipients
     *
     * @return String or null
     */
    String getTaxId();
}
