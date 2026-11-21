package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponsePagoUnicoDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/*
 * HU-11: Cálculo de deuda de pago único.
 * Calcula los intereses compensatorios según la tasa pactada
 * y registra la obligación financiera del cliente.
 */
@Service
@RequiredArgsConstructor
public class PagoUnicoService {

    private static final ZoneId ZONA_PERU = ZoneId.of("America/Lima");

    private final VentaRepositorio ventas;
    private final CreditoRepositorio creditos;
    private final CuotaRepositorio cuotas;
    private final CondicionCreditoRepositorio condiciones;
    private final TasaInteresRepositorio tasasInteres;
    private final TasaMoratoriaRepositorio tasasMora;
    private final AccesoNegocioService acceso;

    public record Generacion(
            boolean creado,
            ResponsePagoUnicoDTO resultado
    ) {}

    @Transactional
    public Generacion generar(Long idVenta, UsuarioPrincipal usuario) {

        Venta venta = ventas.bloquearPorId(idVenta)
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.NOT_FOUND, "Venta no encontrada"));

        acceso.exigir(venta.getIdCliente().getIdTienda().getId(), usuario);
        validarVenta(venta);

        var existente = creditos.findByIdVenta_Id(idVenta);

        if (existente.isPresent()) {
            return new Generacion(false, construirRespuesta(venta, existente.get()));
        }

        CondicionCredito condicion = condiciones
                .findByIdCliente_Id(venta.getIdCliente().getId())
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.CONFLICT,
                        "El cliente no tiene condiciones crediticias"));

        TasaInteres tasa = tasasInteres
                .findByIdCondicion_Id(condicion.getId())
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.CONFLICT,
                        "El cliente no tiene tasa de interés configurada"));

        CalculoFinancieroUtil.ResultadoPagoUnico resultado;
        BigDecimal tedMora;
        var moraPactada = tasasMora.findByIdCondicion_Id(condicion.getId());

        try {
            resultado = CalculoFinancieroUtil.calcularPagoUnico(
                    venta.getTotalVenta(),
                    venta.getFechaVenta(),
                    venta.getFechaPagoAsignada(),
                    tasa.getTipoTasa(),
                    tasa.getTasaInteres(),
                    tasa.getPeriodoTasa(),
                    tasa.getCapitalizacionTasa()
            );

            tedMora = moraPactada
                    .map(m -> CalculoFinancieroUtil.tasaEfectivaDiaria(
                            m.getTipoTasaMora(),
                            m.getTasaMoratoria(),
                            m.getPeriodoTasaMora(),
                            m.getCapitalizacionMora()
                    ))
                    .orElse(BigDecimal.ZERO);

        } catch (IllegalArgumentException ex) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        Credito credito = new Credito();
        credito.setIdVenta(venta);
        credito.setCondicionesAplicadas(new CondicionesAplicadas(condicion, tasa, moraPactada.orElse(null), null, null));
        credito.setCodigoCredito(String.format("CR-%06d", venta.getId()));
        credito.setCapitalVenta(resultado.capital());
        credito.setCapitalCapitalizado(resultado.capital());
        credito.setTotalFinanciamiento(resultado.totalPagar());
        credito.setSaldoCredito(resultado.capital());
        credito.setInteresesTotales(resultado.interesCompensatorio());
        credito.setFechaCorte(
                venta.getFechaCorteAsignada().atZone(ZONA_PERU).toInstant()
        );
        credito.setFechaPrimerPago(venta.getFechaPagoAsignada());
        credito.setEstadoCredito("PENDIENTE");
        credito.setTasaEfectivaDiariaAplicada(resultado.tasaEfectivaDiaria());
        credito.setTasaMoraDiariaAplicada(tedMora);

        credito = creditos.saveAndFlush(credito);

        Cuota cuota = new Cuota();
        cuota.setIdCredito(credito);
        cuota.setNumeroCuota(1);
        cuota.setFechaVencimiento(venta.getFechaPagoAsignada());
        cuota.setEstadoCuota("PROGRAMADA");
        cuota.setSaldoInicial(resultado.capital());
        cuota.setInteresCuota(resultado.interesCompensatorio());
        cuota.setAmortizacion(resultado.capital());
        cuota.setImporte(resultado.totalPagar());
        cuota.setSaldoFinal(new BigDecimal("0.00"));

        cuotas.saveAndFlush(cuota);

        return new Generacion(true, construirRespuesta(venta, credito));
    }

    @Transactional(readOnly = true)
    public ResponsePagoUnicoDTO consultar(
            Long idVenta,
            UsuarioPrincipal usuario) {

        Venta venta = ventas.findById(idVenta)
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.NOT_FOUND, "Venta no encontrada"));

        if ("CLIENTE".equals(usuario.getRol())) {
            if (venta.getIdCliente().getIdUsuario() == null
                    || !venta.getIdCliente().getIdUsuario().getId()
                    .equals(usuario.getId())) {
                throw new NegocioException(
                        HttpStatus.NOT_FOUND, "Venta no encontrada");
            }
        } else {
            acceso.exigir(venta.getIdCliente().getIdTienda().getId(), usuario);
        }

        validarModalidad(venta);

        Credito credito = creditos.findByIdVenta_Id(idVenta)
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "La venta no tiene deuda de pago único generada"));

        return construirRespuesta(venta, credito);
    }

    private void validarVenta(Venta venta) {
        validarModalidad(venta);

        if ("ANULADA".equalsIgnoreCase(venta.getEstadoVenta())) {
            throw new NegocioException(
                    HttpStatus.BAD_REQUEST, "La venta está anulada");
        }

        if (venta.getFechaCorteAsignada() == null
                || venta.getFechaPagoAsignada() == null) {
            throw new NegocioException(
                    HttpStatus.CONFLICT,
                    "La venta no tiene fechas de corte y pago asignadas");
        }
    }

    private void validarModalidad(Venta venta) {
        if (!"UNICO".equals(venta.getModalidadPago())
                || !Integer.valueOf(1).equals(venta.getNumeroCuotas())) {
            throw new NegocioException(
                    HttpStatus.BAD_REQUEST,
                    "La venta no corresponde a pago único");
        }
    }

    private ResponsePagoUnicoDTO construirRespuesta(
            Venta venta,
            Credito credito) {

        List<Cuota> lista = cuotas
                .findAllByIdCredito_IdOrderByNumeroCuotaAsc(credito.getId());

        if (lista.size() != 1
                || !credito.getFechaPrimerPago()
                .equals(venta.getFechaPagoAsignada())) {
            throw new NegocioException(
                    HttpStatus.CONFLICT,
                    "La obligación guardada no coincide con el pago único");
        }

        Cuota cuota = lista.get(0);

        long dias = ChronoUnit.DAYS.between(
                venta.getFechaVenta(),
                credito.getFechaPrimerPago()
        );

        BigDecimal tasaPeriodo = CalculoFinancieroUtil.convertirTasaEfectiva(
                credito.getTasaEfectivaDiariaAplicada(),
                1,
                Math.toIntExact(dias)
        );

        return new ResponsePagoUnicoDTO(
                venta.getId(),
                credito.getId(),
                cuota.getId(),
                credito.getCodigoCredito(),
                venta.getMonedaVenta(),
                venta.getFechaVenta(),
                venta.getFechaCorteAsignada(),
                cuota.getFechaVencimiento(),
                dias,
                credito.getCapitalVenta(),
                credito.getTasaEfectivaDiariaAplicada(),
                tasaPeriodo,
                cuota.getInteresCuota(),
                cuota.getImporte(),
                cuota.getEstadoCuota()
        );
    }
}
