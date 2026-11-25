-- JADES | Datos ficticios para una base PostgreSQL de pruebas, no de producción.
-- Este archivo no se ejecuta automáticamente: spring.sql.init.mode=never.
-- IMPORTANTE: Las ventas históricas insertadas por este archivo NO incluyen filas
-- de credito ni cuota. Los endpoints POST /cronogramas/ventas/{idVenta} y
-- /deudas/pago-unico/ventas/{idVenta} únicamente consultan obligaciones existentes.
-- No se deben recalcular créditos históricos con condiciones vigentes que podrían
-- diferir de las originalmente pactadas. Para probar el flujo completo, registre
-- ventas nuevas mediante el endpoint de ventas en una base de pruebas separada.
-- Nunca ejecutar este archivo sobre datos de la tienda real sin revisión previa.
--
-- Usuario de muestra: admin.jades.demo@example.com.
-- Antes de usar estas credenciales, verificar el hash de la contraseña de prueba.
--
-- SIMULACIÓN sin guardar (mismo admin):
-- POST /api/v1/cronogramas/simular
-- {"monto":1200,"tipoTasa":"NOMINAL","valorTasa":0.18,"periodoTasa":"ANUAL",
--  "capitalizacionTasa":"MENSUAL","numeroCuotas":3,"fechaVenta":"2026-09-15",
--  "horaVenta":"12:00:00","diaCorte":20,"horaCorte":"18:00:00","diaPago":26,
--  "graciaAdicional":0,"moneda":"PEN"}
--
-- RESULTADOS MENSUALES (antes de registrar pagos):
-- TEA: 16 días de gracia, capital 1009.61, TEM 1.8087582%.
-- 26/11/2026 = 348.78; 26/12/2026 = 348.78; 26/01/2027 = 348.79.
-- Total 1046.35. Consulta la primera cuota al 01/12/2026 para obtener
-- 1.49 de mora y 350.27 exigible. El ID de esa cuota se obtiene en
-- GET /portal/clientes/{idCliente}/cuenta-corriente (obligaciones).
-- El registro de pago efectivo solo admite fechas hasta el día actual.
-- TNA: 11 días de gracia, capital 1206.57, TEM 1.5000000%.
-- 26/10/2026 = 414.32; 26/11/2026 = 414.32; 26/12/2026 = 414.31.
-- Total 1242.95. Ambos cronogramas terminan con saldo 0.00.

DO $jades_demo$
DECLARE
tienda_demo bigint;
    tienda_otra bigint;
    usuario_admin bigint;
    usuario_otro bigint;
    usuario_cliente bigint;
    cliente_demo bigint;
    condicion_demo bigint;
    producto_demo bigint;
    venta_demo bigint;
    caso record;
    hash_demo constant text := '$2b$10$zM2VRw5T89p8BsgsJgQ7UemJgePixeMv8t.mBjzsQ6Mz9fh0UJdX.';
BEGIN
    IF EXISTS (SELECT 1 FROM tienda WHERE documento_tienda = '20990000101') THEN
        RAISE NOTICE 'La tienda de datos demo ya existe. Se omite la carga.';
        RETURN;
END IF;

INSERT INTO tienda (nombre_tienda, razon_social, documento_tienda, giro_de_negocio, direccion, estado_tienda)
VALUES ('Bodega JADES Demo', 'Bodega JADES Demo SAC', '20990000101', 'Comercio', 'Dirección ficticia 101', 'ACTIVO')
    RETURNING id_tienda INTO tienda_demo;

INSERT INTO tienda (nombre_tienda, razon_social, documento_tienda, giro_de_negocio, direccion, estado_tienda)
VALUES ('Otra Tienda JADES Demo', 'Otra Tienda JADES Demo SAC', '20990000102', 'Comercio', 'Dirección ficticia 102', 'ACTIVO')
    RETURNING id_tienda INTO tienda_otra;

INSERT INTO usuario (rol, nombre_usuario, correo_usuario, password_hash, activo)
VALUES ('ADMIN_SISTEMA', 'Sistema Demo', 'sistema.jades.demo@example.com', hash_demo, true);

INSERT INTO usuario (rol, nombre_usuario, correo_usuario, password_hash, activo)
VALUES ('ADMIN_NEGOCIO', 'Administrador Demo', 'admin.jades.demo@example.com', hash_demo, true)
    RETURNING id_usuario INTO usuario_admin;

INSERT INTO usuario (rol, nombre_usuario, correo_usuario, password_hash, activo)
VALUES ('ADMIN_NEGOCIO', 'Administrador Otra Tienda', 'otra.jades.demo@example.com', hash_demo, true)
    RETURNING id_usuario INTO usuario_otro;

INSERT INTO usuario_tienda (id_usuario, id_tienda, estado_acceso, fecha_asignacion)
VALUES (usuario_admin, tienda_demo, 'ACTIVO', DATE '2026-09-01'),
       (usuario_otro, tienda_otra, 'ACTIVO', DATE '2026-09-01');

