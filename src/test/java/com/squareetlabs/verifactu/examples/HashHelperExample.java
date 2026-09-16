package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.helpers.HashHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Generacion manual de la "huella" (hash SHA-256) de un registro de
 * facturacion, sin pasar por {@code AeatClient} (util para pruebas,
 * depuracion o si necesitas calcular el hash de forma independiente).
 * {@code HashHelper} expone dos variantes equivalentes del mismo algoritmo.
 */
public class HashHelperExample {

    public static void main(String[] args) {
        // Variante 1: Map con claves snake_case (valida que no falten ni
        // sobren campos, lanza IllegalArgumentException en caso contrario).
        Map<String, String> data = new HashMap<>();
        data.put("issuer_tax_id", "B12345678");
        data.put("invoice_number", "F2026-001");
        data.put("issue_date", "16-09-2026");
        data.put("invoice_type", "F1");
        data.put("total_tax", "21.00");
        data.put("total_amount", "121.00");
        data.put("previous_hash", ""); // "" para el primer registro de la cadena
        data.put("generated_at", "16-09-2026T12:00:00+02:00");

        Map<String, String> result = HashHelper.generateInvoiceHash(data);
        System.out.println("Hash: " + result.get("hash"));
        System.out.println("Cadena de entrada: " + result.get("inputString"));

        // Variante 2: parametros posicionales directos (la que usa
        // internamente AeatClient#sendInvoice).
        String hash = HashHelper.generateInvoiceHash(
                "B12345678", // NIF emisor
                "F2026-001", // Numero de factura
                "16-09-2026", // Fecha de expedicion (dd-MM-yyyy)
                "F1", // Tipo de factura
                "21.00", // Cuota total
                "121.00", // Importe total
                "", // Huella del registro anterior ("" si es el primero)
                "16-09-2026T12:00:00+02:00" // Fecha/hora de generacion del registro
        );
        System.out.println("Hash (variante posicional): " + hash);
    }
}
