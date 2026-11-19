# Motor Financiero — `CalculoFinancieroUtil`

**Responsable:** Renzo Retuerto  
**HU relacionadas:** HU-12 (cronograma de cuotas), HU-14 (intereses por mora), base para HU-13.  
**Código:** [`CalculoFinancieroUtil.java`](../src/main/java/com/upc/jadesbackend/util/CalculoFinancieroUtil.java)  
**Pruebas:** [`CalculoFinancieroUtilTest.java`](../src/test/java/com/upc/jadesbackend/util/CalculoFinancieroUtilTest.java)

Clase utilitaria sin dependencias de Spring ni de base de datos. Contiene todas las fórmulas del Reporte
Técnico (sección 2) para que los servicios (cronograma, mora, corte) las reutilicen.

---

## 1. Convenciones

| Constante | Valor | Significado |
| :--- | :--- | :--- |
| `BASE_ANUAL` | 360 | Año comercial |
| `BASE_MENSUAL` | 30 | Mes comercial |
| `DECIMALES_MONEDA` | 2 | Montos redondeados con `HALF_UP` |
| `DECIMALES_TASA` | 12 | Precisión de tasas (cumple el mínimo de 7 y la escala de BD `DECIMAL(20,12)`) |

> **Formato de tasas:** se reciben como fracción decimal. 18% = `0.18`.

---

## 2. Métodos disponibles

| Método | Fórmula / descripción |
| :--- | :--- |
| `diasDelPeriodo(periodo)` | DIARIO=1, QUINCENAL=15, MENSUAL=30, BIMESTRAL=60, TRIMESTRAL=90, CUATRIMESTRAL=120, SEMESTRAL=180, ANUAL=360 |
| `tasaEfectiva(tipo, valor, periodo, capitalizacion, dias)` | EFECTIVA: `(1+i)^(dias/diasPeriodo) - 1` · NOMINAL: `(1 + i·diasCap/diasPeriodo)^(dias/diasCap) - 1` |
| `tasaEfectivaMensual(...)` / `tasaEfectivaDiaria(...)` | Atajos para 30 días y 1 día |
| `convertirTasaEfectiva(tasa, diasOrigen, diasDestino)` | Equivalencia entre tasas efectivas |
| `capitalizarGracia(capital, TEM, diasGracia)` | `C0 · (1+TEM)^(dias/30)` (equivale a `C0 · (1+TED)^dias`) |
| `calcularCuotaFrancesa(capital, TEP, n)` | `R = C · [TEP(1+TEP)^n] / [(1+TEP)^n − 1]` |
| `generarCronograma(capital, TEP, n, primerVencimiento)` | Tabla completa: saldo inicial, interés, amortización, cuota, saldo final |
| `diasAtraso(vencimiento, fechaPago)` | Días entre fechas; 0 si se paga a tiempo |
| `calcularMora(cuotaVencida, TEDmora, dias)` | `Mora = Cuota · ((1+TEDm)^dias − 1)` |

### Objetos de resultado
- `ResultadoCronograma`: `capitalFinanciado`, `tasaPeriodo`, `cuotaBase`, `ajusteUltimaCuota`,
  `interesesTotales`, `totalFinanciamiento`, `filas`.
- `FilaCronograma`: `numeroCuota`, `fechaVencimiento`, `saldoInicial`, `interes`, `amortizacion`, `cuota`, `saldoFinal`.
- `ResultadoMora`: `baseMora`, `tasaMoraDiaria`, `diasAtraso`, `interesMoratorio`, `totalExigible`
  (la mora se presenta como concepto separado, como exige la HU-14).

---

## 3. Tratamiento del redondeo (criterio HU-12)

1. La cuota base `R` se redondea a 2 decimales.
2. En cada periodo: `interés = round(saldoInicial × TEP, 2)` y `amortización = R − interés`.
3. **En la última cuota** la amortización se fija igual al saldo inicial pendiente, y
   `cuota final = amortización + interés`.
4. La diferencia `cuota final − R` se expone como `ajusteUltimaCuota` (normalmente ±0.01).

Así el **saldo final siempre es exactamente S/ 0.00**.

---

## 4. Caso de prueba N.° 2 — Venta del 15/09/2026

**Datos:** S/ 1,200.00 · TNA 18% capitalización mensual (TEM 1.5%) · 3 cuotas · corte día 20 · pago día 26.

| Paso | Resultado |
| :--- | :--- |
| Días de gracia (15/09 → 26/09) | 11 |
| Capital post-gracia | S/ 1,206.57 |
| Cuota base | S/ 414.32 |

| Cuota | Vence | Saldo inicial | Interés | Amortización | Cuota | Saldo final |
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| 1 | 26/10/2026 | 1,206.57 | 18.10 | 396.22 | 414.32 | 810.35 |
| 2 | 26/11/2026 | 810.35 | 12.16 | 402.16 | 414.32 | 408.19 |
| 3 | 26/12/2026 | 408.19 | 6.12 | 408.19 | **414.31** | **0.00** |

