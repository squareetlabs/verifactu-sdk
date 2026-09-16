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

/**
 * VeriFactu contempla TRES mecanismos de "representacion" distintos,
 * opcionales e independientes entre si. Ninguno se activa a menos que lo
 * configures explicitamente.
 * <p>
 * <b>Aviso legal:</b> rellenar {@code Representante} sin la autorizacion
 * correspondiente (apoderamiento inscrito o Convenio de colaboracion social
 * Tipo 017 con los modelos normalizados firmados segun la Resolucion de la
 * AEAT de 18-dic-2024) es un incumplimiento. Consulta con tu asesoria fiscal
 * antes de activarlo en produccion.
 */
public class ThirdPartyRepresentationExample {

    /**
     * Mecanismo 1: quien REMITE el fichero a la AEAT ({@code
     * Cabecera.Representante}). Tu plataforma envia los registros en nombre
     * de tu cliente, en lugar de que el cliente lo haga con su propio
     * certificado.
     */
    public static AeatClient representativeSubmission() {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678"); // el obligado tributario
        config.setRepresentativeName("Odei Software S.L."); // quien remite el fichero
        config.setRepresentativeVat("B87654321");

        // El certPath/certPassword debe ser el certificado cualificado del
        // REPRESENTANTE (Odei), no el del cliente, porque es quien se
        // autentica frente a la Sede Electronica de la AEAT.
        return new AeatClient("/ruta/certificado-odei.p12", "password", config, false, true);
    }

    /**
     * Mecanismo 2: quien EMITE la factura ({@code Tercero} /
     * {@code EmitidaPorTerceroODestinatario}). La factura la expide
     * materialmente un tercero en nombre del obligado.
     */
    public static Invoice thirdPartyIssuance() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setIssuerTaxId("B12345678"); // el obligado a expedir factura (el cliente)
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));

        invoice.setIssuedByThirdPartyOrRecipient("T"); // "T" = Tercero, "D" = autofactura por el destinatario
        invoice.setThirdPartyName("Odei Software S.L.");
        invoice.setThirdPartyTaxId("B87654321");
        // Deja issuedByThirdPartyOrRecipient a null (valor por defecto) en el
        // caso habitual en que el propio obligado emite su factura.
        return invoice;
    }

    /**
     * Mecanismo 3: sistema multi-cliente / SaaS ({@code
     * IndicadorMultiplesOT}). Este indicador DEBE calcularse
     * automaticamente por cliente en cada envio; nunca debe fijarse a mano.
     */
    public static void multiTenantSaaS() {
        VeriFactuConfig config = new VeriFactuConfig("Odei Software S.L.", "B87654321");

        // Declara la capacidad ESTRUCTURAL del sistema UNA SOLA VEZ: este
        // SIF puede llevar la facturacion de varios obligados tributarios.
        config.setMultiObligatedCapable("S");

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setBreakdowns(Collections.singletonList(
                new Breakdown(RegimeType.GENERAL.getCode(), OperationType.S1.getCode(), 21.0, 100.00, 21.00)));

        // Por CADA factura, informa el indicador dinamico segun ESE cliente
        // concreto (tu logica de negocio decide si este cliente tiene mas de
        // una "facturacion" registrada en el sistema en este momento).
        boolean esteClienteTieneMasDeUnaFacturacionEnElSaaS = true; // <- tu logica real aqui
        invoice.setMultipleObligatedIndicator(esteClienteTieneMasDeUnaFacturacionEnElSaaS);

        // Si no se informa multipleObligatedIndicator en la factura, el SDK
        // usa como respaldo VeriFactuConfig.getHasMultipleObligated() (pensado
        // para integraciones de un solo cliente).
    }
}
