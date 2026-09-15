package com.squareetlabs.verifactu;

import com.squareetlabs.verifactu.aeat.CabeceraType;
import com.squareetlabs.verifactu.aeat.RegistroFacturacionAltaType;
import com.squareetlabs.verifactu.aeat.SistemaInformaticoType;
import com.squareetlabs.verifactu.models.Invoice;
import com.squareetlabs.verifactu.services.AeatClient;
import com.squareetlabs.verifactu.services.VeriFactuConfig;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Tests for "sistemas informaticos de facturacion en representacion de
 * terceros": Cabecera.Representante, RegistroFacturacionAltaType.Tercero /
 * EmitidaPorTerceroODestinatario, and the per-invoice
 * SistemaInformatico.IndicadorMultiplesOT override.
 */
public class ThirdPartyRepresentationTest {

    @Test
    public void testHasRepresentativeIsFalseByDefault() {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        assertFalse(config.hasRepresentative());
    }

    @Test
    public void testHasRepresentativeRequiresBothNameAndVat() {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        config.setRepresentativeName("Odei Software S.L.");
        assertFalse(config.hasRepresentative());

        config.setRepresentativeVat("B87654321");
        assertTrue(config.hasRepresentative());
    }

    @Test
    public void testBuildHeaderOmitsRepresentanteWhenNotConfigured() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        CabeceraType cabecera = invokeBuildHeader(client, "Cliente S.L.", "B12345678");

        assertNotNull(cabecera.getObligadoEmision());
        assertEquals("B12345678", cabecera.getObligadoEmision().getNIF());
        assertNull(cabecera.getRepresentante());
    }

    @Test
    public void testBuildHeaderIncludesRepresentanteWhenConfigured() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        config.setRepresentativeName("Odei Software S.L.");
        config.setRepresentativeVat("B87654321");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        CabeceraType cabecera = invokeBuildHeader(client, "Cliente S.L.", "B12345678");

        assertNotNull(cabecera.getObligadoEmision());
        assertEquals("B12345678", cabecera.getObligadoEmision().getNIF());

        assertNotNull("Representante debe rellenarse cuando el SDK esta configurado como representante",
                cabecera.getRepresentante());
        assertEquals("B87654321", cabecera.getRepresentante().getNIF());
        assertEquals("Odei Software S.L.", cabecera.getRepresentante().getNombreRazon());
    }

    @Test
    public void testApplyThirdPartyIssuanceOmittedByDefault() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678");

        RegistroFacturacionAltaType registroAlta = new RegistroFacturacionAltaType();
        invokeApplyThirdPartyIssuance(client, registroAlta, invoice);

        assertNull(registroAlta.getEmitidaPorTerceroODestinatario());
        assertNull(registroAlta.getTercero());
    }

    @Test
    public void testApplyThirdPartyIssuanceSetsTerceroWhenIssuedByThirdParty() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678");
        invoice.setIssuedByThirdPartyOrRecipient("T");
        invoice.setThirdPartyName("Odei Software S.L.");
        invoice.setThirdPartyTaxId("B87654321");

        RegistroFacturacionAltaType registroAlta = new RegistroFacturacionAltaType();
        invokeApplyThirdPartyIssuance(client, registroAlta, invoice);

        assertNotNull(registroAlta.getEmitidaPorTerceroODestinatario());
        assertEquals("T", registroAlta.getEmitidaPorTerceroODestinatario().value());
        assertNotNull(registroAlta.getTercero());
        assertEquals("B87654321", registroAlta.getTercero().getNIF());
        assertEquals("Odei Software S.L.", registroAlta.getTercero().getNombreRazon());
    }

    @Test
    public void testApplyThirdPartyIssuanceSetsFlagOnlyForSelfBilling() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678");
        invoice.setIssuedByThirdPartyOrRecipient("D");

        RegistroFacturacionAltaType registroAlta = new RegistroFacturacionAltaType();
        invokeApplyThirdPartyIssuance(client, registroAlta, invoice);

        assertEquals("D", registroAlta.getEmitidaPorTerceroODestinatario().value());
        assertNull("No debe rellenarse Tercero en autofacturacion (D)", registroAlta.getTercero());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testApplyThirdPartyIssuanceRequiresThirdPartyData() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678");
        invoice.setIssuedByThirdPartyOrRecipient("T");
        // thirdPartyName / thirdPartyTaxId left unset on purpose

        RegistroFacturacionAltaType registroAlta = new RegistroFacturacionAltaType();
        try {
            invokeApplyThirdPartyIssuance(client, registroAlta, invoice);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void testIndicadorMultiplesOTFallsBackToConfigWhenInvoiceDoesNotOverride() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        config.setHasMultipleObligated("N");
        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678");
        // multipleObligatedIndicator left null on purpose -> use config default

        SistemaInformaticoType si = invokeBuildSystemInfo(client, invoice);

        assertEquals("N", si.getIndicadorMultiplesOT().value());
    }

    @Test
    public void testIndicadorMultiplesOTUsesPerInvoiceOverride() throws Exception {
        VeriFactuConfig config = new VeriFactuConfig("Cliente S.L.", "B12345678");
        config.setHasMultipleObligated("N"); // static default says "N"...

        AeatClient client = new AeatClient("/tmp/test.pem", null, config, false, true);

        Invoice invoice = new Invoice();
        invoice.setIssuerTaxId("B12345678");
        invoice.setMultipleObligatedIndicator(true); // ...but this specific SaaS tenant has >1 facturacion

        SistemaInformaticoType si = invokeBuildSystemInfo(client, invoice);

        assertEquals("S", si.getIndicadorMultiplesOT().value());
    }

    // --- Reflection helpers (AeatClient builders are intentionally private) ---

    private CabeceraType invokeBuildHeader(AeatClient client, String issuerName, String issuerVat) throws Exception {
        Method method = AeatClient.class.getDeclaredMethod("buildHeader", String.class, String.class);
        method.setAccessible(true);
        return (CabeceraType) method.invoke(client, issuerName, issuerVat);
    }

    private void invokeApplyThirdPartyIssuance(AeatClient client, RegistroFacturacionAltaType registroAlta,
            Invoice invoice) throws Exception {
        Method method = AeatClient.class.getDeclaredMethod("applyThirdPartyIssuance",
                RegistroFacturacionAltaType.class, com.squareetlabs.verifactu.contracts.VeriFactuInvoice.class);
        method.setAccessible(true);
        method.invoke(client, registroAlta, invoice);
    }

    private SistemaInformaticoType invokeBuildSystemInfo(AeatClient client, Invoice invoice) throws Exception {
        Method method = AeatClient.class.getDeclaredMethod("buildSystemInfo",
                com.squareetlabs.verifactu.contracts.VeriFactuInvoice.class);
        method.setAccessible(true);
        return (SistemaInformaticoType) method.invoke(client, invoice);
    }
}
