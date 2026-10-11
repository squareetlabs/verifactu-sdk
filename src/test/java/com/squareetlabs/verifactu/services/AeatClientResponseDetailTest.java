package com.squareetlabs.verifactu.services;

import com.squareetlabs.verifactu.aeat.DatosPresentacionType;
import com.squareetlabs.verifactu.aeat.EstadoEnvioType;
import com.squareetlabs.verifactu.aeat.EstadoRegistroType2;
import com.squareetlabs.verifactu.aeat.IDFacturaExpedidaType;
import com.squareetlabs.verifactu.aeat.RespuestaExpedidaType;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * La respuesta de la AEAT debe conservar el detalle por registro (codigo y descripcion de error),
 * no solo el estado global del envio.
 */
public class AeatClientResponseDetailTest {

    private static RespuestaExpedidaType line(EstadoRegistroType2 status, Integer code, String description) {
        RespuestaExpedidaType line = new RespuestaExpedidaType();
        line.setEstadoRegistro(status);
        if (code != null) {
            line.setCodigoErrorRegistro(BigInteger.valueOf(code));
        }
        line.setDescripcionErrorRegistro(description);
        IDFacturaExpedidaType id = new IDFacturaExpedidaType();
        id.setIDEmisorFactura("A28015865");
        id.setNumSerieFactura("FCT2600001");
        id.setFechaExpedicionFactura("11-10-2026");
        line.setIDFactura(id);
        return line;
    }

    private static Map<String, Object> build(EstadoEnvioType estado, List<RespuestaExpedidaType> lines) {
        DatosPresentacionType datos = new DatosPresentacionType();
        datos.setNIFPresentador("B00000000");
        return AeatClient.buildSoapResponse(estado, "CSV123", datos, "60", lines,
                "HASH", "INPUT", "FCT2600001", "11-10-2026", "ts");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errors(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("aeat_record_errors");
    }

    @Test
    public void rejected_exposesCodeAndDescriptionOfTheRecord() {
        Map<String, Object> r = build(EstadoEnvioType.INCORRECTO,
                Collections.singletonList(line(EstadoRegistroType2.INCORRECTO, 1100, "NIF no censado")));

        assertEquals("Incorrecto", r.get("aeat_status"));
        assertEquals("Incorrecto", r.get("aeat_record_status"));
        assertEquals("1100", r.get("aeat_error_code"));
        assertEquals("NIF no censado", r.get("aeat_error_message"));
        assertEquals("Codigo[1100].NIF no censado", r.get("aeat_message"));
        assertEquals("60", r.get("aeat_submission_wait"));
        assertEquals("B00000000", r.get("aeat_presenter_nif"));
        assertEquals(1, errors(r).size());
        assertEquals("1100", errors(r).get(0).get("errorCode"));
        assertEquals("FCT2600001", ((Map<?, ?>) errors(r).get(0).get("invoiceId")).get("number"));
    }

    @Test
    public void accepted_hasNoErrorAndKeepsExistingKeys() {
        Map<String, Object> r = build(EstadoEnvioType.CORRECTO,
                Collections.singletonList(line(EstadoRegistroType2.CORRECTO, null, null)));

        assertEquals("success", r.get("status"));
        assertEquals("Correcto", r.get("aeat_status"));
        assertEquals("CSV123", r.get("csv"));
        assertEquals("HASH", r.get("hash"));
        assertEquals("INPUT", r.get("hashInput"));
        assertEquals("FCT2600001", r.get("number"));
        assertEquals("11-10-2026", r.get("date"));
        assertEquals("ts", r.get("timestamp"));
        assertEquals(Boolean.TRUE, r.get("submittedToAeat"));
        assertNull(r.get("aeat_error_code"));
        assertNull(r.get("aeat_error_message"));
        assertEquals("", r.get("aeat_message"));
        assertEquals("Correcto", r.get("aeat_record_status"));
    }

    @Test
    public void acceptedWithErrors_reportsTheRecordErrorButKeepsTheStatus() {
        Map<String, Object> r = build(EstadoEnvioType.PARCIALMENTE_CORRECTO,
                Collections.singletonList(line(EstadoRegistroType2.ACEPTADO_CON_ERRORES, 2000, "Aviso")));

        assertEquals("ParcialmenteCorrecto", r.get("aeat_status"));
        assertEquals("AceptadoConErrores", r.get("aeat_record_status"));
        assertEquals("2000", r.get("aeat_error_code"));
    }

    @Test
    public void severalLines_firstWithErrorGoesToFlatKeysAndAllToTheList() {
        List<RespuestaExpedidaType> lines = Arrays.asList(
                line(EstadoRegistroType2.CORRECTO, null, null),
                line(EstadoRegistroType2.INCORRECTO, 4116, "Cabecera"),
                line(EstadoRegistroType2.INCORRECTO, 3000, "Duplicado"));
        Map<String, Object> r = build(EstadoEnvioType.INCORRECTO, lines);

        assertEquals("4116", r.get("aeat_error_code"));
        assertEquals(3, errors(r).size());
    }

    @Test
    public void duplicate_isReportedInsideTheLine() {
        RespuestaExpedidaType line = line(EstadoRegistroType2.INCORRECTO, 3000, "Registro duplicado");
        com.squareetlabs.verifactu.aeat.RegistroDuplicadoType dup = new com.squareetlabs.verifactu.aeat.RegistroDuplicadoType();
        dup.setIdPeticionRegistroDuplicado("REQ-1");
        dup.setCodigoErrorRegistro(BigInteger.valueOf(0));
        dup.setDescripcionErrorRegistro("duplicado de REQ-1");
        line.setRegistroDuplicado(dup);

        Map<String, Object> r = build(EstadoEnvioType.INCORRECTO, Collections.singletonList(line));

        Map<?, ?> duplicate = (Map<?, ?>) errors(r).get(0).get("duplicate");
        assertEquals("REQ-1", duplicate.get("requestId"));
        assertEquals("duplicado de REQ-1", duplicate.get("errorMessage"));
    }

    @Test
    public void noLines_keysArePresentWithEmptyValues() {
        Map<String, Object> r = build(EstadoEnvioType.INCORRECTO, null);

        assertTrue(r.containsKey("aeat_error_code"));
        assertNull(r.get("aeat_error_code"));
        assertTrue(errors(r).isEmpty());
        assertEquals("", r.get("aeat_message"));
        assertNull(r.get("aeat_record_status"));
    }

    @Test
    public void nullStatus_isUnknown() {
        assertEquals("UNKNOWN", build(null, new ArrayList<>()).get("aeat_status"));
    }
}
