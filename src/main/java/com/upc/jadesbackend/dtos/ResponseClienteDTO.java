package com.upc.jadesbackend.dtos;
import com.upc.jadesbackend.entities.Cliente;
public record ResponseClienteDTO(
        Long id,
        String nombreCliente,
        String dniCliente,
        String telefonoCliente,
        String correoCliente,
        String estadoCliente)
{
    public static ResponseClienteDTO de(Cliente e) {
        return new ResponseClienteDTO(
                e.getId(),
                e.getNombreCliente(),
                e.getDniCliente(),
                e.getTelefonoCliente(),
                e.getCorreoCliente(),
                e.getEstadoCliente()); }
}