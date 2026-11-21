package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestRegistrarPagoDTO;
import com.upc.jadesbackend.dtos.ResponsePagoDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/*
 * HU-15: Controlador de registro de pagos.
 * Permite al administrador cancelar una obligación
 * y actualizar el estado financiero del crédito.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pagos")
public class PagoController {

    private final PagoService servicio;

    @PostMapping("/cuotas/{idCuota}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseEntity<ResponsePagoDTO> registrar(
            @PathVariable Long idCuota,
            @Valid @RequestBody RequestRegistrarPagoDTO solicitud,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {

        var resultado = servicio.registrar(
                idCuota,
                solicitud,
                usuario
        );

        return ResponseEntity
                .status(resultado.creado()
                        ? HttpStatus.CREATED
                        : HttpStatus.OK)
                .body(resultado.resultado());
    }
}
