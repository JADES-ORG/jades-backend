package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestVentaDTO;
import com.upc.jadesbackend.dtos.ResponseVentaDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tiendas/{tienda}/ventas")
@PreAuthorize("hasRole('ADMIN_NEGOCIO')")
public class VentaController {
    private final VentaService servicio;

    @PostMapping
    public ResponseEntity<ResponseVentaDTO> registrar(@PathVariable Long tienda, @Valid @RequestBody RequestVentaDTO dto,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
                                                      @AuthenticationPrincipal UsuarioPrincipal usuario) {
        var resultado = servicio.registrar(tienda, dto, usuario, claveIdempotencia);
        return ResponseEntity.created(URI.create("/api/v1/tiendas/" + tienda + "/ventas/" + resultado.id())).body(resultado);
    }

    @GetMapping
    public List<ResponseVentaDTO> listar(@PathVariable Long tienda, @RequestParam(required = false) Long cliente,
                                         @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.listar(tienda, cliente, usuario);
    }

    @GetMapping("/{id}")
    public ResponseVentaDTO consultar(@PathVariable Long tienda, @PathVariable Long id,
                                      @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.consultar(tienda, id, usuario);
    }
}
