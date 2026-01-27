package com.squareetlabs.verifactu.models;

/**
 * Calificación de la operación según AEAT.
 * Operation qualification according to AEAT.
 */
public enum OperationType {
    /**
     * S1: Operación sujeta y no exenta - Sin inversión del sujeto pasivo
     */
    S1("S1", "Sujeta - Sin inversión"),

    /**
     * S2: Operación sujeta y no exenta - Con inversión del sujeto pasivo
     */
    S2("S2", "Sujeta - Con inversión"),

    /**
     * S3: Operación sujeta y exenta
     */
    S3("S3", "Sujeta y exenta"),

    /**
     * N1: Operación no sujeta por reglas de localización (Prestación de servicios)
     */
    N1("N1", "No sujeta - Localización"),

    /**
     * N2: Operación no sujeta por otras causas
     */
    N2("N2", "No sujeta - Otras causas");

    private final String code;
    private final String description;

    OperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static OperationType fromCode(String code) {
        for (OperationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid operation type code: " + code);
    }

    @Override
    public String toString() {
        return code;
    }
}
