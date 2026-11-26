package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.util.CalendarioCreditoUtil;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import com.upc.jadesbackend.exceptions.NegocioException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class AsignacionCicloService {
    //HU10: Asignar la compra a su ciclo
    public ResultadoCiclo asignar(
            LocalDate fechaVenta,
            LocalTime horaVenta,
            String modalidadPago,
            int graciaAdicional,
            CondicionCredito condicion) {

        LocalTime horaCorte = condicion.getHoraCorte() == null
                ? LocalTime.of(18, 0)
                : condicion.getHoraCorte();

        LocalDate fechaCorte = CalendarioCreditoUtil.fechaCorte(
                fechaVenta,
                horaVenta,
                condicion.getDiaCorte(),
                horaCorte
        );

        if (graciaAdicional != 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se permiten meses adicionales de gracia");

        LocalDate finGracia = CalendarioCreditoUtil.fechaFinGracia(
                fechaCorte,
                condicion.getDiaCorte(),
                condicion.getDiaPago(),
                0
        );

        LocalDate fechaPago = "MENSUAL".equals(modalidadPago)
                ? CalendarioCreditoUtil.fechaVencimiento(
                finGracia, 1, condicion.getDiaPago())
                : finGracia;

        return new ResultadoCiclo(
                LocalDateTime.of(fechaCorte, horaCorte),
                fechaPago
        );
    }

    public record ResultadoCiclo(
            LocalDateTime fechaCorte,
            LocalDate fechaPago) {
    }
}
