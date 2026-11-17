package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoraServiceTest {

    @Mock
    private CuotaRepositorio cuotas;
    @Mock
    private PagoRepositorio pagos;
    @Mock
    private ClienteRepositorio clientes;
    @Mock
    private TasaMoratoriaRepositorio tasasMora;
    @Mock
    private CondicionCreditoRepositorio condiciones;
    @Mock
    private UsuarioTiendaRepositorio accesos;

    private MoraService servicio;

    @BeforeEach
    void setUp() {
        servicio = new MoraService(cuotas, pagos, clientes, tasasMora, condiciones, accesos);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "Esperado " + esperado + " pero fue " + real);
    }

    private Cuota crearCuotaMock(BigDecimal importe, LocalDate fechaVencimiento, BigDecimal tasaMoraDiaria) {
        Tienda tienda = new Tienda();
        tienda.setId(1L);

        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setNombreCliente("Juan Perez");
        cliente.setDniCliente("12345678");
        cliente.setIdTienda(tienda);

        Venta venta = new Venta();
        venta.setId(100L);
        venta.setIdCliente(cliente);
        venta.setMonedaVenta("PEN");

        Credito credito = new Credito();
        credito.setId(200L);
        credito.setCodigoCredito("CR-000100");
        credito.setIdVenta(venta);
        credito.setTasaMoraDiariaAplicada(tasaMoraDiaria);

        Cuota cuota = new Cuota();
        cuota.setId(300L);
        cuota.setIdCredito(credito);
        cuota.setNumeroCuota(1);
        cuota.setFechaVencimiento(fechaVencimiento);
        cuota.setImporte(importe);
        cuota.setEstadoCuota("PROGRAMADA");
        return cuota;
    }

    @Test
    @DisplayName("HU-14: Pago en plazo (o antes del vencimiento) no genera mora")
    void cuotaAlDiaNoGeneraMora() {
        Cuota cuota = crearCuotaMock(new BigDecimal("414.32"), LocalDate.of(2026, 10, 26), new BigDecimal("0.001"));
        when(pagos.findByIdCuota_Id(300L)).thenReturn(Optional.empty());

        LocalDate fechaPago = LocalDate.of(2026, 10, 26);
        ResponseCalculoMoraDTO res = servicio.construirDetalleMora(cuota, cuota.getIdCredito(), cuota.getIdCredito().getIdVenta(), fechaPago);

        assertFalse(res.enMora());
        assertFalse(res.pagada());
        assertEquals(0L, res.diasAtraso());
        igual("0.00", res.interesMoratorio());
        igual("414.32", res.baseMora());
        igual("414.32", res.totalExigible());
        assertEquals("PROGRAMADA", res.estadoCuota());
    }

    @Test
    @DisplayName("HU-14: Pago con 10 días de atraso calcula mora compuesta diaria sobre la cuota vencida")
    void cuotaVencidaGeneraMoraCompuestaDiaria() {
        Cuota cuota = crearCuotaMock(new BigDecimal("414.32"), LocalDate.of(2026, 10, 26), new BigDecimal("0.001"));
        when(pagos.findByIdCuota_Id(300L)).thenReturn(Optional.empty());

        LocalDate fechaEvaluacion = LocalDate.of(2026, 11, 5); // 10 días de atraso
        ResponseCalculoMoraDTO res = servicio.construirDetalleMora(cuota, cuota.getIdCredito(), cuota.getIdCredito().getIdVenta(), fechaEvaluacion);

        assertTrue(res.enMora());
        assertFalse(res.pagada());
        assertEquals(10L, res.diasAtraso());
        igual("414.32", res.baseMora());
        igual("0.001", res.tasaMoraDiaria());
        igual("4.16", res.interesMoratorio()); // 414.32 * ((1.001)^10 - 1) = 4.16
        igual("418.48", res.totalExigible());  // 414.32 + 4.16 = 418.48
        assertEquals("EN_MORA", res.estadoCuota());
    }

    @Test
    @DisplayName("HU-14: Presenta la mora como concepto separado con su base, tasa, días y total")
    void conceptoSeparadoMora() {
        Cuota cuota = crearCuotaMock(new BigDecimal("500.00"), LocalDate.of(2026, 9, 1), new BigDecimal("0.0005"));
        when(pagos.findByIdCuota_Id(300L)).thenReturn(Optional.empty());

        LocalDate fechaEvaluacion = LocalDate.of(2026, 9, 21); // 20 días de atraso
        ResponseCalculoMoraDTO res = servicio.construirDetalleMora(cuota, cuota.getIdCredito(), cuota.getIdCredito().getIdVenta(), fechaEvaluacion);

        assertNotNull(res.baseMora());
        assertNotNull(res.tasaMoraDiaria());
        assertNotNull(res.interesMoratorio());
        assertNotNull(res.totalExigible());

        assertEquals(20L, res.diasAtraso());
        igual("500.00", res.baseMora());
        igual("0.0005", res.tasaMoraDiaria());
        igual("5.02", res.interesMoratorio()); // 500 * ((1.0005)^20 - 1) = 5.02
        igual("505.02", res.totalExigible());
    }

    @Test
    @DisplayName("HU-14: Cuota ya cancelada devuelve el registro del pago efectuado")
    void cuotaYaPagada() {
        Cuota cuota = crearCuotaMock(new BigDecimal("414.32"), LocalDate.of(2026, 10, 26), new BigDecimal("0.001"));

        Pago pago = new Pago();
        pago.setId(99L);
        pago.setIdCuota(cuota);
        pago.setFechaPagoEfectivo(LocalDate.of(2026, 11, 1));
        pago.setDiasAtraso(6);
        pago.setMoraPagada(new BigDecimal("2.49"));
        pago.setTotalExigible(new BigDecimal("416.81"));
        pago.setEstadoPago("CANCELADO");

        when(pagos.findByIdCuota_Id(300L)).thenReturn(Optional.of(pago));

        ResponseCalculoMoraDTO res = servicio.construirDetalleMora(cuota, cuota.getIdCredito(), cuota.getIdCredito().getIdVenta(), LocalDate.of(2026, 11, 1));

        assertTrue(res.pagada());
        assertEquals(6L, res.diasAtraso());
        igual("2.49", res.interesMoratorio());
        igual("416.81", res.totalExigible());
    }
}
