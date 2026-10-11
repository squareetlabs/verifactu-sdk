package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.services.VeriFactuConfig;

/** Configuracion de pruebas con un NIF de desarrollador valido (el cliente valida la configuracion antes de enviar). */
public final class TestConfig {

    private TestConfig() {
    }

    public static VeriFactuConfig of(String issuerName, String issuerVat) {
        VeriFactuConfig config = new VeriFactuConfig(issuerName, issuerVat);
        config.setDeveloperNif("B72877814");
        return config;
    }
}
