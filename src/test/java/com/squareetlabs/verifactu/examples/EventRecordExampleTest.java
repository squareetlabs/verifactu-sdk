package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.services.EventRecordService;
import com.squareetlabs.verifactu.services.VeriFactuConfig;
import org.junit.Test;

import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** El ejemplo del registro de eventos se genera con el mismo servicio que usa el SDK: si deja de ser correcto, falla. */
public class EventRecordExampleTest {

    @Test
    public void theExampleChainsAnAnomalyToTheStartEvent() {
        URL cert = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(cert);
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B72877814");
        config.setDeveloperNif("B72877814");
        EventRecordService events = new EventRecordService(cert.getPath(), "testpass123", config);

        Map<String, Object> start = EventRecordExample.start(events);
        Map<String, Object> anomaly = EventRecordExample.anomaly(events, start);

        assertEquals(String.valueOf(start), "success", start.get("status"));
        assertEquals(String.valueOf(anomaly), "success", anomaly.get("status"));
        assertNotEquals(start.get("hash"), anomaly.get("hash"));
        assertTrue(((String) anomaly.get("hashInput")).contains("&HuellaEvento=" + start.get("hash") + "&"));
        assertTrue(HashHelper.verify((String) anomaly.get("hash"), (String) anomaly.get("hashInput")));
    }
}
