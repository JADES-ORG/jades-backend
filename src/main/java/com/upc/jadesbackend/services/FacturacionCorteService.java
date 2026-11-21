package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.CompraCiclo;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.ObligacionExigible;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.ResumenCorte;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
import com.upc.jadesbackend.util.CalendarioCreditoUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/**
 * HU-13: generar el listado de pago al corte.
 *
 * <p>Criterios de Aceptación:
 * <ul>
 *   <li>El listado corresponde al cliente, tienda y ciclo correctos.</li>
 *   <li>Detalla las compras ordenadas por fecha y los intereses correspondientes.</li>
 *   <li>Muestra la fecha de pago y el total exigible.</li>
 *   <li><b>Idempotencia:</b> no duplica obligaciones ni registros si el proceso vuelve a ejecutarse.</li>
 * </ul>
 */
@Service
public class FacturacionCorteService {

    public static final ZoneId ZONA_PERU = ZoneId.of("America/Lima");
    private static final LocalTime HORA_CORTE_DEFECTO = LocalTime.of(18, 0);

    private final ClienteRepositorio clientes;
    private final CondicionCreditoRepositorio condiciones;
    private final VentaRepositorio ventas;
    private final CreditoRepositorio creditos;
    private final CuotaRepositorio cuotas;
    private final PagoRepositorio pagos;
    private final MoraService moraService;
    private final UsuarioTiendaRepositorio accesos;

    public FacturacionCorteService(ClienteRepositorio clientes,
                                   CondicionCreditoRepositorio condiciones,
                                   VentaRepositorio ventas,
                                   CreditoRepositorio creditos,
                                   CuotaRepositorio cuotas,
                                   PagoRepositorio pagos,
                                   MoraService moraService,
                                   UsuarioTiendaRepositorio accesos) {
        this.clientes = clientes;
        this.condiciones = condiciones;
        this.ventas = ventas;
        this.creditos = creditos;
        this.cuotas = cuotas;
        this.pagos = pagos;
        this.moraService = moraService;
        this.accesos = accesos;
    }

    /**
     * Genera el listado de pago al corte de un cliente para un ciclo específico (ej. 2026-09).
     * El proceso es 100% idempotente: consultar o ejecutar varias veces no crea duplicados.
     */
    @Transactional(readOnly = true)
    public ResponseListadoCorteDTO generarListadoCorteCliente(
            Long idCliente,
            YearMonth ciclo,
            LocalDate fechaEvaluacion,
            UsuarioPrincipal principal) {

        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        validarAcceso(principal, cliente);

        YearMonth cicloFacturacion = ciclo != null ? ciclo : YearMonth.now(ZONA_PERU);
        LocalDate fechaRef = fechaEvaluacion != null ? fechaEvaluacion : LocalDate.now(ZONA_PERU);

        return construirListadoCorte(cliente, cicloFacturacion, fechaRef);
    }

    /**
     * Genera el listado de corte consolidado de todos los clientes de una tienda en un ciclo dado.
     */
    @Transactional(readOnly = true)
    public List<ResponseListadoCorteDTO> generarListadoCorteTienda(
            Long idTienda,
            YearMonth ciclo,
            LocalDate fechaEvaluacion,
            Long idUsuarioAdmin) {

        if (!accesos.tieneVinculoActivo(idUsuarioAdmin, idTienda)) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Tienda no autorizada para el usuario administrador");
        }

        YearMonth cicloFacturacion = ciclo != null ? ciclo : YearMonth.now(ZONA_PERU);
        LocalDate fechaRef = fechaEvaluacion != null ? fechaEvaluacion : LocalDate.now(ZONA_PERU);

        List<Cliente> listaClientes = clientes.findAllByIdTienda_Id(idTienda);
        List<ResponseListadoCorteDTO> resultado = new ArrayList<>();

        for (Cliente c : listaClientes) {
            if (condiciones.findByIdCliente_Id(c.getId()).isPresent()) {
                resultado.add(construirListadoCorte(c, cicloFacturacion, fechaRef));
            }
        }

