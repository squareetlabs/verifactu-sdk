package com.squareetlabs.verifactu.services;

public class VeriFactuConfig {
    private String issuerName;
    private String issuerVat;

    // Representative information (Cabecera.Representante).
    // Fill in ONLY when the invoicing records sent to the AEAT are generated
    // and/or remitted by a representative/advisor acting on behalf of the
    // "obligado tributario" (e.g. a software company or gestoría acting
    // under apoderamiento or colaboración social - Convenio 017).
    // Leave both fields null/empty when the issuer sends its own records.
    private String representativeName;
    private String representativeVat;

    // System developer/manufacturer information
    private String developerName = "SQUAREET LABS";
    private String developerNif = "B12345678";

    private String systemName = "JavaVeriFactu";
    private String systemId = "JV";
    private String systemVersion = "1.0";
    private String installationNumber = "001";
    private String onlyVerifactuCapable = "S";
    // TipoUsoPosibleMultiOT: structural capability of the SIF. Set to "S" when
    // this same software installation is used to manage the invoicing of more
    // than one "obligado tributario" (e.g. a multi-tenant SaaS backend such as
    // Odei's, where each client is a different obligado). Defaults to "N" for
    // backward compatibility with single-tenant integrations.
    private String multiObligatedCapable = "N";
    // IndicadorMultiplesOT: per AEAT rules this MUST be computed automatically
    // for each invoicing record based on how many "facturaciones" the current
    // client/user has registered in the system at that moment - it must never
    // be a fixed value chosen by the developer or the end user. This field is
    // only the fallback used when the invoice being sent does not provide its
    // own value via VeriFactuInvoice#getMultipleObligatedIndicator(). SaaS
    // integrators should compute and pass that per-invoice value instead of
    // relying on this static default.
    private String hasMultipleObligated = "N";

    public VeriFactuConfig(String issuerName, String issuerVat) {
        this.issuerName = issuerName;
        this.issuerVat = issuerVat;
    }

    // Getters and Setters
    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public String getIssuerVat() {
        return issuerVat;
    }

    public void setIssuerVat(String issuerVat) {
        this.issuerVat = issuerVat;
    }

    public String getRepresentativeName() {
        return representativeName;
    }

    public void setRepresentativeName(String representativeName) {
        this.representativeName = representativeName;
    }

    public String getRepresentativeVat() {
        return representativeVat;
    }

    public void setRepresentativeVat(String representativeVat) {
        this.representativeVat = representativeVat;
    }

    /**
     * Whether this config declares a representative acting on behalf of the
     * issuer (i.e. {@code Cabecera.Representante} must be sent). True only
     * when both name and NIF are present.
     */
    public boolean hasRepresentative() {
        return representativeName != null && !representativeName.trim().isEmpty()
                && representativeVat != null && !representativeVat.trim().isEmpty();
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }

    public String getInstallationNumber() {
        return installationNumber;
    }

    public void setInstallationNumber(String installationNumber) {
        this.installationNumber = installationNumber;
    }

    public String getOnlyVerifactuCapable() {
        return onlyVerifactuCapable;
    }

    public void setOnlyVerifactuCapable(String onlyVerifactuCapable) {
        this.onlyVerifactuCapable = onlyVerifactuCapable;
    }

    public String getMultiObligatedCapable() {
        return multiObligatedCapable;
    }

    public void setMultiObligatedCapable(String multiObligatedCapable) {
        this.multiObligatedCapable = multiObligatedCapable;
    }

    public String getHasMultipleObligated() {
        return hasMultipleObligated;
    }

    public void setHasMultipleObligated(String hasMultipleObligated) {
        this.hasMultipleObligated = hasMultipleObligated;
    }

    public String getDeveloperName() {
        return developerName;
    }

    public void setDeveloperName(String developerName) {
        this.developerName = developerName;
    }

    public String getDeveloperNif() {
        return developerNif;
    }

    public void setDeveloperNif(String developerNif) {
        this.developerNif = developerNif;
    }
}
