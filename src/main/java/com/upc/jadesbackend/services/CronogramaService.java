package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestSimularCronogramaDTO;
import com.upc.jadesbackend.dtos.ResponseCronogramaDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
import com.upc.jadesbackend.util.CalculoFinancieroUtil.FilaCronograma;
import com.upc.jadesbackend.util.CalculoFinancieroUtil.ResultadoCronograma;
import com.upc.jadesbackend.util.CalendarioCreditoUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * HU-12: generación del cronograma de cuotas.
 * Capitaliza la gracia total y aplica el método francés vencido simple ordinario (meses de 30 días).
 */
@Service
public class CronogramaService {

    public static final ZoneId ZONA_PERU = ZoneId.of("America/Lima");
    private static final LocalTime HORA_CORTE_DEFECTO = LocalTime.of(18, 0);
    private static final String ESTADO_CREDITO_INICIAL = "PENDIENTE";
    private static final String ESTADO_CUOTA_INICIAL = "PROGRAMADA";

    private final VentaRepositorio ventas;
    private final CreditoRepositorio creditos;
    private final CuotaRepositorio cuotas;
    private final CondicionCreditoRepositorio condiciones;
    private final TasaInteresRepositorio tasasInteres;
    private final TasaMoratoriaRepositorio tasasMora;
    private final UsuarioTiendaRepositorio accesos;

    public CronogramaService(VentaRepositorio ventas, CreditoRepositorio creditos, CuotaRepositorio cuotas,
                             CondicionCreditoRepositorio condiciones, TasaInteresRepositorio tasasInteres,
                             TasaMoratoriaRepositorio tasasMora, UsuarioTiendaRepositorio accesos) {
        this.ventas = ventas;
        this.creditos = creditos;
        this.cuotas = cuotas;
        this.condiciones = condiciones;
        this.tasasInteres = tasasInteres;
        this.tasasMora = tasasMora;
        this.accesos = accesos;
    }

    /** Resultado de la generación: {@code creado = false} si la venta ya tenía cronograma (no se duplica). */
    public record GeneracionCronograma(boolean creado, ResponseCronogramaDTO cronograma) {}

    /** Plan calculado en memoria, común a la simulación y a la generación. */
    record Plan(BigDecimal capitalVenta, LocalDate fechaVenta, LocalDate fechaCorte, LocalDate fechaFinGracia,
                long diasGracia, int diaPago, BigDecimal tem, BigDecimal ted, BigDecimal capitalCapitalizado,
                ResultadoCronograma cronograma) {

        LocalDate vencimiento(int numeroCuota) {
            return CalendarioCreditoUtil.fechaVencimiento(fechaFinGracia, numeroCuota, diaPago);
        }
    }

    // ------------------------------------------------------------------
    // Simulación (no guarda nada)
    // ------------------------------------------------------------------

    public ResponseCronogramaDTO simular(RequestSimularCronogramaDTO s) {
        BigDecimal tem = tasaMensual(s.tipoTasa(), s.valorTasa(), s.periodoTasa(), s.capitalizacionTasa());
        Plan plan = construirPlan(s.monto(), tem, s.numeroCuotas(), s.fechaVenta(), s.horaVenta(),
                s.diaCorte(), s.horaCorte(), s.diaPago(), nvl(s.graciaAdicional()));
        String moneda = s.moneda() == null ? "PEN" : s.moneda();
        return desdePlan(null, null, null, moneda, plan);
    }

    // ------------------------------------------------------------------
    // Generación y persistencia
    // ------------------------------------------------------------------

    @Transactional
    public GeneracionCronograma generar(Long idVenta, Long idUsuarioAdmin) {
        Venta venta = ventas.bloquearPorId(idVenta)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        Cliente cliente = venta.getIdCliente();
        validarAccesoAdmin(idUsuarioAdmin, cliente);

        // Verifica que la venta corresponda a un financiamiento mensual.
        // Las compras de pago único utilizan un cálculo financiero diferente.
        if (!"MENSUAL".equals(venta.getModalidadPago())) {
            throw new NegocioException(
                    HttpStatus.BAD_REQUEST,
                    "Esta operación no corresponde a una venta en cuotas mensuales"
            );
        }

        var existente = creditos.findByIdVenta_Id(idVenta);
        if (existente.isPresent()) {
            return new GeneracionCronograma(false, desdeEntidades(venta, existente.get()));
        }

        CondicionCredito condicion = condiciones.findByIdCliente_Id(cliente.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT,
                        "El cliente no tiene condiciones de crédito configuradas"));

