# JADES Backend

API REST del sistema **JADES** (control de cuentas corrientes y ventas al crédito para pequeños comercios).
Curso Finanzas e Ingeniería Económica, UPC (Sección 7666).

---

## Tecnologías

| Capa | Tecnología |
| :--- | :--- |
| Lenguaje | Java 21 |
| Framework | Spring Boot 4 (Web MVC, Data JPA, Security) |
| Base de datos | PostgreSQL |
| Autenticación | JWT (`jjwt 0.13`) |
| Documentación API | Swagger / OpenAPI (`springdoc`) |
| Utilidades | Lombok, DevTools |
| Build | Maven Wrapper (`mvnw` / `mvnw.cmd`) |

---

## Estructura del proyecto

```text
src/main/java/com/upc/jadesbackend
├── config/        Configuración (OpenAPI)
├── controllers/   Endpoints REST
├── dtos/          Objetos de entrada/salida
├── entities/      Entidades JPA (Tienda, Cliente, Venta, Credito, Cuota, Pago, ...)
├── exceptions/    Manejo centralizado de errores
├── repositories/  Repositorios Spring Data
├── security/      JWT, filtros, login
├── services/      Lógica de negocio
└── util/          Motor financiero (CalculoFinancieroUtil)
```

Documentación adicional:
- [Motor financiero (HU-12 / HU-14)](docs/MOTOR_FINANCIERO.md)

---

## Convenciones de datos

Las tasas se reciben como **fracciones decimales**: 18 % = `0.18`, 125 % = `1.25`. Las tasas pactadas deben ser mayores a cero y no tienen un máximo de negocio; se conserva la precisión técnica de la base de datos. El frontend debe dividir entre 100 si muestra porcentajes. Los valores históricos no se reinterpretan automáticamente.

La moneda `PEN` o `USD` se configura una sola vez para cada cliente. En la edición de condiciones debe mostrarse como solo lectura; el backend rechaza el cambio.

## Cómo correrlo en local

### 1. Requisitos
- **JDK 21** (no basta con un JRE). Verifica con `javac -version`.
- **PostgreSQL** con una base de datos llamada `bd_JADES`.

Si no tienes PostgreSQL, puedes levantarlo con Docker:
```bash
docker run --name jades-postgres -e POSTGRES_DB=bd_JADES -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
```

La configuración de desarrollo utiliza `spring.jpa.hibernate.ddl-auto=update`. Hibernate actualiza el esquema a partir de las entidades JPA al iniciar; las columnas nuevas quedan nulas para operaciones antiguas cuya información original se desconoce. Para las pruebas finales se recomienda una base limpia, sin borrar la existente. La carga de `src/main/resources/insert.sql` es manual y opcional.

### 2. Variables de entorno
Configura `DB_PASSWORD` y `JWT_SECRET` mediante variables de entorno también en desarrollo. El secreto JWT debe tener al menos 32 bytes y ser exclusivo de cada entorno. Las demás variables tienen los siguientes valores predeterminados:

| Variable | Valor por defecto |
| :--- | :--- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bd_JADES` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | obligatorio |
| `JWT_SECRET` | obligatorio |
| `JWT_EXPIRATION_MS` | `1200000` (20 min) |

Ejemplo en PowerShell si tu PostgreSQL usa otro puerto o contraseña:
```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/bd_JADES"
$env:DB_PASSWORD = "mi_clave"
$env:JWT_SECRET = "una-clave-local-unica-de-al-menos-32-bytes"
```

### 3. Arrancar
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw spring-boot:run
```

Cuando veas `Started JadesBackendApplication`, la API está lista en el puerto **8080**:
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### 4. Pagos y liquidación

