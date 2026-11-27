package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestRegistrarTiendaDTO;
import com.upc.jadesbackend.dtos.RequestActualizarTiendaDTO;
import com.upc.jadesbackend.dtos.RequestConfiguracionTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseTiendaDetalleDTO;
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

    @GetMapping
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public List<ResponseTiendaDetalleDTO> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.listar(principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseTiendaDetalleDTO consultar(@PathVariable Long id,
                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.consultarDetalle(id, principal);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseTiendaDetalleDTO actualizar(@PathVariable Long id,
                                                @Valid @RequestBody RequestActualizarTiendaDTO solicitud,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.actualizar(id, solicitud, principal);
    }

    @GetMapping("/{id}/configuracion")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseTiendaDetalleDTO consultarConfiguracion(@PathVariable Long id,
                                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.consultarConfiguracion(id, principal);
    }

    @PutMapping("/{id}/configuracion")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseTiendaDetalleDTO actualizarConfiguracion(@PathVariable Long id,
                                                              @Valid @RequestBody RequestConfiguracionTiendaDTO solicitud,
                                                              @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.actualizarConfiguracion(id, solicitud, principal);
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseBajaTiendaDTO desactivar(@PathVariable Long id,
                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.desactivar(id, principal);
    }

    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    public ResponseTiendaResumenDTO reactivar(@PathVariable Long id,
                                              @AuthenticationPrincipal UsuarioPrincipal principal) {
        return servicio.reactivar(id, principal);
    }
}
