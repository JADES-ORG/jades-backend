package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestRegistrarTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseBajaTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseTiendaResumenDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.TiendaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tiendas")
public class TiendaController {
    private final TiendaService servicio;
    public TiendaController(TiendaService servicio) { this.servicio = servicio; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseEntity<ResponseTiendaDTO> registrar(
            @Valid @RequestBody RequestRegistrarTiendaDTO solicitud,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ResponseTiendaDTO resultado = servicio.registrar(solicitud, principal.getId());
        return ResponseEntity.created(URI.create("/api/v1/tiendas/" + resultado.idTienda())).body(resultado);
    }

    @GetMapping("/mis-tiendas")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public List<ResponseTiendaResumenDTO> misTiendas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.listarMisTiendas(principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseTiendaResumenDTO consultar(@PathVariable Long id) {
        return servicio.consultar(id);
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseBajaTiendaDTO desactivar(@PathVariable Long id,
                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.desactivar(id, principal);
    }
}
