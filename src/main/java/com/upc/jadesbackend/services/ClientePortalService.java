package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCuentaCorrienteDTO;
import com.upc.jadesbackend.entities.*;
        import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
        import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
        import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClientePortalService {

    private static final BigDecimal CERO = BigDecimal.ZERO;

    private final ClienteRepositorio clientes;
    private final VentaRepositorio ventas;
    private final CreditoRepositorio creditos;
    private final CuotaRepositorio cuotas;
    private final PagoRepositorio pagos;

    public ClientePortalService(
            ClienteRepositorio clientes,
            VentaRepositorio ventas,
            CreditoRepositorio creditos,
            CuotaRepositorio cuotas,
            PagoRepositorio pagos) {

        this.clientes = clientes;
        this.ventas = ventas;
        this.creditos = creditos;
        this.cuotas = cuotas;
        this.pagos = pagos;
    }

    @Transactional(readOnly = true)
    public ResponseCuentaCorrienteDTO consultarMiCuenta(Long idUsuario) {

        Cliente cliente = clientes.findByIdUsuario_Id(idUsuario)
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "No existe un cliente vinculado al usuario autenticado"
                ));

        return construirCuenta(cliente);
    }

    // NOTITA: Podria evaluarse reestructurar este metodo para mejor legibilidad y que sea menos complejo
    private ResponseCuentaCorrienteDTO construirCuenta(Cliente cliente) {
        Long idCliente = cliente.getId();
        List<Venta> listaVentas = ventas.findAllByIdCliente_IdOrderByFechaVentaDesc(idCliente);
        List<Credito> listaCreditos = creditos.findAllByIdVenta_IdCliente_Id(idCliente);
        List<Cuota> listaCuotas = cuotas.findAllByIdCredito_IdVenta_IdCliente_IdOrderByFechaVencimientoAsc(idCliente);
        List<Pago> listaPagos = pagos.findAllByIdCuota_IdCredito_IdVenta_IdCliente_IdOrderByFechaPagoEfectivoDesc(idCliente);

        Map<Long, Credito> creditoPorVenta = listaCreditos.stream().collect(
                Collectors.toMap(c -> c.getIdVenta().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Pago> pagoPorCuota = listaPagos.stream().collect(
                Collectors.toMap(p -> p.getIdCuota().getId(), Function.identity(), (a, b) -> a));

        List<ResponseCuentaCorrienteDTO.Compra> comprasDTO = listaVentas.stream().map(v -> {
            Credito credito = creditoPorVenta.get(v.getId());
            return new ResponseCuentaCorrienteDTO.Compra(v.getId(), v.getFechaVenta(),
                    v.getModalidadPago(), v.getNumeroCuotas(), v.getTotalVenta(),
                    v.getMonedaVenta(), v.getEstadoVenta(),
                    credito == null ? null : credito.getId(),
                    credito == null ? null : credito.getCodigoCredito());
        }).toList();

        LocalDate hoy = LocalDate.now();
        List<ResponseCuentaCorrienteDTO.Obligacion> obligacionesDTO = new ArrayList<>();
        BigDecimal capitalPendiente = CERO;
        BigDecimal interesPendiente = CERO;
        BigDecimal totalPendiente = CERO;
        long pendientes = 0;
        long vencidas = 0;

        for (Cuota cuota : listaCuotas) {
            boolean pagada = pagoPorCuota.containsKey(cuota.getId());
            boolean vencida = !pagada && cuota.getFechaVencimiento().isBefore(hoy);
            BigDecimal capital = nvl(cuota.getAmortizacion());
            BigDecimal interes = nvl(cuota.getInteresCuota());
            BigDecimal importePendiente = pagada ? CERO : nvl(cuota.getImporte());
            if (!pagada) {
                capitalPendiente = capitalPendiente.add(capital);
                interesPendiente = interesPendiente.add(interes);
                totalPendiente = totalPendiente.add(importePendiente);
                pendientes++;
                if (vencida) vencidas++;
            }
            obligacionesDTO.add(new ResponseCuentaCorrienteDTO.Obligacion(
                    cuota.getId(), cuota.getIdCredito().getId(), cuota.getIdCredito().getIdVenta().getId(),
                    cuota.getNumeroCuota(), cuota.getFechaVencimiento(), cuota.getEstadoCuota(), vencida,
                    capital, interes, nvl(cuota.getImporte()), importePendiente,
                    null, cuota.getIdCredito().getIdVenta().getMonedaVenta()));
        }

        List<ResponseCuentaCorrienteDTO.PagoRegistrado> pagosDTO = listaPagos.stream().map(p ->
                new ResponseCuentaCorrienteDTO.PagoRegistrado(p.getId(), p.getIdCuota().getId(),
                        p.getFechaPagoEfectivo(), p.getFechaCancelacion(), p.getMedioPago(),
                        p.getDiasAtraso(), nvl(p.getCapitalPagado()), nvl(p.getInteresPagado()),
                        nvl(p.getMoraPagada()), nvl(p.getTotalExigible()), p.getEstadoPago())).toList();

        BigDecimal totalCompras = listaVentas.stream().map(Venta::getTotalVenta)
                .map(ClientePortalService::nvl).reduce(CERO, BigDecimal::add);
        BigDecimal moraPagada = listaPagos.stream().map(Pago::getMoraPagada)
                .map(ClientePortalService::nvl).reduce(CERO, BigDecimal::add);

        return new ResponseCuentaCorrienteDTO(cliente.getId(), cliente.getNombreCliente(),
                cliente.getDniCliente(), cliente.getIdTienda().getId(),
                cliente.getIdTienda().getNombreTienda(), comprasDTO, obligacionesDTO, pagosDTO,
                new ResponseCuentaCorrienteDTO.Resumen(totalCompras, capitalPendiente,
                        interesPendiente, moraPagada, totalPendiente, pendientes, vencidas));
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor == null ? CERO : valor;
    }
}
