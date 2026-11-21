package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.*;

public record RequestAltaClienteDTO(
        @NotBlank @Size(max = 200) String nombreCliente,
        @NotBlank @Pattern(regexp = "[0-9]{8}") String dniCliente,
        @Size(max = 15) @Pattern(regexp = "[+0-9() -]*") String telefonoCliente,
        @NotBlank @Email @Size(max = 150) String correoCliente,
        @NotBlank @Size(min = 8, max = 72) String contrasenaTemporal
) {}
