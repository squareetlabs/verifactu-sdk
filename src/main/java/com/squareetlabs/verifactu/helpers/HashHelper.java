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
