package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturacionCorteServiceTest {

    @Mock
    private ClienteRepositorio clientes;
    @Mock
    private CondicionCreditoRepositorio condiciones;
    @Mock
    private VentaRepositorio ventas;
    @Mock
    private CreditoRepositorio creditos;
    @Mock
    private CuotaRepositorio cuotas;
    @Mock
    private PagoRepositorio pagos;
    @Mock
    private MoraService moraService;
    @Mock
    private UsuarioTiendaRepositorio accesos;

    private FacturacionCorteService servicio;

    @BeforeEach
    void setUp() {
        servicio = new FacturacionCorteService(clientes, condiciones, ventas, creditos, cuotas, pagos, moraService, accesos);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "Esperado " + esperado + " pero fue " + real);
    }

    @Test
    @DisplayName("HU-13: Regla de ciclo: compras dentro de la ventana de corte anterior a actual a las 18:00h")
    void pertenenciaAlCiclo() {
        LocalDate corteAnt = LocalDate.of(2026, 8, 20);
        LocalDate corteAct = LocalDate.of(2026, 9, 20);
        LocalTime horaCorte = LocalTime.of(18, 0);

        // Venta del 15/09 (caso oficial) -> SÍ pertenece
        assertTrue(FacturacionCorteService.perteneceAlCiclo(
                LocalDate.of(2026, 9, 15), LocalTime.of(10, 0), corteAnt, corteAct, horaCorte));

        // Venta el día del corte antes de las 18:00h -> SÍ pertenece
        assertTrue(FacturacionCorteService.perteneceAlCiclo(
                LocalDate.of(2026, 9, 20), LocalTime.of(17, 59), corteAnt, corteAct, horaCorte));

        // Venta el día del corte después de las 18:00h -> NO pertenece (pasa a octubre)
        assertFalse(FacturacionCorteService.perteneceAlCiclo(
                LocalDate.of(2026, 9, 20), LocalTime.of(18, 1), corteAnt, corteAct, horaCorte));

        // Venta el día de corte anterior después de las 18:00h -> SÍ pertenece
        assertTrue(FacturacionCorteService.perteneceAlCiclo(
                LocalDate.of(2026, 8, 20), LocalTime.of(19, 0), corteAnt, corteAct, horaCorte));

        // Venta el día de corte anterior antes de las 18:00h -> NO pertenece
        assertFalse(FacturacionCorteService.perteneceAlCiclo(
                LocalDate.of(2026, 8, 20), LocalTime.of(15, 0), corteAnt, corteAct, horaCorte));
    }

    @Test
    @DisplayName("HU-13: Listado al corte consolida compras, intereses y fecha límite de pago con idempotencia")
    void listadoCorteCompletoEIdempotente() {
        Long idCliente = 5L;
        Tienda tienda = new Tienda();
        tienda.setId(1L);
        tienda.setNombreTienda("Bodega Central");

        Cliente cliente = new Cliente();
        cliente.setId(idCliente);
        cliente.setNombreCliente("Renzo Retuerto");
        cliente.setDniCliente("87654321");
        cliente.setIdTienda(tienda);

        CondicionCredito condicion = new CondicionCredito();
        condicion.setId(50L);
        condicion.setIdCliente(cliente);
        condicion.setDiaCorte(20);
        condicion.setDiaPago(26);
        condicion.setHoraCorte(LocalTime.of(18, 0));
        condicion.setMonedaCredito("PEN");

        when(condiciones.findByIdCliente_Id(idCliente)).thenReturn(Optional.of(condicion));

        // Venta del 15/09 por S/ 1,200
        Venta v1 = new Venta();
        v1.setId(101L);
        v1.setIdCliente(cliente);
        v1.setFechaVenta(LocalDate.of(2026, 9, 15));
        v1.setHoraVenta(LocalTime.of(11, 0));
        v1.setModalidadPago("CREDITO");
        v1.setNumeroCuotas(3);
        v1.setTotalVenta(new BigDecimal("1200.00"));
        v1.setEstadoVenta("VIGENTE");

        when(ventas.findAllByIdCliente_IdOrderByFechaVentaAscHoraVentaAsc(idCliente))
                .thenReturn(List.of(v1));

        Credito cred = new Credito();
        cred.setId(201L);
        cred.setCodigoCredito("CR-000101");
        cred.setIdVenta(v1);
        cred.setInteresesTotales(new BigDecimal("36.38"));

        when(creditos.findByIdVenta_Id(101L)).thenReturn(Optional.of(cred));

        // Cuota que vence el 26/09 (día de pago del ciclo)
        Cuota cuota = new Cuota();
        cuota.setId(301L);
        cuota.setIdCredito(cred);
        cuota.setNumeroCuota(1);
        cuota.setFechaVencimiento(LocalDate.of(2026, 9, 26));
        cuota.setImporte(new BigDecimal("414.32"));
        cuota.setAmortizacion(new BigDecimal("396.22"));
        cuota.setInteresCuota(new BigDecimal("18.10"));
        cuota.setEstadoCuota("PROGRAMADA");

        when(cuotas.findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(idCliente))
                .thenReturn(List.of(cuota));
        when(pagos.findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(idCliente))
                .thenReturn(List.of());

        ResponseCalculoMoraDTO moraDto = new ResponseCalculoMoraDTO(
                301L, 1, 201L, "CR-000101", 101L,
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 20),
                0L, false, false, new BigDecimal("414.32"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("414.32"),
                "PROGRAMADA", "PEN");

        when(moraService.construirDetalleMora(eq(cuota), eq(cred), eq(v1), any(LocalDate.class)))
                .thenReturn(moraDto);

        YearMonth cicloSeptiembre = YearMonth.of(2026, 9);
        LocalDate fechaRef = LocalDate.of(2026, 9, 20);

        // Primera ejecución
        ResponseListadoCorteDTO corte1 = servicio.construirListadoCorte(cliente, cicloSeptiembre, fechaRef);

        assertEquals("2026-09", corte1.ciclo());
        assertEquals(LocalDate.of(2026, 8, 20), corte1.fechaCorteAnterior());
        assertEquals(LocalDate.of(2026, 9, 20), corte1.fechaCorteActual());
        assertEquals(LocalDate.of(2026, 9, 26), corte1.fechaLimitePago());
        assertEquals("Bodega Central", corte1.nombreTienda());

        // Compras detalladas y ordenadas
        assertEquals(1, corte1.compras().size());
        assertEquals(101L, corte1.compras().get(0).idVenta());
        igual("1200.00", corte1.compras().get(0).totalVenta());
        igual("36.38", corte1.compras().get(0).interesEstimado());

        // Obligaciones exigibles y totales
        assertEquals(1, corte1.obligaciones().size());
        igual("414.32", corte1.resumen().totalExigible());
        igual("396.22", corte1.resumen().totalCapitalExigible());
        igual("18.10", corte1.resumen().totalInteresesPeriodo());
        igual("0.00", corte1.resumen().totalMoraAcumulada());

        // Segunda ejecución (Idempotencia estricta: mismos valores sin duplicar)
        ResponseListadoCorteDTO corte2 = servicio.construirListadoCorte(cliente, cicloSeptiembre, fechaRef);
        assertEquals(corte1, corte2);
    }
}
