package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestAdministradorTiendaDTO(
        @NotBlank @Size(max = 200) String nombreUsuario,
        @NotBlank @Email @Size(max = 150) String correoUsuario,
        @Size(max = 15) String telefonoUsuario,
        @NotBlank @Size(min = 12, max = 72) String contrasena
) {}
