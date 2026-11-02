package com.upc.jadesbackend.security.services;

import com.upc.jadesbackend.repositories.UsuarioRepositorio;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepositorio usuarios;

    public UsuarioDetailsService(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) {
        return usuarios.findByCorreoUsuarioIgnoreCase(correo)
                .map(UsuarioPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }
}
