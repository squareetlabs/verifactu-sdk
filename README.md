# VeriFactu Java - Sistema de facturación electrónica

Paquete Java 8+ para gestión y registro de facturación electrónica VeriFactu según las especificaciones de la Agencia Tributaria (AEAT).

## Características principales

- **Modelos POJO** para invoices, breakdowns y recipients
- **Enums** para campos fiscales (invoice type, tax type, regime, operation type, etc.)
- **Helpers** para operaciones de hash, XML, validación de NIF/CIF y generación de QR
- **Servicio AEAT client** configurable con Apache CXF
- **Firma digital XAdES** con EU DSS 5.11.x
- **Validación automática** de facturas (NIF/CIF, consistencia de importes, campos obligatorios)
- **Modos duales**: VERIFACTU y NO VERIFACTU (Requerimiento)
- **Generación de QR**: URLs automáticas para códigos QR según modo y entorno
- **Campos avanzados**: Encadenamiento blockchain, facturas rectificativas, subsanación
- **Multi-tenant**: Soporte para múltiples instalaciones bajo el mismo NIF
- **Clientes extranjeros**: Soporte para identificadores internacionales
- **Tests unitarios** para todos los componentes core
- Listo para extensión y uso en producción

## Instalación

### Opción 1: Desde el repositorio local

Clona el repositorio y construye el paquete:

```bash
git clone https://github.com/squareetlabs/verifactu-sdk.git
cd verifactu-sdk
mvn clean install
```

### Opción 2: Desde GitHub Packages (Recomendado)

Añade el repositorio a tu `pom.xml` o `settings.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/squareetlabs/verifactu-sdk</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

Y la dependencia:

```xml
<dependency>
    <groupId>com.squareetlabs</groupId>
    <artifactId>verifactu-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Nota**: Necesitarás configurar tus credenciales de GitHub (token) en tu `settings.xml` para acceder al paquete.

## Configuración

### Variables de configuración

Crea una instancia de `VeriFactuConfig` con los parámetros de tu empresa:

```java
VeriFactuConfig config = new VeriFactuConfig(
    "Mi Empresa S.L.",           // Nombre del emisor
    "B12345678"                  // NIF/CIF del emisor
);

// Configuración adicional opcional
config.setSystemId("01");                           // ID del sistema informático
config.setDefaultCurrency("EUR");                   // Moneda por defecto
config.setVeriFactuMode(true);                      // Modo VERIFACTU (true) o NO VERIFACTU (false)
config.setTipoUsoPosibleSoloVerifactu("N");        // Tipo de uso posible solo VeriFactu
config.setTipoUsoPosibleMultiOt("S");              // Tipo de uso posible multi-OT
config.setIndicadorMultiplesOt("N");               // Indicador múltiples OT
```

### Modos de facturación

- **VERIFACTU mode (true)**: Genera hash de encadenamiento y cumple con todos los requisitos VeriFactu
- **NO VERIFACTU mode (false)**: Modo de requerimiento sin encadenamiento blockchain

### Inicializar el cliente AEAT

```java
AeatClient client = new AeatClient(
    "/path/to/certificate.p12",  // Ruta al certificado digital
    "certificatePassword",        // Contraseña del certificado
    config,                       // Configuración VeriFactu
    false,                        // false = pre-producción, true = producción
    true                          // true = modo VERIFACTU, false = NO VERIFACTU
);
```

## Uso rápido

### Crear una factura (Ejemplo básico)

```java
import com.squareetlabs.verifactu.models.*;
import com.squareetlabs.verifactu.services.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

public class InvoiceExample {
    public static void main(String[] args) {
        // Configurar cliente
        VeriFactuConfig config = new VeriFactuConfig("Mi Empresa S.L.", "B12345678");
        AeatClient client = new AeatClient("/path/to/cert.p12", "password", config, false, true);
        
        // Crear factura
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("F2024-001");
        invoice.setIssueDate(LocalDate.now());
        invoice.setInvoiceType(InvoiceType.STANDARD);
        invoice.setOperationType(OperationType.STANDARD);
        invoice.setTotalAmount(121.00);
        invoice.setTaxAmount(21.00);
        invoice.setIssuerTaxId("B12345678");
        
        // Añadir desglose (breakdown)
        Breakdown breakdown = new Breakdown(
            TaxType.IVA,              // Tipo de impuesto
            RegimeType.GENERAL,       // Régimen fiscal
            21.0,                     // Tipo impositivo
            100.0,                    // Base imponible
            21.0                      // Cuota
        );
        invoice.setBreakdowns(Arrays.asList(breakdown));
        
        // Añadir destinatario (opcional)
        Recipient recipient = new Recipient();
        recipient.setName("Cliente S.L.");
        recipient.setTaxId("B87654321");
        recipient.setCountry("ES");
        invoice.setRecipients(Arrays.asList(recipient));
        
        // Enviar factura a AEAT
        Map<String, Object> response = client.sendInvoice(invoice, null);
        
        System.out.println("Estado: " + response.get("status"));
        System.out.println("Hash generado: " + response.get("hash"));
        System.out.println("QR URL: " + response.get("qrUrl"));
    }
}
```

