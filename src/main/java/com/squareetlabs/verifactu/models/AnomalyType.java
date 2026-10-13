package com.squareetlabs.verifactu.models;

/**
 * Anomaly types of a billing or event record (list L1E of the annex of Orden HAC/1177/2024). The code is the value of
 * {@code TipoAnomalia} in the event record of an anomaly detection.
 */
public enum AnomalyType {

    INTEGRITY_HASH("01", "Integridad-huella"),
    INTEGRITY_SIGNATURE("02", "Integridad-firma"),
    INTEGRITY_OTHER("03", "Integridad - Otros"),
    CHAIN_RECORD_PREVIOUS_MISSING("04", "Trazabilidad-cadena-registro - Reg. no primero pero con reg. anterior no anotado o inexistente"),
    CHAIN_RECORD_NEXT_MISSING("05", "Trazabilidad-cadena-registro - Reg. no último pero con reg. posterior no anotado o inexistente"),
    CHAIN_RECORD_OTHER("06", "Trazabilidad-cadena-registro - Otros"),
    CHAIN_HASH_NOT_MATCHING_NEXT("07",
            "Trazabilidad-cadena-huella - Huella del reg. no se corresponde con la 'huella del reg. anterior' almacenada en el registro posterior"),
    CHAIN_HASH_PREVIOUS_FIELD("08",
            "Trazabilidad-cadena-huella - Campo 'huella del reg. anterior' no se corresponde con la huella del reg. anterior"),
    CHAIN_HASH_OTHER("09", "Trazabilidad-cadena-huella - Otros"),
    CHAIN_OTHER("10", "Trazabilidad-cadena - Otros"),
    DATES_BEFORE_PREVIOUS("11", "Trazabilidad-fechas - Fecha-hora anterior a la fecha del reg. anterior"),
    DATES_AFTER_NEXT("12", "Trazabilidad-fechas - Fecha-hora posterior a la fecha del reg. posterior"),
    DATES_IN_THE_FUTURE("13", "Trazabilidad-fechas - Reg. con fecha-hora de generación posterior a la fecha-hora actual del sistema"),
    DATES_OTHER("14", "Trazabilidad-fechas - Otros"),
    TRACEABILITY_OTHER("15", "Trazabilidad - Otros"),
    OTHER("90", "Otros");

    private final String code;
    private final String description;

    AnomalyType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
