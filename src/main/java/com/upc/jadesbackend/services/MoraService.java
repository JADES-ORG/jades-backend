package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponseMoraClienteDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
import com.upc.jadesbackend.util.CalculoFinancieroUtil.ResultadoMora;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * HU-14: cálculo de intereses por mora.
 *
 * <p>Reglas (Reporte Técnico, sección 2.4):
 * <ul>
 *   <li>Base imponible: se calcula únicamente sobre la cuota vencida exigible, no sobre el saldo total.</li>
 *   <li>Fórmula: {@code Mora = CuotaVencida * ((1 + TEDmora)^dias_atraso - 1)}.</li>
 *   <li>Un pago realizado en plazo (o cuota aún no vencida) no genera mora (0.00).</li>
 *   <li>La mora se presenta en concepto separado: base, tasa, días, mora y total exigible.</li>
 * </ul>
 */
@Service
public class MoraService {

    private final CuotaRepositorio cuotas;
    private final PagoRepositorio pagos;
    private final ClienteRepositorio clientes;
    private final TasaMoratoriaRepositorio tasasMora;
    private final CondicionCreditoRepositorio condiciones;
    private final UsuarioTiendaRepositorio accesos;

    public MoraService(CuotaRepositorio cuotas,
                       PagoRepositorio pagos,
                       ClienteRepositorio clientes,
                       TasaMoratoriaRepositorio tasasMora,
                       CondicionCreditoRepositorio condiciones,
                       UsuarioTiendaRepositorio accesos) {
        this.cuotas = cuotas;
        this.pagos = pagos;
        this.clientes = clientes;
        this.tasasMora = tasasMora;
        this.condiciones = condiciones;
        this.accesos = accesos;
    }

    /**
     * Calcula la mora de una cuota a una fecha de evaluación dada (o la fecha actual si es null).
     */
    @Transactional(readOnly = true)
    public ResponseCalculoMoraDTO calcularMoraCuota(Long idCuota, LocalDate fechaEvaluacion, UsuarioPrincipal principal) {
        Cuota cuota = cuotas.findById(idCuota)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cuota no encontrada"));

        Credito credito = cuota.getIdCredito();
        Venta venta = credito.getIdVenta();
        Cliente cliente = venta.getIdCliente();

        validarAcceso(principal, cliente);

        LocalDate fechaRef = fechaEvaluacion != null ? fechaEvaluacion : LocalDate.now(FacturacionCorteService.ZONA_PERU);
        return construirDetalleMora(cuota, credito, venta, fechaRef);
    }

    /**
     * Lista y consolida todas las obligaciones en mora de un cliente para el administrador de la tienda.
     */
    @Transactional(readOnly = true)
    public ResponseMoraClienteDTO consultarMorasCliente(Long idCliente, LocalDate fechaEvaluacion, Long idUsuarioAdmin) {
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        validarAccesoAdmin(idUsuarioAdmin, cliente.getIdTienda().getId());

        LocalDate fechaRef = fechaEvaluacion != null ? fechaEvaluacion : LocalDate.now(FacturacionCorteService.ZONA_PERU);
        return construirConsolidadoCliente(cliente, fechaRef);
    }