- Cada compra de pago único o cuota mensual se cancela íntegramente; compras distintas pueden pagarse por separado.
- El pago único anticipado recalcula el interés compensatorio hasta la fecha efectiva con la tasa histórica. Desde el vencimiento se conserva el importe pactado y se añade mora si corresponde.
- En créditos mensuales se paga la cuota pendiente más antigua. Se permite adelantarla dentro de su mes de vencimiento, por su importe programado.
- Desde el mes de la primera cuota, se puede liquidar todo el crédito: las cuotas vencidas y las del mes incluyen interés pactado y mora cuando corresponda; las cuotas de meses futuros se cancelan por su amortización de capital.
- No se permiten meses adicionales de gracia para ventas nuevas o simulaciones. La gracia total obligatoria y los cronogramas históricos se conservan.
- Las ventas nuevas guardan el nombre y la unidad de medida del producto en `detalle_venta`. El historial no consulta esos datos en el catálogo vigente.
- Las cabeceras de cortes se construyen con las fechas históricas del ciclo, no con las condiciones vigentes del cliente. `calendariosMultiples` indica fechas diferentes y `calendarioIncompleto` señala información no disponible; cada obligación conserva sus fechas individuales.
- El historial de cortes distingue obligaciones pendientes de pagos registrados; incluye los pagos del ciclo pactado o del mes del pago efectivo. Sus totales de pagos no son un reporte de ingresos acumulable entre cortes.

| Método | Endpoint | Uso |
|---|---|---|
| GET | `/api/v1/pagos/cuotas/{idCuota}/importe?fechaPagoEfectivo=AAAA-MM-DD` | Consultar importe exacto de una obligación |
| POST | `/api/v1/pagos/cuotas/{idCuota}` | Registrar el pago completo de esa obligación |
| GET | `/api/v1/pagos/creditos/{idCredito}/liquidacion?fechaPagoEfectivo=AAAA-MM-DD` | Cotizar la liquidación de todas las cuotas pendientes |
| POST | `/api/v1/pagos/creditos/{idCredito}/liquidacion` | Registrar la liquidación total en una transacción |

Los POST reciben `monto`, `fechaPagoEfectivo` (AAAA-MM-DD), `horaPagoEfectivo` (HH:mm:ss) y `medioPago`. Los GET aceptan los parámetros opcionales `fechaPagoEfectivo` y `horaPagoEfectivo`. Si la fecha es hoy y se omite la hora, se utiliza la hora actual del servidor en Lima; para fechas anteriores se debe indicar la hora efectiva. No se aceptan momentos futuros ni anteriores a la compra. Si el corte y el vencimiento histórico coinciden por ajuste al último día del mes, el pago de esa obligación se permite estrictamente después de la hora de corte. Los demás pagos anticipados mantienen sus reglas. El administrador registra pagos y el cliente solo consulta sus propios datos. El cronograma original permanece intacto; los importes efectivamente cobrados se consultan en los pagos.

### Administración y configuración de tiendas

| Método | Endpoint | Rol |
|---|---|---|
| GET | `/api/v1/tiendas` | ADMIN_SISTEMA |
| GET | `/api/v1/tiendas/{id}` | ADMIN_SISTEMA |
| PUT | `/api/v1/tiendas/{id}` | ADMIN_SISTEMA |
| GET | `/api/v1/tiendas/{id}/configuracion` | ADMIN_NEGOCIO vinculado |
| PUT | `/api/v1/tiendas/{id}/configuracion` | ADMIN_NEGOCIO vinculado |

La lista y el detalle del administrador del sistema incluyen los datos generales y los administradores vinculados, sin contraseñas ni hashes. Las respuestas conservan los campos de resumen existentes. El PUT administrativo recibe `nombreTienda`, `razonSocial`, `documentoTienda`, `giroDeNegocio`, `direccion`, `telefonoTienda` y `correoTienda`. El documento debe tener 11 dígitos y no estar registrado en otra tienda. La entidad declara una restricción UNIQUE para proteger también las altas y ediciones concurrentes; antes del arranque con ddl-auto=update, comprueba que no existan documentos duplicados en una base antigua. El PUT de configuración solo admite actualizar `nombreTienda`, `giroDeNegocio`, `telefonoTienda` y `correoTienda`; no modifica los datos legales, los usuarios, sus credenciales ni el estado de la tienda. Teléfono y correo son opcionales (null para eliminarlos). Las consultas están habilitadas para tiendas inactivas, pero ambos PUT requieren una tienda activa. La reactivación y desactivación siguen usando sus PATCH existentes. Las modificaciones se registran en la auditoría con los datos anteriores y posteriores.

