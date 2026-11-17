package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.ResponseCuentaCorrienteDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.AdministradorPortalService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portal")
public class AdministradorPortalController {

    private final AdministradorPortalService servicio;

    public AdministradorPortalController(
            AdministradorPortalService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/clientes/{idCliente}/cuenta-corriente")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseCuentaCorrienteDTO consultarCuentaCliente(
            @PathVariable Long idCliente,
            @AuthenticationPrincipal UsuarioPrincipal principal) {

        return servicio.consultarCuentaCliente(
                principal.getId(),
                idCliente
        );
    }
}

