package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.VentaRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

// HU09: Validar el crédito antes de confirmar una venta.
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class EvaluacionCreditoService {
    private final VentaRepositorio ventas;

    public void validarPlazo(CondicionCredito condicion, int numeroCuotas) {
        if (numeroCuotas > condicion.getPlazoMaximo())
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "El plazo solicitado (" + numeroCuotas + " cuotas) supera el máximo autorizado para el cliente ("
                            + condicion.getPlazoMaximo() + " cuotas)");
    }

    public void validarLimite(Long idCliente, CondicionCredito condicion, BigDecimal montoCompra) {
        BigDecimal utilizado = creditoUtilizado(idCliente);
        BigDecimal disponible = creditoDisponible(condicion, utilizado);
        if (montoCompra.compareTo(disponible) > 0) {
            String m = condicion.getMonedaCredito();
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Crédito insuficiente: la compra de " + m + " " + dos(montoCompra)
                            + " excede el crédito disponible de " + m + " " + dos(disponible)
                            + " (límite " + dos(condicion.getLimiteCredito()) + ", utilizado " + dos(utilizado) + ")");
        }
    }

    public BigDecimal creditoUtilizado(Long idCliente) {
        BigDecimal utilizado = ventas.sumarCreditoUtilizadoPorCliente(idCliente);
        return utilizado == null ? BigDecimal.ZERO : utilizado;
    }

    public BigDecimal creditoDisponible(CondicionCredito condicion, BigDecimal utilizado) {
        return condicion.getLimiteCredito().subtract(utilizado).max(BigDecimal.ZERO);
    }

    private static String dos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
