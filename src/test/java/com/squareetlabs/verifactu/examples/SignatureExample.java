package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.services.SignatureService;

/**
 * Firma digital XAdES de un XML con el certificado del emisor/representante.
 * NOTA: {@code SignatureService} solo expone {@code signXml(byte[])}; no
 * existe un metodo {@code verifySignature(...)} en esta clase.
 */
public class SignatureExample {

    public static void main(String[] args) throws Exception {
        SignatureService signatureService = new SignatureService(
                "/ruta/al/certificado.p12",
                "contraseñaDelCertificado");

        String xml = "<factura>...</factura>";
        byte[] signedXml = signatureService.signXml(xml.getBytes("UTF-8"));

        System.out.println("XML firmado: " + signedXml.length + " bytes");
        // java.nio.file.Files.write(java.nio.file.Paths.get("factura-firmada.xml"), signedXml);
    }
}
