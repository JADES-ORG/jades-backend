package com.upc.jadesbackend.security.filters;

import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import com.upc.jadesbackend.security.services.UsuarioDetailsService;
import com.upc.jadesbackend.security.util.JwtUtil;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtBearerFilter extends OncePerRequestFilter {
    private final JwtUtil jwt;
    private final UsuarioDetailsService usuarios;

    public JwtBearerFilter(JwtUtil jwt, UsuarioDetailsService usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                String correo = jwt.correoValidado(authorization.substring(7));
                UsuarioPrincipal usuario = (UsuarioPrincipal) usuarios.loadUserByUsername(correo);
                if (usuario.isEnabled()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            usuario, null, usuario.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