Ajuste última cuota: **−0.01** · Intereses totales: **S/ 36.38** · Total financiamiento: **S/ 1,242.95**.

> **Nota sobre el reporte técnico:** el reporte indica una tasa de gracia de 0.5478473%, pero
> `(1.015)^(11/30) − 1 = 0.5474086%`. El capital post-gracia redondeado (S/ 1,206.57) es el mismo con
> ambos valores; se recomienda corregir la cifra en el informe.

---

## 5. Pruebas automatizadas

```bash
.\mvnw.cmd test "-Dtest=CalculoFinancieroUtilTest,CalendarioCreditoUtilTest,CronogramaServiceTest,MoraServiceTest,FacturacionCorteServiceTest"
```

| Suite | Tests | Qué valida |
| :--- | :---: | :--- |
| `CalculoFinancieroUtilTest` | 10 | Conversión de tasas, auto-normalización de porcentajes (18.0 == 0.18), gracia, método francés, cuotas 1-3, ajuste −0.01, mora |
| `CalendarioCreditoUtilTest` | 6 | Caso 15/09 (oct-nov-dic), venta post-corte, hora 18:00h, día pago menor a corte, gracia adicional, día 31 |
| `CronogramaServiceTest` | 3 | Simulación integral del caso 15/09 (con 0.18 y con 18.0 idénticos), gracia 11d, cuotas, ajuste y validación de tasas inválidas |
| `MoraServiceTest` | 4 | Pago en plazo (mora 0), mora compuesta 10 días (S/ 4.16), concepto separado (base, tasa, días, total), y cuota ya pagada |
| `FacturacionCorteServiceTest` | 2 | Regla de ventana de ciclo a las 18:00h e idempotencia estricta del listado al corte sin duplicar obligaciones |

Resultado actual: **25 tests, 0 fallos**.

---

## 6. Endpoints Implementados (HU-12, HU-13, HU-14)

### Cronograma de Cuotas (HU-12): [`CronogramaController.java`](../src/main/java/com/upc/jadesbackend/controllers/CronogramaController.java)

| Método | Endpoint | Rol | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/cronogramas/simular` | `ADMIN_NEGOCIO` | Simula un cronograma en memoria sin guardar nada (paso 3 del wizard de venta) |
| `POST` | `/api/v1/cronogramas/ventas/{idVenta}` | `ADMIN_NEGOCIO` | Genera y guarda el `Credito` y sus `Cuota`s en BD. Devuelve `201 Created` o `200 OK` si ya existía (idempotente) |
| `GET` | `/api/v1/cronogramas/ventas/{idVenta}` | `ADMIN_NEGOCIO`, `CLIENTE` | Consulta el cronograma persistido de una venta |

### Intereses por Mora (HU-14): [`MoraController.java`](../src/main/java/com/upc/jadesbackend/controllers/MoraController.java)

| Método | Endpoint | Rol | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/mora/cuotas/{idCuota}` | `ADMIN_NEGOCIO`, `CLIENTE` | Calcula la mora de una cuota a una fecha dada (`?fechaEvaluacion=YYYY-MM-DD`). Muestra base, tasa TEDm, días, mora y total exigible |
| `GET` | `/api/v1/mora/clientes/{idCliente}` | `ADMIN_NEGOCIO` | Consolida todas las cuotas vencidas y mora total exigible de un cliente |
| `GET` | `/api/v1/mora/mi-cuenta` | `CLIENTE` | Consulta las cuotas vencidas y mora del cliente autenticado |

### Listado de Pago al Corte (HU-13): [`FacturacionCorteController.java`](../src/main/java/com/upc/jadesbackend/controllers/FacturacionCorteController.java)

| Método | Endpoint | Rol | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/corte/clientes/{idCliente}` | `ADMIN_NEGOCIO`, `CLIENTE` | Genera el estado de cuenta y listado de pago al corte de un cliente para un ciclo (`?ciclo=YYYY-MM`) |
| `GET` | `/api/v1/corte/tiendas/{idTienda}` | `ADMIN_NEGOCIO` | Genera el consolidado de corte para todos los clientes de la tienda en el ciclo |
| `GET` | `/api/v1/corte/mi-cuenta` | `CLIENTE` | Consulta el listado de pago al corte del cliente autenticado |

---

## 7. Resumen de tus Historias de Usuario

Todas las Historias de Usuario asignadas a Renzo (HU-12, HU-13, HU-14) están completadas al 100%:
- **HU-12:** Método francés vencido simple ordinario con capitalización de gracia total y ajuste de redondeo final a S/ 0.00.
- **HU-13:** Listado al corte por cliente, tienda y ciclo, con compras ordenadas cronológicamente, intereses y total exigible garantizando idempotencia estricta.
- **HU-14:** Intereses moratorios en concepto separado (base, tasa, días, mora y total) sobre la cuota vencida.



