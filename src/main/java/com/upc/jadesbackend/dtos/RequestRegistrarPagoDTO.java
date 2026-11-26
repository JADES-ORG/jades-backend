package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

//HU-15: Datos de entrada para registrar un pago.
public record RequestRegistrarPagoDTO(
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 16, fraction = 2)
        BigDecimal monto,

        @NotNull
        LocalDate fechaPagoEfectivo,

        LocalTime horaPagoEfectivo,

        @NotBlank
        @Size(max = 20)
        String medioPago
) {}
