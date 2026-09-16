package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.RegimeType;
import com.squareetlabs.verifactu.models.OperationType;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

/**
 * Ejemplo minimo de extremo a extremo: configurar el emisor, construir una
 * factura estandar (F1) con un unico desglose de IVA general y enviarla a la
 * AEAT.
 * <p>
 * Este fichero forma parte de {@code src/test/java} precisamente para que se
 * compile en cada build ({@code mvn test-compile}) y nunca quede
 * desincronizado del API real. Ver la Wiki del proyecto para la explicacion
 * completa de cada campo.
 */
public class BasicInvoiceExample {

    public static void main(String[] args) {
        // 1. Configuracion del emisor (el "obligado tributario").
        // Estos datos identifican a la empresa/autonomo que expide las
        // facturas y se usan para rellenar Cabecera.ObligadoEmision.
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B12345678");
        config.setSystemName("MiSistemaFacturacion");
        config.setSystemId("01");
        config.setSystemVersion("1.0");
        config.setInstallationNumber("001");

        // 2. Cliente AEAT. El certificado debe ser el del EMISOR salvo que se
        // configure representacion (ver la pagina "Representacion de
        // terceros" de la Wiki). production=false -> entorno de pruebas de
        // la AEAT; verifactuMode=true -> modo VERI*FACTU (con encadenamiento).
        AeatClient client = new AeatClient(
                "/ruta/al/certificado.p12",
                "contraseñaDelCertificado",
                config,
                false, // false = preproduccion, true = produccion
                true // true = modo VERI*FACTU, false = modo NO VERI*FACTU (requerimiento)
        );

        // 3. Construir la factura. NOTA: issuerTaxId/issuerName en Invoice
        // solo se usan para validaciones locales (InvoiceValidator); el NIF
        // y nombre que realmente se envian a la AEAT en Cabecera salen de
        // VeriFactuConfig, no de estos campos del Invoice.
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2026-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.F1); // Factura estandar
        invoice.setIssuerTaxId("B12345678");
        invoice.setTotalAmount(121.00); // Base + cuota
        invoice.setTaxAmount(21.00);

        // 4. Desglose de IVA. El constructor recibe los CODIGOS AEAT como
        // String, no enums: claveRegimen ("01" = general, ver RegimeType),
        // calificacionOperacion ("S1" = sujeta y no exenta, ver
        // OperationType), tipoImpositivo, baseImponible, cuotaRepercutida.
        Breakdown breakdown = new Breakdown(
                RegimeType.GENERAL.getCode(), // "01"
                OperationType.S1.getCode(), // "S1"
                21.0, // Tipo impositivo (%)
                100.00, // Base imponible
                21.00 // Cuota repercutida
        );
        invoice.setBreakdowns(Collections.singletonList(breakdown));

        // 5. Enviar. El segundo parametro es el registro anterior de la
        // cadena (null = primer registro / PrimerRegistroCadenaType.S). Ver
        // ChainingExample para el encadenamiento entre varias facturas.
        Map<String, Object> response = client.sendInvoice(invoice, null);

        if ("success".equals(response.get("status"))) {
            System.out.println("Factura registrada. CSV: " + response.get("csv"));
            System.out.println("Huella (hash): " + response.get("hash"));
            System.out.println("Estado AEAT: " + response.get("aeat_status"));
        } else {
            System.out.println("Error al enviar la factura: " + response.get("message"));
        }
    }
}
