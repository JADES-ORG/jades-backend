package com.upc.jadesbackend.dtos;

public record ResponseTiendaDTO(
        Long idTienda,
        String nombreTienda,
        String documentoTienda,
        String estadoTienda,
        Long idAdministrador,
        String correoAdministrador
) {}
