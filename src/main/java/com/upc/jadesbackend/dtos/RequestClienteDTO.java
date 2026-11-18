package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.*;

public record RequestClienteDTO(
        @NotBlank @Size(max=200) String nombreCliente,
        @NotBlank @Pattern(regexp="[0-9]{8}") String dniCliente,
        @Size(max=15) @Pattern(regexp="[+0-9() -]*") String telefonoCliente,
        @Email @Size(max=200) String correoCliente
) {}