package com.upc.jadesbackend.dtos;

import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.entities.TasaInteres;
import com.upc.jadesbackend.entities.TasaMoratoria;

import java.math.BigDecimal;
import java.time.LocalTime;


// HU-07 (David K.) Se ha adicionado el DTO necesario para que el metodo estatico sea empleado de forma global
public record ResponseCondicionCrediticiaDTO(
        Long id,
        Long idCliente,
        String nombreCliente,
        String monedaCredito,
        BigDecimal limiteCredito,
        BigDecimal creditoUtilizado,
        BigDecimal creditoDisponible,
        Integer plazoMaximo,
        Integer diaCorte,
        Integer diaPago,
        LocalTime horaCorte,
        String tipoTasa,
        BigDecimal tasaInteres,
        String periodoTasa,
        String capitalizacionTasa,
        String tipoTasaMora,
        BigDecimal tasaMoratoria,
        String periodoTasaMora,
        String capitalizacionMora
) {
    public static ResponseCondicionCrediticiaDTO de(CondicionCredito c, TasaInteres ti, TasaMoratoria tm,
                                                      ResponseCreditoDisponibleDTO credito) {
        return new ResponseCondicionCrediticiaDTO(
                c.getId(),
                c.getIdCliente().getId(),
                c.getIdCliente().getNombreCliente(),
                c.getMonedaCredito(),
                c.getLimiteCredito(),
                credito.creditoUtilizado(),
                credito.creditoDisponible(),
                c.getPlazoMaximo(),
                c.getDiaCorte(),
                c.getDiaPago(),
                c.getHoraCorte(),
                ti.getTipoTasa(),
                ti.getTasaInteres(),
                ti.getPeriodoTasa(),
                ti.getCapitalizacionTasa(),
                tm.getTipoTasaMora(),
                tm.getTasaMoratoria(),
                tm.getPeriodoTasaMora(),
                tm.getCapitalizacionMora());
    }
}
