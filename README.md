# VeriFactu Java - Sistema de facturación electrónica

Paquete Java 8+ para gestión y registro de facturación electrónica VeriFactu según las especificaciones de la Agencia Tributaria (AEAT).

> 📖 **Documentación completa**: esta guía cubre lo esencial para empezar. Para el detalle de cada caso de uso (tipos de factura, rectificativas, representación de terceros, multi-tenant, validación, QR, firma digital, solución de problemas...) consulta la **[Wiki del proyecto](https://github.com/squareetlabs/verifactu-sdk/wiki)**.
>
> 💻 Todos los ejemplos de esta guía y de la Wiki tienen su equivalente **compilable y verificado** en [`src/test/java/com/squareetlabs/verifactu/examples/`](src/test/java/com/squareetlabs/verifactu/examples/) — se compilan en cada build (`mvn test-compile`), por lo que nunca quedan desincronizados del código real.

## Características principales

- **Modelos POJO** para facturas, desgloses y destinatarios
- **Enums** para los códigos AEAT (tipo de factura, régimen, calificación de la operación)
- **Cliente AEAT** configurable con Apache CXF (SOAP)
- **Firma digital XAdES** con EU DSS
- **Validación local** de facturas (NIF/CIF, consistencia de importes, campos obligatorios)
- **Modos duales**: VERI\*FACTU (remisión síncrona a la AEAT) y NO VERI\*FACTU (Requerimiento) — en NO VERI\*FACTU el registro se genera, encadena y firma con **XAdES** igualmente en local, pero no se remite en tiempo real
- **Verificación de integridad de la cadena**: `HashHelper.verify(hash, inputString)` permite comprobar de forma independiente que un registro no ha sido alterado
- **Generación de URL/QR** de validación según especificaciones AEAT
- **Encadenamiento** entre registros, facturas rectificativas y asientos resumen
- **Anulación** de registros de facturación ya remitidos (`AeatClient#sendAnnulment`, `RegistroFacturacionAnulacionType`)
- **Representación de terceros**: los tres mecanismos contemplados por VeriFactu (envío por representante, emisión por tercero, sistemas multi-obligado)
- Publicado en **Maven Central**

## Instalación

### Opción 1: Desde Maven Central (recomendado)

```xml
<dependency>
    <groupId>com.squareetlabs</groupId>
    <artifactId>verifactu</artifactId>
    <version>1.4.1</version>
</dependency>
```

Con Gradle:

```groovy
implementation 'com.squareetlabs:verifactu:1.2.0'
```

### Opción 2: Desde el repositorio local

```bash
git clone https://github.com/squareetlabs/verifactu-sdk.git
cd verifactu-sdk
mvn clean install
```

## Configuración

Crea una instancia de `VeriFactuConfig` con los datos del **emisor** (el "obligado tributario"). Estos datos —no los del objeto `Invoice`— son los que se envían a la AEAT en `Cabecera.ObligadoEmision` y en `SistemaInformatico`:

```java
VeriFactuConfig config = new VeriFactuConfig(
    "Mi Empresa S.L.",   // Nombre/razón social del emisor
    "B12345678"          // NIF/CIF del emisor
);

// Datos del sistema informático (SistemaInformatico), opcionales
config.setSystemName("MiSistemaFacturacion");
config.setSystemId("01");
config.setSystemVersion("1.0");
config.setInstallationNumber("001");
```

### Cliente AEAT

```java
AeatClient client = new AeatClient(
    "/ruta/al/certificado.p12",  // Certificado digital del emisor (o del representante, ver la Wiki)
    "contraseñaDelCertificado",
    config,
    false,  // false = preproducción, true = producción
    true    // true = modo VERI*FACTU, false = modo NO VERI*FACTU (requerimiento)
);
```

#### Endpoint según entorno y tipo de certificado

El cuarto parámetro (`production`) y `VeriFactuConfig#setSealCertificate` determinan a qué endpoint SOAP de la AEAT se remite el registro (solo aplica con `verifactuMode = true`):

| `production` | `sealCertificate` | Endpoint |
|---|---|---|
| `false` | `false` (por defecto) | `https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP` |
| `false` | `true` | `https://prewww10.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP` |
| `true` | `false` | `https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP` |
| `true` | `true` | `https://www10.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP` |

Si autenticas con un **certificado de sello** (sello electrónico / sello de entidad, tipos 4 y 8 en @firma) llama a `config.setSealCertificate(true)`: la AEAT sirve esos certificados en un endpoint propio (`www10` / `prewww10`).

> Hasta la versión 1.3.0 el parámetro `production` no seleccionaba el endpoint y todos los envíos reales iban al de producción. Se corrige en 1.3.1.

## Alcance y limitaciones

El SDK cubre la **remisión voluntaria VERI\*FACTU** (altas, anulaciones, subsanaciones y reenvíos tras rechazo) y la generación local del registro firmado en modo **NO VERI\*FACTU**. Todavía no cubre:
- La **remisión bajo requerimiento** de la AEAT (bloque `RemisionRequerimiento` de la cabecera, `RefRequerimiento`, `FinRequerimiento`).
- Los datos opcionales de cabecera `RemisionVoluntaria` (`FechaFinVeriFactu`, `Incidencia`).
- El servicio de **consulta** de registros presentados, los registros de **evento** y los acuerdos de facturación (`NumRegistroAcuerdoFacturacion`, `IdAcuerdoSistemaInformatico`).
- Identificar con `IDOtro` al tercero emisor, al generador de una anulación o al productor del sistema informático (solo se admite NIF).

## Novedades

**1.4.1** — Fecha de generación del registro fijada por quien llama, para reenviar sin riesgo. `Invoice#setGenerationTimestamp` y `Annulment#setGenerationTimestamp` (`ZonedDateTime`, opcional) fijan `FechaHoraHusoGenRegistro`; sin valor, el cliente usa el instante actual como hasta ahora. Un reenvío con el mismo valor reproduce el mismo XML y la misma huella. El validador avisa si la fecha es posterior a la del sistema (la AEAT acepta el registro con errores). Los interfaces `VeriFactuInvoice` y `VeriFactuAnnulment` incorporan `getGenerationTimestamp()` como `default`.

**1.4.0** — El SDK aplica las reglas oficiales de la AEAT ([Validaciones y errores](https://www.agenciatributaria.es/AEAT.desarrolladores/Desarrolladores/_menu_/Documentacion/Sistemas_Informaticos_de_Facturacion_y_Sistemas_VERI_FACTU/Validaciones_y_errores/Validaciones_y_errores.html) v1.2.2, especificación de remisión v1.0.3, huella v0.1.2 y QR v0.5.0).
- `InvoiceValidator` es un validador completo (facturas, anulaciones y configuración, con errores y avisos) y `AeatClient` lo ejecuta antes de enviar. Los importes negativos ya no se rechazan: son legales en las rectificativas por diferencias.
- Destinatarios: obligatorios en F1, F3 y R1-R4, y no admitidos en F2 y R5. Destinatarios extranjeros con `IDOtro` (país y tipo de documento) y validación de la estructura del NIF-IVA.
- Factura sin identificar al destinatario (art. 6.1.d): `setWithoutRecipientIdentification(true)`, **solo en F2 y R5**.
- Rectificativas: `setRectifiedInvoices` (`FacturasRectificadas`) y, para F3, `setSubstitutedInvoices` (`FacturasSustituidas`).
- Reenvío tras un rechazo y subsanaciones: `setSubsanation` y `setPreviousRejection` (`Subsanacion` / `RechazoPrevio`).
- `FacturaSimplificadaArt7273` (`setQualifiedSimplified`), `Cupon` (`setCoupon`, solo R1 y R5), `Macrodato` (automático con importe ≥ 100.000.000 €) e `Impuesto` por línea (`Breakdown#setTax`: IVA, IPSI, IGIC u otros).
- Inversión del sujeto pasivo (S2): se envían `TipoImpositivo = 0` y `CuotaRepercutida = 0`, que la AEAT exige.
- QR: host de producción correcto, servicio `ValidarQRNoVerifactu` para sistemas no verificables y corrección de errores nivel M.
- `Invoice#getOperationDescription` devuelve el valor tal cual (antes devolvía `"Invoice issued"` si no estaba informado).

**1.3.3** — Formatos enviados a la AEAT: `FechaHoraHusoGenRegistro` en ISO 8601 sin fracción de segundo (igual en el XML y en la huella), importes con dos decimales y nunca `-0.00`, operaciones exentas, no sujetas y de inversión del sujeto pasivo solo con la base, descripción de la operación tomada de la factura y URL del QR con los parámetros codificados. Un código de calificación o de exención desconocido falla con un mensaje claro.

**1.3.2** — Detalle de la respuesta de la AEAT (ver más abajo).

## Detalle de la respuesta de la AEAT

Desde la versión 1.3.2, `sendInvoice` y `sendAnnulment` añaden al mapa de respuesta el detalle por registro que devuelve la AEAT (las claves existentes no cambian). Siempre están presentes, con `null`, lista vacía o cadena vacía si no hay dato:

| Clave | Contenido |
|---|---|
| `aeat_error_code` / `aeat_error_message` | Código y descripción de error del primer registro que lo tenga |
| `aeat_record_status` | Estado del primer registro (`Correcto`, `AceptadoConErrores`, `Incorrecto`) |
| `aeat_record_errors` | Lista con el detalle de cada registro (estado, código, descripción, operación, identificador de factura y registro duplicado) |
| `aeat_submission_wait` | Tiempo de espera entre envíos indicado por la AEAT |
| `aeat_presenter_nif` | NIF del presentador |
| `aeat_message` | Resumen en una línea, `Codigo[<código>].<descripción>` |

En modo NO VERI*FACTU las claves existen pero vacías, porque no hay respuesta de la AEAT.

Si el registro no pasa la validación local, la respuesta es `status=error` con `message` y `validation_errors` (lista de errores), y no se firma ni se envía nada. Ejemplo de lectura completa de la respuesta, incluida la regla de **encadenar solo con registros aceptados**: [`ResponseHandlingExample.java`](src/test/java/com/squareetlabs/verifactu/examples/ResponseHandlingExample.java).

## Uso rápido: crear y enviar una factura

```java
import com.squareetlabs.verifactu.models.*;
import com.squareetlabs.verifactu.services.*;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B12345678");
AeatClient client = new AeatClient("/ruta/certificado.p12", "password", config, false, true);

Invoice invoice = new Invoice();
invoice.setInvoiceNumber("F2026-001");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.F1);   // Factura estándar
invoice.setTotalAmount(121.00);           // Base + cuota
invoice.setTaxAmount(21.00);

// El constructor de Breakdown recibe los CÓDIGOS AEAT como String (no enums):
// claveRegimen ("01" = general, ver RegimeType), calificacionOperacion
// ("S1" = sujeta y no exenta, ver OperationType), tipo, base, cuota.
Breakdown breakdown = new Breakdown(
    RegimeType.GENERAL.getCode(),   // "01"
    OperationType.S1.getCode(),     // "S1"
    21.0,                            // Tipo impositivo (%)
    100.00,                          // Base imponible
    21.00                            // Cuota repercutida
);
invoice.setBreakdowns(Collections.singletonList(breakdown));

// Segundo parámetro: registro anterior de la cadena (null = primer registro).
// Ver la Wiki -> "Encadenamiento" para cómo enlazar varias facturas.
Map<String, Object> response = client.sendInvoice(invoice, null);

if ("success".equals(response.get("status"))) {
    System.out.println("Huella (hash): " + response.get("hash"));
    System.out.println("¿Se remitió a la AEAT?: " + response.get("submittedToAeat"));
    System.out.println("Estado AEAT: " + response.get("aeat_status"));
    if (Boolean.TRUE.equals(response.get("submittedToAeat"))) {
        System.out.println("CSV: " + response.get("csv")); // solo presente en modo VERI*FACTU
    }
} else {
    System.out.println("Error: " + response.get("message"));
}
```

> Ejemplo completo y compilable: [`BasicInvoiceExample.java`](src/test/java/com/squareetlabs/verifactu/examples/BasicInvoiceExample.java)

### Respuesta según el modo (VERI\*FACTU vs NO VERI\*FACTU)

El `Map<String, Object>` devuelto por `sendInvoice`/`sendAnnulment` tiene siempre las claves `status`, `hash`, `hashInput`, `number`, `date`, `timestamp` y `submittedToAeat`. El resto depende del quinto parámetro de `AeatClient` (`verifactuMode`):

| Clave | Modo VERI\*FACTU (`verifactuMode=true`) | Modo NO VERI\*FACTU (`verifactuMode=false`) |
|---|---|---|
| `submittedToAeat` | `true` | `false` |
| `aeat_status` | Estado devuelto por la AEAT (`Correcto`, `AceptadoConErrores`, ...) | `"NoRemitido"` (no hay llamada SOAP) |
| `csv` | Presente | Ausente |
| `signedXml` | Ausente | Presente: XML del registro firmado con **XAdES** (Base64), es el documento que debes conservar como registro legal |

En **ambos modos** el hash se calcula y encadena exactamente igual (misma huella, mismo `Encadenamiento`), y `hashInput` contiene la cadena exacta usada para calcular `hash` (útil para verificarlo después con `HashHelper.verify`, ver [Encadenamiento y hash](https://github.com/squareetlabs/verifactu-sdk/wiki/Encadenamiento-y-Hash)). La diferencia real es que en NO VERI\*FACTU (RD 1007/2023) el registro no se remite en tiempo real a la AEAT: el obligado lo conserva íntegro, firmado y accesible para una posible inspección — por eso aquí la firma XAdES es obligatoria, mientras que en VERI\*FACTU es la propia remisión en tiempo real la que aporta esa garantía de integridad.

## Tipos de factura soportados

| Enum | Código AEAT | Descripción |
|---|---|---|
| `InvoiceType.F1` | F1 | Factura estándar |
| `InvoiceType.F2` | F2 | Factura simplificada |
| `InvoiceType.F3` | F3 | Factura emitida en sustitución de facturas simplificadas (se indican cuáles con `setSubstitutedInvoices`, opcional) |
| `InvoiceType.F4` | F4 | Asiento resumen de facturas. **No admitido**: el esquema de la AEAT que usa el SDK no lo incluye; `InvoiceValidator` lo rechaza |
| `InvoiceType.R1` | R1 | Rectificativa (art. 80.1, 80.2, 80.6 LIVA) |
| `InvoiceType.R2` | R2 | Rectificativa (art. 80.3 LIVA) |
| `InvoiceType.R3` | R3 | Rectificativa (art. 80.4 LIVA) |
| `InvoiceType.R4` | R4 | Rectificativa (resto de causas) |
| `InvoiceType.R5` | R5 | Rectificativa de facturas simplificadas |

Ejemplos de construcción de cada tipo (incluyendo rectificativas por sustitución y por diferencia, y facturas con destinatario extranjero): [`InvoiceTypesExample.java`](src/test/java/com/squareetlabs/verifactu/examples/InvoiceTypesExample.java). Detalle completo en la Wiki: **[Tipos de factura](https://github.com/squareetlabs/verifactu-sdk/wiki/Tipos-de-Factura)**.

### Facturas rectificativas (R1-R5)

Una rectificativa necesita el tipo de rectificación (`setCorrectionType("S")` por sustitución, `"I"` por diferencias) e indica qué factura corrige con `setRectifiedInvoices` (`FacturasRectificadas`; es opcional para la AEAT, pero es lo que identifica la factura rectificada salvo en los rappels y casos autorizados):

```java
invoice.setInvoiceType(InvoiceType.R1);
invoice.setCorrectionType("I");
invoice.setRectifiedInvoices(Collections.singletonList(
        new InvoiceReference("F2026-001", LocalDate.of(2026, 1, 15)))); // número y fecha de la original
```

El emisor de la factura rectificada es el mismo de la configuración; usa `new InvoiceReference(nifEmisor, numero, fecha)` si es otro. Por sustitución (`"S"`) hay que informar además los importes originales (`setCorrectedBaseAmount` / `setCorrectedTaxAmount`). Por diferencias los importes de la factura son los de la diferencia y pueden ser negativos.

### Destinatarios

Un destinatario con NIF español se crea con nombre y NIF: `new Recipient("Cliente S.L.", "B12345674")`. Un destinatario **extranjero** se envía con el bloque `IDOtro`: país (ISO 3166-1 alfa-2) y tipo de documento:

```java
new Recipient("Client SARL", "FR12345678901", "FR", "02"); // 02 NIF-IVA
new Recipient("John Smith", "P1234567", "GB", "03");        // 03 pasaporte
```

| Tipo | Documento |
|---|---|
| `02` | NIF-IVA (el prefijo del identificador ya indica el país) |
| `03` | Pasaporte |
| `04` | Documento oficial del país de residencia |
| `05` | Certificado de residencia |
| `06` | Otro documento probatorio |
| `07` | No censado |

La AEAT **rechaza** una F1, F3 o R1-R4 sin destinatarios (hace falta al menos uno) y una F2 o R5 con ellos. Si el destinatario no se puede identificar, emite una factura simplificada (F2); si es una factura del art. 6.1.d RD 1619/2012 (sin identificación del destinatario), márcala con `invoice.setWithoutRecipientIdentification(true)` — la AEAT solo admite esa marca en **F2 y R5**, que no llevan destinatarios — y deja de aplicarse el límite de 3.000 € de las simplificadas. Una factura simplificada cualificada (art. 7.2 y 7.3, `FacturaSimplificadaArt7273`) se marca con `setQualifiedSimplified(true)` y solo vale en F1, F3 y R1-R4.

Reglas de `IDOtro` de la AEAT: con país `ES` solo valen los tipos 03 y 07; el tipo 07 (no censado) exige país `ES` y se acepta con errores; el tipo 02 (NIF-IVA) no necesita país, debe tener la estructura de un Estado miembro (por ejemplo `FR12345678901`) y solo vale en F1, F3 y R1-R4.

### Reenvío idempotente: fijar la fecha de generación

Si un envío falla por comunicación no sabes si la AEAT llegó a registrar el alta. La huella se calcula sobre `FechaHoraHusoGenRegistro`, así que, si el reintento la calcula de nuevo, el registro cambia. Para que sea el mismo:

```java
ZonedDateTime generatedAt = ZonedDateTime.now(ZoneId.of("Europe/Madrid")); // guárdalo antes del primer envío
invoice.setGenerationTimestamp(generatedAt);      // Annulment tiene el mismo método
client.sendInvoice(invoice, previous);            // reintento: mismo valor, misma huella
```

El valor se expresa en Europe/Madrid y se descartan las fracciones de segundo. Ver [`IdempotentResendExample`](src/test/java/com/squareetlabs/verifactu/examples/IdempotentResendExample.java).

### Reenvío tras un rechazo y subsanaciones

Un registro rechazado por la AEAT no existe en su sistema. Si lo corriges y lo vuelves a enviar sin que haga falta una rectificativa, es un **alta por rechazo**: `Subsanacion = S` y `RechazoPrevio = X`.

```java
invoice.setSubsanation(true);
invoice.setPreviousRejection("X");
```

| Operación | `setSubsanation` | `setPreviousRejection` |
|---|---|---|
| Alta normal | no | no (o `"N"`) |
| Alta por rechazo (el alta anterior fue rechazada) | `true` | `"X"` |
| Alta de subsanación (el registro ya existe en la AEAT) | `true` | no (o `"N"`) |
| Alta por rechazo de una subsanación | `true` | `"S"` |
| Subsanación sin registro previo (no se remitió en su día) | `true` | `"X"` |

Para anulaciones: `Annulment#setRetryAfterRejection(true)` (`RechazoPrevio = S`) y `setNotRegisteredAtAeat(true)` (`SinRegistroPrevio = S`).

## Anulación de un registro de facturación

Una **anulación** (`AeatClient.sendAnnulment`) NO borra ni modifica la factura original: es un registro nuevo, encadenado con el anterior, que le indica a la AEAT que un registro de "alta" previo debe considerarse anulado. Úsala cuando una factura ya remitida debe quedar sin efecto (enviada por error, con datos incorrectos, o que nunca llegó a registrarse en la AEAT).

> ⚠️ Si lo que necesitas es **corregir el importe/base** de una factura ya emitida, NO uses una anulación: emite una **factura rectificativa** (`InvoiceType.R1`-`R5`) con `AeatClient.sendInvoice`, ver [Tipos de factura](#tipos-de-factura-soportados).

```java
import com.squareetlabs.verifactu.models.Annulment;
import java.time.LocalDate;

Annulment annulment = new Annulment("F2026-001", LocalDate.of(2026, 1, 15));

// Se encadena igual que una factura de alta: pasa la respuesta del registro
// inmediatamente anterior de la cadena (factura o anulación), o null si es
// el primer registro.
Map<String, Object> response = client.sendAnnulment(annulment, previousResponse);
```

Casos especiales contemplados por la AEAT, disponibles vía setters de `Annulment`:

| Método | Cuándo usarlo |
|---|---|
| `setNotRegisteredAtAeat(true)` | La factura a anular NUNCA llegó a registrarse en la AEAT (p. ej. fue rechazada por errores no admisibles) |
| `setRetryAfterRejection(true)` | Se corrige una anulación previa para la MISMA factura que la AEAT rechazó |
| `setGeneratedBy("T"/"D")` + `setGeneratorName/TaxId` | La anulación la generó un tercero o el propio destinatario, no el obligado |

Una anulación remitida a la AEAT puede ser **rechazada** igual que un alta: `aeat_status` será `Incorrecto` y el motivo vendrá en `aeat_error_code` / `aeat_error_message`. En ese caso el registro de alta sigue vigente en la AEAT; no encadenes sobre la anulación rechazada (ver [`ResponseHandlingExample.isChainable`](src/test/java/com/squareetlabs/verifactu/examples/ResponseHandlingExample.java)).

Ejemplo completo: [`AnnulmentExample.java`](src/test/java/com/squareetlabs/verifactu/examples/AnnulmentExample.java)

## Representación de terceros

VeriFactu contempla **tres mecanismos** de representación, opcionales e independientes entre sí:

1. **Quién remite el fichero a la AEAT** (`Cabecera.Representante`, vía `VeriFactuConfig.setRepresentativeName/Vat`)
2. **Quién emite materialmente la factura** (`Invoice.setIssuedByThirdPartyOrRecipient/setThirdParty*`)
3. **Sistemas multi-cliente / SaaS** (`Invoice.setMultipleObligatedIndicator`, calculado por factura, nunca fijo)

> ⚠️ Rellenar `Representante` sin la autorización legal correspondiente (apoderamiento inscrito o Convenio de colaboración social Tipo 017) es un incumplimiento. Consulta con tu asesoría fiscal antes de activarlo en producción.

Ejemplos: [`ThirdPartyRepresentationExample.java`](src/test/java/com/squareetlabs/verifactu/examples/ThirdPartyRepresentationExample.java). Detalle completo: **[Representación de terceros](https://github.com/squareetlabs/verifactu-sdk/wiki/Representacion-de-Terceros)**.

## Validación local

`InvoiceValidator` valida **antes de enviar** lo que la AEAT rechaza, con mensajes legibles. Todos sus métodos son estáticos y devuelven un `ValidationResult` con dos listas: `errors` (la AEAT lo rechazará o el SDK no puede representarlo: el resultado no es válido) y `warnings` (datos sospechosos que no invalidan).

```java
import com.squareetlabs.verifactu.helpers.InvoiceValidator;
import com.squareetlabs.verifactu.helpers.InvoiceValidator.ValidationResult;

ValidationResult result = InvoiceValidator.validate(invoice, config);      // factura, con el emisor de la configuración
InvoiceValidator.validate(annulment, config);                              // anulación
InvoiceValidator.validate(config);                                         // emisor, representante y sistema informático
InvoiceValidator.validate(invoice);                                        // el emisor se toma de Invoice#getIssuerTaxId

if (!result.isValid()) {
    System.out.println(result.getErrorMessage()); // lista formateada
    result.getErrors();                           // uno a uno
    result.getErrorSummary();                     // todos en una línea
}
result.getWarnings();
```

`AeatClient.sendInvoice` y `sendAnnulment` ejecutan esta validación antes de firmar y enviar: un registro con errores **no sale**, y la respuesta trae `status=error`, un `message` (`Validacion local: ...`) y la lista en `validation_errors`.

Aplica las validaciones de negocio publicadas por la AEAT. Entre otras:
- **Rechazan el registro (error):** campos obligatorios y longitudes del esquema; NIF/CIF/NIE con dígito de control (los NIF especiales K, L y M se aceptan por formato); fecha de expedición no futura y no anterior al 28/10/2024; número de factura con solo caracteres ASCII imprimibles y sin `" ' < > =`; tipo de factura admitido (F4 no); destinatarios según el tipo; desglose de 1 a 12 líneas con calificación u exención, clave de régimen obligatoria y de la lista que corresponde al impuesto (L8A para IVA, L8B más 20 y 21 para IGIC, 01/08/11/18/19/20 para IPSI) y sus reglas (02, 03, 04, 06, 07, 08, 10, 11 y 14); tipos de IVA admitidos (0, 4, 10, 21 y los temporales 5, 2 y 7,5 según la fecha); cuota repercutida = base × tipo con un margen de 10 € y mismo signo; tipos de recargo de equivalencia; inversión del sujeto pasivo (S2) solo con F1, F3 y R1-R4; límite de 3.000 € en F2; rectificativas (tipo de rectificación obligatorio solo en R1-R5, importes corregidos por sustitución); facturas rectificadas solo en R1-R5 y sustituidas solo en F3; reglas de `IDOtro` y NIF-IVA; fechas de operación; tercero y destinatario emisor.
- **La AEAT acepta con errores (aviso):** el importe total y la cuota total no cuadran con el desglose más de 10 € (no se comprueba con las claves de régimen 03, 05, 06, 08 y 09); huella anterior en minúsculas; NIF destinatario informado como no censado (07); rectificativa sin factura rectificada indicada.

Los importes negativos son legales (rectificativas por diferencias): no son un error por sí mismos.

### Validación de NIF/CIF/NIE

```java
import com.squareetlabs.verifactu.helpers.NifValidator;

boolean isValid = NifValidator.isValid("B12345678");
String idType = NifValidator.getIdType("B12345678"); // "NIF", "NIE", "CIF" o "UNKNOWN"
```

Ejemplo completo: [`ValidationExample.java`](src/test/java/com/squareetlabs/verifactu/examples/ValidationExample.java)

## Generación de QR de validación

No existe una clase `QrHelper` independiente: los métodos viven directamente en `Invoice`.

```java
String url = invoice.getValidationUrl(false);          // false = pruebas, true = producción (sistema VERI*FACTU)
String urlNoVf = invoice.getValidationUrl(false, false); // sistema que emite facturas no verificables
byte[] qrPng = invoice.getValidationQR(false, 300);     // PNG de 300x300 px
byte[] qrNoVf = invoice.getValidationQR(false, false, 300);
```

La URL sigue la especificación de la AEAT: servicio `ValidarQR` (VERI\*FACTU) o `ValidarQRNoVerifactu` (no verificable), host `prewww2.aeat.es` en pruebas y `www2.agenciatributaria.gob.es` en producción, y los cuatro parámetros (`nif`, `numserie`, `fecha`, `importe`) codificados en UTF-8. El QR se genera con corrección de errores nivel **M**; la factura debe imprimirlo entre 30x30 y 40x40 mm, con el texto «QR tributario:» encima y, en sistemas VERI\*FACTU, la frase «Factura verificable en la sede electrónica de la AEAT» o «VERI\*FACTU» debajo.

Ejemplo completo: [`QrCodeExample.java`](src/test/java/com/squareetlabs/verifactu/examples/QrCodeExample.java)

## Firma digital XAdES

En **modo NO VERI\*FACTU** (`verifactuMode=false`), `AeatClient` invoca internamente `SignatureService` para firmar el registro antes de devolverlo (ver `response.get("signedXml")`) — no necesitas hacer nada adicional. En modo VERI\*FACTU la integridad la aporta la propia remisión síncrona a la AEAT (TLS mutuo con el certificado), por lo que la firma XAdES explícita es opcional.

Si necesitas firmar XML por tu cuenta (por ejemplo, para archivar copias firmadas fuera del flujo de `sendInvoice`/`sendAnnulment`), puedes usar `SignatureService` directamente:

```java
import com.squareetlabs.verifactu.services.SignatureService;

SignatureService signatureService = new SignatureService("/ruta/certificado.p12", "password");
byte[] signedXml = signatureService.signXml(xmlBytes);
```

Ejemplo completo: [`SignatureExample.java`](src/test/java/com/squareetlabs/verifactu/examples/SignatureExample.java)

## Testing

```bash
mvn test
```

## Estructura del proyecto

```
src/
├── main/java/com/squareetlabs/verifactu/
│   ├── contracts/    # Interfaces (VeriFactuInvoice, VeriFactuBreakdown, VeriFactuRecipient, VeriFactuAnnulment)
│   ├── models/       # Modelos POJO (Invoice, Breakdown, Recipient, Annulment) y enums (InvoiceType, RegimeType, OperationType)
│   ├── services/     # AeatClient, SignatureService, VeriFactuConfig
│   ├── helpers/      # HashHelper, XmlHelper, NifValidator, InvoiceValidator
│   └── aeat/         # Clases JAXB generadas automáticamente desde el WSDL/XSD de la AEAT (no editar a mano)
└── test/java/
    ├── com/squareetlabs/verifactu/examples/  # Ejemplos de uso compilables (ver arriba)
    └── ...                                    # Tests unitarios
```

## Dependencias principales

- **Apache CXF**: transporte SOAP/HTTPS
- **EU DSS**: firma XAdES
- **JAXB**: binding XML generado desde el WSDL/XSD oficial de la AEAT
- **ZXing**: generación de códigos QR
- **JUnit 4**: testing

## Requisitos

- Java 8 o superior
- Maven 3+
- Certificado digital válido para firma electrónica

## Documentación completa

Toda la documentación detallada vive en la **[Wiki del proyecto](https://github.com/squareetlabs/verifactu-sdk/wiki)**:

- [Instalación y configuración](https://github.com/squareetlabs/verifactu-sdk/wiki/Instalacion-y-Configuracion)
- [Tipos de factura](https://github.com/squareetlabs/verifactu-sdk/wiki/Tipos-de-Factura)
- [Facturas rectificativas](https://github.com/squareetlabs/verifactu-sdk/wiki/Facturas-Rectificativas)
- [Representación de terceros](https://github.com/squareetlabs/verifactu-sdk/wiki/Representacion-de-Terceros)
- [Sistemas multi-tenant / SaaS](https://github.com/squareetlabs/verifactu-sdk/wiki/Multi-Tenant-SaaS)
- [Encadenamiento y hash](https://github.com/squareetlabs/verifactu-sdk/wiki/Encadenamiento-y-Hash)
- [Validación de facturas](https://github.com/squareetlabs/verifactu-sdk/wiki/Validacion-de-Facturas)
- [Generación de QR](https://github.com/squareetlabs/verifactu-sdk/wiki/Generacion-de-QR)
- [Firma digital XAdES](https://github.com/squareetlabs/verifactu-sdk/wiki/Firma-Digital-XAdES)
- [Solución de problemas](https://github.com/squareetlabs/verifactu-sdk/wiki/Solucion-de-Problemas)

También se genera Javadoc completo, publicado automáticamente junto al paquete: [javadoc.io/doc/com.squareetlabs/verifactu](https://javadoc.io/doc/com.squareetlabs/verifactu).

## Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Haz un fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Haz commit de tus cambios (`git commit -m 'feat: añadir nueva funcionalidad'`)
4. Haz push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## Licencia

Este paquete es open-source bajo la [Licencia MIT](LICENSE).

## Soporte

- **Documentación técnica AEAT**: [sede.agenciatributaria.gob.es - VeriFactu](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica.html)
- **Issues**: [github.com/squareetlabs/verifactu-sdk/issues](https://github.com/squareetlabs/verifactu-sdk/issues)

## Autores

- **Alberto Rial Barreiro** - [SquareetLabs](https://www.squareet.com)
- **Jacobo Cantorna Cigarrán** - [SquareetLabs](https://www.squareet.com)

---

Si este paquete te ha sido útil, ¡no olvides darle una estrella en GitHub! ⭐
