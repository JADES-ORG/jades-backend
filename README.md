# 🚀 JADES Backend — Sistema de Gestión Financiera y Créditos

<div align="center">

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-brightgreen?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql&logoColor=white)
![Security](https://img.shields.io/badge/Security-JWT%20Bearer-red?logo=jsonwebtokens&logoColor=white)
![OpenAPI](https://img.shields.io/badge/Swagger-OpenAPI%203.0-green?logo=swagger&logoColor=white)
![Status](https://img.shields.io/badge/Build-Passing%20(25%20tests)-success)

**API REST empresarial para el control de cuentas corrientes comerciales, simulación y generación de cronogramas de amortización (Método Francés), cálculo de mora diaria compuesta y facturación al corte.**

*Desarrollado para el curso de **Finanzas e Ingeniería Económica** — **Universidad Peruana de Ciencias Aplicadas (UPC)** — **Sección 7666**.*  
*Docente: **Senmache Sarmiento**.*

</div>

---

## 📌 Tabla de Contenidos
1. [Descripción General](#-descripción-general)
2. [Arquitectura y Estructura](#-arquitectura-y-estructura)
3. [Historias de Usuario y Motor Financiero](#-historias-de-usuario-y-motor-financiero)
4. [Endpoints de la API](#-endpoints-de-la-api)
5. [Pruebas Rápidas con Postman y Swagger](#-pruebas-rápidas-con-postman-y-swagger)
6. [Cómo Desplegar y Ejecutar en Local](#-cómo-desplegar-y-ejecutar-en-local)
7. [Pruebas Unitarias Automatizadas](#-pruebas-unitarias-automatizadas)
8. [Seguridad y Roles](#-seguridad-y-roles)

---

## 💡 Descripción General

**JADES** es una plataforma orientada a tiendas comerciales y microempresas para gestionar ventas a crédito y cuentas corrientes de clientes. Incorpora un **motor financiero de alta precisión** (`BigDecimal`, 12 decimales de cálculo y redondeo bancario comercial `HALF_UP`), garantizando el estricto cumplimiento de la normativa financiera y los estándares del curso.

### Capacidades Destacadas:
- 📊 **Método Francés ordinario vencido (360/30):** Cálculo de cuotas fijas con capitalización exacta durante el período de gracia total.
- ⚖️ **Ajuste automático de centavos:** Liquidación de saldo final exactamente en `S/ 0.00` en la última cuota sin residuo de redondeo.
- ⏰ **Regla de corte estricta (18:00 h):** Las compras posteriores a la hora de corte ingresan automáticamente a la facturación del siguiente ciclo.
- 📈 **Mora compuesta diaria:** Cálculo de intereses moratorios a partir del día siguiente al vencimiento usando la Tasa Efectiva Diaria moratoria ($TED_m$).
- 🛡️ **Arquitectura Segura:** Autenticación Stateless mediante Tokens JWT y control de acceso basado en roles (`ADMIN_SISTEMA`, `ADMIN_NEGOCIO`, `CLIENTE`).

---

## 🏗️ Arquitectura y Estructura

El backend sigue una arquitectura por capas desacoplada y orientada a dominios:

```text
src/main/java/com/upc/jadesbackend/
├── config/            # Configuración OpenAPI / Swagger UI
├── controllers/       # Controladores REST versionados (/api/v1)
├── dtos/              # Data Transfer Objects validados (Bean Validation)
├── entities/          # Entidades JPA relacionales (PostgreSQL)
├── exceptions/        # Manejo centralizado de excepciones (ControllerAdvice)
├── repositories/      # Repositorios Spring Data JPA con bloqueo pesimista
├── security/          # Filtros JWT, UserDetailsService, AuthenticationManager
├── services/          # Lógica del negocio y transaccionalidad (@Transactional)
└── util/              # Motor financiero puro (CalculoFinancieroUtil, CalendarioCreditoUtil)
```

Documentación matemática detallada:
- 📖 [Documentación del Motor Financiero](docs/MOTOR_FINANCIERO.md)

---

## 🎯 Historias de Usuario y Motor Financiero

| Historia | Módulo | Descripción | Criterio de Aceptación Oficial |
| :---: | :--- | :--- | :--- |
| **HU-12** | **Cronograma de Cuotas** | Simular y generar cronograma de amortización francesa. | **Caso 15/09/2026:** S/ 1,200 a 3 cuotas, TNA 18%, corte día 20, pago día 26. Gracia total = 11 días $\to$ capitalizado = `S/ 1,206.57`. Cuotas: `414.32`, `414.32` y `414.31` (ajuste -0.01). Saldo final: `S/ 0.00`. |
| **HU-13** | **Listado de Pago al Corte** | Generar estado de cuenta consolidado al día de corte. | Ventana estricta a las **18:00 h**. Detalla compras del ciclo, cuotas exigibles, mora desglosada y total exigible de forma idempotente. |
| **HU-14** | **Intereses Moratorios** | Liquidar intereses por mora sobre cuotas vencidas. | Interés compuesto diario: $\text{Mora} = \text{Cuota} \times ((1 + TED_m)^{\text{días}} - 1)$. Desglose auditado e inalterable. |

---

## 📡 Endpoints de la API

### 1. Autenticación (`/api/v1/autenticacion`)
| Método | Endpoint | Rol requerido | Descripción |
| :---: | :--- | :---: | :--- |
| `POST` | `/iniciar-sesion` | *Público* | Envía `{ "correo", "contrasena" }` y retorna el Bearer Token JWT. |

### 2. Cronogramas y Créditos — HU-12 (`/api/v1/cronogramas`)
| Método | Endpoint | Rol requerido | Descripción |
| :---: | :--- | :---: | :--- |
| `POST` | `/simular` | `ADMIN_NEGOCIO` | Simulación matemática previa del cronograma sin persistir en BD. |
| `POST` | `/ventas/{idVenta}` | `ADMIN_NEGOCIO` | Genera y guarda el crédito con sus cuotas (idempotente). |
| `GET` | `/ventas/{idVenta}` | `ADMIN_NEGOCIO`, `CLIENTE` | Consulta el cronograma guardado de una venta. |

### 3. Facturación al Corte — HU-13 (`/api/v1/corte`)
| Método | Endpoint | Rol requerido | Descripción |
| :---: | :--- | :---: | :--- |
| `GET` | `/clientes/{idCliente}` | `ADMIN_NEGOCIO` | Listado consolidado al corte para un cliente y fecha específica. |
| `GET` | `/tiendas/{idTienda}` | `ADMIN_NEGOCIO` | Reporte general al corte de todos los clientes de la tienda. |
| `GET` | `/mi-cuenta` | `CLIENTE` | Consulta de la facturación al corte para el cliente en sesión. |

### 4. Intereses por Mora — HU-14 (`/api/v1/mora`)
| Método | Endpoint | Rol requerido | Descripción |
| :---: | :--- | :---: | :--- |
| `GET` | `/cuotas/{idCuota}` | `ADMIN_NEGOCIO` | Cálculo exacto de mora de una cuota vencida a la fecha de consulta. |
| `GET` | `/clientes/{idCliente}` | `ADMIN_NEGOCIO` | Mora consolidada de todas las cuotas vencidas del cliente. |
| `GET` | `/mi-cuenta` | `CLIENTE` | Mora exigible del cliente autenticado. |

---

## 🧪 Pruebas Rápidas con Postman y Swagger

### 1. Swagger UI (Documentación Interactiva)
Una vez iniciado el backend, abre en tu navegador:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**
*(Para peticiones protegidas, inicia sesión en `/iniciar-sesion`, copia el `tokenAcceso` y pégalo en el botón verde **Authorize**)*.

### 2. Colección de Postman
En la carpeta `docs/` se incluye la colección oficial de Postman con todas las peticiones listas para importar:
📁 **[`docs/JADES_FINANZAS.postman_collection.json`](docs/JADES_FINANZAS.postman_collection.json)**

> ⚡ **Tip de Postman:** Al ejecutar la petición `0. Iniciar Sesión`, un test script almacena automáticamente el JWT en la variable `{{token}}`, dejando todas las demás peticiones autenticadas al instante.

---

## 💻 Cómo Desplegar y Ejecutar en Local

### Requisitos previos:
- **Java Development Kit (JDK) 21** o superior.
- **Docker & Docker Compose** (o PostgreSQL 16 instalado localmente).

### 1. Levantar la Base de Datos
Desde la raíz del proyecto donde reside `docker-compose.yml`:
```bash
docker-compose up -d
```

### 2. Configuración (`application.properties`)
El backend viene configurado con valores predeterminados para desarrollo local seguro:
```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/bd_JADES}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=update
```
*(Puedes sobreescribir cualquier variable de entorno como `DB_URL` si tu base de datos usa otro puerto como `5433`)*.

### 3. Iniciar el Servidor Backend
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

---

## 🚦 Pruebas Unitarias Automatizadas

El proyecto cuenta con **25 pruebas unitarias** que validan la matemática financiera, los calendarios de corte y la idempotencia de los servicios:

```bash
# Ejecutar todas las pruebas unitarias
.\mvnw.cmd test

# Ejecutar únicamente las pruebas del motor financiero
.\mvnw.cmd test -Dtest=CalculoFinancieroUtilTest
```

**Resultado de la suite de pruebas:**
```text
[INFO] Running com.upc.jadesbackend.util.CalculoFinancieroUtilTest (10 tests) - OK
[INFO] Running com.upc.jadesbackend.util.CalendarioCreditoUtilTest (6 tests) - OK
[INFO] Running com.upc.jadesbackend.services.CronogramaServiceTest (3 tests) - OK
[INFO] Running com.upc.jadesbackend.services.MoraServiceTest (4 tests) - OK
[INFO] Running com.upc.jadesbackend.services.FacturacionCorteServiceTest (2 tests) - OK
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 🔐 Seguridad y Roles

- **`ADMIN_SISTEMA`:** Gestión global de tiendas y altas administrativas.
- **`ADMIN_NEGOCIO`:** Simulación y emisión de cronogramas, consulta de mora y facturación al corte de su comercio.
- **`CLIENTE`:** Acceso exclusivo a sus cuotas, su mora pendiente y su propio estado de cuenta corriente.