## Tipos de factura disponibles

El paquete soporta todos los tipos de factura según la normativa AEAT:

- `InvoiceType.STANDARD` - Factura estándar (F1)
- `InvoiceType.SIMPLIFIED` - Factura simplificada (F2)
- `InvoiceType.SUBSTITUTE` - Factura sin identificación del destinatario (F3)
- `InvoiceType.EXPORT` - Asiento resumen de facturas (F4)
- `InvoiceType.RECTIFICATIVE_R1` - Factura rectificativa (Error fundado en derecho)
- `InvoiceType.RECTIFICATIVE_R2` - Factura rectificativa (Art. 80.1, 80.2, 80.6 LIVA)
- `InvoiceType.RECTIFICATIVE_R3` - Factura rectificativa (Art. 80.3, 80.4 LIVA)
- `InvoiceType.RECTIFICATIVE_R4` - Factura rectificativa (Resto)
- `InvoiceType.RECTIFICATIVE_R5` - Factura rectificativa en facturas simplificadas

## Ejemplos de tipos de factura

### Factura estándar

```java
Invoice invoice = new Invoice();
invoice.setInvoiceNumber("F2024-001");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.STANDARD);
invoice.setOperationType(OperationType.STANDARD);
invoice.setTotalAmount(121.00);
invoice.setTaxAmount(21.00);
invoice.setIssuerTaxId("B12345678");

Breakdown breakdown = new Breakdown(TaxType.IVA, RegimeType.GENERAL, 21.0, 100.0, 21.0);
invoice.setBreakdowns(Arrays.asList(breakdown));
```

### Factura simplificada

```java
Invoice invoice = new Invoice();
invoice.setInvoiceNumber("FS2024-001");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.SIMPLIFIED);
invoice.setOperationType(OperationType.STANDARD);
invoice.setTotalAmount(121.00);
invoice.setTaxAmount(21.00);
invoice.setIssuerTaxId("B12345678");
invoice.setSimplified(true);  // Marca como simplificada

Breakdown breakdown = new Breakdown(TaxType.IVA, RegimeType.GENERAL, 21.0, 100.0, 21.0);
invoice.setBreakdowns(Arrays.asList(breakdown));
```

### Factura rectificativa por sustitución con ImporteRectificacion

```java
Invoice invoice = new Invoice();
invoice.setInvoiceNumber("FR2024-001");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.RECTIFICATIVE_R1);
invoice.setOperationType(OperationType.STANDARD);
invoice.setCorrectionType("S");  // Sustitución
invoice.setIssuerTaxId("B12345678");

// Importes NUEVOS de la factura rectificativa
invoice.setTotalAmount(150.00);
invoice.setTaxAmount(31.50);

// Importes ORIGINALES de la factura que se corrige (ImporteRectificacion)
invoice.setCorrectedBaseAmount(100.00);   // Base original
invoice.setCorrectedTaxAmount(21.00);     // IVA original
invoice.setCorrectedSurchargeAmount(5.20); // Recargo original (opcional)

// Referencia a la factura original
invoice.setOriginalInvoiceNumber("F2024-001");
invoice.setOriginalIssueDate(LocalDate.of(2024, 1, 15));

Breakdown breakdown = new Breakdown(TaxType.IVA, RegimeType.GENERAL, 21.0, 150.0, 31.50);
invoice.setBreakdowns(Arrays.asList(breakdown));
```

### Factura rectificativa (R1) - Por diferencia

```java
Invoice invoice = new Invoice();
invoice.setInvoiceNumber("FR2024-001");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.RECTIFICATIVE_R1);
invoice.setOperationType(OperationType.STANDARD);
invoice.setTotalAmount(121.00);
invoice.setTaxAmount(21.00);
invoice.setIssuerTaxId("B12345678");

// Referencia a la factura original
invoice.setOriginalInvoiceNumber("F2024-001");
invoice.setOriginalIssueDate(LocalDate.of(2024, 1, 15));

Breakdown breakdown = new Breakdown(TaxType.IVA, RegimeType.GENERAL, 21.0, 100.0, 21.0);
invoice.setBreakdowns(Arrays.asList(breakdown));
```

