package com.squareetlabs.verifactu.models;

import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;

/**
 * Implementation of invoice breakdown (desglose) according to AEAT VeriFactu
 * specifications.
 */
public class Breakdown implements VeriFactuBreakdown {
    // Core AEAT fields
    private String claveRegimen;
    private String calificacionOperacion;
    private String operacionExenta;
    private double taxRate;
    private double baseAmount;
    private Double baseImponibleACoste;
    private double taxAmount;
    private Double tipoRecargoEquivalencia;
    private Double cuotaRecargoEquivalencia;

    // Legacy fields (deprecated)
    @Deprecated
    private String regimeType;
    @Deprecated
    private String operationType;

    /**
     * Constructor with all required AEAT fields.
     */
    public Breakdown(String claveRegimen, String calificacionOperacion, double taxRate,
            double baseAmount, double taxAmount) {
        this.claveRegimen = claveRegimen;
        this.calificacionOperacion = calificacionOperacion;
        this.taxRate = taxRate;
        this.baseAmount = baseAmount;
        this.taxAmount = taxAmount;

        // Set legacy fields for backward compatibility
        this.regimeType = claveRegimen;
        this.operationType = calificacionOperacion;
    }

    public Breakdown() {

    }

    // AEAT specification getters
    @Override
    public String getClaveRegimen() {
        return claveRegimen;
    }

    public void setClaveRegimen(String claveRegimen) {
        this.claveRegimen = claveRegimen;
        this.regimeType = claveRegimen; // Keep legacy in sync
    }

    @Override
    public String getCalificacionOperacion() {
        return calificacionOperacion;
    }

    public void setCalificacionOperacion(String calificacionOperacion) {
        this.calificacionOperacion = calificacionOperacion;
        this.operationType = calificacionOperacion; // Keep legacy in sync
    }

    @Override
    public String getOperacionExenta() {
        return operacionExenta;
    }

    public void setOperacionExenta(String operacionExenta) {
        this.operacionExenta = operacionExenta;
    }

    @Override
    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = taxRate;
    }

    @Override
    public double getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(double baseAmount) {
        this.baseAmount = baseAmount;
    }

    @Override
    public Double getBaseImponibleACoste() {
        return baseImponibleACoste;
    }

    public void setBaseImponibleACoste(Double baseImponibleACoste) {
        this.baseImponibleACoste = baseImponibleACoste;
    }

    @Override
    public double getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(double taxAmount) {
        this.taxAmount = taxAmount;
    }

    @Override
    public Double getTipoRecargoEquivalencia() {
        return tipoRecargoEquivalencia;
    }

    public void setTipoRecargoEquivalencia(Double tipoRecargoEquivalencia) {
        this.tipoRecargoEquivalencia = tipoRecargoEquivalencia;
    }

    @Override
    public Double getCuotaRecargoEquivalencia() {
        return cuotaRecargoEquivalencia;
    }

    public void setCuotaRecargoEquivalencia(Double cuotaRecargoEquivalencia) {
        this.cuotaRecargoEquivalencia = cuotaRecargoEquivalencia;
    }

    // Legacy getters (deprecated)
    @Override
    @Deprecated
    public String getRegimeType() {
        return claveRegimen != null ? claveRegimen : regimeType;
    }

    @Deprecated
    public void setRegimeType(String regimeType) {
        this.regimeType = regimeType;
        this.claveRegimen = regimeType; // Keep new field in sync
    }

    @Override
    @Deprecated
    public String getOperationType() {
        return calificacionOperacion != null ? calificacionOperacion : operationType;
    }

    @Deprecated
    public void setOperationType(String operationType) {
        this.operationType = operationType;
        this.calificacionOperacion = operationType; // Keep new field in sync
    }
}
