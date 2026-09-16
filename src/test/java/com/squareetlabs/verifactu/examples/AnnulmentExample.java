package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.OperationType;
import com.squareetlabs.verifactu.models.RegimeType;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

/**
 * Anulación de un registro de facturación ya remitido a la AEAT.
 * <p>
 * IMPORTANTE: una anulación NO borra ni modifica la factura original - es un
 * registro nuevo, encadenado con el anterior, que le indica a la AEAT que un
 * registro de "alta" previo debe considerarse anulado. Es el mecanismo
 * correcto cuando una factura ya remitida debe quedar sin efecto (p. ej. fue
 * enviada por error, con datos incorrectos, o nunca llegó a registrarse en
 * la AEAT).
 * <p>
 * Si lo que necesitas es corregir el importe/base de una factura ya emitida,
 * NO uses una anulación: emite una factura rectificativa (R1-R5) con
 * {@code InvoiceType.R1}..{@code R5} a través de {@code AeatClient#sendInvoice},
 * ver {@link InvoiceTypesExample}.
 */
public class AnnulmentExample {

    public static void main(String[] args) {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B12345678");
        AeatClient client = new AeatClient("/ruta/al/certificado.p12", "password", config, false, true);

        // --- Factura previa de la cadena ---
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-001");
        invoice.setIssueDate(LocalDate.of(2026, 1, 15));
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));

        Map<String, Object> invoiceResponse = client.sendInvoice(invoice, null);

        // --- Caso habitual: la factura a anular SI esta registrada en la AEAT ---
        // Se encadena igual que una factura de alta: pasa la respuesta del
        // registro inmediatamente anterior de la cadena (sea una factura o
        // una anulación previa), o null si es el primer registro.
        Annulment annulment = new Annulment("F2026-001", LocalDate.of(2026, 1, 15));
        Map<String, Object> response = client.sendAnnulment(annulment, invoiceResponse);

        System.out.println("Estado: " + response.get("status"));
        System.out.println("Hash de la anulacion: " + response.get("hash"));

        // --- Caso: la factura a anular NUNCA llego a registrarse en la AEAT ---
        // (p. ej. fue rechazada por errores no admisibles, o se generó cuando
        // el sistema aun no era capaz de remitir registros verificables).
        Annulment neverRegistered = new Annulment("F2026-002", LocalDate.of(2026, 1, 16));
        neverRegistered.setNotRegisteredAtAeat(true);
        client.sendAnnulment(neverRegistered, response);

        // --- Caso: se corrige una anulacion previa que la AEAT rechazo ---
        Annulment retry = new Annulment("F2026-003", LocalDate.of(2026, 1, 17));
        retry.setRetryAfterRejection(true);
        client.sendAnnulment(retry, response);
    }
}
