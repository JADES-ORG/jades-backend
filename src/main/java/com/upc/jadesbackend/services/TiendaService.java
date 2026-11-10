package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestRegistrarTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseBajaTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseTiendaResumenDTO;
import com.upc.jadesbackend.entities.Auditoria;
import com.upc.jadesbackend.entities.Tienda;
import com.upc.jadesbackend.entities.Usuario;
import com.upc.jadesbackend.entities.UsuarioTienda;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.AuditoriaRepositorio;
import com.upc.jadesbackend.repositories.TiendaRepositorio;
import com.upc.jadesbackend.repositories.UsuarioRepositorio;
import com.upc.jadesbackend.repositories.UsuarioTiendaRepositorio;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class TiendaService {
    private final TiendaRepositorio tiendas;
    private final UsuarioRepositorio usuarios;
    private final UsuarioTiendaRepositorio vinculos;
    private final AuditoriaRepositorio auditorias;
    private final PasswordEncoder encoder;

    public TiendaService(TiendaRepositorio tiendas, UsuarioRepositorio usuarios,
                         UsuarioTiendaRepositorio vinculos, AuditoriaRepositorio auditorias,
                         PasswordEncoder encoder) {
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.vinculos = vinculos;
        this.auditorias = auditorias;
        this.encoder = encoder;
    }

    @Transactional
    public ResponseTiendaDTO registrar(RequestRegistrarTiendaDTO dto, Long idResponsable) {
        String documento = dto.documentoTienda().trim();
        String correoAdmin = dto.administrador().correoUsuario().trim().toLowerCase(java.util.Locale.ROOT);
        if (tiendas.existsByDocumentoTienda(documento)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una tienda con el documento indicado");
        }
        if (usuarios.existsByCorreoUsuarioIgnoreCase(correoAdmin)) {
            throw new NegocioException(HttpStatus.CONFLICT, "El correo del administrador ya está registrado");
        }

        Tienda tienda = new Tienda();
        tienda.setNombreTienda(dto.nombreTienda().trim());
        tienda.setRazonSocial(dto.razonSocial().trim());
        tienda.setDocumentoTienda(documento);
        tienda.setGiroDeNegocio(dto.giroDeNegocio().trim());
        tienda.setDireccion(dto.direccion().trim());
        tienda.setTelefonoTienda(dto.telefonoTienda());
        tienda.setCorreoTienda(dto.correoTienda());
        tienda.setEstadoTienda("ACTIVO");
        tienda = tiendas.saveAndFlush(tienda);

        Usuario administrador = new Usuario();
        administrador.setRol("ADMIN_NEGOCIO"); // Rol existente en el proyecto del equipo
        administrador.setNombreUsuario(dto.administrador().nombreUsuario().trim());
        administrador.setCorreoUsuario(correoAdmin);
        administrador.setTelefonoUsuario(dto.administrador().telefonoUsuario());
        administrador.setPasswordHash(encoder.encode(dto.administrador().contrasena()));
        administrador.setActivo(true);
        administrador = usuarios.saveAndFlush(administrador);

        UsuarioTienda vinculo = new UsuarioTienda();
        vinculo.setIdUsuario(administrador);
        vinculo.setIdTienda(tienda);
        vinculo.setEstadoAcceso("ACTIVO");
        vinculo.setFechaAsignacion(LocalDate.now());
        vinculos.save(vinculo);

        // Registrar quién dio de alta, también aprovechando la entidad existente.
        Usuario responsable = usuarios.findById(idResponsable)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Administrador no encontrado"));
        Auditoria auditoria = nuevaAuditoria(responsable, tienda, "INSERT", null,
                Map.of("estado_tienda", "ACTIVO", "documento_tienda", documento));
        auditorias.save(auditoria);
        return new ResponseTiendaDTO(tienda.getId(), tienda.getNombreTienda(), documento,
                tienda.getEstadoTienda(), administrador.getId(), correoAdmin);
    }

    @Transactional(readOnly = true)
    public ResponseTiendaResumenDTO consultar(Long idTienda) {
        Tienda tienda = tiendas.findById(idTienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        return new ResponseTiendaResumenDTO(tienda.getId(), tienda.getNombreTienda(),
                tienda.getDocumentoTienda(), tienda.getEstadoTienda());
    }

    @Transactional
    public ResponseBajaTiendaDTO desactivar(Long idTienda, UsuarioPrincipal principal) {
        // Bloqueo de escritura para serializar la desactivación con ventas en curso.
        Tienda tienda = tiendas.bloquearParaBaja(idTienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        if (!"ACTIVO".equals(tienda.getEstadoTienda())) {
            throw new NegocioException(HttpStatus.CONFLICT, "La tienda ya está desactivada");
        }
        String anterior = tienda.getEstadoTienda();
        tienda.setEstadoTienda("INACTIVO");
        Usuario responsable = usuarios.findById(principal.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Administrador no encontrado"));

        Auditoria auditoria = nuevaAuditoria(responsable, tienda, "UPDATE",
                Map.of("estado_tienda", anterior), Map.of("estado_tienda", "INACTIVO"));
        auditorias.save(auditoria);
        tiendas.save(tienda);
        return new ResponseBajaTiendaDTO(tienda.getId(), tienda.getEstadoTienda(), principal.getId(), auditoria.getFechaAccion());
    }

    private Auditoria nuevaAuditoria(Usuario responsable, Tienda tienda, String accion,
                                     Map<String, Object> antes, Map<String, Object> despues) {
        Auditoria a = new Auditoria();
        a.setIdUsuario(responsable);
        a.setTablaAfectada("tienda");
        a.setIdRegistro(tienda.getId());
        a.setAccion(accion); // En la tabla existente tiene límite de 6 caracteres.
        a.setDatosAnteriores(antes);
        a.setDatosNuevos(despues);
        a.setFechaAccion(OffsetDateTime.now(ZoneOffset.UTC));
        return a;
    }
}