    /**
     * Consulta para el portal del cliente autenticado.
     */
    @Transactional(readOnly = true)
    public ResponseMoraClienteDTO consultarMorasMiCuenta(Long idUsuarioCliente, LocalDate fechaEvaluacion) {
        Cliente cliente = clientes.findByIdUsuario_Id(idUsuarioCliente)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "No existe un cliente vinculado al usuario autenticado"));

        LocalDate fechaRef = fechaEvaluacion != null ? fechaEvaluacion : LocalDate.now(FacturacionCorteService.ZONA_PERU);
        return construirConsolidadoCliente(cliente, fechaRef);
    }

    // ------------------------------------------------------------------
    // Métodos de cálculo
    // ------------------------------------------------------------------

    ResponseCalculoMoraDTO construirDetalleMora(Cuota cuota, Credito credito, Venta venta, LocalDate fechaRef) {
        Optional<Pago> pagoOpt = pagos.findByIdCuota_Id(cuota.getId());
        String moneda = venta.getMonedaVenta() != null ? venta.getMonedaVenta() : "PEN";
        BigDecimal tasaMoraDiaria = obtenerTasaMoraDiaria(credito, venta.getIdCliente());

        if (pagoOpt.filter(p -> p.getFechaPagoEfectivo() != null && !p.getFechaPagoEfectivo().isAfter(fechaRef)).isPresent()) {
            Pago p = pagoOpt.get();
            return new ResponseCalculoMoraDTO(
                    cuota.getId(),
                    cuota.getNumeroCuota(),
                    credito.getId(),
                    credito.getCodigoCredito(),
                    venta.getId(),
                    cuota.getFechaVencimiento(),
                    p.getFechaPagoEfectivo(),
                    p.getDiasAtraso() == null ? 0L : p.getDiasAtraso().longValue(),
                    p.getMoraPagada() != null && p.getMoraPagada().signum() > 0,
                    true,
                    cuota.getImporte(),
                    tasaMoraDiaria,
                    p.getMoraPagada() == null ? BigDecimal.ZERO : p.getMoraPagada(),
                    p.getTotalExigible() == null ? cuota.getImporte() : p.getTotalExigible(),
                    cuota.getEstadoCuota(),
                    moneda
            );
        }

        long dias = CalculoFinancieroUtil.diasAtraso(cuota.getFechaVencimiento(), fechaRef);
        ResultadoMora resultadoMora = CalculoFinancieroUtil.calcularMora(cuota.getImporte(), tasaMoraDiaria, dias);

        boolean enMora = dias > 0 && resultadoMora.interesMoratorio().signum() > 0;
        String estadoCuota = enMora ? "EN_MORA" : "PROGRAMADA";

        return new ResponseCalculoMoraDTO(
                cuota.getId(),
                cuota.getNumeroCuota(),
                credito.getId(),
                credito.getCodigoCredito(),
                venta.getId(),
                cuota.getFechaVencimiento(),
                fechaRef,
                dias,
                enMora,
                false,
                resultadoMora.baseMora(),
                resultadoMora.tasaMoraDiaria(),
                resultadoMora.interesMoratorio(),
                resultadoMora.totalExigible(),
                estadoCuota,
                moneda
        );
    }

    private ResponseMoraClienteDTO construirConsolidadoCliente(Cliente cliente, LocalDate fechaRef) {
        List<Cuota> listaCuotas = cuotas.findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(cliente.getId());
        List<Pago> listaPagos = pagos.findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(cliente.getId());

        Set<Long> cuotasPagadasIds = new HashSet<>();
        for (Pago p : listaPagos) {
            if (p.getIdCuota() != null && p.getFechaPagoEfectivo() != null
                    && !p.getFechaPagoEfectivo().isAfter(fechaRef)) {
                cuotasPagadasIds.add(p.getIdCuota().getId());
            }
        }

        List<ResponseCalculoMoraDTO> cuotasVencidas = new ArrayList<>();
        BigDecimal totalCuotas = BigDecimal.ZERO;
        BigDecimal totalMora = BigDecimal.ZERO;
        BigDecimal totalExigible = BigDecimal.ZERO;
        String moneda = "PEN";

        for (Cuota c : listaCuotas) {
            if (cuotasPagadasIds.contains(c.getId())) {
                continue; // Ya pagada
            }
            if (c.getFechaVencimiento().isBefore(fechaRef)) {
                Credito cred = c.getIdCredito();
                Venta v = cred.getIdVenta();
                moneda = v.getMonedaVenta() != null ? v.getMonedaVenta() : moneda;

                ResponseCalculoMoraDTO detalle = construirDetalleMora(c, cred, v, fechaRef);
                cuotasVencidas.add(detalle);

                totalCuotas = totalCuotas.add(detalle.baseMora());
                totalMora = totalMora.add(detalle.interesMoratorio());
                totalExigible = totalExigible.add(detalle.totalExigible());
            }
        }

        return new ResponseMoraClienteDTO(
                cliente.getId(),
                cliente.getNombreCliente(),
                cliente.getDniCliente(),
                cliente.getIdTienda().getId(),
                fechaRef,
                cuotasVencidas.size(),
                CalculoFinancieroUtil.redondearMoneda(totalCuotas),
                CalculoFinancieroUtil.redondearMoneda(totalMora),
                CalculoFinancieroUtil.redondearMoneda(totalExigible),
                moneda,
                cuotasVencidas
        );
    }

    BigDecimal obtenerTasaMoraDiaria(Credito credito, Cliente cliente) {
        if (credito.getTasaMoraDiariaAplicada() != null) {
            return credito.getTasaMoraDiariaAplicada();
        }
        return condiciones.findByIdCliente_Id(cliente.getId())
                .flatMap(c -> tasasMora.findByIdCondicion_Id(c.getId()))
                .map(m -> CalculoFinancieroUtil.tasaEfectivaDiaria(
                        m.getTipoTasaMora(),
                        m.getTasaMoratoria(),
                        m.getPeriodoTasaMora(),
                        m.getCapitalizacionMora()))
                .orElse(BigDecimal.ZERO);
    }

    private void validarAcceso(UsuarioPrincipal principal, Cliente cliente) {
        if ("CLIENTE".equals(principal.getRol())) {
            boolean esSuya = cliente.getIdUsuario() != null && principal.getId().equals(cliente.getIdUsuario().getId());
            if (!esSuya) {
                throw new NegocioException(HttpStatus.NOT_FOUND, "Cuota no encontrada");
            }
        } else {
            validarAccesoAdmin(principal.getId(), cliente.getIdTienda().getId());
        }
    }

    private void validarAccesoAdmin(Long idUsuarioAdmin, Long idTienda) {
        if (!accesos.tieneAccesoActivo(idUsuarioAdmin, idTienda)) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Información no encontrada en las tiendas autorizadas");
        }
    }
}