        return resultado;
    }

    /**
     * Consulta para el portal del cliente autenticado.
     */
    @Transactional(readOnly = true)
    public ResponseListadoCorteDTO consultarMiListadoCorte(
            YearMonth ciclo,
            LocalDate fechaEvaluacion,
            Long idUsuarioCliente) {

        Cliente cliente = clientes.findByIdUsuario_Id(idUsuarioCliente)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "No existe un cliente vinculado al usuario autenticado"));

        YearMonth cicloFacturacion = ciclo != null ? ciclo : YearMonth.now(ZONA_PERU);
        LocalDate fechaRef = fechaEvaluacion != null ? fechaEvaluacion : LocalDate.now(ZONA_PERU);

        return construirListadoCorte(cliente, cicloFacturacion, fechaRef);
    }

    // ------------------------------------------------------------------
    // Construcción del listado (Lógica pura idempotente)
    // ------------------------------------------------------------------

    ResponseListadoCorteDTO construirListadoCorte(Cliente cliente, YearMonth ciclo, LocalDate fechaRef) {
        CondicionCredito condicion = condiciones.findByIdCliente_Id(cliente.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT,
                        "El cliente no tiene condiciones de crédito configuradas"));

        int diaCorte = condicion.getDiaCorte();
        int diaPago = condicion.getDiaPago();
        LocalTime horaCorte = condicion.getHoraCorte() != null ? condicion.getHoraCorte() : HORA_CORTE_DEFECTO;

        List<Venta> todasVentas = ventas.findAllByIdCliente_IdOrderByFechaVentaAscHoraVentaAsc(cliente.getId());
        String moneda = null;
        LocalDate corteActual = CalendarioCreditoUtil.fechaEnMes(ciclo, diaCorte);
        LocalDate corteAnterior = CalendarioCreditoUtil.fechaEnMes(ciclo.minusMonths(1), diaCorte);
        LocalDate fechaLimitePago = CalendarioCreditoUtil.fechaFinGracia(corteActual, diaCorte, diaPago, 0);

        String nombreTienda = cliente.getIdTienda() != null ? cliente.getIdTienda().getNombreTienda() : "Tienda";

        // 1. Compras ordenadas cronológicamente
        List<CompraCiclo> comprasCiclo = new ArrayList<>();
        BigDecimal totalCompras = BigDecimal.ZERO;
        Set<LocalDate> fechasCorteHistoricas = new HashSet<>();
        Set<LocalTime> horasCorteHistoricas = new HashSet<>();

        for (Venta v : todasVentas) {
            if ("ANULADA".equalsIgnoreCase(v.getEstadoVenta())) {
                continue;
            }

            //permite que las ventas nuevas utilicen su ciclo guardado y que las antiguas,
            // que todavía tienen fechas nulas, sigan funcionando con la lógica anterior.
            boolean pertenece = v.getFechaCorteAsignada() != null
                    ? YearMonth.from(v.getFechaCorteAsignada()).equals(ciclo)
                    : perteneceAlCiclo(
                    v.getFechaVenta(),
                    v.getHoraVenta(),
                    corteAnterior,
                    corteActual,
                    horaCorte
            );

            if (pertenece) {
                if (moneda == null) moneda = v.getMonedaVenta();
                validarMoneda(moneda, v);
                if (v.getFechaCorteAsignada() != null) {
                    fechasCorteHistoricas.add(v.getFechaCorteAsignada().toLocalDate());
                    horasCorteHistoricas.add(v.getFechaCorteAsignada().toLocalTime());
                }
                Optional<Credito> credOpt = creditos.findByIdVenta_Id(v.getId());
                Long idCredito = credOpt.map(Credito::getId).orElse(null);
                String codigoCredito = credOpt.map(Credito::getCodigoCredito).orElse(null);
                BigDecimal interesEstimado = credOpt.map(c ->
                        c.getTotalFinanciamiento() != null && c.getCapitalVenta() != null
                                ? c.getTotalFinanciamiento().subtract(c.getCapitalVenta())
                                : c.getInteresesTotales()).orElse(BigDecimal.ZERO);

                comprasCiclo.add(new CompraCiclo(
                        v.getId(),
                        v.getFechaVenta(),
                        v.getHoraVenta(),
                        v.getModalidadPago(),
                        v.getNumeroCuotas(),
                        v.getTotalVenta(),
                        idCredito,
                        codigoCredito,
                        interesEstimado
                ));
                totalCompras = totalCompras.add(v.getTotalVenta());
            }
        }

        // 2. Obligaciones exigibles al corte
        List<Cuota> todasCuotas = cuotas.findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(cliente.getId());
        List<Pago> todosPagos = pagos.findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(cliente.getId());

        Set<Long> cuotasPagadasIds = new HashSet<>();
        for (Pago p : todosPagos) {
            if (p.getIdCuota() != null && p.getFechaPagoEfectivo() != null
                    && !p.getFechaPagoEfectivo().isAfter(fechaRef)) {
                cuotasPagadasIds.add(p.getIdCuota().getId());
            }
        }

        List<ObligacionExigible> obligaciones = new ArrayList<>();
        BigDecimal totalCapital = BigDecimal.ZERO;
        BigDecimal totalInteres = BigDecimal.ZERO;
        BigDecimal totalMora = BigDecimal.ZERO;
        BigDecimal totalExigible = BigDecimal.ZERO;
        int cuotasEnMoraCount = 0;
        Set<LocalDate> vencimientosDelCiclo = new HashSet<>();

        for (Cuota c : todasCuotas) {
            if (cuotasPagadasIds.contains(c.getId())) {
                continue; // Ya cancelada
            }

            LocalDate venc = c.getFechaVencimiento();
            // Una cuota es exigible en este corte si vence en la fecha límite de pago o si ya estaba vencida
            if (esExigibleEnCiclo(c, ciclo)) {
                if (!esExigibleEnCiclo(c, ciclo.minusMonths(1))) vencimientosDelCiclo.add(venc);
                Credito cred = c.getIdCredito();
                Venta v = cred.getIdVenta();

                if (moneda == null) moneda = v.getMonedaVenta();
                validarMoneda(moneda, v);
                ResponseCalculoMoraDTO moraDto = moraService.construirDetalleMora(c, cred, v, fechaRef);

                obligaciones.add(new ObligacionExigible(
                        c.getId(),
                        cred.getId(),
                        cred.getCodigoCredito(),
                        v.getId(),
                        c.getNumeroCuota(),
                        venc,
                        c.getAmortizacion(),
                        c.getInteresCuota(),
                        c.getImporte(),
                        moraDto.diasAtraso(),
                        moraDto.interesMoratorio(),
                        moraDto.totalExigible(),
                        moraDto.estadoCuota(),
                        moraDto.enMora()
                ));

                totalCapital = totalCapital.add(c.getAmortizacion());
                totalInteres = totalInteres.add(c.getInteresCuota());
                totalMora = totalMora.add(moraDto.interesMoratorio());
                totalExigible = totalExigible.add(moraDto.totalExigible());

                if (moraDto.enMora()) {
                    cuotasEnMoraCount++;
                }
            }
        }

        ResumenCorte resumen = new ResumenCorte(
                CalculoFinancieroUtil.redondearMoneda(totalCompras),
                CalculoFinancieroUtil.redondearMoneda(totalCapital),
                CalculoFinancieroUtil.redondearMoneda(totalInteres),
                CalculoFinancieroUtil.redondearMoneda(totalMora),
                CalculoFinancieroUtil.redondearMoneda(totalExigible),
                comprasCiclo.size(),
                obligaciones.size(),
                cuotasEnMoraCount
        );

        if (fechasCorteHistoricas.size() == 1) {
            corteActual = fechasCorteHistoricas.iterator().next();
            corteAnterior = null; // Sin pacto histórico completo no es seguro reconstruir el corte anterior.
            if (horasCorteHistoricas.size() == 1) horaCorte = horasCorteHistoricas.iterator().next();
        } else if (fechasCorteHistoricas.size() > 1) {
            corteActual = null;
            corteAnterior = null;
            horaCorte = null; // No existe una cabecera única para calendarios históricos diferentes.
        }
        if (moneda == null) moneda = condicion.getMonedaCredito() == null ? "PEN" : condicion.getMonedaCredito();

        return new ResponseListadoCorteDTO(
                cliente.getId(),
                cliente.getNombreCliente(),
                cliente.getDniCliente(),
                cliente.getIdTienda().getId(),
                nombreTienda,
                ciclo.toString(),
                corteAnterior,
                corteActual,
                horaCorte,
                vencimientosDelCiclo.size() > 1 ? null : vencimientosDelCiclo.stream().findFirst().orElse(fechaLimitePago),
                moneda,
                resumen,
                comprasCiclo,
                obligaciones
        );
    }

    boolean esExigibleEnCiclo(Cuota cuota, YearMonth ciclo) {
        Credito credito = cuota.getIdCredito();
        CondicionesAplicadas pacto = credito.getCondicionesAplicadas();
        YearMonth mesVencimiento = YearMonth.from(cuota.getFechaVencimiento());
        if (pacto != null && pacto.getDiaPago() != null && pacto.getDiaCorte() != null) {
            YearMonth mesCorte = pacto.getDiaPago() <= pacto.getDiaCorte()
                    ? mesVencimiento.minusMonths(1) : mesVencimiento;
            return !mesCorte.isAfter(ciclo);
        }
        if (mesVencimiento.compareTo(ciclo) <= 0) return true;
        if (mesVencimiento.isAfter(ciclo.plusMonths(1))) return false;
        Venta venta = credito.getIdVenta();
        if (credito.getFechaCorte() == null || credito.getFechaPrimerPago() == null)
            throw new NegocioException(HttpStatus.CONFLICT, "Falta el calendario histórico de la cuota " + cuota.getId());
        int meses = "MENSUAL".equals(venta.getModalidadPago())
                ? 1 + Objects.requireNonNullElse(venta.getGraciaAdicional(), 0) : 0;
        YearMonth corteOriginal = YearMonth.from(credito.getFechaCorte().atZone(ZONA_PERU));
        YearMonth pagoOriginal = YearMonth.from(credito.getFechaPrimerPago()).minusMonths(meses);
        return pagoOriginal.equals(corteOriginal.plusMonths(1));
    }

    private void validarMoneda(String moneda, Venta venta) {
        if (venta.getMonedaVenta() != null && moneda != null && !moneda.equals(venta.getMonedaVenta()))
            throw new NegocioException(HttpStatus.CONFLICT, "El ciclo contiene obligaciones en monedas distintas");
    }

    /**
     * Evalúa si una compra pertenece al ciclo definido entre (corteAnterior @ horaCorte) y (corteActual @ horaCorte).
     */
    public static boolean perteneceAlCiclo(
            LocalDate fechaVenta,
            LocalTime horaVenta,
            LocalDate corteAnterior,
            LocalDate corteActual,
            LocalTime horaCorte) {

        if (fechaVenta.isBefore(corteAnterior)) {
            return false;
        }
        if (fechaVenta.isEqual(corteAnterior)) {
            return horaVenta != null && horaCorte != null && horaVenta.isAfter(horaCorte);
        }
        if (fechaVenta.isAfter(corteAnterior) && fechaVenta.isBefore(corteActual)) {
            return true;
        }
        if (fechaVenta.isEqual(corteActual)) {
            return horaVenta == null || horaCorte == null || !horaVenta.isAfter(horaCorte);
        }
        return false;
    }

    private void validarAcceso(UsuarioPrincipal principal, Cliente cliente) {
        if ("CLIENTE".equals(principal.getRol())) {
            boolean esSuya = cliente.getIdUsuario() != null && principal.getId().equals(cliente.getIdUsuario().getId());
            if (!esSuya) {
                throw new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
            }
        } else {
            if (!accesos.tieneVinculoActivo(principal.getId(), cliente.getIdTienda().getId())) {
                throw new NegocioException(HttpStatus.NOT_FOUND, "Cliente no autorizado en las tiendas asignadas");
            }
        }
    }
}
