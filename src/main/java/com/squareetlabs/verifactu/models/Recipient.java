package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuRecipient;

public class Recipient implements VeriFactuRecipient {
    private String name;
    private String taxId;

    public Recipient(String name, String taxId) {
        this.name = name;
        this.taxId = taxId;
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
}
