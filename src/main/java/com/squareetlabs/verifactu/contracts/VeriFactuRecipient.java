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

    /**
     * ISO 3166-1 alpha-2 country code of a recipient identified with a foreign document
     * ({@code IDOtro}). {@code null} (default) or {@code "ES"} with no {@link #getIdType()} means a
     * Spanish NIF.
     */
    default String getCountryCode() {
        return null;
    }

    /**
     * AEAT identification type for a foreign recipient ({@code IDOtro.IDType}): 02 NIF-IVA,
     * 03 passport, 04 official document of the country of residence, 05 residence certificate,
     * 06 other proof, 07 not registered. {@code null} (default) means a Spanish NIF.
     */
    default String getIdType() {
        return null;
    }
}
