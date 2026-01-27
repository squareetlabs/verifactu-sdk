package com.squareetlabs.verifactu.services;

public class VeriFactuConfig {
    private String issuerName;
    private String issuerVat;

    // System developer/manufacturer information
    private String developerName = "SQUAREET LABS";
    private String developerNif = "B12345678";

    private String systemName = "JavaVeriFactu";
    private String systemId = "JV";
    private String systemVersion = "1.0";
    private String installationNumber = "001";
    private String onlyVerifactuCapable = "S";
    private String multiObligatedCapable = "N";
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
