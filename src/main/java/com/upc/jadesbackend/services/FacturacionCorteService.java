package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.CompraCiclo;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.ObligacionExigible;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.ResumenCorte;
import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO.PagoRegistrado;
import com.upc.jadesbackend.dtos.ResponseVentaDTO;
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
import java.time.LocalDateTime;
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

    private final ClienteRepositorio clientes;
    private final CondicionCreditoRepositorio condiciones;
    private final VentaRepositorio ventas;
    private final CreditoRepositorio creditos;
    private final CuotaRepositorio cuotas;
    private final PagoRepositorio pagos;
    private final MoraService moraService;
    private final UsuarioTiendaRepositorio accesos;
    private final ClienteService fichas;

    public FacturacionCorteService(ClienteRepositorio clientes,
                                   CondicionCreditoRepositorio condiciones,
                                   VentaRepositorio ventas,
                                   CreditoRepositorio creditos,
                                   CuotaRepositorio cuotas,
                                   PagoRepositorio pagos,
                                   MoraService moraService,
                                   UsuarioTiendaRepositorio accesos, ClienteService fichas) {
        this.clientes = clientes;
        this.condiciones = condiciones;
        this.ventas = ventas;
        this.creditos = creditos;
        this.cuotas = cuotas;
        this.pagos = pagos;
        this.moraService = moraService;
        this.accesos = accesos;
        this.fichas = fichas;
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
            Long idUsuarioCliente, Long idTienda) {

        Cliente cliente = fichas.consultarFichaPropia(idUsuarioCliente, idTienda);

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

        List<Venta> todasVentas = ventas.findAllByIdCliente_IdOrderByFechaVentaAscHoraVentaAsc(cliente.getId());
        String moneda = null;

        String nombreTienda = cliente.getIdTienda() != null ? cliente.getIdTienda().getNombreTienda() : "Tienda";

        // 1. Compras ordenadas cronológicamente
        List<CompraCiclo> comprasCiclo = new ArrayList<>();
        BigDecimal totalCompras = BigDecimal.ZERO;
        Set<LocalDateTime> cortesHistoricos = new HashSet<>();
        Set<LocalDate> cortesAnteriores = new HashSet<>();
        Set<LocalDate> vencimientosDelCiclo = new HashSet<>();

        for (Venta v : todasVentas) {
            if ("ANULADA".equalsIgnoreCase(v.getEstadoVenta()) || v.getFechaVenta().isAfter(fechaRef)) {
                continue;
            }

            Optional<Credito> credOpt = creditos.findByIdVenta_Id(v.getId());
            Credito creditoVenta = credOpt.orElse(null);
            LocalDateTime corteVenta = CalendarioCreditoUtil.corteHistoricoVenta(v, creditoVenta);
            if (corteVenta == null)
                throw new NegocioException(HttpStatus.CONFLICT, "Falta la fecha de corte histórica de la venta " + v.getId());
            boolean pertenece = YearMonth.from(corteVenta).equals(ciclo);

            if (pertenece) {
                if (moneda == null) moneda = v.getMonedaVenta();
                validarMoneda(moneda, v);
                CondicionesAplicadas pacto = creditoVenta == null ? null : creditoVenta.getCondicionesAplicadas();
                LocalDate pagoCiclo = "UNICO".equals(v.getModalidadPago()) ? v.getFechaPagoAsignada()
                        : pacto != null && pacto.getDiaCorte() != null && pacto.getDiaPago() != null
                            ? CalendarioCreditoUtil.fechaFinGracia(corteVenta.toLocalDate(), pacto.getDiaCorte(), pacto.getDiaPago(), 0)
                            : null;
                if (pagoCiclo == null && "UNICO".equals(v.getModalidadPago()) && creditoVenta != null)
                    pagoCiclo = creditoVenta.getFechaPrimerPago();
                registrarCalendario(cortesHistoricos, cortesAnteriores, vencimientosDelCiclo, corteVenta, pacto, pagoCiclo);
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
                        interesEstimado,
                        corteVenta,
                        v.getFechaPagoAsignada() != null ? v.getFechaPagoAsignada() : creditoVenta == null ? null : creditoVenta.getFechaPrimerPago(),
                        detallesVenta(v)
                ));
                totalCompras = totalCompras.add(v.getTotalVenta());
            }
        }

        // 2. Obligaciones exigibles al corte
        List<Cuota> todasCuotas = cuotas.findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(cliente.getId());
        List<Pago> todosPagos = pagos.findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(cliente.getId());

        Map<Long, Pago> pagosPorCuota = new HashMap<>();
        for (Pago p : todosPagos) {
            if (p.getIdCuota() != null && p.getFechaPagoEfectivo() != null
                    && !p.getFechaPagoEfectivo().isAfter(fechaRef)) {
                pagosPorCuota.put(p.getIdCuota().getId(), p);
            }
        }
        List<PagoRegistrado> historial = new ArrayList<>();
        BigDecimal totalPagadoListado = BigDecimal.ZERO;

        List<ObligacionExigible> obligaciones = new ArrayList<>();
        BigDecimal totalCapital = BigDecimal.ZERO;
        BigDecimal totalInteres = BigDecimal.ZERO;
        BigDecimal totalMora = BigDecimal.ZERO;
        BigDecimal totalExigible = BigDecimal.ZERO;
        int cuotasEnMoraCount = 0;

        for (Cuota c : todasCuotas) {
            Credito cred = c.getIdCredito();
            Venta v = cred.getIdVenta();
            if ("ANULADA".equalsIgnoreCase(v.getEstadoVenta()) || v.getFechaVenta().isAfter(fechaRef)) continue;

            LocalDate venc = c.getFechaVencimiento();
            Pago pago = pagosPorCuota.get(c.getId());
            if (pago != null) {
                YearMonth cicloObligacion = CalendarioCreditoUtil.cicloHistoricoCuota(c);
                boolean pertenece = ciclo.equals(cicloObligacion)
                        || ciclo.equals(YearMonth.from(pago.getFechaPagoEfectivo()));
                if (pertenece) {
                    if (moneda == null) moneda = v.getMonedaVenta();
                    validarMoneda(moneda, v);
                    if (ciclo.equals(cicloObligacion))
                        registrarCalendario(cortesHistoricos, cortesAnteriores, vencimientosDelCiclo,
                                CalendarioCreditoUtil.corteHistoricoCuota(c), cred.getCondicionesAplicadas(), venc);
                    historial.add(new PagoRegistrado(
                            pago.getId(), c.getId(), cred.getId(), v.getId(),
                            cred.getCodigoCredito(), v.getModalidadPago(), c.getNumeroCuota(),
                            v.getFechaVenta(), CalendarioCreditoUtil.corteHistoricoCuota(c), venc, pago.getFechaPagoEfectivo(), pago.getHoraPagoEfectivo(),
                            pago.getMedioPago(), pago.getDiasAtraso(), pago.getCapitalPagado(),
                            pago.getInteresPagado(), pago.getMoraPagada(), pago.getTotalExigible(),
                            cred.getSaldoCredito(), saldoAlCierrePago(cred, pago.getFechaPagoEfectivo(), todosPagos),
                            v.getMonedaVenta(), "PAGADA", detallesVenta(v)));
                    totalPagadoListado = totalPagadoListado.add(pago.getTotalExigible());
                }
                continue;
            }

            if (esExigibleEnCiclo(c, ciclo)) {
                if (ciclo.equals(CalendarioCreditoUtil.cicloHistoricoCuota(c)))
                    registrarCalendario(cortesHistoricos, cortesAnteriores, vencimientosDelCiclo,
                            CalendarioCreditoUtil.corteHistoricoCuota(c), cred.getCondicionesAplicadas(), venc);

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
                        moraDto.enMora(),
                        CalendarioCreditoUtil.corteHistoricoCuota(c),
                        detallesVenta(v)
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

        historial.sort(Comparator.comparing(PagoRegistrado::fechaPagoEfectivo)
                .thenComparing(PagoRegistrado::horaPagoEfectivo, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(PagoRegistrado::idPago));
        ResumenCorte resumen = new ResumenCorte(
                CalculoFinancieroUtil.redondearMoneda(totalCompras),
                CalculoFinancieroUtil.redondearMoneda(totalCapital),
                CalculoFinancieroUtil.redondearMoneda(totalInteres),
                CalculoFinancieroUtil.redondearMoneda(totalMora),
                CalculoFinancieroUtil.redondearMoneda(totalExigible),
                comprasCiclo.size(),
                obligaciones.size(),
                cuotasEnMoraCount,
                CalculoFinancieroUtil.redondearMoneda(totalPagadoListado),
                historial.size()
        );

        LocalDateTime corteUnico = valorUnico(cortesHistoricos);
        boolean calendariosMultiples = cortesHistoricos.stream().filter(Objects::nonNull).count() > 1
                || vencimientosDelCiclo.stream().filter(Objects::nonNull).count() > 1
                || cortesAnteriores.stream().filter(Objects::nonNull).count() > 1;
        boolean calendarioIncompleto = cortesHistoricos.contains(null) || vencimientosDelCiclo.contains(null)
                || cortesAnteriores.contains(null);
        if (moneda == null) moneda = condicion.getMonedaCredito() == null ? "PEN" : condicion.getMonedaCredito();

        return new ResponseListadoCorteDTO(
                cliente.getId(),
                cliente.getNombreCliente(),
                cliente.getDniCliente(),
                cliente.getIdTienda().getId(),
                nombreTienda,
                ciclo.toString(),
                valorUnico(cortesAnteriores),
                corteUnico == null ? null : corteUnico.toLocalDate(),
                corteUnico == null ? null : corteUnico.toLocalTime(),
                valorUnico(vencimientosDelCiclo),
                calendariosMultiples,
                calendarioIncompleto,
                moneda,
                resumen,
                comprasCiclo,
                obligaciones,
                historial
        );
    }

    private BigDecimal saldoAlCierrePago(Credito credito, LocalDate fechaPago, List<Pago> lista) {
        BigDecimal capitalPagado = lista.stream()
                .filter(p -> p.getIdCuota().getIdCredito().getId().equals(credito.getId()))
                .filter(p -> p.getFechaPagoEfectivo() != null && !p.getFechaPagoEfectivo().isAfter(fechaPago))
                .map(Pago::getCapitalPagado).reduce(BigDecimal.ZERO, BigDecimal::add);
        return credito.getCapitalCapitalizado().subtract(capitalPagado);
    }

    private List<ResponseVentaDTO.Detalle> detallesVenta(Venta venta) {
        return venta.getDetalles().stream().map(ResponseVentaDTO.Detalle::de).toList();
    }

    private void registrarCalendario(Set<LocalDateTime> cortes, Set<LocalDate> anteriores,
                                     Set<LocalDate> vencimientos, LocalDateTime corte,
                                     CondicionesAplicadas pacto, LocalDate vencimiento) {
        cortes.add(corte);
        anteriores.add(corte != null && pacto != null && pacto.getDiaCorte() != null
                ? CalendarioCreditoUtil.fechaEnMes(YearMonth.from(corte).minusMonths(1), pacto.getDiaCorte()) : null);
        vencimientos.add(vencimiento);
    }

    private static <T> T valorUnico(Set<T> valores) {
        return valores.size() == 1 ? valores.iterator().next() : null;
    }

    boolean esExigibleEnCiclo(Cuota cuota, YearMonth ciclo) {
        YearMonth cicloCuota = CalendarioCreditoUtil.cicloHistoricoCuota(cuota);
        if (cicloCuota == null)
            throw new NegocioException(HttpStatus.CONFLICT, "Falta el calendario histórico de la cuota " + cuota.getId());
        return !cicloCuota.isAfter(ciclo);
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
