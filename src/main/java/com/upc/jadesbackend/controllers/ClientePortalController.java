package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.ResponseCuentaCorrienteDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.ClientePortalService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portal")
public class ClientePortalController {

    private final ClientePortalService servicio;

    public ClientePortalController(ClientePortalService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mi-cuenta")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseCuentaCorrienteDTO consultarMiCuenta(
            @AuthenticationPrincipal UsuarioPrincipal principal) {

        return servicio.consultarMiCuenta(principal.getId());
    }
}
