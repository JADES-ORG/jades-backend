package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.*;

public record RequestConfiguracionTiendaDTO(
        @NotBlank @Size(max = 120) String nombreTienda,
        @NotBlank @Size(max = 50) String giroDeNegocio,
        @Size(max = 15) String telefonoTienda,
        @Email @Size(max = 150) String correoTienda
) {}
