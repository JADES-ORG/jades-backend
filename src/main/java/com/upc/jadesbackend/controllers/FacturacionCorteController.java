package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.ResponseListadoCorteDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.FacturacionCorteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * HU-13: generar el listado de pago al corte.
 */
@RestController
@RequestMapping("/api/v1/corte")
public class FacturacionCorteController {

    private final FacturacionCorteService servicio;

    public FacturacionCorteController(FacturacionCorteService servicio) {
        this.servicio = servicio;
    }

    /**
     * Genera el listado de pago al corte para un cliente y ciclo específico.
     * Ejemplo de ciclo: 2026-09. Si se omite, se usa el ciclo actual.
     */
    @GetMapping("/clientes/{idCliente}")
    @PreAuthorize("hasAnyRole('ADMIN_NEGOCIO','CLIENTE')")
    public ResponseListadoCorteDTO consultarCorteCliente(
            @PathVariable Long idCliente,
            @RequestParam(required = false) String ciclo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEvaluacion,
            @AuthenticationPrincipal UsuarioPrincipal principal) {

        YearMonth ym = parsearCiclo(ciclo);
        return servicio.generarListadoCorteCliente(idCliente, ym, fechaEvaluacion, principal);
    }

    /**
     * Genera el listado de pago al corte para todos los clientes de una tienda en un ciclo dado.
     */
    @GetMapping("/tiendas/{idTienda}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public List<ResponseListadoCorteDTO> consultarCorteTienda(
            @PathVariable Long idTienda,
            @RequestParam(required = false) String ciclo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEvaluacion,
            @AuthenticationPrincipal UsuarioPrincipal principal) {

        YearMonth ym = parsearCiclo(ciclo);
        return servicio.generarListadoCorteTienda(idTienda, ym, fechaEvaluacion, principal.getId());
    }

    /**
     * Consulta el listado de pago al corte del cliente autenticado (Portal Cliente).
     */
    @GetMapping("/mi-cuenta")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseListadoCorteDTO consultarMiCorte(
            @RequestParam(required = false) String ciclo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEvaluacion,
            @AuthenticationPrincipal UsuarioPrincipal principal) {

        YearMonth ym = parsearCiclo(ciclo);
        return servicio.consultarMiListadoCorte(ym, fechaEvaluacion, principal.getId());
    }

    private static YearMonth parsearCiclo(String ciclo) {
        if (ciclo == null || ciclo.isBlank()) {
            return null;
        }
        return YearMonth.parse(ciclo.trim());
    }
}
