package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuInvoiceReference;

import java.time.LocalDate;

/** Default {@link VeriFactuInvoiceReference} implementation. */
public class InvoiceReference implements VeriFactuInvoiceReference {
    private String issuerTaxId;
    private String invoiceNumber;
    private LocalDate issueDate;

    public InvoiceReference() {
    }

    public InvoiceReference(String invoiceNumber, LocalDate issueDate) {
        this(null, invoiceNumber, issueDate);
    }

    public InvoiceReference(String issuerTaxId, String invoiceNumber, LocalDate issueDate) {
        this.issuerTaxId = issuerTaxId;
        this.invoiceNumber = invoiceNumber;
        this.issueDate = issueDate;
    }

    @Override
    public String getIssuerTaxId() {
        return issuerTaxId;
    }

    public void setIssuerTaxId(String issuerTaxId) {
        this.issuerTaxId = issuerTaxId;
    }

    @Override
    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    @Override
    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }
}
