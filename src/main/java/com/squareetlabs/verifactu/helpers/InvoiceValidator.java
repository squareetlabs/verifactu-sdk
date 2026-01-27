package com.squareetlabs.verifactu.helpers;

import com.squareetlabs.verifactu.contracts.VeriFactuBreakdown;
import com.squareetlabs.verifactu.contracts.VeriFactuInvoice;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for invoices according to AEAT VeriFactu specifications.
 * Validates data integrity, required fields, and business rules.
 */
public class InvoiceValidator {

    /**
     * Validates an invoice for AEAT compliance.
     * 
     * @param invoice The invoice to validate
     * @return ValidationResult with validation status and error messages
     */
    public static ValidationResult validate(VeriFactuInvoice invoice) {
        List<String> errors = new ArrayList<>();

        // Validar campos obligatorios
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
            errors.add("Número de factura obligatorio");
        }

        if (invoice.getIssueDate() == null) {
            errors.add("Fecha de emisión obligatoria");
        }

        if (invoice.getInvoiceType() == null || invoice.getInvoiceType().trim().isEmpty()) {
            errors.add("Tipo de factura obligatorio");
        }

        // Validar NIF emisor (si está disponible en la interfaz)
        String issuerTaxId = getIssuerTaxId(invoice);
        if (issuerTaxId != null && !issuerTaxId.isEmpty()) {
            if (!NifValidator.isValid(issuerTaxId)) {
                errors.add("NIF emisor inválido: " + issuerTaxId);
            }
        } else {
            errors.add("NIF emisor obligatorio");
        }

        // Validar NIF cliente (si existe)
        if (invoice.getCustomerTaxId() != null && !invoice.getCustomerTaxId().isEmpty()) {
            if (!NifValidator.isValid(invoice.getCustomerTaxId())) {
                errors.add("NIF cliente inválido: " + invoice.getCustomerTaxId());
            }
        }

        // Validar importes
        if (invoice.getTotalAmount() < 0) {
            errors.add("Importe total no puede ser negativo");
        }

        if (invoice.getTaxAmount() < 0) {
            errors.add("Cuota total no puede ser negativa");
        }

        // Validar coherencia de importes con desgloses
        if (invoice.getBreakdowns() != null && !invoice.getBreakdowns().isEmpty()) {
            double calculatedBase = 0.0;
            double calculatedTax = 0.0;

            for (VeriFactuBreakdown b : invoice.getBreakdowns()) {
                calculatedBase += b.getBaseAmount();
                calculatedTax += b.getTaxAmount();
            }

            double calculatedTotal = calculatedBase + calculatedTax;

            // Permitir pequeña diferencia por redondeo (0.02 euros)
            if (Math.abs(calculatedTotal - invoice.getTotalAmount()) > 0.02) {
                errors.add(String.format(
                        "Total incoherente: calculado=%.2f (base=%.2f + cuota=%.2f), declarado=%.2f",
                        calculatedTotal, calculatedBase, calculatedTax, invoice.getTotalAmount()));
            }

            if (Math.abs(calculatedTax - invoice.getTaxAmount()) > 0.02) {
                errors.add(String.format(
                        "Cuota total incoherente: calculada=%.2f, declarada=%.2f",
                        calculatedTax, invoice.getTaxAmount()));
            }
        }

        // Validar desgloses individuales
        if (invoice.getBreakdowns() != null) {
            for (int i = 0; i < invoice.getBreakdowns().size(); i++) {
                VeriFactuBreakdown b = invoice.getBreakdowns().get(i);

                if (b.getBaseAmount() < 0) {
                    errors.add("Desglose " + (i + 1) + ": Base imponible no puede ser negativa");
                }

                if (b.getTaxAmount() < 0) {
                    errors.add("Desglose " + (i + 1) + ": Cuota repercutida no puede ser negativa");
                }

                if (b.getTaxRate() < 0 || b.getTaxRate() > 100) {
                    errors.add("Desglose " + (i + 1) + ": Tipo impositivo debe estar entre 0 y 100");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Intenta obtener el NIF del emisor de la factura.
     * Como VeriFactuInvoice no tiene este método, intentamos obtenerlo
     * si la implementación es Invoice.
     */
    private static String getIssuerTaxId(VeriFactuInvoice invoice) {
        try {
            // Si es una instancia de Invoice, podemos acceder al issuerTaxId
            if (invoice instanceof com.squareetlabs.verifactu.models.Invoice) {
                return ((com.squareetlabs.verifactu.models.Invoice) invoice).getIssuerTaxId();
            }
        } catch (Exception e) {
            // Ignorar si no se puede obtener
        }
        return null;
    }

    /**
     * Result of invoice validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            if (valid) {
                return "Factura válida";
            }
            return "Errores de validación:\n- " + String.join("\n- ", errors);
        }

        @Override
        public String toString() {
            return getErrorMessage();
        }
    }
}
