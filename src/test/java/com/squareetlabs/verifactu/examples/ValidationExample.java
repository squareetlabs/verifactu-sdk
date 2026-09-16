package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.helpers.NifValidator;
import com.squareetlabs.verifactu.models.Invoice;

/**
 * Validacion local de facturas (sin llamar a la AEAT) e identificadores
 * fiscales (NIF/NIE/CIF).
 */
public class ValidationExample {

    public static void main(String[] args) {
        Invoice invoice = InvoiceTypesExample.standard();
        invoice.setIssuerTaxId("B12345678"); // usado por InvoiceValidator (no por AeatClient, ver BasicInvoiceExample)

        // InvoiceValidator.validate(...) es un metodo ESTATICO: no se
        // instancia la clase. Devuelve un ValidationResult, no una List<String>.
        InvoiceValidator.ValidationResult result = InvoiceValidator.validate(invoice);

        if (result.isValid()) {
            System.out.println("Factura valida");
        } else {
            System.out.println(result.getErrorMessage()); // incluye todos los errores formateados
            // o iterar manualmente:
            result.getErrors().forEach(System.out::println);
        }

        // Validacion de NIF / NIE / CIF (todo en NifValidator, tambien estatico)
        boolean isValid = NifValidator.isValid("B12345678");
        String idType = NifValidator.getIdType("B12345678"); // "NIF", "NIE", "CIF" o "UNKNOWN"
        System.out.println("¿Valido? " + isValid + " | Tipo: " + idType);
    }
}
