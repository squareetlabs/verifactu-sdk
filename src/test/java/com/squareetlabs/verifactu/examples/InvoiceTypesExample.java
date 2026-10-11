package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceReference;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.OperationType;
import com.squareetlabs.verifactu.models.RegimeType;
import com.squareetlabs.verifactu.models.Recipient;

import java.time.LocalDate;
import java.util.Arrays;
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
        // Una F1 identifica al destinatario (si no se identifica: ver withoutRecipientIdentification()).
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Ejemplo S.L.", "B12345674")));
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

    /**
     * F3: factura emitida en sustitucion de facturas simplificadas previas. Hay que indicar las
     * facturas simplificadas que sustituye ({@code FacturasSustituidas}); el emisor de cada una es
     * el mismo salvo que se indique otro.
     */
    public static Invoice substituteOfSimplified() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-010");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F3);
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Ejemplo S.L.", "B12345674")));
        invoice.setSubstitutedInvoices(Arrays.asList(
                new InvoiceReference("FS2026-001", LocalDate.now().minusDays(3)),
                new InvoiceReference("FS2026-002", LocalDate.now().minusDays(2))));
        return invoice;
    }

    /**
     * F4 (asiento resumen de facturas) existe en {@link InvoiceType}, pero el esquema de la AEAT que
     * usa este SDK no lo admite ({@code ClaveTipoFacturaType}): {@code InvoiceValidator} lo
     * rechaza y {@code AeatClient} no lo envia. No lo uses.
     */
    public static Invoice summaryEntryNotSupported() {
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
     * Factura con destinatarios extranjeros. Se envian con el bloque {@code IDOtro}: pais ISO
     * 3166-1 alfa-2 y tipo de documento (02 NIF-IVA, 03 pasaporte, 04 documento oficial del pais
     * de residencia, 05 certificado de residencia, 06 otro documento probatorio, 07 no censado).
     * Con NIF-IVA (02) el prefijo del identificador ya indica el pais. Un destinatario con NIF
     * espanol se crea solo con nombre y NIF.
     */
    public static Invoice withForeignRecipient() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-020");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTotalAmount(1000.00);
        invoice.setTaxAmount(0.0);
        // Exportacion: ClaveRegimen 02 solo admite operaciones exentas (E2 = art. 21 LIVA).
        Breakdown export = new Breakdown();
        export.setClaveRegimen(RegimeType.EXPORTACION.getCode());
        export.setOperacionExenta("E2");
        export.setBaseAmount(1000.00);
        invoice.setBreakdowns(Collections.singletonList(export));

        invoice.setRecipients(Arrays.asList(
                new Recipient("Foreign Client SARL", "FR12345678901", "FR", "02"),
                new Recipient("John Smith", "P1234567", "GB", "03")));
        return invoice;
    }

    /**
     * Factura emitida SIN identificar al destinatario (art. 6.1.d RD 1619/2012). Es una factura
     * simplificada (F2) o R5 marcada con {@code FacturaSinIdentifDestinatarioArt61d = S}: la AEAT
     * solo admite la marca en F2 y R5, y esos tipos no llevan destinatarios. Al llevar la marca
     * no se aplica el limite de 3.000 EUR de las simplificadas.
     */
    public static Invoice withoutRecipientIdentification() {
        Invoice invoice = simplified();
        invoice.setInvoiceNumber("FS2026-002");
        invoice.setWithoutRecipientIdentification(true);
        return invoice;
    }

    /**
     * F1 de operacion con inversion del sujeto pasivo (S2): el tipo impositivo y la cuota se envian
     * a 0 (la AEAT los exige y deben valer 0). Solo admite F1, F3 y R1-R4.
     */
    public static Invoice reverseCharge() {
        Invoice invoice = standard();
        invoice.setInvoiceNumber("F2026-022");
        invoice.setTotalAmount(100.00);
        invoice.setTaxAmount(0.0);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S2.getCode(), 0.0, 100.00, 0.0)));
        return invoice;
    }

    /**
     * Reenvio de una factura que la AEAT rechazo: "alta por rechazo" ({@code Subsanacion = S} y
     * {@code RechazoPrevio = X}). La clave de la factura no existe en la AEAT porque el alta
     * anterior fue rechazada. Si la factura ya estaba registrada y solo se corrigen datos, usa
     * solo {@code setSubsanation(true)} ("alta de subsanacion").
     */
    public static Invoice resentAfterRejection() {
        Invoice invoice = standard();
        invoice.setSubsanation(true);
        invoice.setPreviousRejection("X");
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

        // Importes NUEVOS (los de esta factura rectificativa): base 150 + cuota 31,50
        invoice.setTotalAmount(181.50);
        invoice.setTaxAmount(31.50);

        // Importes ORIGINALES de la factura que se corrige -> ImporteRectificacion
        invoice.setCorrectedBaseAmount(100.00);
        invoice.setCorrectedTaxAmount(21.00);
        // invoice.setCorrectedSurchargeAmount(5.20); // Opcional: recargo de equivalencia original

        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 150.00, 31.50)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Ejemplo S.L.", "B12345674")));
        // La factura que se corrige (FacturasRectificadas): su numero y su fecha de expedicion.
        invoice.setRectifiedInvoices(Collections.singletonList(
                new InvoiceReference("F2026-001", LocalDate.now().minusDays(10))));
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
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Ejemplo S.L.", "B12345674")));
        invoice.setRectifiedInvoices(Collections.singletonList(
                new InvoiceReference("F2026-001", LocalDate.now().minusDays(10))));
        return invoice;
    }
}