### Imágenes de productos y servicios

Las imágenes PNG/JPEG se validan por contenido real (máximo 5 MB y 20 megapíxeles) y se guardan como PNG (máximo 20 MB procesados). PostgreSQL almacena solo su URL.

| Método | Endpoint | Cuerpo |
|---|---|---|
| POST | `/api/v1/tiendas/{tienda}/productos` | multipart: `producto` (JSON) y `archivo` (imagen obligatoria) |
| PUT | `/api/v1/tiendas/{tienda}/productos/{id}` | JSON para editar datos sin cambiar imagen; multipart con `producto` y `archivo` opcional para reemplazarla |
| POST | `/api/v1/tiendas/{tienda}/productos/{id}/imagen` | multipart: `archivo` para reemplazar solo la imagen |
| GET | `/api/v1/tiendas/{tienda}/productos/{id}/imagenes/{nombre}` | Devuelve el PNG con autenticación JWT |

En multipart, la parte `producto` debe tener Content-Type `application/json` y `archivo` contiene el fichero. No se debe enviar una URL nueva en `imagenProducto`; el backend la genera. Para mostrar la imagen, el frontend debe solicitar su URL con `Authorization: Bearer <token>` y usar el Blob recibido; al retirar la imagen de la vista debe revocar su object URL. Las tiendas inactivas pueden consultar imágenes, pero no subirlas ni modificarlas.

En Docker sobre AWS, configura `PRODUCTOS_IMAGENES_DIR` con una ruta dentro del contenedor respaldada por un volumen persistente, y `PRODUCTOS_IMAGENES_BASE_URL` con el origen HTTPS público estable del backend (no localhost). Por ejemplo, añade estas opciones al comando de ejecución del contenedor, usando el dominio real:

```bash
-v jades-productos:/app/uploads/productos \
-e PRODUCTOS_IMAGENES_DIR=/app/uploads/productos \
-e PRODUCTOS_IMAGENES_BASE_URL=https://api.example.com
```

El proceso del contenedor necesita permisos de escritura sobre el volumen. Conserva y respalda tanto el volumen de imágenes como PostgreSQL; no elimines el volumen al recrear el contenedor. Un volumen local sobre la VM no sustituye las copias de seguridad ni comparte archivos entre varias instancias. Las imágenes antiguas de prueba sin archivo deben cargarse nuevamente. Si cambia el dominio público, las URL absolutas ya guardadas deben actualizarse o mantenerse accesibles mediante el dominio anterior.

### 5. Ejecutar pruebas
Las pruebas funcionales y financieras se realizarán manualmente mediante Swagger o Postman al finalizar las correcciones.

---

## Problemas frecuentes

| Error | Causa | Solución |
| :--- | :--- | :--- |
| `No compiler is provided in this environment` | Maven usa un JRE o Java antiguo | Instala JDK 21 y apunta `JAVA_HOME` a él; reabre la terminal |
| `password authentication failed for user "postgres"` | La contraseña no coincide, o hay otro PostgreSQL en el puerto 5432 | Ajusta `DB_PASSWORD` o usa otro puerto con `DB_URL` |
| `Unable to determine Dialect without JDBC metadata` | Consecuencia del error de conexión anterior | Corrige la conexión a la BD |

---

## Seguridad
- Endpoint público: `POST /api/v1/autenticacion/iniciar-sesion` (devuelve el token JWT).
- Swagger es público; el resto de endpoints requiere `Authorization: Bearer <token>`.
- Roles: `ADMIN_NEGOCIO`, `CLIENTE` (y administrador del sistema).
