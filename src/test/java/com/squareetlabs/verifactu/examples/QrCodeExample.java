package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.Invoice;

/**
 * Generacion de la URL/QR de validacion de la factura segun especificaciones
 * AEAT. NOTA: no existe una clase "QrHelper" independiente; estos metodos
 * viven directamente en {@link Invoice}.
 */
public class QrCodeExample {

    public static void main(String[] args) throws Exception {
        Invoice invoice = InvoiceTypesExample.standard();
        invoice.setIssuerTaxId("B12345678"); // requerido para generar la URL

        // Solo la URL de validacion (para imprimirla como texto o generar tu
        // propio codigo QR con otra libreria).
        String url = invoice.getValidationUrl(false); // false = entorno de pruebas, true = produccion
        System.out.println("URL de validacion: " + url);

        // Sistema que emite facturas NO verificables (modo NO VERI*FACTU): la AEAT usa otro servicio
        // de cotejo (ValidarQRNoVerifactu).
        String urlNoVerifactu = invoice.getValidationUrl(false, false);
        System.out.println("URL (no verificable): " + urlNoVerifactu);

        // Imagen PNG del QR ya generada (usa ZXing internamente con correccion de errores nivel M,
        // como exige la AEAT), tamaño por defecto 200x200.
        byte[] qrPngDefault = invoice.getValidationQR(false);

        // O especificando el tamaño en pixeles:
        byte[] qrPng300 = invoice.getValidationQR(false, 300);
        byte[] qrNoVerifactu = invoice.getValidationQR(false, false, 300);

        // En la factura impresa: tamaño entre 30x30 y 40x40 mm, el texto "QR tributario:" encima y,
        // en sistemas VERI*FACTU, "Factura verificable en la sede electronica de la AEAT" o
        // "VERI*FACTU" debajo.

        System.out.println("QR generado: " + qrPng300.length + " bytes (PNG)");

        // Para guardarlo a disco:
        // java.nio.file.Files.write(java.nio.file.Paths.get("factura-qr.png"), qrPng300);
    }
}
