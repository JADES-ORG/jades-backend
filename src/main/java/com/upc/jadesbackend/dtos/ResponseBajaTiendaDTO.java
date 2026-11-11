package com.upc.jadesbackend.dtos;

import java.time.OffsetDateTime;

public record ResponseBajaTiendaDTO(
        Long idTienda,
        String estadoTienda,
        Long idUsuarioResponsable,
        OffsetDateTime fechaBaja
) {}
