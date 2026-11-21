package com.upc.jadesbackend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record RequestRegistrarTiendaDTO(
        @NotBlank @Size(max = 120) String nombreTienda,
        @NotBlank @Size(max = 150) String razonSocial,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "debe contener 11 dígitos") String documentoTienda,
        @NotBlank @Size(max = 50) String giroDeNegocio,
        @NotBlank @Size(max = 200) String direccion,
        @Size(max = 15) String telefonoTienda,
        @Email @Size(max = 150) String correoTienda,
        @NotNull @Valid RequestAdministradorTiendaDTO administrador
) {}
