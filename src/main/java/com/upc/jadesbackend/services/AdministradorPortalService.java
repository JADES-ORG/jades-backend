package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.ResponseCuentaCorrienteDTO;
import com.upc.jadesbackend.entities.Cliente;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.ClienteRepositorio;
import com.upc.jadesbackend.repositories.UsuarioTiendaRepositorio;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministradorPortalService {

    private final ClienteRepositorio clientes;
    private final UsuarioTiendaRepositorio accesos;
    private final ClientePortalService consultaCuenta;

    public AdministradorPortalService(
            ClienteRepositorio clientes,
            UsuarioTiendaRepositorio accesos,
            ClientePortalService consultaCuenta) {

        this.clientes = clientes;
        this.accesos = accesos;
        this.consultaCuenta = consultaCuenta;
    }

    @Transactional(readOnly = true)
    public ResponseCuentaCorrienteDTO consultarCuentaCliente(
            Long idUsuarioAdmin,
            Long idCliente) {

        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "Cliente no encontrado"
                ));

        Long idTienda = cliente.getIdTienda().getId();

        if (!accesos.tieneAccesoActivo(idUsuarioAdmin, idTienda)) {

            throw new NegocioException(
                    HttpStatus.NOT_FOUND,
                    "Cliente no encontrado en las tiendas autorizadas"
            );
        }

        return consultaCuenta.construirCuenta(cliente);
    }
}