### Factura de exportación (F4)

```java
Invoice invoice = new Invoice();
invoice.setInvoiceNumber("FE2024-001");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.EXPORT);
invoice.setOperationType(OperationType.EXPORT);
invoice.setTotalAmount(1000.00);
invoice.setTaxAmount(0.0);
invoice.setIssuerTaxId("B12345678");

Recipient recipient = new Recipient();
recipient.setName("Foreign Client Ltd.");
recipient.setTaxId("FR123456789");
recipient.setCountry("FR");
invoice.setRecipients(Arrays.asList(recipient));

Breakdown breakdown = new Breakdown(TaxType.IVA, RegimeType.EXPORT, 0.0, 1000.0, 0.0);
invoice.setBreakdowns(Arrays.asList(breakdown));
```

### Factura con encadenamiento blockchain

```java
Invoice invoice = new Invoice();
invoice.setInvoiceNumber("F2024-002");
invoice.setIssueDate(LocalDate.now());
invoice.setInvoiceType(InvoiceType.STANDARD);
invoice.setOperationType(OperationType.STANDARD);
invoice.setTotalAmount(242.00);
invoice.setTaxAmount(42.00);
invoice.setIssuerTaxId("B12345678");

// Encadenamiento con factura anterior
invoice.setPreviousInvoiceNumber("F2024-001");
invoice.setPreviousHash("ABC123DEF456...");  // Hash de la factura anterior

Breakdown breakdown = new Breakdown(TaxType.IVA, RegimeType.GENERAL, 21.0, 200.0, 42.0);
invoice.setBreakdowns(Arrays.asList(breakdown));
```

## Campos avanzados del modelo Invoice

### Campos de encadenamiento blockchain

- `previousInvoiceNumber`: Número de la factura anterior en la cadena
- `previousHash`: Hash de la factura anterior
- `previousIssueDate`: Fecha de emisión de la factura anterior

### Campos de facturas rectificativas

- `originalInvoiceNumber`: Número de la factura original a rectificar
- `originalIssueDate`: Fecha de emisión de la factura original
- `rectificationType`: Tipo de rectificación (R1-R5)
- `correctionType`: Tipo de corrección ("S" = Sustitución, "I" = Por diferencia)
- `correctedBaseAmount`: Base imponible original (requerido para tipo "S")
- `correctedTaxAmount`: Cuota de IVA original (requerido para tipo "S")
- `correctedSurchargeAmount`: Cuota de recargo original (opcional)

### Campos de subsanación

- `subsanationInvoiceNumber`: Número de factura de subsanación
- `subsanationDate`: Fecha de subsanación
- `rejectionCode`: Código de rechazo AEAT

### Campos multi-tenant

- `installationId`: ID de la instalación (para múltiples instalaciones)
- `deviceId`: ID del dispositivo emisor

### Campos de estado de respuesta AEAT

- `aeatStatus`: Estado de la respuesta AEAT (ACCEPTED, REJECTED, PENDING)
- `aeatResponseCode`: Código de respuesta AEAT
- `aeatResponseMessage`: Mensaje de respuesta AEAT
- `aeatRegistrationDate`: Fecha de registro en AEAT

### Campos adicionales

- `description`: Descripción de la factura
- `simplified`: Indica si es factura simplificada
- `thirdPartyIssuer`: Indica si la factura es emitida por tercero
- `macroInvoice`: Indica si es una macro-factura

## Envío de factura a AEAT

```java
// Enviar factura
Map<String, Object> response = client.sendInvoice(invoice, null);

// Verificar respuesta
if ("ACCEPTED".equals(response.get("status"))) {
    System.out.println("Factura aceptada");
    System.out.println("Hash: " + response.get("hash"));
    System.out.println("CSV: " + response.get("csv"));
    System.out.println("QR URL: " + response.get("qrUrl"));
} else {
    System.out.println("Factura rechazada: " + response.get("message"));
}
```

### Consultar estado de facturas enviadas

```java
// Implementar consulta de estado según necesidades
// El cliente AEAT proporciona métodos para verificar el estado
```

## Validación automática de facturas

El paquete incluye validación automática mediante `InvoiceValidator`:

```java
import com.squareetlabs.verifactu.helpers.InvoiceValidator;

InvoiceValidator validator = new InvoiceValidator();
List<String> errors = validator.validate(invoice);

if (errors.isEmpty()) {
    System.out.println("Factura válida");
} else {
    System.out.println("Errores de validación:");
    errors.forEach(System.out::println);
}
```

