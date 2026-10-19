package com.squareetlabs.verifactu.helpers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

public class HashHelper {
    private static final Set<String> INVOICE_REQUIRED_FIELDS = new HashSet<>(Arrays.asList(
            "issuer_tax_id",
            "invoice_number",
            "issue_date",
            "invoice_type",
            "total_tax",
            "total_amount",
            "previous_hash",
            "generated_at"));

    private static final Set<String> ANNULMENT_REQUIRED_FIELDS = new HashSet<>(Arrays.asList(
            "issuer_tax_id",
            "invoice_number",
            "issue_date",
            "previous_hash",
            "generated_at"));

    /**
     * Generates the hash for an invoice record according to AEAT specifications.
     *
     * @param data Invoice record data with snake_case keys (for compatibility).
     * @return Map containing 'hash' and 'inputString'
     * @throws IllegalArgumentException if required fields are missing or unexpected
     *                                  fields are present
     */
    public static Map<String, String> generateInvoiceHash(Map<String, String> data) {
        validateData(INVOICE_REQUIRED_FIELDS, data.keySet());

        StringBuilder inputString = new StringBuilder();
        inputString.append(field("IDEmisorFactura", data.get("issuer_tax_id"), true));
        inputString.append(field("NumSerieFactura", data.get("invoice_number"), true));
        inputString.append(field("FechaExpedicionFactura", data.get("issue_date"), true));
        inputString.append(field("TipoFactura", data.get("invoice_type"), true));
        inputString.append(field("CuotaTotal", data.get("total_tax"), true));
        inputString.append(field("ImporteTotal", data.get("total_amount"), true));
        inputString.append(field("Huella", data.get("previous_hash"), true));
        inputString.append(field("FechaHoraHusoGenRegistro", data.get("generated_at"), false));

        String hash = sha256(inputString.toString()).toUpperCase();

        Map<String, String> result = new HashMap<>();
        result.put("hash", hash);
        result.put("inputString", inputString.toString());
        return result;
    }

    public static String generateInvoiceHash(
            String issuerVat, String numSerie, String fechaExp, String tipoFactura,
            String cuotaTotal, String importeTotal, String huellaAnterior, String fechaHora) {

        StringBuilder inputString = new StringBuilder();
        inputString.append(field("IDEmisorFactura", issuerVat, true));
        inputString.append(field("NumSerieFactura", numSerie, true));
        inputString.append(field("FechaExpedicionFactura", fechaExp, true));
        inputString.append(field("TipoFactura", tipoFactura, true));
        inputString.append(field("CuotaTotal", cuotaTotal, true));
        inputString.append(field("ImporteTotal", importeTotal, true));
        inputString.append(field("Huella", huellaAnterior, true));
        inputString.append(field("FechaHoraHusoGenRegistro", fechaHora, false));

        return sha256(inputString.toString()).toUpperCase();
    }

    /**
     * Generates the hash for an annulment ("anulación") record according to
     * AEAT specifications ("Detalle de las especificaciones técnicas para la
     * generación de la huella o hash de los registros de facturación",
     * apartado b: registros de facturación de anulación).
     *
     * @param data Annulment record data with snake_case keys (for compatibility).
     * @return Map containing 'hash' and 'inputString'
     * @throws IllegalArgumentException if required fields are missing or unexpected
     *                                  fields are present
     */
    public static Map<String, String> generateAnnulmentHash(Map<String, String> data) {
        validateData(ANNULMENT_REQUIRED_FIELDS, data.keySet());

        StringBuilder inputString = new StringBuilder();
        inputString.append(field("IDEmisorFacturaAnulada", data.get("issuer_tax_id"), true));
        inputString.append(field("NumSerieFacturaAnulada", data.get("invoice_number"), true));
        inputString.append(field("FechaExpedicionFacturaAnulada", data.get("issue_date"), true));
        inputString.append(field("Huella", data.get("previous_hash"), true));
        inputString.append(field("FechaHoraHusoGenRegistro", data.get("generated_at"), false));

        String hash = sha256(inputString.toString()).toUpperCase();

        Map<String, String> result = new HashMap<>();
        result.put("hash", hash);
        result.put("inputString", inputString.toString());
        return result;
    }

    public static String generateAnnulmentHash(
            String issuerVat, String numSerie, String fechaExp, String huellaAnterior, String fechaHora) {

        StringBuilder inputString = new StringBuilder();
        inputString.append(field("IDEmisorFacturaAnulada", issuerVat, true));
        inputString.append(field("NumSerieFacturaAnulada", numSerie, true));
        inputString.append(field("FechaExpedicionFacturaAnulada", fechaExp, true));
        inputString.append(field("Huella", huellaAnterior, true));
        inputString.append(field("FechaHoraHusoGenRegistro", fechaHora, false));

        return sha256(inputString.toString()).toUpperCase();
    }

