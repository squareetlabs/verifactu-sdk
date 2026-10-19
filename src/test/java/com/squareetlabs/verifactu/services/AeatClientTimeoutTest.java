package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.TestConfig;
import com.squareetlabs.verifactu.aeat.CabeceraType;
import com.squareetlabs.verifactu.aeat.DatosPresentacionType;
import com.squareetlabs.verifactu.aeat.EstadoEnvioType;
import com.squareetlabs.verifactu.aeat.RespuestaExpedidaType;
import com.squareetlabs.verifactu.aeat.SfPortTypeVerifactu;
import com.squareetlabs.verifactu.aeat.SfVerifactu;
import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.junit.BeforeClass;
import org.junit.Test;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Holder;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** La llamada SOAP a la AEAT tiene timeouts de conexion y de lectura: una AEAT que no responde no bloquea al llamante. */
public class AeatClientTimeoutTest {

    private static SfVerifactu service;

    @BeforeClass
    public static void loadService() {
        URL wsdl = AeatClientTimeoutTest.class.getClassLoader().getResource("SistemaFacturacion.wsdl");
        assertNotNull(wsdl);
        service = new SfVerifactu(wsdl);
    }

    private static VeriFactuConfig config(int connect, int read) {
        VeriFactuConfig config = TestConfig.of("Empresa Test SL", "B72877814");
        config.setConnectTimeoutMillis(connect);
        config.setReadTimeoutMillis(read);
        return config;
    }

    @Test
    public void defaultsAreTenSecondsToConnectAndThirtyToRead() {
        VeriFactuConfig config = new VeriFactuConfig("Empresa Test SL", "B72877814");

        assertEquals(10_000, config.getConnectTimeoutMillis());
        assertEquals(30_000, config.getReadTimeoutMillis());
    }

    @Test
    public void timeoutsAreAppliedToTheHttpConduit() {
        SfPortTypeVerifactu port = AeatClient.selectPort(service, false, false);
        HTTPConduit http = (HTTPConduit) ClientProxy.getClient(port).getConduit();

        AeatClient.applyTimeouts(http, config(1_500, 2_500));

        assertEquals(1_500, http.getClient().getConnectionTimeout());
        assertEquals(2_500, http.getClient().getReceiveTimeout());
    }

    @Test
    public void aServerThatNeverAnswersEndsInAReadTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread silent = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    Thread.sleep(10_000); // acepta y no responde
                } catch (Exception ignored) {
                    // el test cierra el socket al terminar
                }
            });
            silent.setDaemon(true);
            silent.start();

            SfPortTypeVerifactu port = AeatClient.selectPort(service, false, false);
            ((BindingProvider) port).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                    "http://127.0.0.1:" + server.getLocalPort() + "/aeat");
            AeatClient.applyTimeouts((HTTPConduit) ClientProxy.getClient(port).getConduit(), config(2_000, 700));

            long started = System.currentTimeMillis();
            try {
                port.regFactuSistemaFacturacion(new CabeceraType(), new ArrayList<>(), new Holder<String>(),
                        new Holder<DatosPresentacionType>(), new Holder<CabeceraType>(), new Holder<String>(),
                        new Holder<EstadoEnvioType>(), new Holder<List<RespuestaExpedidaType>>());
                fail("Debia agotar el tiempo de espera");
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - started;
                assertTrue("Tardo " + elapsed + " ms", elapsed < 8_000);
                assertTrue(e.toString(), AeatClient.isTimeout(e));
            }
        }
    }

    @Test
    public void timeoutClassification() {
        assertTrue(AeatClient.isTimeout(new RuntimeException("x", new SocketTimeoutException("Read timed out"))));
        assertTrue(AeatClient.isTimeout(new java.net.ConnectException("Connection timed out")));
        assertFalse(AeatClient.isTimeout(new java.net.ConnectException("Connection refused")));
        assertFalse(AeatClient.isTimeout(new IllegalStateException("otro error")));
    }

    @Test
    public void nonPositiveTimeoutsAreAConfigurationError() {
        assertTrue(InvoiceValidator.validate(config(1_000, 1_000)).isValid());
        assertFalse(InvoiceValidator.validate(config(0, 1_000)).isValid());
        assertFalse(InvoiceValidator.validate(config(1_000, -1)).isValid());
    }
}
