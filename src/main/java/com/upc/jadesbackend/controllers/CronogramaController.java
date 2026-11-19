package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestSimularCronogramaDTO;
import com.upc.jadesbackend.dtos.ResponseCronogramaDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.CronogramaService;
import com.upc.jadesbackend.services.CronogramaService.GeneracionCronograma;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** HU-12: generar y consultar el cronograma de cuotas. */
@RestController
@RequestMapping("/api/v1/cronogramas")
public class CronogramaController {

    private final CronogramaService servicio;

    public CronogramaController(CronogramaService servicio) {
        this.servicio = servicio;
    }

    /** Calcula un cronograma sin guardarlo (simulación previa a confirmar la venta). */
    @PostMapping("/simular")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseCronogramaDTO simular(@Valid @RequestBody RequestSimularCronogramaDTO solicitud) {
        return servicio.simular(solicitud);
    }

    /** Genera y guarda el crédito y sus cuotas. Si ya existía, lo devuelve sin duplicar (200). */
    @PostMapping("/ventas/{idVenta}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseEntity<ResponseCronogramaDTO> generar(@PathVariable Long idVenta,
                                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        GeneracionCronograma resultado = servicio.generar(idVenta, principal.getId());
        HttpStatus estado = resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(resultado.cronograma());
    }

    /** Consulta el cronograma guardado de una venta (administrador de la tienda o el propio cliente). */
    @GetMapping("/ventas/{idVenta}")
    @PreAuthorize("hasAnyRole('ADMIN_NEGOCIO','CLIENTE')")
    public ResponseCronogramaDTO consultar(@PathVariable Long idVenta,
                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.consultar(idVenta, principal);
    }
}
