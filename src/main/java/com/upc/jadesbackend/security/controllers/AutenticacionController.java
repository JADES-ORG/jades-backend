package com.upc.jadesbackend.security.controllers;

import com.upc.jadesbackend.security.dtos.ResponseLoginDTO;
import com.upc.jadesbackend.security.dtos.RequestLoginDTO;
import com.upc.jadesbackend.security.services.AutenticacionService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/autenticacion")
public class AutenticacionController {
    private final AutenticacionService servicio;

    public AutenticacionController(AutenticacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/iniciar-sesion")
    @SecurityRequirements
    public ResponseLoginDTO iniciarSesion(@Valid @RequestBody RequestLoginDTO solicitud) {
        return servicio.iniciarSesion(solicitud);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
    }
}
