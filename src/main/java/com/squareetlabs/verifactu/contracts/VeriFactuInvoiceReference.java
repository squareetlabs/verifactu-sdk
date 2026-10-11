package com.squareetlabs.verifactu.contracts;

import java.time.LocalDate;

/**
 * Reference to another invoice (rectified or substituted by the one being sent):
 * {@code IDFacturaARType} of the AEAT schema.
 */
public interface VeriFactuInvoiceReference {

    /**
     * Tax ID of the issuer of the referenced invoice. {@code null} means "the same issuer as the
     * invoice being sent" (the usual case).
     */
    String getIssuerTaxId();

    String getInvoiceNumber();

    LocalDate getIssueDate();
}