### Validaciones incluidas:

- **NIF/CIF**: Validación de formato y dígito de control
- **Consistencia de importes**: Base imponible + IVA = Total
- **Campos obligatorios**: Número, fecha, tipo, importes
- **Desgloses**: Al menos un breakdown por factura
- **Facturas rectificativas**: Referencia a factura original obligatoria

## Uso de Helpers

### Generación de hash (Huella)

```java
import com.squareetlabs.verifactu.helpers.HashHelper;

String hash = HashHelper.generateHash(
    "B12345678",           // NIF emisor
    "F2024-001",          // Número factura
    "2024-01-27",         // Fecha emisión
    "121.00",             // Importe total
    "ABC123..."           // Hash anterior (opcional)
);
```

### Validación de NIF/CIF

```java
import com.squareetlabs.verifactu.helpers.NifValidator;

boolean isValid = NifValidator.validate("B12345678");
String nifType = NifValidator.getType("B12345678");  // CIF, NIF, NIE, etc.
```

### Generación de URL para códigos QR

```java
import com.squareetlabs.verifactu.helpers.QrHelper;

String qrUrl = QrHelper.generateQrUrl(
    "B12345678",
    "F2024-001",
    "2024-01-27",
    "121.00",
    "ABC123...",
    true,  // Modo VERIFACTU
    false  // Pre-producción
);

// Generar imagen QR
byte[] qrImage = QrHelper.generateQrImage(qrUrl, 300, 300);
```

## Firma digital XAdES

```java
import com.squareetlabs.verifactu.services.SignatureService;

SignatureService signatureService = new SignatureService(
    "/path/to/cert.p12",
    "password"
);

// Firmar XML
byte[] xmlBytes = xmlString.getBytes();
byte[] signedXml = signatureService.signXml(xmlBytes);

// Verificar firma
boolean isValid = signatureService.verifySignature(signedXml);
```

## Testing

Ejecuta todos los tests unitarios:

```bash
mvn test
```

### Tests incluidos:

- `InvoiceValidatorTest`: Validación de facturas
- `NifValidatorTest`: Validación de NIF/CIF
- `HashHelperTest`: Generación de hashes
- `VeriFactuComplianceTest`: Cumplimiento con especificaciones AEAT
- `AeatClientTest`: Funcionalidad del cliente AEAT

## Estructura del proyecto

```
src/
├── main/java/com/squareetlabs/verifactu/
│   ├── contracts/          # Interfaces
│   │   ├── VeriFactuInvoice.java
│   │   ├── VeriFactuBreakdown.java
│   │   └── VeriFactuRecipient.java
│   ├── models/             # Modelos POJO
│   │   ├── Invoice.java
│   │   ├── Breakdown.java
│   │   ├── Recipient.java
│   │   ├── InvoiceType.java
│   │   ├── TaxType.java
│   │   ├── RegimeType.java
│   │   └── OperationType.java
│   ├── services/           # Servicios
│   │   ├── AeatClient.java
│   │   ├── SignatureService.java
│   │   └── VeriFactuConfig.java
│   ├── helpers/            # Utilidades
│   │   ├── HashHelper.java
│   │   ├── XmlHelper.java
│   │   ├── NifValidator.java
│   │   ├── InvoiceValidator.java
│   │   └── QrHelper.java
│   └── exceptions/         # Excepciones personalizadas
└── test/java/              # Tests unitarios
```

## Dependencias

- **Apache CXF 3.5.7**: SOAP/HTTP Secure transport
- **EU DSS 5.11.1**: XAdES (XML Advanced Electronic Signatures)
- **JAXB 2.3.8**: XML binding
- **ZXing 3.5.3**: Generación de códigos QR
- **JUnit 4.13.2**: Testing

## Requisitos

- Java 8 o superior
- Maven 3+
- Certificado digital válido para firma electrónica

## Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -m 'feat: añadir nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## Licencia

Este paquete es open-source bajo la [Licencia MIT](LICENSE).

## Soporte

- **Documentación técnica AEAT**: [https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica.html](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica.html)
- **Issues**: [https://github.com/squareetlabs/verifactu/issues](https://github.com/squareetlabs/verifactu/issues)

## Autores

- **Alberto Rial Barreiro** - [SquareetLabs](https://www.squareet.com)
- **Jacobo Cantorna Cigarrán** - [SquareetLabs](https://www.squareet.com)

---

Si este paquete te ha sido útil, ¡no olvides darle una estrella en GitHub! ⭐
