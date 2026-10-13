package com.squareetlabs.verifactu.models;

/**
 * Event types of the event log of a billing system (list L2E of the annex of Orden HAC/1177/2024). The code is
 * the value of {@code TipoEvento} in the event record.
 */
public enum EventType {

    NO_VERIFACTU_START("01", "Inicio del funcionamiento del sistema informático como «NO VERI*FACTU»"),
    NO_VERIFACTU_END("02", "Fin del funcionamiento del sistema informático como «NO VERI*FACTU»"),
    BILLING_ANOMALY_CHECK_LAUNCHED("03", "Lanzamiento del proceso de detección de anomalías en los registros de facturación"),
    BILLING_ANOMALY_DETECTED("04",
            "Detección de anomalías en la integridad, inalterabilidad y trazabilidad de registros de facturación"),
    EVENT_ANOMALY_CHECK_LAUNCHED("05", "Lanzamiento del proceso de detección de anomalías en los registros de evento"),
    EVENT_ANOMALY_DETECTED("06",
            "Detección de anomalías en la integridad, inalterabilidad y trazabilidad de registros de evento"),
    BACKUP_RESTORED("07",
            "Restauración de copia de seguridad, cuando ésta se gestione desde el propio sistema informático de facturación"),
    BILLING_RECORDS_EXPORTED("08", "Exportación de registros de facturación generados en un periodo"),
    EVENT_RECORDS_EXPORTED("09", "Exportación de registros de evento generados en un periodo"),
    EVENTS_SUMMARY("10", "Registro resumen de eventos"),
    OTHER("90", "Otros tipos de eventos a registrar voluntariamente por la persona o entidad productora del sistema informático");

    private final String code;
    private final String description;

    EventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static EventType fromCode(String code) {
        for (EventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + code);
    }
}
