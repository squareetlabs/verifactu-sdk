package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.helpers.InvoiceValidator.ValidationResult;
import com.squareetlabs.verifactu.helpers.NifValidator;
import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.time.LocalDate;

/**
 * Validacion local (sin llamar a la AEAT) de facturas, anulaciones y configuracion, e
 * identificadores fiscales (NIF/NIE/CIF).
 * <p>
 * {@link InvoiceValidator} tiene metodos ESTATICOS y devuelve un {@link ValidationResult} con dos
 * listas: {@code errors} (la AEAT lo rechazara, o el SDK no puede representarlo: el resultado no
 * es valido) y {@code warnings} (datos sospechosos que la AEAT podria rechazar segun el caso: no
 * invalidan). {@code AeatClient} ejecuta esta misma validacion antes de firmar y enviar: un
 * registro con errores no sale y la respuesta trae {@code status=error}, un {@code message}
 * legible y la lista en {@code validation_errors}.
 */
public class ValidationExample {

    public static void main(String[] args) {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B72877814");
        config.setDeveloperNif("B72877814");

        // 1. Factura tal y como la enviara AeatClient: el emisor es el de la configuracion.
        Invoice invoice = InvoiceTypesExample.standard();
        ValidationResult result = InvoiceValidator.validate(invoice, config);
        System.out.println("Valida: " + result.isValid());
        result.getWarnings().forEach(w -> System.out.println("Aviso: " + w));

        // 2. Con datos incorrectos: todos los errores a la vez.
        invoice.setIssueDate(LocalDate.now().plusDays(1)); // fecha futura
        invoice.setTotalAmount(999.99); // no cuadra con el desglose
        ValidationResult bad = InvoiceValidator.validate(invoice, config);
        if (!bad.isValid()) {
            System.out.println(bad.getErrorMessage()); // lista formateada
            bad.getErrors().forEach(System.out::println); // o uno a uno
            System.out.println(bad.getErrorSummary()); // todos en una linea (logs, respuestas)
        }

        // 3. Anulaciones.
        ValidationResult annulment = InvoiceValidator.validate(new Annulment("F2026-001", LocalDate.now()), config);
        System.out.println("Anulacion valida: " + annulment.isValid());

        // 4. Configuracion del emisor, representante y sistema informatico.
        ValidationResult configuration = InvoiceValidator.validate(config);
        System.out.println("Configuracion valida: " + configuration.isValid());

        // 5. Compatibilidad: validate(invoice) toma el emisor de Invoice#getIssuerTaxId.
        invoice.setIssuerTaxId("B72877814");
        System.out.println(InvoiceValidator.validate(invoice).isValid());

        // 6. NIF / NIE / CIF (NifValidator, tambien estatico).
        boolean isValid = NifValidator.isValid("B72877814");
        String idType = NifValidator.getIdType("B72877814"); // "NIF", "NIE", "CIF" o "UNKNOWN"
        System.out.println("¿Valido? " + isValid + " | Tipo: " + idType);
    }
}
