package com.upc.jadesbackend.security.services;

import com.upc.jadesbackend.entities.Usuario;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.UsuarioRepositorio;
import com.upc.jadesbackend.security.dtos.RecuperacionContrasenaDTO.ResponseCodigo;
import com.upc.jadesbackend.security.dtos.RecuperacionContrasenaDTO.ResponseVerificacion;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class RecuperacionContrasenaService {
    private static final int VIGENCIA_SEGUNDOS = 300;
    private static final int ESPERA_REENVIO_SEGUNDOS = 60;
    private static final int MAX_INTENTOS = 5;

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transacciones;
    private final boolean habilitada;
    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, Solicitud> solicitudes = new HashMap<>();

    public RecuperacionContrasenaService(UsuarioRepositorio usuarios, PasswordEncoder encoder,
            PlatformTransactionManager transactionManager,
            @Value("${app.recuperacion.demo.habilitada:false}") boolean habilitada) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.transacciones = new TransactionTemplate(transactionManager);
        this.habilitada = habilitada;
    }

    public synchronized ResponseCodigo solicitar(String correoIngresado) {
        String correo = exigirModoDemo(correoIngresado);
        Instant ahora = Instant.now();
        Solicitud anterior = solicitudes.get(correo);
        if (anterior != null && ahora.isBefore(anterior.generada.plusSeconds(ESPERA_REENVIO_SEGUNDOS))) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, "Espera un minuto antes de solicitar otro código");
        }
        Usuario usuario = usuarios.findByCorreoUsuarioIgnoreCase(correo)
                .filter(this::habilitado).orElseThrow(this::cuentaNoDisponible);
        String codigo = String.format(Locale.ROOT, "%05d", aleatorio.nextInt(100000));
        Solicitud solicitud = new Solicitud(usuario.getId(), usuario.getPasswordHash(), codigo, ahora);
        solicitudes.put(correo, solicitud);
        return new ResponseCodigo(codigo, VIGENCIA_SEGUNDOS, true);
    }

    public synchronized ResponseVerificacion verificar(String correoIngresado, String codigo) {
        String correo = exigirModoDemo(correoIngresado);
        Solicitud solicitud = solicitudes.get(correo);
        Instant ahora = Instant.now();
        if (solicitud == null || !ahora.isBefore(solicitud.generada.plusSeconds(VIGENCIA_SEGUNDOS))
                || solicitud.token != null || solicitud.intentos >= MAX_INTENTOS) {
            throw codigoInvalido();
        }
        if (!coincide(solicitud.codigo, codigo)) {
            solicitud.intentos++;
            throw codigoInvalido();
        }
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        solicitud.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        solicitud.tokenVence = ahora.plusSeconds(VIGENCIA_SEGUNDOS);
        return new ResponseVerificacion(solicitud.token, VIGENCIA_SEGUNDOS);
    }

    public synchronized void restablecer(String correoIngresado, String token, String nuevaContrasena) {
        String correo = exigirModoDemo(correoIngresado);
        Solicitud solicitud = solicitudes.get(correo);
        if (solicitud == null || solicitud.token == null || !Instant.now().isBefore(solicitud.tokenVence)
                || !coincide(solicitud.token, token)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La autorización de recuperación es inválida o venció");
        }
        if (nuevaContrasena == null || nuevaContrasena.isBlank() || nuevaContrasena.length() < 12
                || nuevaContrasena.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 12 caracteres y máximo 72 bytes UTF-8");
        }
        String hashNuevo = encoder.encode(nuevaContrasena);
        transacciones.executeWithoutResult(estado -> {
            Usuario usuario = usuarios.bloquearParaRestablecerContrasena(solicitud.idUsuario)
                    .filter(this::habilitado).orElseThrow(this::cuentaNoDisponible);
            if (!normalizar(usuario.getCorreoUsuario()).equals(correo)
                    || !Objects.equals(usuario.getPasswordHash(), solicitud.hashAnterior)
                    || !Instant.now().isBefore(solicitud.tokenVence)) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "La autorización de recuperación ya no es válida");
            }
            usuario.setPasswordHash(hashNuevo);
            usuarios.saveAndFlush(usuario);
        });
        solicitudes.remove(correo);
    }

    private String exigirModoDemo(String correoIngresado) {
        if (!habilitada) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "La recuperación simulada no está habilitada");
        }
        return normalizar(correoIngresado);
    }

    private boolean habilitado(Usuario usuario) {
        return new UsuarioPrincipal(usuario).isEnabled();
    }

    private String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    private boolean coincide(String esperado, String ingresado) {
        return esperado != null && ingresado != null && MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), ingresado.getBytes(StandardCharsets.UTF_8));
    }

    private NegocioException cuentaNoDisponible() {
        return new NegocioException(HttpStatus.BAD_REQUEST, "La cuenta no está disponible para recuperación simulada");
    }

    private NegocioException codigoInvalido() {
        return new NegocioException(HttpStatus.BAD_REQUEST, "Código inválido, vencido, utilizado o sin intentos disponibles");
    }

    private static final class Solicitud {
        private final Long idUsuario;
        private final String hashAnterior;
        private final String codigo;
        private final Instant generada;
        private int intentos;
        private String token;
        private Instant tokenVence;

        private Solicitud(Long idUsuario, String hashAnterior, String codigo, Instant generada) {
            this.idUsuario = idUsuario;
            this.hashAnterior = hashAnterior;
            this.codigo = codigo;
            this.generada = generada;
        }
    }
}