FOR caso IN
SELECT * FROM (VALUES
                   ('tea',   'Cliente Demo TEA',   '99000101', 'Equipo Demo 1000', 950.00, 1000.00,
                    'PRODUCTO', 'EFECTIVA', 0.24, 'ANUAL', NULL, 'MENSUAL', 3,
                    DATE '2026-10-10', TIMESTAMP '2026-10-20 18:00:00', DATE '2026-11-26'),
                   ('tna',   'Cliente Demo TNA',   '99000102', 'Equipo Demo 1200', 1150.00, 1200.00,
                    'PRODUCTO', 'NOMINAL', 0.18, 'ANUAL', 'MENSUAL', 'MENSUAL', 3,
                    DATE '2026-09-15', TIMESTAMP '2026-09-20 18:00:00', DATE '2026-10-26'),
                   ('unico', 'Cliente Demo Único', '99000103', 'Servicio Demo 200', 190.00, 200.00,
                    'SERVICIO', 'EFECTIVA', 0.03, 'MENSUAL', NULL, 'UNICO', 1,
                    DATE '2026-09-21', TIMESTAMP '2026-10-20 18:00:00', DATE '2026-10-26')
              ) AS datos (alias_cliente, nombre, dni, producto, precio_contado, precio_credito,
                          tipo_producto, tipo_tasa, tasa, periodo, capitalizacion, modalidad,
                          num_cuotas, fecha_compra, corte, pago)
    LOOP
INSERT INTO usuario (rol, nombre_usuario, correo_usuario, password_hash, activo)
VALUES ('CLIENTE', caso.nombre, caso.alias_cliente || '.jades.demo@example.com', hash_demo, true)
    RETURNING id_usuario INTO usuario_cliente;

INSERT INTO cliente (id_tienda, id_usuario, nombre_cliente, dni_cliente, correo_cliente, estado_cliente)
VALUES (tienda_demo, usuario_cliente, caso.nombre, caso.dni,
        caso.alias_cliente || '.jades.demo@example.com', 'ACTIVO')
    RETURNING id_cliente INTO cliente_demo;

INSERT INTO condicion_credito (id_cliente, limite_credito, plazo_maximo, dia_corte,
                               dia_pago, moneda_credito, hora_corte)
VALUES (cliente_demo, 2000.00, 6, 20, 26, 'PEN', TIME '18:00:00')
    RETURNING id_condicion INTO condicion_demo;

INSERT INTO tasa_interes (id_condicion, tipo_tasa, tasa_interes, periodo_tasa, capitalizacion_tasa)
VALUES (condicion_demo, caso.tipo_tasa, caso.tasa, caso.periodo, caso.capitalizacion);

INSERT INTO tasa_moratoria (id_condicion, tasa_moratoria, tipo_tasa_mora, periodo_tasa_mora, capitalizacion_mora)
VALUES (condicion_demo, 0.36, 'EFECTIVA', 'ANUAL', NULL);

INSERT INTO producto (id_tienda, nombre_producto, descripcion, precio_contado, precio_credito,
                      tipo_producto, unidad_medida, modalidad, estado_producto)
VALUES (tienda_demo, caso.producto, 'Artículo ficticio para pruebas',
        caso.precio_contado, caso.precio_credito, caso.tipo_producto, 'UNIDAD', 'AMBAS', 'ACTIVO')
    RETURNING id_producto INTO producto_demo;

INSERT INTO venta (id_cliente, id_usuario_registro, fecha_venta, hora_venta, modalidad_pago,
                   numero_cuotas, total_venta, moneda_venta, fecha_corte_asignada,
                   fecha_pago_asignada, gracia_adicional, estado_venta, clave_idempotencia)
VALUES (cliente_demo, usuario_admin, caso.fecha_compra, TIME '12:00:00', caso.modalidad,
        caso.num_cuotas, caso.precio_credito, 'PEN', caso.corte, caso.pago, 0, 'VIGENTE',
        'jades-demo-2026-' || caso.alias_cliente)
    RETURNING id_venta INTO venta_demo;

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal)
VALUES (venta_demo, producto_demo, 1, caso.precio_credito, caso.precio_credito);
END LOOP;
END;
$jades_demo$;

-- IDs para las llamadas de prueba. El filtro excluye ventas nuevas de la interfaz.
-- SELECT c.id_tienda, c.id_cliente, c.correo_cliente, v.id_venta, d.id_producto,
--        v.modalidad_pago, v.fecha_venta, v.fecha_corte_asignada, v.fecha_pago_asignada, v.total_venta
-- FROM venta v
-- JOIN cliente c ON c.id_cliente = v.id_cliente
-- JOIN detalle_venta d ON d.id_venta = v.id_venta
-- WHERE v.clave_idempotencia IN ('jades-demo-2026-tea', 'jades-demo-2026-tna', 'jades-demo-2026-unico')
-- ORDER BY v.fecha_venta;
