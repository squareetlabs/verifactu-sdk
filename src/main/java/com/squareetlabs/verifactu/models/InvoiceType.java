package com.squareetlabs.verifactu.models;

/**
 * Invoice types according to AEAT VeriFactu specifications.
 * Tipos de factura según las especificaciones AEAT.
 */
public enum InvoiceType {
    /**
     * F1: Factura (art. 6, 7.2 y 7.3 RD 1619/2012)
     * Standard invoice
     */
    F1("F1", "Factura"),

    /**
     * F2: Factura simplificada (art. 7.2 y 7.3 RD 1619/2012)
     * Simplified invoice
     */
    F2("F2", "Factura simplificada"),

    /**
     * F3: Factura emitida en sustitución de facturas simplificadas facturadas y
     * declaradas
     * Invoice issued to replace simplified invoices that were billed and declared
     */
    F3("F3", "Factura sustitución simplificadas"),

    /**
     * F4: Asiento resumen de facturas
     * Summary entry of invoices
     */
    F4("F4", "Asiento resumen"),

    /**
     * R1: Factura rectificativa (art. 80.1, 80.2 y 80.6 LIVA)
     * Corrective invoice (art. 80.1, 80.2 and 80.6 LIVA)
     */
    R1("R1", "Rectificativa art. 80.1, 80.2, 80.6"),

    /**
     * R2: Factura rectificativa (art. 80.3)
     * Corrective invoice (art. 80.3)
     */
    R2("R2", "Rectificativa art. 80.3"),

    /**
     * R3: Factura rectificativa (art. 80.4)
     * Corrective invoice (art. 80.4)
     */
    R3("R3", "Rectificativa art. 80.4"),

    /**
     * R4: Factura rectificativa (resto)
     * Corrective invoice (other)
     */
    R4("R4", "Rectificativa resto"),

    /**
     * R5: Factura rectificativa en facturas simplificadas
     * Corrective invoice for simplified invoices
     */
    R5("R5", "Rectificativa simplificadas");

    private final String code;
    private final String description;

    InvoiceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get InvoiceType from code string.
     * 
     * @param code The invoice type code (F1, F2, etc.)
     * @return The corresponding InvoiceType
     * @throws IllegalArgumentException if code is not valid
     */
    public static InvoiceType fromCode(String code) {
        for (InvoiceType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid invoice type code: " + code);
    }

    @Override
    public String toString() {
        return code;
    }
}
