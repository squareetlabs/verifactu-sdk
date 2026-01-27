package com.squareetlabs.verifactu.models;

/**
 * Clave de régimen especial o trascendencia según AEAT.
 * Special regime or transcendence key according to AEAT.
 */
public enum RegimeType {
    /**
     * 01: Operación de régimen general
     */
    GENERAL("01", "Régimen general"),

    /**
     * 02: Exportación
     */
    EXPORTACION("02", "Exportación"),

    /**
     * 03: Operaciones a las que se aplique el régimen especial de bienes usados
     */
    BIENES_USADOS("03", "Bienes usados"),

    /**
     * 04: Régimen especial del oro de inversión
     */
    ORO_INVERSION("04", "Oro de inversión"),

    /**
     * 05: Régimen especial de las agencias de viajes
     */
    AGENCIAS_VIAJES("05", "Agencias de viajes"),

    /**
     * 06: Régimen especial grupo de entidades en IVA (Nivel Avanzado)
     */
    GRUPO_ENTIDADES("06", "Grupo de entidades"),

    /**
     * 07: Régimen especial del criterio de caja
     */
    CRITERIO_CAJA("07", "Criterio de caja"),

    /**
     * 08: Operaciones sujetas al IPSI / IGIC (Impuesto sobre la Producción, los
     * Servicios y la Importación / Impuesto General Indirecto Canario)
     */
    IPSI_IGIC("08", "IPSI/IGIC"),

    /**
     * 09: Facturación de las prestaciones de servicios de agencias de viaje que
     * actúan como mediadoras en nombre y por cuenta ajena (D.A.4ª RD1619/2012)
     */
    AGENCIAS_MEDIADORAS("09", "Agencias mediadoras"),

    /**
     * 10: Cobros por cuenta de terceros de honorarios profesionales o de derechos
     * derivados de la propiedad industrial, de autor u otros
     */
    COBROS_TERCEROS("10", "Cobros por cuenta de terceros"),

    /**
     * 11: Operaciones de arrendamiento de local de negocio
     */
    ARRENDAMIENTO_LOCAL("11", "Arrendamiento local negocio"),

    /**
     * 12: Operaciones de arrendamiento de local de negocio sujetas a retención
     */
    ARRENDAMIENTO_RETENCION("12", "Arrendamiento con retención"),

    /**
     * 13: Operaciones de arrendamiento de local de negocio no sujetas a retención
     */
    ARRENDAMIENTO_SIN_RETENCION("13", "Arrendamiento sin retención"),

    /**
     * 14: Factura con IVA pendiente de devengo (certificaciones de obra cuyo
     * destinatario sea una Administración Pública)
     */
    IVA_PENDIENTE("14", "IVA pendiente de devengo"),

    /**
     * 15: Factura con inversión del sujeto pasivo
     */
    INVERSION_SUJETO_PASIVO("15", "Inversión sujeto pasivo");

    private final String code;
    private final String description;

    RegimeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static RegimeType fromCode(String code) {
        for (RegimeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid regime type code: " + code);
    }

    @Override
    public String toString() {
        return code;
    }
}
