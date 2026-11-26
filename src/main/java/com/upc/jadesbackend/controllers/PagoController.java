package com.upc.jadesbackend.controllers;

import com.upc.jadesbackend.dtos.RequestRegistrarPagoDTO;
import com.upc.jadesbackend.dtos.ResponseLiquidacionDTO;
import com.upc.jadesbackend.dtos.ResponsePagoDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.services.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pagos")
public class PagoController {

    private final PagoService servicio;

    @GetMapping("/cuotas/{idCuota}/importe")
    @PreAuthorize("hasAnyRole('ADMIN_NEGOCIO','CLIENTE')")
    public ResponseLiquidacionDTO consultarImporte(
            @PathVariable Long idCuota,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPagoEfectivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaPagoEfectivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.consultarImporte(idCuota, fechaPagoEfectivo, horaPagoEfectivo, usuario);
    }

    @PostMapping("/cuotas/{idCuota}")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseEntity<ResponsePagoDTO> registrar(
            @PathVariable Long idCuota,
            @Valid @RequestBody RequestRegistrarPagoDTO solicitud,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        var resultado = servicio.registrar(idCuota, solicitud, usuario);
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.resultado());
    }

    @GetMapping("/creditos/{idCredito}/liquidacion")
    @PreAuthorize("hasAnyRole('ADMIN_NEGOCIO','CLIENTE')")
    public ResponseLiquidacionDTO consultarLiquidacion(
            @PathVariable Long idCredito,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPagoEfectivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaPagoEfectivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.consultarLiquidacion(idCredito, fechaPagoEfectivo, horaPagoEfectivo, usuario);
    }

    @PostMapping("/creditos/{idCredito}/liquidacion")
    @PreAuthorize("hasRole('ADMIN_NEGOCIO')")
    public ResponseEntity<ResponseLiquidacionDTO> liquidar(
            @PathVariable Long idCredito,
            @Valid @RequestBody RequestRegistrarPagoDTO solicitud,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.liquidar(idCredito, solicitud, usuario));
    }
}
