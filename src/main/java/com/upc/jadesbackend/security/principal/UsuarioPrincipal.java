package com.upc.jadesbackend.security.principal;

import com.upc.jadesbackend.entities.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioPrincipal implements UserDetails {
    private final Long id;
    private final String correo;
    private final String passwordHash;
    private final String rol;
    private final boolean activo;

    public UsuarioPrincipal(Usuario usuario) {
        this.id = usuario.getId();
        this.correo = usuario.getCorreoUsuario();
        this.passwordHash = usuario.getPasswordHash();
        this.rol = usuario.getRol();
        this.activo = Boolean.TRUE.equals(usuario.getActivo());
    }

    public Long getId() { return id; }
    public String getRol() { return rol; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (!List.of("ADMIN_SISTEMA", "ADMIN_NEGOCIO", "CLIENTE").contains(rol)) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return correo; }

    @Override
    public boolean isEnabled() {
        return activo && List.of("ADMIN_SISTEMA", "ADMIN_NEGOCIO", "CLIENTE").contains(rol);
    }
}
