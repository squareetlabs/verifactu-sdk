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

        // Imagen PNG del QR ya generada (usa ZXing internamente), tamaño por defecto 200x200.
        byte[] qrPngDefault = invoice.getValidationQR(false);

        // O especificando el tamaño en pixeles:
        byte[] qrPng300 = invoice.getValidationQR(false, 300);

        System.out.println("QR generado: " + qrPng300.length + " bytes (PNG)");

        // Para guardarlo a disco:
        // java.nio.file.Files.write(java.nio.file.Paths.get("factura-qr.png"), qrPng300);
    }
}
