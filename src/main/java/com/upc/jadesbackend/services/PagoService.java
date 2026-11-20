package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestRegistrarPagoDTO;
import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponsePagoDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/*
 * HU-15: Registro de pagos de obligaciones financieras.
 * Valida el pago total, calcula la mora, aplica el orden
 * de imputación y actualiza la cuota y el saldo del crédito.
 */
@Service
@RequiredArgsConstructor
public class PagoService {

    private static final ZoneId ZONA_PERU = ZoneId.of("America/Lima");

    private final PagoRepositorio pagos;
    private final CuotaRepositorio cuotas;
    private final CreditoRepositorio creditos;
    private final MoraService moraService;
    private final AccesoNegocioService acceso;

    public record RegistroPago(
            boolean creado,
            ResponsePagoDTO resultado
    ) {}

    @Transactional
    public RegistroPago registrar(
            Long idCuota,
            RequestRegistrarPagoDTO solicitud,
            UsuarioPrincipal usuario) {

        Cuota cuota = cuotas.findById(idCuota)
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "Cuota no encontrada"
                ));

        Credito credito = creditos.bloquearPorId(
                cuota.getIdCredito().getId()
        ).orElseThrow(() -> new NegocioException(
                HttpStatus.NOT_FOUND,
                "Crédito no encontrado"
        ));

        Venta venta = credito.getIdVenta();

        acceso.exigir(
                venta.getIdCliente().getIdTienda().getId(),
                usuario
        );

        String medioPago = solicitud.medioPago()
                .trim()
                .toUpperCase(Locale.ROOT);

        var pagoExistente = pagos.findByIdCuota_Id(idCuota);

        if (pagoExistente.isPresent()) {
            Pago anterior = pagoExistente.get();

            boolean mismaOperacion =
                    anterior.getFechaPagoEfectivo()
                            .equals(solicitud.fechaPagoEfectivo())
                            && anterior.getMedioPago().equals(medioPago)
                            && anterior.getTotalExigible()
                            .compareTo(solicitud.monto()) == 0;

            if (!mismaOperacion) {
                throw new NegocioException(
                        HttpStatus.CONFLICT,
                        "Esta obligación ya tiene un pago registrado"
                );
            }

            return new RegistroPago(
                    false,
                    construirRespuesta(anterior, credito)
            );
        }

        if ("PAGADA".equals(cuota.getEstadoCuota())
                || "CANCELADO".equals(credito.getEstadoCredito())) {
            throw new NegocioException(
                    HttpStatus.CONFLICT,
                    "La obligación ya se encuentra cancelada"
            );
        }

        LocalDate fechaPago = solicitud.fechaPagoEfectivo();
        LocalDate hoy = LocalDate.now(ZONA_PERU);

        if (fechaPago.isAfter(hoy)
                || fechaPago.isBefore(venta.getFechaVenta())) {
            throw new NegocioException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha efectiva del pago no es válida"
            );
        }

        ResponseCalculoMoraDTO mora = moraService.construirDetalleMora(
                cuota,
                credito,
                venta,
                fechaPago
        );

        BigDecimal totalExigible = mora.totalExigible();

        if (solicitud.monto().compareTo(totalExigible) != 0) {
            throw new NegocioException(
                    HttpStatus.BAD_REQUEST,
                    "El importe debe coincidir con el total exigible de "
                            + venta.getMonedaVenta() + " "
                            + totalExigible.setScale(2, RoundingMode.HALF_UP)
            );
        }

        BigDecimal sumaComponentes = cuota.getAmortizacion()
                .add(cuota.getInteresCuota());

        if (sumaComponentes.compareTo(cuota.getImporte()) != 0) {
            throw new NegocioException(
                    HttpStatus.CONFLICT,
                    "Los importes de la cuota no son consistentes"
            );
        }

        // HU-15: Imputación en orden mora, interés y capital.
        BigDecimal restante = solicitud.monto();

        BigDecimal moraPagada = restante.min(mora.interesMoratorio());
        restante = restante.subtract(moraPagada);

        BigDecimal interesPagado = restante.min(cuota.getInteresCuota());
        restante = restante.subtract(interesPagado);

        BigDecimal capitalPagado = restante.min(cuota.getAmortizacion());
        restante = restante.subtract(capitalPagado);

        if (restante.signum() != 0
                || moraPagada.compareTo(mora.interesMoratorio()) != 0
                || interesPagado.compareTo(cuota.getInteresCuota()) != 0
                || capitalPagado.compareTo(cuota.getAmortizacion()) != 0) {
            throw new NegocioException(
                    HttpStatus.CONFLICT,
                    "No fue posible distribuir correctamente el pago"
            );
        }

        BigDecimal saldoActualizado = credito.getSaldoCredito()
                .subtract(capitalPagado);

        if (saldoActualizado.signum() < 0) {
            throw new NegocioException(
                    HttpStatus.CONFLICT,
                    "El saldo del crédito no coincide con las cuotas pendientes"
            );
        }

        Pago pago = new Pago();
        pago.setIdCuota(cuota);
        pago.setFechaPagoEfectivo(fechaPago);
        pago.setFechaCancelacion(fechaPago);
        pago.setDiasAtraso(Math.toIntExact(mora.diasAtraso()));
        pago.setMedioPago(medioPago);
        pago.setMoraPagada(moraPagada);
        pago.setInteresPagado(interesPagado);
        pago.setCapitalPagado(capitalPagado);
        pago.setTotalExigible(totalExigible);
        pago.setEstadoPago("REGISTRADO");

        pagos.saveAndFlush(pago);

        cuota.setEstadoCuota("PAGADA");
        cuotas.save(cuota);

        credito.setSaldoCredito(saldoActualizado);

        List<Cuota> cuotasCredito = cuotas
                .findAllByIdCredito_IdOrderByNumeroCuotaAsc(credito.getId());

        boolean todasPagadas = !cuotasCredito.isEmpty()
                && cuotasCredito.stream().allMatch(c ->
                "PAGADA".equals(c.getEstadoCuota())
                        && pagos.findByIdCuota_Id(c.getId()).isPresent()
        );

        if (todasPagadas) {
            if (saldoActualizado.signum() != 0) {
                throw new NegocioException(
                        HttpStatus.CONFLICT,
                        "El saldo del crédito no coincide con los pagos realizados"
                );
            }

            credito.setEstadoCredito("CANCELADO");
        }

        creditos.save(credito);

        return new RegistroPago(
                true,
                construirRespuesta(pago, credito)
        );
    }

    private ResponsePagoDTO construirRespuesta(
            Pago pago,
            Credito credito) {

        Cuota cuota = pago.getIdCuota();

        return new ResponsePagoDTO(
                pago.getId(),
                cuota.getId(),
                credito.getId(),
                credito.getIdVenta().getId(),
                credito.getCodigoCredito(),
                credito.getIdVenta().getMonedaVenta(),
                cuota.getFechaVencimiento(),
                pago.getFechaPagoEfectivo(),
                pago.getDiasAtraso(),
                pago.getMedioPago(),
                pago.getMoraPagada(),
                pago.getInteresPagado(),
                pago.getCapitalPagado(),
                pago.getTotalExigible(),
                credito.getSaldoCredito(),
                cuota.getEstadoCuota(),
                credito.getEstadoCredito()
        );
    }
}
