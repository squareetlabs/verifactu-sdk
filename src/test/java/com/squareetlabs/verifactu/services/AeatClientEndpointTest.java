package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.aeat.SfPortTypeVerifactu;
import com.squareetlabs.verifactu.aeat.SfVerifactu;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.ws.BindingProvider;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * El endpoint SOAP de la AEAT depende del entorno ({@code production}) y del tipo de certificado
 * ({@code sealCertificate}). Un cliente de pruebas nunca debe poder alcanzar produccion.
 */
public class AeatClientEndpointTest {

    private static SfVerifactu service;

    @BeforeClass
    public static void loadService() {
        URL wsdl = AeatClientEndpointTest.class.getClassLoader().getResource("SistemaFacturacion.wsdl");
        assertNotNull("Falta SistemaFacturacion.wsdl en el classpath", wsdl);
        service = new SfVerifactu(wsdl);
    }

    private String endpoint(boolean production, boolean seal) {
        SfPortTypeVerifactu port = AeatClient.selectPort(service, production, seal);
        return (String) ((BindingProvider) port).getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);
    }

    @Test
    public void production_personalCertificate_usesWww1() {
        assertEquals("https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                endpoint(true, false));
    }

    @Test
    public void production_sealCertificate_usesWww10() {
        assertEquals("https://www10.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                endpoint(true, true));
    }

    @Test
    public void test_personalCertificate_usesPrewww1() {
        assertEquals("https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                endpoint(false, false));
    }

    @Test
    public void test_sealCertificate_usesPrewww10() {
        assertEquals("https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP",
                endpoint(false, true));
    }

    @Test
    public void testEnvironment_neverReachesProductionHosts() {
        assertTrue(endpoint(false, false).contains("prewww"));
        assertTrue(endpoint(false, true).contains("prewww"));
    }

    @Test
    public void sealCertificate_defaultsToFalse() {
        assertEquals(false, new VeriFactuConfig("X", "B12345678").isSealCertificate());
    }
}
