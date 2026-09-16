package com.squareetlabs.verifactu.examples;

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
 * Encadenamiento entre facturas: cada registro VERI*FACTU debe referenciar el
 * hash del registro anterior de la cadena
 * ({@code RegistroAnterior.Huella}). El SDK gestiona esto automaticamente si
 * le pasas la respuesta de la factura anterior como segundo parametro de
 * {@code sendInvoice}.
 */
public class ChainingExample {

    public static void main(String[] args) {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B12345678");
        AeatClient client = new AeatClient("/ruta/al/certificado.p12", "password", config, false, true);

        // --- Primera factura de la cadena ---
        Invoice first = new Invoice();
        first.setInvoiceNumber("F2026-001");
        first.setIssueDate(LocalDate.now());
        first.setInvoiceType(InvoiceType.F1);
        first.setTotalAmount(121.00);
        first.setTaxAmount(21.00);
        first.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));

        // previous = null -> este es el PRIMER registro de la cadena
        // (PrimerRegistroCadenaType.S). El SDK genera el hash y lo devuelve
        // en la respuesta bajo la clave "hash".
        Map<String, Object> firstResponse = client.sendInvoice(first, null);

        // --- Segunda factura: se encadena con la primera ---
        Invoice second = new Invoice();
        second.setInvoiceNumber("F2026-002");
        second.setIssueDate(LocalDate.now());
        second.setInvoiceType(InvoiceType.F1);
        second.setTotalAmount(242.00);
        second.setTaxAmount(42.00);
        second.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 200.00, 42.00)));

        // Pasando la respuesta completa de la factura anterior, el SDK rellena
        // Encadenamiento.RegistroAnterior con su numero/fecha/huella.
        Map<String, Object> secondResponse = client.sendInvoice(second, firstResponse);

        System.out.println("Hash 1: " + firstResponse.get("hash"));
        System.out.println("Hash 2: " + secondResponse.get("hash"));

        // Alternativa: si guardas tu mismo el hash anterior en tu base de
        // datos (recomendado para procesos asincronos/reinicios), puedes
        // saltarte la respuesta anterior y fijarlo directamente en el
        // Invoice con setPreviousHash(...); el SDK lo usa como fallback
        // cuando el mapa "previous" es null o no contiene "hash":
        // second.setPreviousHash(firstResponse.get("hash").toString());
        // client.sendInvoice(second, null);
    }
}
