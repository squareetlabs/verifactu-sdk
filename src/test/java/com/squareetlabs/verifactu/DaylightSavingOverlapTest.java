package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.helpers.HashHelper;
import com.squareetlabs.verifactu.models.Annulment;
import com.squareetlabs.verifactu.models.Breakdown;
import com.squareetlabs.verifactu.models.Event;
import com.squareetlabs.verifactu.models.EventType;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.models.InvoiceType;
import com.squareetlabs.verifactu.models.Recipient;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.EventRecordService;
import org.junit.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * La hora repetida del cambio de hora: un domingo de octubre a las 03:00 los relojes de Madrid vuelven a las 02:00, y de
 * 02:00 a 02:59 cada hora local ocurre dos veces (+02:00 y +01:00). La fecha de generación de un registro o de un evento
 * debe conservar su huso: un reenvío con la fecha de la primera pasada firma la misma huella, y no la de una hora después.
 * Se usa la del 2025-10-26 porque el validador rechaza una fecha de generación futura.
 */
public class DaylightSavingOverlapTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime REPEATED = LocalDateTime.of(2025, 10, 26, 2, 30, 10);
    /** 02:30:10+02:00, primera pasada (horario de verano). */
    private static final ZonedDateTime FIRST_PASS = ZonedDateTime.ofLocal(REPEATED, MADRID, ZoneOffset.ofHours(2));
    /** 02:30:10+01:00, segunda pasada (horario de invierno), una hora después. */
    private static final ZonedDateTime SECOND_PASS = FIRST_PASS.withLaterOffsetAtOverlap();

    private String certPath() {
        URL resource = getClass().getClassLoader().getResource("test-cert.p12");
        assertNotNull(resource);
        return resource.getPath();
    }

    private AeatClient client() {
        return new AeatClient(certPath(), "testpass123", TestConfig.of("Empresa Test SL", "B72877814"), false, false);
    }

    private EventRecordService events() {
        return new EventRecordService(certPath(), "testpass123", TestConfig.of("Empresa Test SL", "B72877814"));
    }

    private Invoice invoice(ZonedDateTime generated) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("DST-001");
        invoice.setIssueDate(LocalDate.of(2025, 10, 26));
        invoice.setInvoiceType(InvoiceType.F1);
        invoice.setTaxAmount(21.0);
        invoice.setTotalAmount(121.0);
        invoice.setBreakdowns(Collections.singletonList(new Breakdown("01", "S1", 21.0, 100.0, 21.0)));
        invoice.setRecipients(Collections.singletonList(new Recipient("Cliente Uno SL", "B12345674")));
        invoice.setGenerationTimestamp(generated);
        return invoice;
    }

    private Event event(ZonedDateTime generated) {
        return new Event(EventType.values()[0], "B72877814", "Empresa Test SL").setGeneratedAt(generated);
    }

    private static String xml(Map<String, Object> response) {
        assertEquals(String.valueOf(response), "success", response.get("status"));
        return new String(Base64.getDecoder().decode((String) response.get("signedXml")), StandardCharsets.UTF_8);
    }

    @Test
    public void invoice_firstPassKeepsItsOffset() {
        Map<String, Object> response = client().sendInvoice(invoice(FIRST_PASS), null);

        assertTrue((String) response.get("hashInput"), ((String) response.get("hashInput"))
                .endsWith("FechaHoraHusoGenRegistro=2025-10-26T02:30:10+02:00"));
        assertTrue(xml(response).contains("FechaHoraHusoGenRegistro>2025-10-26T02:30:10+02:00<"));
    }

    @Test
    public void invoice_bothPassesGiveDifferentHashes() {
        Map<String, Object> first = client().sendInvoice(invoice(FIRST_PASS), null);
        Map<String, Object> second = client().sendInvoice(invoice(SECOND_PASS), null);

        assertTrue(((String) second.get("hashInput")).endsWith("FechaHoraHusoGenRegistro=2025-10-26T02:30:10+01:00"));
        assertNotEquals(first.get("hash"), second.get("hash"));
    }

    @Test
    public void invoice_sameInstantInAnotherZoneIsWrittenInMadrid() {
        ZonedDateTime utc = FIRST_PASS.withZoneSameInstant(ZoneOffset.UTC);

        Map<String, Object> fromUtc = client().sendInvoice(invoice(utc), null);
        Map<String, Object> fromMadrid = client().sendInvoice(invoice(FIRST_PASS), null);

        assertEquals(fromMadrid.get("hashInput"), fromUtc.get("hashInput"));
        assertEquals(fromMadrid.get("hash"), fromUtc.get("hash"));
    }

    @Test
    public void annulment_firstPassKeepsItsOffset() {
        Annulment annulment = new Annulment("DST-001", LocalDate.of(2025, 10, 26));
        annulment.setGenerationTimestamp(FIRST_PASS);

        Map<String, Object> response = client().sendAnnulment(annulment, null);

        assertTrue(((String) response.get("hashInput")).endsWith("FechaHoraHusoGenRegistro=2025-10-26T02:30:10+02:00"));
        assertTrue(xml(response).contains("FechaHoraHusoGenRegistro>2025-10-26T02:30:10+02:00<"));
    }

    @Test
    public void event_firstPassKeepsItsOffset() {
        Map<String, Object> response = events().generate(event(FIRST_PASS));

        assertEquals("2025-10-26T02:30:10+02:00", response.get("generated_at"));
        assertTrue(((String) response.get("hashInput")).endsWith("FechaHoraHusoGenEvento=2025-10-26T02:30:10+02:00"));
        assertTrue(xml(response).contains("FechaHoraHusoGenEvento>2025-10-26T02:30:10+02:00<"));
    }

    @Test
    public void event_suppliedTimestampKeepsItsOwnOffset() {
        // Como en la 1.5.0: una fecha dada se firma con su huso, para que un evento regenerado desde su huella
        // (HashHelper.generatedAtOf) dé el mismo texto y la misma huella. Solo la fecha por defecto pasa a Madrid.
        Map<String, Object> fromUtc = events().generate(event(FIRST_PASS.withZoneSameInstant(ZoneOffset.UTC)));

        assertEquals("2025-10-26T00:30:10Z", fromUtc.get("generated_at"));
    }

    @Test
    public void event_defaultTimestampIsInMadridWhateverTheJvmZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals(MADRID, new Event(EventType.values()[0], "B72877814", "Empresa Test SL").getGeneratedAt().getZone());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void generatedAtOf_recoversTheExactInstantOfARecord() {
        Map<String, Object> response = client().sendInvoice(invoice(FIRST_PASS), null);

        ZonedDateTime recovered = HashHelper.generatedAtOf((String) response.get("hashInput"));

        assertEquals(FIRST_PASS.toInstant(), recovered.toInstant());
        assertEquals(MADRID, recovered.getZone());
        assertEquals(ZoneOffset.ofHours(2), recovered.getOffset());
        // Un reenvío con la fecha recuperada reproduce la misma huella.
        assertEquals(response.get("hash"), client().sendInvoice(invoice(recovered), null).get("hash"));
    }

    @Test
    public void generatedAtOf_recoversTheExactInstantOfAnEvent() {
        Map<String, Object> response = events().generate(event(FIRST_PASS));

        ZonedDateTime recovered = HashHelper.generatedAtOf((String) response.get("hashInput"));

        assertEquals(FIRST_PASS.toInstant(), recovered.toInstant());
        assertEquals(ZoneOffset.ofHours(2), recovered.getOffset());
        assertEquals(response.get("hash"), events().generate(event(recovered)).get("hash"));
    }

    @Test
    public void generatedAtOf_recoversTheSecondPassOfAnAnnulment() {
        Annulment annulment = new Annulment("DST-001", LocalDate.of(2025, 10, 26));
        annulment.setGenerationTimestamp(SECOND_PASS);
        Map<String, Object> response = client().sendAnnulment(annulment, null);

        ZonedDateTime recovered = HashHelper.generatedAtOf((String) response.get("hashInput"));

        assertEquals(SECOND_PASS.toInstant(), recovered.toInstant());
        assertEquals(ZoneOffset.ofHours(1), recovered.getOffset());
        Annulment resend = new Annulment("DST-001", LocalDate.of(2025, 10, 26));
        resend.setGenerationTimestamp(recovered);
        assertEquals(response.get("hash"), client().sendAnnulment(resend, null).get("hash"));
    }

    @Test
    public void generatedAtOf_regeneratesAnEventGeneratedInUtcWithTheSameHash() {
        // Un evento de la 1.5.0 generado en una JVM en UTC lleva "Z" en su huella: se regenera igual.
        Map<String, Object> response = events().generate(event(FIRST_PASS.withZoneSameInstant(ZoneOffset.UTC)));
        assertTrue(((String) response.get("hashInput")).endsWith("FechaHoraHusoGenEvento=2025-10-26T00:30:10Z"));

        ZonedDateTime recovered = HashHelper.generatedAtOf((String) response.get("hashInput"));

        assertEquals(ZoneOffset.UTC, recovered.getOffset());
        assertEquals(response.get("hash"), events().generate(event(recovered)).get("hash"));
    }

    @Test
    public void generatedAtOf_isNullWithoutAReadableTimestamp() {
        assertNull(HashHelper.generatedAtOf(null));
        assertNull(HashHelper.generatedAtOf("IDEmisorFactura=B72877814&Huella="));
        assertNull(HashHelper.generatedAtOf("FechaHoraHusoGenRegistro=no-es-una-fecha"));
    }
}
