package com.upc.jadesbackend.dtos;

import java.math.BigDecimal;

public record ResponseTiendaClienteDTO(
        Long idTienda,
        Long idCliente,
        String nombreTienda,
        String estadoTienda,
        String estadoCliente,
        String monedaCredito,
        BigDecimal capitalPendiente,
        BigDecimal totalPendiente,
        long cuotasPendientes,
        long cuotasVencidas
) {}
