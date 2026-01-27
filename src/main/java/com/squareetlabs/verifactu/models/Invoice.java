package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;
import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Invoice implements VeriFactuInvoice {
    private String invoiceNumber;
    private LocalDate issueDate;
    private InvoiceType invoiceType;
    private double totalAmount;
    private double taxAmount;
    private String customerName;
    private String customerTaxId;
    private List<VeriFactuBreakdown> breakdowns = new ArrayList<>();
    private List<VeriFactuRecipient> recipients = new ArrayList<>();
    private String previousHash;
    private String operationDescription;
    private LocalDate operationDate;
    private String taxPeriod;
    private String correctionType;
    private String externalReference;

    // Corrected amounts for ImporteRectificacion block
    private Double correctedBaseAmount;
    private Double correctedTaxAmount;
    private Double correctedSurchargeAmount;

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

    /**
     * Gets the invoice type code (legacy method for backward compatibility).
     * 
     * @return The invoice type code (F1, F2, etc.)
     * @deprecated Use {@link #getInvoiceTypeEnum()} instead
     */
    @Override
    @Deprecated
    public String getInvoiceType() {
        return invoiceType != null ? invoiceType.getCode() : null;
    }

    /**
     * Gets the invoice type as enum.
     * 
     * @return The InvoiceType enum
     */
    public InvoiceType getInvoiceTypeEnum() {
        return invoiceType;
    }

    /**
     * Sets the invoice type using enum (recommended).
     * 
     * @param invoiceType The InvoiceType enum
     */
    public void setInvoiceType(InvoiceType invoiceType) {
        this.invoiceType = invoiceType;
    }

    /**
     * Sets the invoice type using code string (legacy method for backward
     * compatibility).
     * 
     * @param invoiceTypeCode The invoice type code (F1, F2, etc.)
     * @deprecated Use {@link #setInvoiceType(InvoiceType)} instead
     */
    @Deprecated
    public void setInvoiceType(String invoiceTypeCode) {
        this.invoiceType = invoiceTypeCode != null ? InvoiceType.fromCode(invoiceTypeCode) : null;
    }

    @Override
    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public double getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(double taxAmount) {
        this.taxAmount = taxAmount;
    }

    private String issuerName;
    private String issuerTaxId;

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public String getIssuerTaxId() {
        return issuerTaxId;
    }

    public void setIssuerTaxId(String issuerTaxId) {
        this.issuerTaxId = issuerTaxId;
    }

    @Override
    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    @Override
    public String getCustomerTaxId() {
        return customerTaxId;
    }

    public void setCustomerTaxId(String customerTaxId) {
        this.customerTaxId = customerTaxId;
    }

    @Override
    public List<VeriFactuBreakdown> getBreakdowns() {
        return breakdowns;
    }

    public void setBreakdowns(List<VeriFactuBreakdown> breakdowns) {
        this.breakdowns = breakdowns;
    }

    @Override
    public List<VeriFactuRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<VeriFactuRecipient> recipients) {
        this.recipients = recipients;
    }

    @Override
    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    @Override
    public String getOperationDescription() {
        return operationDescription == null ? "Invoice issued" : operationDescription;
    }

    public void setOperationDescription(String operationDescription) {
        this.operationDescription = operationDescription;
    }

    @Override
    public LocalDate getOperationDate() {
        return operationDate;
    }

    public void setOperationDate(LocalDate operationDate) {
        this.operationDate = operationDate;
    }

    @Override
    public String getTaxPeriod() {
        return taxPeriod;
    }

    public void setTaxPeriod(String taxPeriod) {
        this.taxPeriod = taxPeriod;
    }

    @Override
    public String getCorrectionType() {
        return correctionType;
    }

    public void setCorrectionType(String correctionType) {
        this.correctionType = correctionType;
    }

    @Override
    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    @Override
    public Double getCorrectedBaseAmount() {
        return correctedBaseAmount;
    }

    public void setCorrectedBaseAmount(Double correctedBaseAmount) {
        this.correctedBaseAmount = correctedBaseAmount;
    }

    @Override
    public Double getCorrectedTaxAmount() {
        return correctedTaxAmount;
    }

    public void setCorrectedTaxAmount(Double correctedTaxAmount) {
        this.correctedTaxAmount = correctedTaxAmount;
    }

    @Override
    public Double getCorrectedSurchargeAmount() {
        return correctedSurchargeAmount;
    }

    public void setCorrectedSurchargeAmount(Double correctedSurchargeAmount) {
        this.correctedSurchargeAmount = correctedSurchargeAmount;
    }

    /**
     * Generates the validation URL for the invoice according to AEAT
     * specifications.
     * This URL can be used in the QR code for invoice validation.
     *
     * @param production true for production environment, false for test environment
     * @return The validation URL
     */
    public String getValidationUrl(boolean production) {
        if (issuerTaxId == null || invoiceNumber == null || issueDate == null) {
            throw new IllegalStateException(
                    "Cannot generate validation URL: issuerTaxId, invoiceNumber, and issueDate are required");
        }

        String baseUrl = production
                ? "https://www2.aeat.es/wlpl/TIKE-CONT/ValidarQR"
                : "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR";

        String fecha = issueDate.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        return String.format("%s?nif=%s&numserie=%s&fecha=%s&importe=%.2f",
                baseUrl,
                issuerTaxId,
                invoiceNumber,
                fecha,
                totalAmount);
    }

    /**
     * Generates a QR code image containing the validation URL for this invoice.
     * The QR code follows AEAT specifications for invoice validation.
     *
     * @param production true for production environment, false for test environment
     * @param size       the size of the QR code image in pixels (width and height)
     * @return byte array containing the QR code image in PNG format
     * @throws Exception if QR code generation fails
     */
    public byte[] getValidationQR(boolean production, int size) throws Exception {
        String url = getValidationUrl(production);

        com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
        com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size);

        java.io.ByteArrayOutputStream pngOutputStream = new java.io.ByteArrayOutputStream();
        com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        return pngOutputStream.toByteArray();
    }

    /**
     * Generates a QR code image with default size (200x200 pixels).
     *
     * @param production true for production environment, false for test environment
     * @return byte array containing the QR code image in PNG format
     * @throws Exception if QR code generation fails
     */
    public byte[] getValidationQR(boolean production) throws Exception {
        return getValidationQR(production, 200);
    }
}
