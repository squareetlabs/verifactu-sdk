package com.squareetlabs.verifactu.examples;

import com.squareetlabs.verifactu.models.AnomalyType;
import com.squareetlabs.verifactu.models.Event;
import com.squareetlabs.verifactu.models.EventType;
import com.squareetlabs.verifactu.services.EventRecordService;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Registro de eventos de un sistema en modo NO VERI*FACTU: cada evento se genera, se encadena con el anterior y se
 * firma (XAdES enveloped). Nada de esto se envía a la AEAT.
 */
public class EventRecordExample {

    public static void main(String[] args) {
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B72877814");
        config.setDeveloperNif("B72877814");
        EventRecordService events = new EventRecordService("/ruta/al/certificado.p12", "contraseñaDelCertificado", config);

        Map<String, Object> start = start(events);
        Map<String, Object> anomaly = anomaly(events, start);
        System.out.println("Inicio: " + start.get("hash") + ", anomalía: " + anomaly.get("hash"));
    }

    /** El primer evento de la cadena de un obligado: sin evento anterior ({@code PrimerEvento = S}). */
    public static Map<String, Object> start(EventRecordService events) {
        Event start = new Event(EventType.NO_VERIFACTU_START, "B72877814", "Mi Empresa S.L.");
        return events.generate(start);
    }

    /**
     * Un evento posterior se encadena con el anterior: guarda de cada evento su tipo, su fecha ({@code generated_at}) y
     * su huella ({@code hash}), y pásalos con {@code setPrevious} al generar el siguiente.
     */
    public static Map<String, Object> anomaly(EventRecordService events, Map<String, Object> previous) {
        Event.Reference previousEvent = new Event.Reference(EventType.NO_VERIFACTU_START,
                ZonedDateTime.parse((String) previous.get("generated_at")), (String) previous.get("hash"));

        Event.AnomalyDetection anomaly = new Event.AnomalyDetection(AnomalyType.INTEGRITY_HASH)
                .setOtherData("La huella no se reproduce desde sus datos")
                .setInvoice(new Event.InvoiceReference("B72877814", "FT-2026-0001", LocalDate.of(2026, 10, 12), null));

        Event event = new Event(EventType.BILLING_ANOMALY_DETECTED, "B72877814", "Mi Empresa S.L.")
                .setPrevious(previousEvent)
                .setDetails(anomaly);
        return events.generate(event);
    }
}
