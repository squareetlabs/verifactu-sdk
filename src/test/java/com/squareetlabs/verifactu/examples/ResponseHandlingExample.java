package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.util.List;
import java.util.Map;

/**
 * Como interpretar el mapa que devuelven {@code sendInvoice} y {@code sendAnnulment}.
 * <p>
 * Hay tres resultados distintos:
 * <ol>
 * <li>{@code status = "error"}: no hay respuesta de la AEAT. Falla la validacion local
 * ({@code validation_errors}), la comunicacion, el certificado o la cabecera. {@code message} dice
 * por que.</li>
 * <li>{@code status = "success"} y {@code aeat_status} {@code Correcto} o
 * {@code ParcialmenteCorrecto}: la AEAT acepto el registro.</li>
 * <li>{@code status = "success"} y {@code aeat_status = "Incorrecto"}: la AEAT lo rechazo. Los
 * campos {@code aeat_error_code}, {@code aeat_error_message} y {@code aeat_record_errors}
 * explican el motivo.</li>
 * </ol>
 * En modo NO VERI*FACTU ({@code submittedToAeat = false}) no hay respuesta de la AEAT: el registro
 * se genera, se encadena y se firma localmente ({@code signedXml}).
 */
public class ResponseHandlingExample {

    /** {@code true} si el registro se puede usar como eslabon anterior de la cadena. */
    public static boolean isChainable(Map<String, Object> response) {
        if (!"success".equals(response.get("status"))) {
            return false;
        }
        if (Boolean.FALSE.equals(response.get("submittedToAeat"))) {
            return true; // NO VERI*FACTU: se confirma localmente
        }
        Object status = response.get("aeat_status");
        return "Correcto".equals(status) || "ParcialmenteCorrecto".equals(status);
    }

    @SuppressWarnings("unchecked")
    public static void describe(Map<String, Object> response) {
        if (!"success".equals(response.get("status"))) {
            System.out.println("Error: " + response.get("message"));
            List<String> validation = (List<String>) response.get("validation_errors");
            if (validation != null) {
                validation.forEach(e -> System.out.println("  - " + e));
            }
            return;
        }

        System.out.println("Estado AEAT: " + response.get("aeat_status"));
        System.out.println("CSV: " + response.get("csv"));
        System.out.println("Huella: " + response.get("hash"));

        // Detalle por registro (siempre presente; vacio si no hay error).
        String errorCode = (String) response.get("aeat_error_code");
        if (errorCode != null) {
            System.out.println("Codigo de error: " + errorCode);
            System.out.println("Motivo: " + response.get("aeat_error_message"));
            System.out.println("Resumen: " + response.get("aeat_message")); // Codigo[1244].<descripcion>
        }
        for (Map<String, Object> line : (List<Map<String, Object>>) response.get("aeat_record_errors")) {
            System.out.println("Registro " + line.get("invoiceId") + ": " + line.get("recordStatus")
                    + " " + line.get("errorCode") + " " + line.get("errorMessage"));
            if (line.get("duplicate") != null) {
                System.out.println("  duplicado de: " + line.get("duplicate"));
            }
        }
        System.out.println("Espera entre envios (s): " + response.get("aeat_submission_wait"));
        System.out.println("NIF del presentador: " + response.get("aeat_presenter_nif"));
    }

    public static void main(String[] args) {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B72877814");
        config.setDeveloperNif("B72877814"); // NIF de tu sistema informatico (obligatorio)
        AeatClient client = new AeatClient("/ruta/al/certificado.p12", "password", config, false, true);

        Invoice first = InvoiceTypesExample.standard();
        Map<String, Object> firstResponse = client.sendInvoice(first, null);
        describe(firstResponse);

        // Encadena SOLO con un registro aceptado: la huella de un registro rechazado no entra en la
        // cadena de la AEAT. Si el anterior fue rechazado, vuelve a pasar el ultimo aceptado.
        Map<String, Object> lastAccepted = isChainable(firstResponse) ? firstResponse : null;

        Invoice second = InvoiceTypesExample.standard();
        second.setInvoiceNumber("F2026-002");
        describe(client.sendInvoice(second, lastAccepted));
    }
}
