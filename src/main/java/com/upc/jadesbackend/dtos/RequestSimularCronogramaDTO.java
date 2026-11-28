package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Datos para simular un cronograma sin guardar nada (paso 3 del wizard de venta). */
public record RequestSimularCronogramaDTO(
        @NotNull @DecimalMin(value = "0.01", message = "debe ser mayor a cero") @Digits(integer = 16, fraction = 2) BigDecimal monto,
        @NotBlank @Pattern(regexp = "(?i)NOMINAL|EFECTIVA", message = "debe ser NOMINAL o EFECTIVA") String tipoTasa,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "La tasa debe ser mayor a cero") @Digits(integer = 8, fraction = 9) BigDecimal valorTasa,
        @NotBlank String periodoTasa,
        String capitalizacionTasa,
        @NotNull @Min(1) @Max(360) Integer numeroCuotas,
        @NotNull LocalDate fechaVenta,
        LocalTime horaVenta,
        @NotNull @Min(1) @Max(31) Integer diaCorte,
        LocalTime horaCorte,
        @NotNull @Min(1) @Max(31) Integer diaPago,
        @Min(0) @Max(value = 0, message = "No se permiten meses adicionales de gracia") Integer graciaAdicional,
        @Pattern(regexp = "PEN|USD", message = "debe ser PEN o USD") String moneda
) {}
