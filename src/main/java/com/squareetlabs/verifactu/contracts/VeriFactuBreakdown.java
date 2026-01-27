package com.squareetlabs.verifactu.contracts;

/**
 * Contract for invoice breakdown (desglose) according to AEAT VeriFactu
 * specifications.
 * Represents the tax breakdown details for each line item in an invoice.
 */
public interface VeriFactuBreakdown {

    /**
     * Clave del régimen especial o trascendencia.
     * Examples: "01" (General), "02" (Exportación), etc.
     */
    String getClaveRegimen();

    /**
     * Calificación de la operación.
     * Examples: "S1" (Sujeta), "S2" (Sujeta y exenta), "N1" (No sujeta), etc.
     */
    String getCalificacionOperacion();

    /**
     * Tipo de operación exenta (if applicable).
     * Examples: "E1", "E2", "E3", "E4", "E5", "E6"
     */
    String getOperacionExenta();

    /**
     * Tax rate percentage.
     * Examples: 21.0, 10.0, 4.0, 0.0
     */
    double getTaxRate();

    /**
     * Base imponible o importe no sujeto.
     * The taxable base amount or non-taxable amount.
     */
    double getBaseAmount();

    /**
     * Base imponible a coste (for special regimes).
     * Optional field for certain special tax regimes.
     */
    Double getBaseImponibleACoste();

    /**
     * Cuota repercutida (tax amount charged).
     * The actual tax amount calculated on the base.
     */
    double getTaxAmount();

    /**
     * Tipo de recargo de equivalencia (equivalence surcharge rate).
     * Optional, used in certain tax regimes.
     */
    Double getTipoRecargoEquivalencia();

    /**
     * Cuota de recargo de equivalencia (equivalence surcharge amount).
     * Optional, used in certain tax regimes.
     */
    Double getCuotaRecargoEquivalencia();

    // Legacy methods for backward compatibility
    @Deprecated
    String getRegimeType();

    @Deprecated
    String getOperationType();
}
