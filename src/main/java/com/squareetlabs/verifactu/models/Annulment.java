package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuAnnulment;

import java.time.LocalDate;

/**
 * Default {@link VeriFactuAnnulment} implementation: requests the AEAT
 * annulment ("anulación") of a previously sent billing record.
 *
 * @see VeriFactuAnnulment
 */
public class Annulment implements VeriFactuAnnulment {
    private String invoiceNumber;
    private LocalDate issueDate;
    private String previousHash;
    private String externalReference;
    private boolean notRegisteredAtAeat;
    private boolean retryAfterRejection;
    private String generatedBy;
    private String generatorName;
    private String generatorTaxId;

    public Annulment() {
    }

    public Annulment(String invoiceNumber, LocalDate issueDate) {
        this.invoiceNumber = invoiceNumber;
        this.issueDate = issueDate;
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

    @Override
    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    @Override
    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    @Override
    public boolean isNotRegisteredAtAeat() {
        return notRegisteredAtAeat;
    }

    public void setNotRegisteredAtAeat(boolean notRegisteredAtAeat) {
        this.notRegisteredAtAeat = notRegisteredAtAeat;
    }

    @Override
    public boolean isRetryAfterRejection() {
        return retryAfterRejection;
    }

    public void setRetryAfterRejection(boolean retryAfterRejection) {
        this.retryAfterRejection = retryAfterRejection;
    }

    @Override
    public String getGeneratedBy() {
        return generatedBy;
    }

    /**
     * @param generatedBy "T", "D" or null (see {@link VeriFactuAnnulment#getGeneratedBy()})
     */
    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    @Override
    public String getGeneratorName() {
        return generatorName;
    }

    public void setGeneratorName(String generatorName) {
        this.generatorName = generatorName;
    }

    @Override
    public String getGeneratorTaxId() {
        return generatorTaxId;
    }

    public void setGeneratorTaxId(String generatorTaxId) {
        this.generatorTaxId = generatorTaxId;
    }
}
