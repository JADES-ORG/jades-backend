package com.upc.jadesbackend.security.controllers;

import com.upc.jadesbackend.security.dtos.ResponseLoginDTO;
import com.upc.jadesbackend.security.dtos.RequestLoginDTO;
import com.upc.jadesbackend.security.services.AutenticacionService;
import com.upc.jadesbackend.security.services.RecuperacionContrasenaService;
import com.upc.jadesbackend.security.dtos.RecuperacionContrasenaDTO.*;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
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
    private final RecuperacionContrasenaService recuperacion;

    public AutenticacionController(AutenticacionService servicio, RecuperacionContrasenaService recuperacion) {
        this.servicio = servicio;
        this.recuperacion = recuperacion;
    }

    @PostMapping("/iniciar-sesion")
    @SecurityRequirements
    public ResponseLoginDTO iniciarSesion(@Valid @RequestBody RequestLoginDTO solicitud) {
        return servicio.iniciarSesion(solicitud);
    }

    @PostMapping("/recuperar-contrasena")
    @SecurityRequirements
    public ResponseEntity<ResponseCodigo> recuperarContrasena(@Valid @RequestBody RequestRecuperar solicitud) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(recuperacion.solicitar(solicitud.correo()));
    }

    @PostMapping("/verificar-codigo")
    @SecurityRequirements
    public ResponseEntity<ResponseVerificacion> verificarCodigo(@Valid @RequestBody RequestVerificar solicitud) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(recuperacion.verificar(solicitud.correo(), solicitud.codigo()));
    }

    @PostMapping("/restablecer-contrasena")
    @SecurityRequirements
    public ResponseEntity<Void> restablecerContrasena(@Valid @RequestBody RequestRestablecer solicitud) {
        recuperacion.restablecer(solicitud.correo(), solicitud.tokenRecuperacion(), solicitud.nuevaContrasena());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
    }
}
