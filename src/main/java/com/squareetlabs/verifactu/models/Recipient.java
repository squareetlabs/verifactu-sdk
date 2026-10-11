package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;

public class Recipient implements VeriFactuRecipient {
    private String name;
    private String taxId;
    private String countryCode;
    private String idType;

    public Recipient(String name, String taxId) {
        this.name = name;
        this.taxId = taxId;
    }

    /** Foreign recipient identified with an {@code IDOtro} document. */
    public Recipient(String name, String taxId, String countryCode, String idType) {
        this(name, taxId);
        this.countryCode = countryCode;
        this.idType = idType;
    }

    public Recipient() {
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    @Override
    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @Override
    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }
}
