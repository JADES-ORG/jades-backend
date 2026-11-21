package com.upc.jadesbackend.dtos;

import java.time.OffsetDateTime;
import java.util.Map;

public record ResponseErrorDTO(
        OffsetDateTime fecha,
        int estado,
        String mensaje,
        Map<String, String> errores
) {}
