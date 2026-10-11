package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoiceReference;
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
    private java.time.ZonedDateTime generationTimestamp;
    private String operationDescription;
    private LocalDate operationDate;
    private String taxPeriod;
    private String correctionType;
    private String externalReference;

    // Corrected amounts for ImporteRectificacion block
    private Double correctedBaseAmount;
    private Double correctedTaxAmount;
    private Double correctedSurchargeAmount;

    // Third-party issuance (EmitidaPorTerceroODestinatario / Tercero) and
    // per-invoice multi-tenant indicator (IndicadorMultiplesOT override)
    private String issuedByThirdPartyOrRecipient;
    private String thirdPartyName;
    private String thirdPartyTaxId;
    private Boolean multipleObligatedIndicator;

    private List<VeriFactuInvoiceReference> rectifiedInvoices = new ArrayList<>();
    private List<VeriFactuInvoiceReference> substitutedInvoices = new ArrayList<>();
    private boolean withoutRecipientIdentification;
    private boolean subsanation;
    private String previousRejection;
    private boolean qualifiedSimplified;
    private boolean coupon;

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
    public java.time.ZonedDateTime getGenerationTimestamp() {
        return generationTimestamp;
    }

    public void setGenerationTimestamp(java.time.ZonedDateTime generationTimestamp) {
        this.generationTimestamp = generationTimestamp;
    }

    @Override
    public String getOperationDescription() {
        return operationDescription;
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

    @Override
    public String getIssuedByThirdPartyOrRecipient() {
        return issuedByThirdPartyOrRecipient;
    }

    /**
     * Marks this invoice as issued by a third party ("T") or by the
     * recipient itself / self-billing ("D"), instead of by the obligado
     * itself. Pass {@code null} (default) when the issuer expide la factura
     * por sí mismo.
     *
     * @param issuedByThirdPartyOrRecipient "T", "D" or null
     */
    public void setIssuedByThirdPartyOrRecipient(String issuedByThirdPartyOrRecipient) {
        this.issuedByThirdPartyOrRecipient = issuedByThirdPartyOrRecipient;
    }

    @Override
    public String getThirdPartyName() {
        return thirdPartyName;
    }

    public void setThirdPartyName(String thirdPartyName) {
        this.thirdPartyName = thirdPartyName;
    }

    @Override
    public String getThirdPartyTaxId() {
        return thirdPartyTaxId;
    }

    public void setThirdPartyTaxId(String thirdPartyTaxId) {
        this.thirdPartyTaxId = thirdPartyTaxId;
    }

    @Override
    public Boolean getMultipleObligatedIndicator() {
        return multipleObligatedIndicator;
    }

    /**
     * Per-invoice override for {@code IndicadorMultiplesOT}. Set this to the
     * value computed by your backend for the specific client/tenant that
     * owns this invoice (true when that client currently has more than one
     * "facturación" registered in your multi-tenant system). Leave as
     * {@code null} to fall back to
     * {@link com.squareetlabs.verifactu.services.VeriFactuConfig#getHasMultipleObligated()}.
     *
     * @param multipleObligatedIndicator Boolean or null
     */
    public void setMultipleObligatedIndicator(Boolean multipleObligatedIndicator) {
        this.multipleObligatedIndicator = multipleObligatedIndicator;
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
        return getValidationUrl(production, true);
    }

    /**
     * URL of the AEAT invoice lookup service for the QR code. The AEAT publishes two services:
     * {@code ValidarQR} for systems issuing verifiable invoices (VERI*FACTU) and
     * {@code ValidarQRNoVerifactu} for the rest.
     *
     * @param production   true for production, false for the external test environment
     * @param verifactuMode true when the system issues verifiable invoices (VERI*FACTU)
     */
    public String getValidationUrl(boolean production, boolean verifactuMode) {
        if (issuerTaxId == null || invoiceNumber == null || issueDate == null) {
            throw new IllegalStateException(
                    "Cannot generate validation URL: issuerTaxId, invoiceNumber, and issueDate are required");
        }

        String host = production
                ? "https://www2.agenciatributaria.gob.es"
                : "https://prewww2.aeat.es";
        String service = verifactuMode ? "ValidarQR" : "ValidarQRNoVerifactu";
        String baseUrl = host + "/wlpl/TIKE-CONT/" + service;

        String fecha = issueDate.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        java.math.BigDecimal amount = java.math.BigDecimal.valueOf(totalAmount)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        String importe = amount.signum() == 0 ? "0.00" : amount.toPlainString();

        // AEAT QR specification: every parameter value is URL-encoded (UTF-8); a series such as
        // "A/2026 1" must not break the query string. Only these four parameters may go in the QR.
        return String.format("%s?nif=%s&numserie=%s&fecha=%s&importe=%s",
                baseUrl,
                urlEncode(issuerTaxId),
                urlEncode(invoiceNumber),
                fecha,
                importe);
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
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
        return getValidationQR(production, true, size);
    }

    /**
     * Generates the QR code image of the validation URL. The AEAT requires ISO/IEC 18004:2015 with
     * error correction level M and a printed size between 30x30 and 40x40 mm.
     *
     * @param production    true for production, false for the external test environment
     * @param verifactuMode true when the system issues verifiable invoices (VERI*FACTU)
     * @param size          the size of the QR code image in pixels (width and height)
     * @return byte array containing the QR code image in PNG format
     */
    public byte[] getValidationQR(boolean production, boolean verifactuMode, int size) throws Exception {
        String url = getValidationUrl(production, verifactuMode);

        java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
        hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);

        com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
        com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(
                url,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                hints);

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

    @Override
    public List<VeriFactuInvoiceReference> getRectifiedInvoices() {
        return rectifiedInvoices;
    }

    public void setRectifiedInvoices(List<VeriFactuInvoiceReference> rectifiedInvoices) {
        this.rectifiedInvoices = rectifiedInvoices != null ? rectifiedInvoices : new ArrayList<>();
    }

    @Override
    public List<VeriFactuInvoiceReference> getSubstitutedInvoices() {
        return substitutedInvoices;
    }

    public void setSubstitutedInvoices(List<VeriFactuInvoiceReference> substitutedInvoices) {
        this.substitutedInvoices = substitutedInvoices != null ? substitutedInvoices : new ArrayList<>();
    }

    @Override
    public boolean isWithoutRecipientIdentification() {
        return withoutRecipientIdentification;
    }

    public void setWithoutRecipientIdentification(boolean withoutRecipientIdentification) {
        this.withoutRecipientIdentification = withoutRecipientIdentification;
    }

    @Override
    public boolean isSubsanation() {
        return subsanation;
    }

    public void setSubsanation(boolean subsanation) {
        this.subsanation = subsanation;
    }

    @Override
    public String getPreviousRejection() {
        return previousRejection;
    }

    public void setPreviousRejection(String previousRejection) {
        this.previousRejection = previousRejection;
    }

    @Override
    public boolean isQualifiedSimplified() {
        return qualifiedSimplified;
    }

    public void setQualifiedSimplified(boolean qualifiedSimplified) {
        this.qualifiedSimplified = qualifiedSimplified;
    }

    @Override
    public boolean isCoupon() {
        return coupon;
    }

    public void setCoupon(boolean coupon) {
        this.coupon = coupon;
    }
}
