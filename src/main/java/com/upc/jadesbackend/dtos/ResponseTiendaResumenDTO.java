package com.upc.jadesbackend.dtos;

import com.upc.jadesbackend.entities.Tienda;

public record ResponseTiendaResumenDTO(
        Long idTienda, String nombreTienda, String documentoTienda, String estadoTienda
) {
    public static ResponseTiendaResumenDTO de(Tienda tienda) {
        return new ResponseTiendaResumenDTO(tienda.getId(), tienda.getNombreTienda(),
                tienda.getDocumentoTienda(), tienda.getEstadoTienda());
    }
}
