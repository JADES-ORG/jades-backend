package com.upc.jadesbackend.services;

import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.util.CalendarioCreditoUtil;
import org.springframework.stereotype.Service;

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

        int mesesGracia = "MENSUAL".equals(modalidadPago)
                ? graciaAdicional : 0;

        LocalDate finGracia = CalendarioCreditoUtil.fechaFinGracia(
                fechaCorte,
                condicion.getDiaCorte(),
                condicion.getDiaPago(),
                mesesGracia
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
