package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;

public record RequestCondicionCrediticiaDTO(
        // HU-07: condiciones generales (David K.)
        @NotBlank @Pattern(regexp = "PEN|USD", message = "La moneda debe ser PEN o USD") String monedaCredito,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal limiteCredito,
        @NotNull @Min(1) @Max(120) Integer plazoMaximo,
        @NotNull @Min(1) @Max(31) Integer diaCorte,
        @NotNull @Min(1) @Max(31) Integer diaPago,
        LocalTime horaCorte,

        // HU-07: tasa compensatoria (David K.)
        @NotBlank @Pattern(regexp = "NOMINAL|EFECTIVA", message = "El tipo de tasa debe ser NOMINAL o EFECTIVA") String tipoTasa,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") @Digits(integer = 8, fraction = 12) BigDecimal tasaInteres,
        @NotBlank @Pattern(regexp = "DIARIA|QUINCENAL|MENSUAL|BIMESTRAL|TRIMESTRAL|CUATRIMESTRAL|SEMESTRAL|ANUAL", message = "Periodo de tasa inválido") String periodoTasa,
        @Pattern(regexp = "DIARIA|QUINCENAL|MENSUAL|BIMESTRAL|TRIMESTRAL|CUATRIMESTRAL|SEMESTRAL|ANUAL", message = "Periodo de capitalización inválido") String capitalizacionTasa,

        // HU-08: tasa moratoria (David K.)
        @NotBlank @Pattern(regexp = "NOMINAL|EFECTIVA", message = "El tipo de tasa moratoria debe ser NOMINAL o EFECTIVA") String tipoTasaMora,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") @Digits(integer = 8, fraction = 12) BigDecimal tasaMoratoria,
        @NotBlank @Pattern(regexp = "DIARIA|QUINCENAL|MENSUAL|BIMESTRAL|TRIMESTRAL|CUATRIMESTRAL|SEMESTRAL|ANUAL", message = "Periodo de tasa moratoria inválido") String periodoTasaMora,
        @Pattern(regexp = "DIARIA|QUINCENAL|MENSUAL|BIMESTRAL|TRIMESTRAL|CUATRIMESTRAL|SEMESTRAL|ANUAL", message = "Periodo de capitalización moratoria inválido") String capitalizacionMora
) {}
