package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.ResponsePagoUnicoDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.PagoUnicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deudas/pago-unico")
public class PagoUnicoController {

    private final PagoUnicoService servicio;

    @PostMapping("/ventas/{idVenta}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseEntity<ResponsePagoUnicoDTO> generar(
            @PathVariable Long idVenta,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {

        return ResponseEntity.ok(servicio.consultar(idVenta, usuario));
    }

    @GetMapping("/ventas/{idVenta}")
    @PreAuthorize("hasAnyRole('ADMIN_NEGOCIO','CLIENTE')")
    public ResponsePagoUnicoDTO consultar(
            @PathVariable Long idVenta,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {

        return servicio.consultar(idVenta, usuario);
    }
}
