package com.upc.jadesbackend.dtos;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RequestProductoDTO(
        @NotBlank @Size(max=150) String nombreProducto,
        @Size(max=500) String descripcion,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal precioContado,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal precioCredito,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal precioContadoUsd,
        @NotNull @DecimalMin("0.01") @Digits(integer=16, fraction=2) BigDecimal precioCreditoUsd,
        @NotBlank @Pattern(regexp="PRODUCTO|SERVICIO") String tipoProducto,
        @Size(max=150) String proveedor,
        @Size(max=100) String marca,
        @NotBlank @Pattern(regexp="UNIDAD|PAQUETE|SERVICIO|SESION|CAJA|DOCENA|PAR|KILOGRAMO|GRAMO|LITRO|MILILITRO|METRO|CENTIMETRO|METRO_CUADRADO|HORA") String unidadMedida,
        @Size(max=500) String imagenProducto,
        @NotBlank @Pattern(regexp="UNICO|MENSUAL|AMBAS") String modalidad
) {}