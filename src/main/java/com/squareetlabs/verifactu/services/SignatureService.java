package com.squareetlabs.verifactu.services;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;

import java.io.InputStream;
import java.security.KeyStore;

public class SignatureService {
    private String certPath;
    private String certPassword;

    public SignatureService(String certPath, String certPassword) {
        this.certPath = certPath;
        this.certPassword = certPassword;
    }

    public byte[] signXml(byte[] xmlContent) throws Exception {
        // Load the token (PKCS12)
        Pkcs12SignatureToken token = new Pkcs12SignatureToken(certPath,
                new KeyStore.PasswordProtection(certPassword.toCharArray()));

        // Prepare parameters
        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B); // Basic XAdES, adjust if needed (e.g. EPES)
        parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED); // Signature inside the XML
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);

        // Get the key (private key entry)
        eu.europa.esig.dss.token.DSSPrivateKeyEntry signingKey = token.getKeys().get(0);
        parameters.setSigningCertificate(signingKey.getCertificate());
        parameters.setCertificateChain(signingKey.getCertificateChain());

        // Create the service
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        XAdESService service = new XAdESService(verifier);

        // Create document to be signed
        DSSDocument toSignDocument = new InMemoryDocument(xmlContent);

        // 1. Get data to sign
        ToBeSigned dataToSign = service.getDataToSign(toSignDocument, parameters);

        // 2. Sign
        SignatureValue signatureValue = token.sign(dataToSign, parameters.getDigestAlgorithm(), signingKey);

        // 3. Create the final signed document
        DSSDocument signedDocument = service.signDocument(toSignDocument, parameters, signatureValue);

        // Read and return bytes
        try (InputStream is = signedDocument.openStream()) {
            byte[] targetArray = new byte[is.available()];
            is.read(targetArray);
            return targetArray;
        }
    }
}
