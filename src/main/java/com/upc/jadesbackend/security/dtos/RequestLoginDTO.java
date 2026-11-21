package com.upc.jadesbackend.security.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestLoginDTO(@NotBlank @Email String correo, @NotBlank String contrasena) {}
