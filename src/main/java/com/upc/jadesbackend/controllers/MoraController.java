package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.ResponseCalculoMoraDTO;
import com.upc.jadesbackend.dtos.ResponseMoraClienteDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.MoraService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * HU-14: cálculo de intereses por mora.
 */
@RestController
@RequestMapping("/api/v1/mora")
public class MoraController {

    private final MoraService servicio;

    public MoraController(MoraService servicio) {
        this.servicio = servicio;
    }

    /**
     * Consulta la liquidación de mora para una cuota específica.
     * Si no se especifica fechaEvaluacion, se toma la fecha actual del sistema.
     */
    @GetMapping("/cuotas/{idCuota}")
    @PreAuthorize("hasAnyRole('ADMIN_NEGOCIO','CLIENTE')")
    public ResponseCalculoMoraDTO consultarMoraCuota(
            @PathVariable Long idCuota,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEvaluacion,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.calcularMoraCuota(idCuota, fechaEvaluacion, principal);
    }

    /**
     * Consulta todas las cuotas en mora y el acumulado exigible para un cliente (Portal Administrador).
     */
    @GetMapping("/clientes/{idCliente}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseMoraClienteDTO consultarMorasCliente(
            @PathVariable Long idCliente,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEvaluacion,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.consultarMorasCliente(idCliente, fechaEvaluacion, principal.getId());
    }

    /**
     * Consulta las cuotas en mora de las compras del cliente autenticado (Portal Cliente).
     */
    @GetMapping("/mi-cuenta")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseMoraClienteDTO consultarMorasMiCuenta(
            @RequestParam(required = false) Long idTienda,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEvaluacion,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.consultarMorasMiCuenta(principal.getId(), idTienda, fechaEvaluacion);
    }
}
