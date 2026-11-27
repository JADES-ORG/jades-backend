package com.upc.jadesbackend.security.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RecuperacionContrasenaDTO {
    private RecuperacionContrasenaDTO() {}

    public record RequestRecuperar(
            @NotBlank @Email @Size(max = 150) String correo
    ) {}

    public record RequestVerificar(
            @NotBlank @Email @Size(max = 150) String correo,
            @NotBlank @Pattern(regexp = "[0-9]{5}") String codigo
    ) {}

    public record RequestRestablecer(
            @NotBlank @Email @Size(max = 150) String correo,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String tokenRecuperacion,
            @NotBlank @Size(min = 12, max = 72) String nuevaContrasena
    ) {}

    public record ResponseCodigo(String codigo, int expiraEnSegundos, boolean simulacion) {}

    public record ResponseVerificacion(String tokenRecuperacion, int expiraEnSegundos) {}
}