        if ("ANULADA".equalsIgnoreCase(venta.getEstadoVenta())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se puede generar cronograma para una venta anulada");
        }

        if (venta.getModalidadPago() != null && "CONTADO".equalsIgnoreCase(venta.getModalidadPago().trim())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se puede generar crédito para una venta con modalidad de pago CONTADO");
        }

        if (condicion.getMonedaCredito() != null && venta.getMonedaVenta() != null
                && !condicion.getMonedaCredito().trim().equalsIgnoreCase(venta.getMonedaVenta().trim())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, String.format(
                    "La moneda de la venta (%s) no coincide con la moneda de la línea de crédito del cliente (%s)",
                    venta.getMonedaVenta(), condicion.getMonedaCredito()));
        }

        BigDecimal montoVenta = CalculoFinancieroUtil.redondearMoneda(venta.getTotalVenta());
        // La venta ya forma parte del utilizado mientras no tenga crédito; no sumarla otra vez.
        BigDecimal deudaProyectada = ventas.sumarCreditoUtilizadoPorCliente(cliente.getId());
        if (deudaProyectada == null) deudaProyectada = BigDecimal.ZERO;
        BigDecimal deudaActual = deudaProyectada.subtract(montoVenta).max(BigDecimal.ZERO);
        if (condicion.getLimiteCredito() != null && deudaProyectada.compareTo(condicion.getLimiteCredito()) > 0) {
            BigDecimal disponible = condicion.getLimiteCredito().subtract(deudaActual);
            if (disponible.signum() < 0) disponible = BigDecimal.ZERO;
            throw new NegocioException(HttpStatus.BAD_REQUEST, String.format(
                    "La venta (S/ %.2f) supera la línea de crédito disponible del cliente. Límite: S/ %.2f, Saldo actual: S/ %.2f, Disponible: S/ %.2f",
                    montoVenta, condicion.getLimiteCredito(), deudaActual, disponible));
        }

        TasaInteres tasa = tasasInteres.findByIdCondicion_Id(condicion.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT,
                        "El cliente no tiene una tasa de interés configurada"));

        int numeroCuotas = venta.getNumeroCuotas() == null ? 0 : venta.getNumeroCuotas();
        if (numeroCuotas < 1) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La venta debe tener al menos una cuota");
        }
        if (condicion.getPlazoMaximo() != null && numeroCuotas > condicion.getPlazoMaximo()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El número de cuotas supera el plazo máximo del cliente (" + condicion.getPlazoMaximo() + ")");
        }

        BigDecimal tem = tasaMensual(tasa.getTipoTasa(), tasa.getTasaInteres(), tasa.getPeriodoTasa(),
                tasa.getCapitalizacionTasa());
        var moraPactada = tasasMora.findByIdCondicion_Id(condicion.getId());
        BigDecimal tedMora = moraPactada
                .map(m -> tasaDiaria(m.getTipoTasaMora(), m.getTasaMoratoria(), m.getPeriodoTasaMora(),
                        m.getCapitalizacionMora()))
                .orElse(BigDecimal.ZERO);

        LocalTime horaCorte = condicion.getHoraCorte() == null ? HORA_CORTE_DEFECTO : condicion.getHoraCorte();
        Plan plan = construirPlan(venta.getTotalVenta(), tem, numeroCuotas, venta.getFechaVenta(),
                venta.getHoraVenta(), condicion.getDiaCorte(), horaCorte, condicion.getDiaPago(),
                nvl(venta.getGraciaAdicional()));

        ResultadoCronograma r = plan.cronograma();
        Credito credito = new Credito();
        credito.setIdVenta(venta);
        credito.setCondicionesAplicadas(new CondicionesAplicadas(condicion, tasa, moraPactada.orElse(null),
                plan.fechaFinGracia(), plan.diasGracia()));
        credito.setCodigoCredito(String.format("CR-%06d", venta.getId()));
        credito.setCapitalVenta(CalculoFinancieroUtil.redondearMoneda(plan.capitalVenta()));
        credito.setCapitalCapitalizado(plan.capitalCapitalizado());
        credito.setCuotaMensual(r.cuotaBase());
        credito.setInteresesTotales(r.interesesTotales());
        credito.setTotalFinanciamiento(r.totalFinanciamiento());
        credito.setSaldoCredito(plan.capitalCapitalizado());
        credito.setFechaCorte(LocalDateTime.of(plan.fechaCorte(), horaCorte).atZone(ZONA_PERU).toInstant());
        credito.setFechaPrimerPago(plan.vencimiento(1));
        credito.setEstadoCredito(ESTADO_CREDITO_INICIAL);
        credito.setTasaEfectivaDiariaAplicada(plan.ted());
        credito.setTasaMoraDiariaAplicada(tedMora);
        credito = creditos.save(credito);

        List<Cuota> nuevas = new ArrayList<>();
        for (FilaCronograma f : r.filas()) {
            Cuota c = new Cuota();
            c.setIdCredito(credito);
            c.setNumeroCuota(f.numeroCuota());
            c.setFechaVencimiento(plan.vencimiento(f.numeroCuota()));
            c.setSaldoInicial(f.saldoInicial());
            c.setInteresCuota(f.interes());
            c.setAmortizacion(f.amortizacion());
            c.setImporte(f.cuota());
            c.setSaldoFinal(f.saldoFinal());
            c.setEstadoCuota(ESTADO_CUOTA_INICIAL);
            nuevas.add(c);
        }
        cuotas.saveAll(nuevas);

        return new GeneracionCronograma(true,
                desdePlan(credito.getId(), credito.getCodigoCredito(), venta.getId(), venta.getMonedaVenta(), plan));
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResponseCronogramaDTO consultar(Long idVenta, UsuarioPrincipal principal) {
        Venta venta = ventas.findById(idVenta)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        Cliente cliente = venta.getIdCliente();

        if ("CLIENTE".equals(principal.getRol())) {
            boolean esSuya = cliente.getIdUsuario() != null && principal.getId().equals(cliente.getIdUsuario().getId());
            if (!esSuya) {
                throw new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada");
            }
        } else {
            validarAccesoAdmin(principal.getId(), cliente);
        }

        Credito credito = creditos.findByIdVenta_Id(idVenta)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "La venta aún no tiene cronograma generado"));
        return desdeEntidades(venta, credito);
    }

    // ------------------------------------------------------------------
    // Cálculo
    // ------------------------------------------------------------------

    Plan construirPlan(BigDecimal monto, BigDecimal tem, int numeroCuotas, LocalDate fechaVenta, LocalTime horaVenta,
                       int diaCorte, LocalTime horaCorte, int diaPago, int graciaAdicional) {
        LocalTime corteHora = horaCorte == null ? HORA_CORTE_DEFECTO : horaCorte;
        LocalDate fechaCorte = CalendarioCreditoUtil.fechaCorte(fechaVenta, horaVenta, diaCorte, corteHora);
        LocalDate finGracia = CalendarioCreditoUtil.fechaFinGracia(fechaCorte, diaCorte, diaPago, graciaAdicional);
        LocalDate primerDiaPago = CalendarioCreditoUtil.fechaFinGracia(fechaCorte, diaCorte, diaPago, 0);
        long diasGracia = ChronoUnit.DAYS.between(fechaVenta, primerDiaPago) + 30L * graciaAdicional;

        BigDecimal capitalCapitalizado = CalculoFinancieroUtil.capitalizarGracia(monto, tem, diasGracia);
        ResultadoCronograma cronograma = CalculoFinancieroUtil.generarCronograma(capitalCapitalizado, tem, numeroCuotas, null);
        BigDecimal ted = CalculoFinancieroUtil.convertirTasaEfectiva(tem, CalculoFinancieroUtil.BASE_MENSUAL, 1);

        return new Plan(monto, fechaVenta, fechaCorte, finGracia, diasGracia, diaPago, tem, ted,
                capitalCapitalizado, cronograma);
    }

    private ResponseCronogramaDTO desdePlan(Long idCredito, String codigo, Long idVenta, String moneda, Plan plan) {
        ResultadoCronograma r = plan.cronograma();
        List<ResponseCronogramaDTO.CuotaCronograma> filas = r.filas().stream()
                .map(f -> new ResponseCronogramaDTO.CuotaCronograma(f.numeroCuota(), plan.vencimiento(f.numeroCuota()),
                        f.saldoInicial(), f.interes(), f.amortizacion(), f.cuota(), f.saldoFinal(), ESTADO_CUOTA_INICIAL))
                .toList();
        BigDecimal capitalVenta = CalculoFinancieroUtil.redondearMoneda(plan.capitalVenta());

        return new ResponseCronogramaDTO(idCredito, codigo, idVenta, moneda, plan.fechaVenta(), plan.fechaCorte(),
                plan.fechaFinGracia(), plan.diasGracia(), plan.tem(), plan.ted(), capitalVenta,
                plan.capitalCapitalizado().subtract(capitalVenta), plan.capitalCapitalizado(), filas.size(),
                r.cuotaBase(), r.ajusteUltimaCuota(), r.interesesTotales(), r.totalFinanciamiento(), filas);
    }

    private ResponseCronogramaDTO desdeEntidades(Venta venta, Credito credito) {
        List<Cuota> lista = cuotas.findAllByIdCredito_IdOrderByNumeroCuotaAsc(credito.getId());
        List<ResponseCronogramaDTO.CuotaCronograma> filas = lista.stream()
                .map(c -> new ResponseCronogramaDTO.CuotaCronograma(c.getNumeroCuota(), c.getFechaVencimiento(),
                        c.getSaldoInicial(), c.getInteresCuota(), c.getAmortizacion(), c.getImporte(),
                        c.getSaldoFinal(), c.getEstadoCuota()))
                .toList();

        CondicionesAplicadas pactadas = credito.getCondicionesAplicadas();
        LocalDate finGracia = pactadas != null && pactadas.getFinGracia() != null
                ? pactadas.getFinGracia() : credito.getFechaPrimerPago().minusMonths(1);
        long diasGracia = pactadas != null && pactadas.getDiasGracia() != null
                ? pactadas.getDiasGracia() : ChronoUnit.DAYS.between(venta.getFechaVenta(), finGracia);
        BigDecimal ted = credito.getTasaEfectivaDiariaAplicada();
        BigDecimal tem = CalculoFinancieroUtil.convertirTasaEfectiva(ted, 1, CalculoFinancieroUtil.BASE_MENSUAL);
        BigDecimal ajuste = lista.isEmpty() || credito.getCuotaMensual() == null
                ? BigDecimal.ZERO
                : lista.get(lista.size() - 1).getImporte().subtract(credito.getCuotaMensual());

        return new ResponseCronogramaDTO(credito.getId(), credito.getCodigoCredito(), venta.getId(),
                venta.getMonedaVenta(), venta.getFechaVenta(),
                LocalDate.ofInstant(credito.getFechaCorte(), ZONA_PERU), finGracia,
                diasGracia, tem, ted, credito.getCapitalVenta(),
                credito.getCapitalCapitalizado().subtract(credito.getCapitalVenta()), credito.getCapitalCapitalizado(),
                filas.size(), credito.getCuotaMensual(), ajuste, credito.getInteresesTotales(),
                credito.getTotalFinanciamiento(), filas);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void validarAccesoAdmin(Long idUsuarioAdmin, Cliente cliente) {
        if (!accesos.tieneAccesoActivo(idUsuarioAdmin, cliente.getIdTienda().getId())) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada en las tiendas autorizadas");
        }
    }

    private static BigDecimal tasaMensual(String tipo, BigDecimal valor, String periodo, String capitalizacion) {
        try {
            return CalculoFinancieroUtil.tasaEfectivaMensual(tipo, valor, periodo, capitalizacion);
        } catch (IllegalArgumentException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static BigDecimal tasaDiaria(String tipo, BigDecimal valor, String periodo, String capitalizacion) {
        try {
            return CalculoFinancieroUtil.tasaEfectivaDiaria(tipo, valor, periodo, capitalizacion);
        } catch (IllegalArgumentException e) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Tasa moratoria inválida: " + e.getMessage());
        }
    }

    private static int nvl(Integer valor) {
        return valor == null ? 0 : valor;
    }
}
