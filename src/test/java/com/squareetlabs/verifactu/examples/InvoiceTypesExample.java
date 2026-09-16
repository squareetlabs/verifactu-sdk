package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.OperationType;
import com.squareetlabs.verifactu.models.RegimeType;
import com.squareetlabs.verifactu.models.Recipient;

import java.time.LocalDate;
import java.util.Collections;

/**
 * Construccion de cada tipo de factura soportado por {@link InvoiceType}.
 * Estos metodos NO envian nada a la AEAT (eso lo hace
 * {@code AeatClient#sendInvoice}); solo muestran como rellenar el
 * {@link Invoice} correctamente en cada caso.
 */
public class InvoiceTypesExample {

    /** F1: factura estandar. */
    public static Invoice standard() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));
        return invoice;
    }

    /** F2: factura simplificada (ticket). No requiere destinatario. */
    public static Invoice simplified() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FS2026-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F2);
        invoice.setTotalAmount(12.10);
        invoice.setTaxAmount(2.10);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 10.00, 2.10)));
        return invoice;
    }

    /** F3: factura emitida en sustitucion de facturas simplificadas previas. */
    public static Invoice substituteOfSimplified() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-010");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F3);
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));
        return invoice;
    }

    /**
     * F4: asiento resumen de facturas (uso tipico: grandes volumenes de
     * facturas simplificadas resumidas en un unico asiento diario/periodico).
     */
    public static Invoice summaryEntry() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("RESUMEN-2026-01");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F4);
        invoice.setTotalAmount(5000.00);
        invoice.setTaxAmount(868.60);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 4131.40, 868.60)));
        return invoice;
    }

    /**
     * Factura con destinatario extranjero. LIMITACION ACTUAL: {@code
     * AeatClient#buildRecipients} solo envia Nombre+NIF de cada destinatario;
     * todavia no soporta el bloque {@code IDOtro}/{@code CodigoPais} para
     * clientes sin NIF espanol. Si tu destinatario no tiene NIF espanol,
     * revisa este punto en la Wiki antes de usarlo en produccion.
     */
    public static Invoice withForeignRecipient() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-020");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTotalAmount(1000.00);
        invoice.setTaxAmount(0.0);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.EXPORTACION.getCode(), OperationType.N1.getCode(), 0.0, 1000.00, 0.0)));

        Recipient recipient = new Recipient("Foreign Client Ltd.", "FR123456789");
        invoice.setRecipients(Collections.singletonList(recipient));
        return invoice;
    }

    /**
     * R1-R5 rectificativa por SUSTITUCION: la nueva factura sustituye por
     * completo a la original y hay que informar los importes ORIGINALES en el
     * bloque ImporteRectificacion (correctedBaseAmount / correctedTaxAmount).
     */
    public static Invoice rectificativeBySubstitution() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FR2026-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("S"); // S = Sustitucion

        // Importes NUEVOS (los de esta factura rectificativa)
        invoice.setTotalAmount(150.00);
        invoice.setTaxAmount(31.50);

        // Importes ORIGINALES de la factura que se corrige -> ImporteRectificacion
        invoice.setCorrectedBaseAmount(100.00);
        invoice.setCorrectedTaxAmount(21.00);
        // invoice.setCorrectedSurchargeAmount(5.20); // Opcional: recargo de equivalencia original

        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 150.00, 31.50)));
        return invoice;
    }

    /**
     * R1-R5 rectificativa POR DIFERENCIA: la factura rectificativa solo
     * declara la diferencia (delta) respecto a la original, no hay que
     * informar ImporteRectificacion.
     */
    public static Invoice rectificativeByDifference() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("FR2026-002");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.R1);
        invoice.setCorrectionType("I"); // I = Por diferencia
        invoice.setTotalAmount(29.00); // Solo la diferencia
        invoice.setTaxAmount(5.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 24.00, 5.00)));
        return invoice;
    }
}
