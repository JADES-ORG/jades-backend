package com.upc.jadesbackend.dtos;

public record ResponseTiendaResumenDTO(
        Long idTienda, String nombreTienda, String documentoTienda, String estadoTienda
) {}
