package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestRegistrarPagoDTO;
import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponseLiquidacionDTO;
import com.upc.jadesbackend.dtos.ResponseLiquidacionDTO.Detalle;
import com.upc.jadesbackend.dtos.ResponsePagoDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
import com.upc.jadesbackend.util.CalendarioCreditoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final ZoneId ZONA_PERU = ZoneId.of("America/Lima");

    private final PagoRepositorio pagos;
    private final CuotaRepositorio cuotas;
    private final CreditoRepositorio creditos;
    private final MoraService moraService;
    private final AccesoNegocioService acceso;

    public record RegistroPago(boolean creado, ResponsePagoDTO resultado) {}

    private record Importe(Cuota cuota, BigDecimal capital, BigDecimal interes,
                           BigDecimal mora, long diasAtraso) {
        BigDecimal total() {
            return capital.add(interes).add(mora);
        }
    }

    @Transactional(readOnly = true)
    public ResponseLiquidacionDTO consultarImporte(Long idCuota, LocalDate fechaPago, LocalTime horaPago, UsuarioPrincipal usuario) {
        Cuota cuota = buscarCuota(idCuota);
        Credito credito = cuota.getIdCredito();
        validarConsulta(credito, usuario);
        LocalDateTime momento = fechaReferencia(fechaPago, horaPago);
        LocalDate fecha = momento.toLocalDate();
        validarFecha(credito, momento);
        List<Cuota> pendientes = pendientes(credito, momento);
        validarCuotaIndividual(cuota, credito, pendientes, fecha);
        validarHoraCorte(cuota, momento);
        return resumen(credito, momento, List.of(calcularImporte(cuota, credito, fecha, false)));
    }

    @Transactional
    public RegistroPago registrar(Long idCuota, RequestRegistrarPagoDTO solicitud, UsuarioPrincipal usuario) {
        Cuota cuota = buscarCuota(idCuota);
        Credito credito = bloquearCredito(cuota.getIdCredito().getId());
        acceso.exigirParaCobro(credito.getIdVenta().getIdCliente().getIdTienda().getId(), usuario);
        String medioPago = normalizarMedio(solicitud.medioPago());
        var existente = pagos.findByIdCuota_Id(idCuota);

        if (existente.isPresent()) {
            Pago anterior = existente.get();
            if (!anterior.getFechaPagoEfectivo().equals(solicitud.fechaPagoEfectivo())
                    || (solicitud.horaPagoEfectivo() != null && !solicitud.horaPagoEfectivo().truncatedTo(ChronoUnit.SECONDS).equals(anterior.getHoraPagoEfectivo()))
                    || !anterior.getMedioPago().equals(medioPago)
                    || anterior.getTotalExigible().compareTo(solicitud.monto()) != 0) {
                throw new NegocioException(HttpStatus.CONFLICT, "Esta obligación ya tiene un pago registrado");
            }
            return new RegistroPago(false, construirRespuesta(anterior, credito));
        }

        LocalDateTime momento = fechaReferencia(solicitud.fechaPagoEfectivo(), solicitud.horaPagoEfectivo());
        LocalDate fecha = momento.toLocalDate();
        validarFecha(credito, momento);
        List<Cuota> pendientes = pendientes(credito, momento);
        validarCuotaIndividual(cuota, credito, pendientes, fecha);
        validarHoraCorte(cuota, momento);

        Importe importe = calcularImporte(cuota, credito, fecha, false);
        validarMonto(solicitud.monto(), importe.total(), credito);
        Pago pago = registrarImporte(credito, importe, momento, medioPago);
        actualizarCredito(credito, importe.capital(), pendientes.size() == 1);

        return new RegistroPago(true, construirRespuesta(pago, credito));
    }

    @Transactional(readOnly = true)
    public ResponseLiquidacionDTO consultarLiquidacion(Long idCredito, LocalDate fechaPago, LocalTime horaPago, UsuarioPrincipal usuario) {
        Credito credito = creditos.findById(idCredito).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Crédito no encontrado"));
        validarConsulta(credito, usuario);
        LocalDateTime momento = fechaReferencia(fechaPago, horaPago);
        List<Importe> importes = calcularLiquidacion(credito, momento);
        return resumen(credito, momento, importes);
    }

    @Transactional
    public ResponseLiquidacionDTO liquidar(Long idCredito, RequestRegistrarPagoDTO solicitud, UsuarioPrincipal usuario) {
        Credito credito = bloquearCredito(idCredito);
        acceso.exigirParaCobro(credito.getIdVenta().getIdCliente().getIdTienda().getId(), usuario);
        LocalDateTime momento = fechaReferencia(solicitud.fechaPagoEfectivo(), solicitud.horaPagoEfectivo());
        List<Importe> importes = calcularLiquidacion(credito, momento);
        ResponseLiquidacionDTO resultado = resumen(credito, momento, importes);
        validarMonto(solicitud.monto(), resultado.totalPagar(), credito);

        String medio = normalizarMedio(solicitud.medioPago());
        BigDecimal capital = BigDecimal.ZERO;
        for (Importe importe : importes) {
            registrarImporte(credito, importe, momento, medio);
            capital = capital.add(importe.capital());
        }
        actualizarCredito(credito, capital, true);
        return resultado;
    }

    private Cuota buscarCuota(Long idCuota) {
        return cuotas.findById(idCuota).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Cuota no encontrada"));
    }

    private Credito bloquearCredito(Long idCredito) {
        return creditos.bloquearPorId(idCredito).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Crédito no encontrado"));
    }

    private void validarConsulta(Credito credito, UsuarioPrincipal usuario) {
        Cliente cliente = credito.getIdVenta().getIdCliente();
        if ("CLIENTE".equals(usuario.getRol())) {
            if (cliente.getIdUsuario() == null || !cliente.getIdUsuario().getId().equals(usuario.getId()))
                throw new NegocioException(HttpStatus.NOT_FOUND, "Crédito no encontrado");
        } else {
            acceso.exigirParaCobro(cliente.getIdTienda().getId(), usuario);
        }
    }

    private LocalDateTime fechaReferencia(LocalDate fecha, LocalTime hora) {
        LocalDateTime ahora = LocalDateTime.now(ZONA_PERU).truncatedTo(ChronoUnit.SECONDS);
        LocalDate dia = fecha == null ? ahora.toLocalDate() : fecha;
        if (hora == null && !dia.equals(ahora.toLocalDate()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Indica la hora efectiva para un pago de otra fecha");
        return LocalDateTime.of(dia, hora == null ? ahora.toLocalTime() : hora.truncatedTo(ChronoUnit.SECONDS));
    }

    private void validarFecha(Credito credito, LocalDateTime momento) {
        Venta venta = credito.getIdVenta();
        if (momento.isAfter(LocalDateTime.now(ZONA_PERU))
                || momento.toLocalDate().isBefore(venta.getFechaVenta())
                || (momento.toLocalDate().equals(venta.getFechaVenta()) && venta.getHoraVenta() != null
                    && momento.toLocalTime().isBefore(venta.getHoraVenta())))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La fecha y hora efectivas del pago no son válidas");
        if ("ANULADA".equalsIgnoreCase(venta.getEstadoVenta()))
            throw new NegocioException(HttpStatus.CONFLICT, "La venta está anulada");
    }

    private List<Cuota> pendientes(Credito credito, LocalDateTime momento) {
        LocalDate fecha = momento.toLocalDate();
        List<Cuota> lista = cuotas.findAllByIdCredito_IdOrderByNumeroCuotaAsc(credito.getId());
        List<Cuota> resultado = new ArrayList<>();
        BigDecimal capitalPendiente = BigDecimal.ZERO;

        for (Cuota cuota : lista) {
            var pago = pagos.findByIdCuota_Id(cuota.getId());
            if (pago.isPresent()) {
                if (pago.get().getFechaPagoEfectivo().isAfter(fecha)
                        || (pago.get().getFechaPagoEfectivo().equals(fecha) && pago.get().getHoraPagoEfectivo() != null
                        && pago.get().getHoraPagoEfectivo().isAfter(momento.toLocalTime())))
                    throw new NegocioException(HttpStatus.BAD_REQUEST, "La fecha no puede ser anterior a un pago ya registrado en el crédito");
                if (!"PAGADA".equals(cuota.getEstadoCuota()))
                    throw new NegocioException(HttpStatus.CONFLICT, "El estado de una cuota no coincide con su pago");
            } else {
                if ("PAGADA".equals(cuota.getEstadoCuota()))
                    throw new NegocioException(HttpStatus.CONFLICT, "Falta el pago registrado de una cuota cancelada");
                resultado.add(cuota);
                capitalPendiente = capitalPendiente.add(cuota.getAmortizacion());
            }
        }

        if (resultado.isEmpty() || "CANCELADO".equals(credito.getEstadoCredito()))
            throw new NegocioException(HttpStatus.CONFLICT, "El crédito no tiene obligaciones pendientes");
        if (capitalPendiente.compareTo(credito.getSaldoCredito()) != 0)
            throw new NegocioException(HttpStatus.CONFLICT, "El saldo del crédito no coincide con las cuotas pendientes");

        return resultado;
    }

    private void validarCuotaIndividual(Cuota cuota, Credito credito, List<Cuota> pendientes, LocalDate fecha) {
        if (pendientes.stream().noneMatch(c -> c.getId().equals(cuota.getId())))
            throw new NegocioException(HttpStatus.CONFLICT, "La obligación ya se encuentra cancelada");

        if ("MENSUAL".equals(credito.getIdVenta().getModalidadPago())) {
            if (!pendientes.getFirst().getId().equals(cuota.getId()))
                throw new NegocioException(HttpStatus.BAD_REQUEST, "Debes cancelar primero la cuota pendiente más antigua");
            if (YearMonth.from(cuota.getFechaVencimiento()).isAfter(YearMonth.from(fecha)))
                throw new NegocioException(HttpStatus.BAD_REQUEST, "Solo se permite adelantar la cuota del mes correspondiente");
        }
    }

    private void validarHoraCorte(Cuota cuota, LocalDateTime momento) {
        LocalDateTime corte = CalendarioCreditoUtil.corteHistoricoCuota(cuota);
        if (corte == null)
            throw new NegocioException(HttpStatus.CONFLICT, "Falta el calendario histórico de la obligación");
        if (corte.toLocalDate().equals(cuota.getFechaVencimiento()) && !momento.isAfter(corte))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El pago se habilita después de la hora de corte pactada");
    }

    private List<Importe> calcularLiquidacion(Credito credito, LocalDateTime momento) {
        LocalDate fecha = momento.toLocalDate();
        validarFecha(credito, momento);
        if (!"MENSUAL".equals(credito.getIdVenta().getModalidadPago()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La liquidación total corresponde a créditos en cuotas");
        if (credito.getFechaPrimerPago() == null || YearMonth.from(fecha).isBefore(YearMonth.from(credito.getFechaPrimerPago())))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La liquidación está disponible desde el mes de la primera cuota");

        List<Importe> resultado = new ArrayList<>();
        for (Cuota cuota : pendientes(credito, momento)) {
            boolean futura = YearMonth.from(cuota.getFechaVencimiento()).isAfter(YearMonth.from(fecha));
            if (!futura) validarHoraCorte(cuota, momento);
            resultado.add(calcularImporte(cuota, credito, fecha, futura));
        }
        return resultado;
    }

    private Importe calcularImporte(Cuota cuota, Credito credito, LocalDate fecha, boolean soloCapital) {
        BigDecimal capital = cuota.getAmortizacion();
        BigDecimal interes = cuota.getInteresCuota();

        if (capital.signum() < 0 || interes.signum() < 0
                || capital.add(interes).compareTo(cuota.getImporte()) != 0)
            throw new NegocioException(HttpStatus.CONFLICT, "Los importes de la cuota no son consistentes");

        if (soloCapital) return new Importe(cuota, capital, BigDecimal.ZERO, BigDecimal.ZERO, 0);

        Venta venta = credito.getIdVenta();
        if ("UNICO".equals(venta.getModalidadPago()) && fecha.isBefore(cuota.getFechaVencimiento())) {
            BigDecimal ted = credito.getTasaEfectivaDiariaAplicada();
            if (ted == null || ted.signum() < 0)
                throw new NegocioException(HttpStatus.CONFLICT, "Falta la tasa compensatoria histórica del crédito");
            int dias = Math.toIntExact(ChronoUnit.DAYS.between(venta.getFechaVenta(), fecha));
            BigDecimal tasaPeriodo = CalculoFinancieroUtil.convertirTasaEfectiva(ted, 1, dias);
            interes = CalculoFinancieroUtil.redondearMoneda(capital.multiply(tasaPeriodo));
            return new Importe(cuota, capital, interes, BigDecimal.ZERO, 0);
        }

        ResponseCalculoMoraDTO mora = moraService.construirDetalleMora(cuota, credito, venta, fecha);
        return new Importe(cuota, capital, interes, mora.interesMoratorio(), mora.diasAtraso());
    }

    private ResponseLiquidacionDTO resumen(Credito credito, LocalDateTime momento, List<Importe> importes) {
        BigDecimal capital = BigDecimal.ZERO;
        BigDecimal interes = BigDecimal.ZERO;
        BigDecimal mora = BigDecimal.ZERO;
        List<Detalle> detalles = new ArrayList<>();

        for (Importe i : importes) {
            capital = capital.add(i.capital());
            interes = interes.add(i.interes());
            mora = mora.add(i.mora());
            detalles.add(new Detalle(i.cuota().getId(), i.cuota().getNumeroCuota(),
                    i.cuota().getFechaVencimiento(), i.capital(), i.interes(), i.mora(), i.diasAtraso(), i.total()));
        }

        return new ResponseLiquidacionDTO(credito.getId(), credito.getIdVenta().getId(),
                credito.getIdVenta().getMonedaVenta(), momento.toLocalDate(), momento.toLocalTime(), capital, interes, mora,
                capital.add(interes).add(mora), credito.getSaldoCredito().subtract(capital), detalles);
    }

    private void validarMonto(BigDecimal recibido, BigDecimal esperado, Credito credito) {
        if (recibido == null || recibido.compareTo(esperado) != 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El importe debe coincidir con el total exigible de "
                    + credito.getIdVenta().getMonedaVenta() + " " + CalculoFinancieroUtil.redondearMoneda(esperado));
    }

    private String normalizarMedio(String medioPago) {
        return medioPago.trim().toUpperCase(Locale.ROOT);
    }

    private Pago registrarImporte(Credito credito, Importe importe, LocalDateTime momento, String medio) {
        LocalDate fecha = momento.toLocalDate();
        Cuota cuota = importe.cuota();
        BigDecimal restante = importe.total();
        BigDecimal moraPagada = restante.min(importe.mora());
        restante = restante.subtract(moraPagada);
        BigDecimal interesPagado = restante.min(importe.interes());
        restante = restante.subtract(interesPagado);
        BigDecimal capitalPagado = restante.min(importe.capital());
        restante = restante.subtract(capitalPagado);

        if (restante.signum() != 0 || capitalPagado.compareTo(importe.capital()) != 0
                || interesPagado.compareTo(importe.interes()) != 0 || moraPagada.compareTo(importe.mora()) != 0)
            throw new NegocioException(HttpStatus.CONFLICT, "No fue posible distribuir correctamente el pago");

        Pago pago = new Pago();
        pago.setIdCuota(cuota);
        pago.setFechaPagoEfectivo(fecha);
        pago.setHoraPagoEfectivo(momento.toLocalTime());
        pago.setFechaCancelacion(fecha);
        pago.setDiasAtraso(Math.toIntExact(importe.diasAtraso()));
        pago.setMedioPago(medio);
        pago.setMoraPagada(moraPagada);
        pago.setInteresPagado(interesPagado);
        pago.setCapitalPagado(capitalPagado);
        pago.setTotalExigible(importe.total());
        pago.setEstadoPago("REGISTRADO");
        pagos.saveAndFlush(pago);
        cuota.setEstadoCuota("PAGADA");
        cuotas.save(cuota);
        return pago;
    }

    private void actualizarCredito(Credito credito, BigDecimal capital, boolean finalizar) {
        BigDecimal saldo = credito.getSaldoCredito().subtract(capital);
        if (saldo.signum() < 0 || (finalizar && saldo.signum() != 0))
            throw new NegocioException(HttpStatus.CONFLICT, "El saldo del crédito no coincide con los pagos realizados");
        credito.setSaldoCredito(saldo);
        if (finalizar) credito.setEstadoCredito("CANCELADO");
        creditos.save(credito);
    }

    private ResponsePagoDTO construirRespuesta(Pago pago, Credito credito) {
        Cuota cuota = pago.getIdCuota();
        return new ResponsePagoDTO(
                pago.getId(), cuota.getId(), credito.getId(), credito.getIdVenta().getId(),
                credito.getCodigoCredito(), credito.getIdVenta().getMonedaVenta(),
                cuota.getFechaVencimiento(), pago.getFechaPagoEfectivo(), pago.getHoraPagoEfectivo(), pago.getDiasAtraso(),
                pago.getMedioPago(), pago.getMoraPagada(), pago.getInteresPagado(),
                pago.getCapitalPagado(), pago.getTotalExigible(), credito.getSaldoCredito(),
                cuota.getEstadoCuota(), credito.getEstadoCredito());
    }
}
