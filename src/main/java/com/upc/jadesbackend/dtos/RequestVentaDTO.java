package com.upc.jadesbackend.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

// Adicion de la HU08 (David K.)
public record RequestVentaDTO(
        @NotNull Long idCliente,
        @NotBlank @Pattern(regexp = "UNICO|MENSUAL", message = "La modalidad debe ser UNICO o MENSUAL") String modalidadPago,
        @NotNull @Min(1) @Max(120) Integer numeroCuotas,
        @Min(0) @Max(30) Integer graciaAdicional,
        @NotEmpty(message = "La venta debe tener al menos un producto o servicio")
        @Size(max = 50) List<@Valid @NotNull Detalle> detalles
) {
    public record Detalle(
            @NotNull Long idProducto,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal cantidad
    ) {}
}