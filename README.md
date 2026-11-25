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

Hibernate valida el esquema existente. Para incorporar las condiciones históricas, el repositorio incluye `docs/credito_condiciones_aplicadas.sql`. Es una ampliación manual del esquema que deja vacíos los campos de créditos anteriores. El backend no ejecuta el script ni carga datos al iniciar.

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

### 4. Ejecutar pruebas
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
