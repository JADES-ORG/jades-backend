package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestCondicionCrediticiaDTO;
import com.upc.jadesbackend.dtos.ResponseCondicionCrediticiaDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.CondicionCreditoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// HU07 (David K) Endpoints
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tiendas/{tienda}/clientes/{cliente}/condicion-crediticia")
@PreAuthorize("hasRole('ADMIN_NEGOCIO')")
public class CondicionCrediticiaController {
    private final CondicionCreditoService servicio;

    @PostMapping
    public ResponseEntity<ResponseCondicionCrediticiaDTO> registrar(@PathVariable Long tienda, @PathVariable Long cliente,
                                                                    @Valid @RequestBody RequestCondicionCrediticiaDTO dto,
                                                                    @AuthenticationPrincipal UsuarioPrincipal usuario) {
        var resultado = servicio.registrar(tienda, cliente, dto, usuario);
        return ResponseEntity.created(URI.create("/api/v1/tiendas/" + tienda + "/clientes/" + cliente + "/condicion-crediticia"))
                .body(resultado);
    }

    @GetMapping
    public ResponseCondicionCrediticiaDTO consultar(@PathVariable Long tienda, @PathVariable Long cliente,
                                                    @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.consultar(tienda, cliente, usuario);
    }

    @PutMapping
    public ResponseCondicionCrediticiaDTO actualizar(@PathVariable Long tienda, @PathVariable Long cliente,
                                                     @Valid @RequestBody RequestCondicionCrediticiaDTO dto,
                                                     @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.actualizar(tienda, cliente, dto, usuario);
    }
}
