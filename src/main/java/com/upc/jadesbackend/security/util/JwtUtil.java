package com.upc.jadesbackend.security.util;

import com.upc.jadesbackend.security.principal.UsuarioPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe contener al menos 32 bytes UTF-8 para HS256");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION_MS debe ser positivo");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generar(UsuarioPrincipal usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder().subject(usuario.getUsername())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(expirationMs)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public String correoValidado(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String correo = claims.getSubject();
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("JWT sin sujeto");
        }
        return correo;
    }
}
