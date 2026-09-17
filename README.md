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
    <version>1.2.0</version>
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
| `InvoiceType.F3` | F3 | Factura emitida en sustitución de facturas simplificadas |
| `InvoiceType.F4` | F4 | Asiento resumen de facturas |
| `InvoiceType.R1` | R1 | Rectificativa (art. 80.1, 80.2, 80.6 LIVA) |
| `InvoiceType.R2` | R2 | Rectificativa (art. 80.3 LIVA) |
| `InvoiceType.R3` | R3 | Rectificativa (art. 80.4 LIVA) |
| `InvoiceType.R4` | R4 | Rectificativa (resto de causas) |
| `InvoiceType.R5` | R5 | Rectificativa de facturas simplificadas |

Ejemplos de construcción de cada tipo (incluyendo rectificativas por sustitución y por diferencia, y facturas con destinatario extranjero): [`InvoiceTypesExample.java`](src/test/java/com/squareetlabs/verifactu/examples/InvoiceTypesExample.java). Detalle completo en la Wiki: **[Tipos de factura](https://github.com/squareetlabs/verifactu-sdk/wiki/Tipos-de-Factura)**.

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

Ejemplo completo: [`AnnulmentExample.java`](src/test/java/com/squareetlabs/verifactu/examples/AnnulmentExample.java)

## Representación de terceros

VeriFactu contempla **tres mecanismos** de representación, opcionales e independientes entre sí:

1. **Quién remite el fichero a la AEAT** (`Cabecera.Representante`, vía `VeriFactuConfig.setRepresentativeName/Vat`)
2. **Quién emite materialmente la factura** (`Invoice.setIssuedByThirdPartyOrRecipient/setThirdParty*`)
3. **Sistemas multi-cliente / SaaS** (`Invoice.setMultipleObligatedIndicator`, calculado por factura, nunca fijo)

> ⚠️ Rellenar `Representante` sin la autorización legal correspondiente (apoderamiento inscrito o Convenio de colaboración social Tipo 017) es un incumplimiento. Consulta con tu asesoría fiscal antes de activarlo en producción.

Ejemplos: [`ThirdPartyRepresentationExample.java`](src/test/java/com/squareetlabs/verifactu/examples/ThirdPartyRepresentationExample.java). Detalle completo: **[Representación de terceros](https://github.com/squareetlabs/verifactu-sdk/wiki/Representacion-de-Terceros)**.

## Validación local de facturas

```java
import com.squareetlabs.verifactu.helpers.InvoiceValidator;

// Metodo ESTATICO: no se instancia InvoiceValidator.
InvoiceValidator.ValidationResult result = InvoiceValidator.validate(invoice);

if (result.isValid()) {
    System.out.println("Factura válida");
} else {
    System.out.println(result.getErrorMessage());
}
```

Valida: NIF/CIF (formato y dígito de control), coherencia base+cuota=total, campos obligatorios, y que las facturas rectificativas referencien correctamente la original.

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
String url = invoice.getValidationUrl(false);       // false = pruebas, true = producción
byte[] qrPng = invoice.getValidationQR(false, 300);  // PNG de 300x300 px
```

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
