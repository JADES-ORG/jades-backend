package com.upc.jadesbackend.security.services;

import com.upc.jadesbackend.security.dtos.ResponseLoginDTO;
import com.upc.jadesbackend.security.dtos.RequestLoginDTO;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.security.util.JwtUtil;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AutenticacionService {
    private final AuthenticationManager manager;
    private final JwtUtil jwt;

    public AutenticacionService(AuthenticationManager manager, JwtUtil jwt) {
        this.manager = manager;
        this.jwt = jwt;
    }

    public ResponseLoginDTO iniciarSesion(RequestLoginDTO solicitud) {
        var autenticacion = manager.authenticate(new UsernamePasswordAuthenticationToken(
                solicitud.correo(), solicitud.contrasena()));
        UsuarioPrincipal usuario = (UsuarioPrincipal) autenticacion.getPrincipal();
        return new ResponseLoginDTO(jwt.generar(usuario), usuario.getId(), usuario.getUsername(), usuario.getRol());
    }
}
