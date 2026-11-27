package com.upc.jadesbackend.dtos;

import com.upc.jadesbackend.entities.Tienda;
import java.util.List;

public record ResponseTiendaDetalleDTO(
        Long idTienda, String nombreTienda, String documentoTienda, String estadoTienda,
        String razonSocial, String giroDeNegocio, String direccion,
        String telefonoTienda, String correoTienda, List<Administrador> administradores
) {
    public record Administrador(Long idUsuario, String nombreUsuario, String correoUsuario,
                                String telefonoUsuario, boolean activo) {}

    public static ResponseTiendaDetalleDTO de(Tienda tienda, List<Administrador> administradores) {
        return new ResponseTiendaDetalleDTO(tienda.getId(), tienda.getNombreTienda(),
                tienda.getDocumentoTienda(), tienda.getEstadoTienda(), tienda.getRazonSocial(),
                tienda.getGiroDeNegocio(), tienda.getDireccion(), tienda.getTelefonoTienda(),
                tienda.getCorreoTienda(), administradores);
    }
}
