package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Reenvio idempotente: si el envio falla por un corte de comunicacion no sabes si la AEAT llego a
 * registrar el alta. Para poder reenviar sin riesgo, fija la fecha de generacion del registro
 * ({@code FechaHoraHusoGenRegistro}) UNA vez, guardala junto con el registro y usa siempre el mismo
 * valor en los reintentos: el SDK reproduce el mismo XML y la misma huella, de modo que la AEAT
 * reconoce el registro como duplicado en lugar de recibir uno distinto.
 */
public class IdempotentResendExample {

    /** Fecha de generacion que la aplicacion guardo la primera vez (aqui, la actual). */
    public static ZonedDateTime generatedOnce() {
        return ZonedDateTime.now(ZoneId.of("Europe/Madrid")).withNano(0);
    }

    /** La misma factura de {@link InvoiceTypesExample#standard()} con la fecha de generacion fijada. */
    public static Invoice standardWithFixedTimestamp(ZonedDateTime generatedAt) {
        Invoice invoice = InvoiceTypesExample.standard();
        invoice.setGenerationTimestamp(generatedAt);
        return invoice;
    }

    /** Lo mismo para una anulacion. */
    public static Annulment annulmentWithFixedTimestamp(ZonedDateTime generatedAt) {
        Annulment annulment = new Annulment("F2026-001", LocalDate.now());
        annulment.setGenerationTimestamp(generatedAt);
        return annulment;
    }

    public static void main(String[] args) {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B12345678");
        AeatClient client = new AeatClient("/ruta/al/certificado.p12", "password", config, false, true);

        // 1. Antes del primer envio, fija y GUARDA la fecha de generacion (y la huella anterior).
        ZonedDateTime generatedAt = generatedOnce();

        // 2. Primer intento. Si falla por comunicacion, no sabes si la AEAT lo registro.
        Map<String, Object> first = client.sendInvoice(standardWithFixedTimestamp(generatedAt), null);

        // 3. Reintento con LOS MISMOS datos y LA MISMA fecha: mismo registro, misma huella. Si la AEAT
        //    ya lo tenia, lo rechaza como duplicado; el detalle viene en aeat_record_errors (ver
        //    ResponseHandlingExample) y el registro original sigue siendo valido.
        if (!ResponseHandlingExample.isChainable(first)) {
            Map<String, Object> retry = client.sendInvoice(standardWithFixedTimestamp(generatedAt), null);
            System.out.println(retry.get("aeat_status"));
        }
    }
}