    /**
     * Generates the hash of an event record ("registro de evento") according to the AEAT document "Detalle de las
     * especificaciones técnicas para la generación de la huella o hash de los registros" (section 3.c): the fields in
     * the order of the record design, as {@code name=value} joined by {@code &}. The first event of a chain has no
     * previous hash ({@code HuellaEvento=} stays empty). The software system is identified by the developer's tax id
     * ({@code NIF}); {@code ID} stays empty.
     *
     * @param developerTaxId     {@code SistemaInformatico/NIF}
     * @param systemId           {@code SistemaInformatico/IdSistemaInformatico}
     * @param systemVersion      {@code SistemaInformatico/Version}
     * @param installationNumber {@code SistemaInformatico/NumeroInstalacion}
     * @param obligorTaxId       {@code ObligadoEmision/NIF}
     * @param eventType          {@code TipoEvento} (code of two digits)
     * @param previousHash       {@code Encadenamiento/EventoAnterior/HuellaEvento}, or {@code null} for the first event
     * @param generatedAt        {@code FechaHoraHusoGenEvento} (ISO 8601 with offset)
     * @return Map containing 'hash' and 'inputString'
     */
    public static Map<String, String> generateEventHash(String developerTaxId, String systemId, String systemVersion,
                                                         String installationNumber, String obligorTaxId,
                                                         String eventType, String previousHash, String generatedAt) {
        StringBuilder inputString = new StringBuilder();
        inputString.append(field("NIF", developerTaxId, true));
        inputString.append(field("ID", null, true));
        inputString.append(field("IdSistemaInformatico", systemId, true));
        inputString.append(field("Version", systemVersion, true));
        inputString.append(field("NumeroInstalacion", installationNumber, true));
        inputString.append(field("NIF", obligorTaxId, true));
        inputString.append(field("TipoEvento", eventType, true));
        inputString.append(field("HuellaEvento", previousHash, true));
        inputString.append(field("FechaHoraHusoGenEvento", generatedAt, false));

        Map<String, String> result = new HashMap<>();
        result.put("hash", sha256(inputString.toString()).toUpperCase());
        result.put("inputString", inputString.toString());
        return result;
    }

    /**
     * The generation instant of a record or an event, exactly as it entered its hash: {@code FechaHoraHusoGenRegistro}
     * or {@code FechaHoraHusoGenEvento} of the hash input string ({@code hashInput} of the response), in Europe/Madrid.
     * It keeps the offset, so in the repeated hour of the change of time (the last Sunday of October, from 02:00 to
     * 02:59) a value of the first pass (+02:00) is not read an hour later. Pass it to {@code setGenerationTimestamp} or
     * {@code Event.setGeneratedAt} to resend or regenerate the same record with the same hash; a local date-time column
     * without offset cannot tell the two passes apart.
     *
     * @param hashInput hash input string of a record or an event
     * @return the instant, or {@code null} if the string has no readable generation timestamp
     */
    public static java.time.ZonedDateTime generatedAtOf(String hashInput) {
        if (hashInput == null) {
            return null;
        }
        for (String name : new String[] {"FechaHoraHusoGenRegistro=", "FechaHoraHusoGenEvento="}) {
            int start = hashInput.lastIndexOf(name);
            if (start < 0) {
                continue;
            }
            start += name.length();
            int end = hashInput.indexOf('&', start);
            try {
                return java.time.OffsetDateTime.parse(end < 0 ? hashInput.substring(start) : hashInput.substring(start, end))
                        .atZoneSameInstant(java.time.ZoneId.of("Europe/Madrid"));
            } catch (java.time.format.DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Verifies that {@code hash} is exactly the SHA-256 digest of {@code inputString}, i.e. that
     * a previously generated/stored record has not been tampered with.
     * <p>
     * Callers should persist the {@code inputString} returned by {@link #generateInvoiceHash(Map)}
     * / {@link #generateAnnulmentHash(Map)} alongside the {@code hash} itself, precisely to be able
     * to call this method later for an independent re-verification - required both to check the
     * integrity of the chain and, before generating a new record, to verify that the chaining with
     * the previous record is correct (RD 1007/2023, modalidad "NO VERI*FACTU": el sistema debe
     * ofrecer la posibilidad de comprobar las huellas y comprobar obligatoriamente el
     * encadenamiento al crear cada nuevo registro).
     *
     * @param hash        the stored/expected hash (case-insensitive hex SHA-256).
     * @param inputString the exact string the hash was computed over.
     * @return {@code true} if the hash matches; {@code false} if either argument is {@code null}
     * or the hash does not match.
     */
    public static boolean verify(String hash, String inputString) {
        if (hash == null || inputString == null) {
            return false;
        }
        return hash.equalsIgnoreCase(sha256(inputString));
    }

    private static void validateData(Set<String> requiredFields, Set<String> dataTheKeys) {
        Set<String> missing = new HashSet<>(requiredFields);
        missing.removeAll(dataTheKeys);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required fields: " + String.join(", ", missing));
        }

        Set<String> extra = new HashSet<>(dataTheKeys);
        extra.removeAll(requiredFields);
        if (!extra.isEmpty()) {
            throw new IllegalArgumentException("Unexpected fields: " + String.join(", ", extra));
        }
    }

    private static String field(String name, String value, boolean includeSeparator) {
        if (value == null) {
            value = ""; // Should not happen if validated, but safety
        } else {
            value = value.trim();
        }
        return name + "=" + value + (includeSeparator ? "&" : "");
    }

    private static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
