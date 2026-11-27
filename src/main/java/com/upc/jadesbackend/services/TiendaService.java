package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestRegistrarTiendaDTO;
import com.upc.jadesbackend.dtos.RequestActualizarTiendaDTO;
import com.upc.jadesbackend.dtos.RequestConfiguracionTiendaDTO;
import com.upc.jadesbackend.dtos.ResponseTiendaDetalleDTO;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;

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
        return ResponseTiendaResumenDTO.de(tienda);
    }

    /** Tiendas vinculadas al administrador autenticado; no acepta IDs de usuario externos. */
    @Transactional(readOnly = true)
    public List<ResponseTiendaResumenDTO> listarMisTiendas(UsuarioPrincipal principal) {
        if (principal == null || !principal.isEnabled() || !"ADMIN_NEGOCIO".equals(principal.getRol())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tienes acceso a las tiendas");
        }
        return vinculos.listarTiendasVinculadas(principal.getId()).stream()
                .map(ResponseTiendaResumenDTO::de)
                .toList();
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

    @Transactional
    public ResponseTiendaResumenDTO reactivar(Long idTienda, UsuarioPrincipal principal) {
        if (principal == null || !principal.isEnabled() || !"ADMIN_SISTEMA".equals(principal.getRol())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Solo el administrador del sistema puede reactivar tiendas");
        }
        Tienda tienda = tiendas.bloquearParaBaja(idTienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        if (!"INACTIVO".equals(tienda.getEstadoTienda())) {
            throw new NegocioException(HttpStatus.CONFLICT, "La tienda no está desactivada");
        }
        String anterior = tienda.getEstadoTienda();
        tienda.setEstadoTienda("ACTIVO");
        Usuario responsable = usuarios.findById(principal.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Administrador no encontrado"));

        auditorias.save(nuevaAuditoria(responsable, tienda, "UPDATE",
                Map.of("estado_tienda", anterior), Map.of("estado_tienda", "ACTIVO")));
        return ResponseTiendaResumenDTO.de(tiendas.save(tienda));
    }


    @Transactional(readOnly = true)
    public List<ResponseTiendaDetalleDTO> listar(UsuarioPrincipal principal) {
        exigirAdministradorSistema(principal);
        List<Tienda> resultado = tiendas.findAll(Sort.by("id"));
        if (resultado.isEmpty()) return List.of();
        Map<Long, List<ResponseTiendaDetalleDTO.Administrador>> administradores =
                vinculos.listarAdministradores(resultado.stream().map(Tienda::getId).toList())
                        .stream().collect(Collectors.groupingBy(
                                vinculo -> vinculo.getIdTienda().getId(),
                                Collectors.mapping(this::datosAdministrador, Collectors.toList())));
        return resultado.stream().map(tienda -> ResponseTiendaDetalleDTO.de(tienda,
                administradores.getOrDefault(tienda.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public ResponseTiendaDetalleDTO consultarDetalle(Long idTienda, UsuarioPrincipal principal) {
        exigirAdministradorSistema(principal);
        return detalle(buscarTienda(idTienda));
    }

    @Transactional
    public ResponseTiendaDetalleDTO actualizar(Long idTienda, RequestActualizarTiendaDTO dto,
                                                UsuarioPrincipal principal) {
        exigirAdministradorSistema(principal);
        Tienda tienda = bloquearTiendaActiva(idTienda);
        String documento = dto.documentoTienda().trim();
        if (tiendas.existsByDocumentoTiendaAndIdNot(documento, idTienda)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una tienda con el documento indicado");
        }
        Map<String, Object> antes = datosTienda(tienda);
        tienda.setRazonSocial(dto.razonSocial().trim());
        tienda.setDocumentoTienda(documento);
        tienda.setDireccion(dto.direccion().trim());
        aplicarConfiguracion(tienda, dto.nombreTienda(), dto.giroDeNegocio(),
                dto.telefonoTienda(), dto.correoTienda());
        guardarCambios(tienda, antes, principal);
        return detalle(tienda);
    }

    @Transactional(readOnly = true)
    public ResponseTiendaDetalleDTO consultarConfiguracion(Long idTienda, UsuarioPrincipal principal) {
        exigirVinculo(idTienda, principal);
        return ResponseTiendaDetalleDTO.de(buscarTienda(idTienda), List.of());
    }

    @Transactional
    public ResponseTiendaDetalleDTO actualizarConfiguracion(Long idTienda, RequestConfiguracionTiendaDTO dto,
                                                            UsuarioPrincipal principal) {
        exigirVinculo(idTienda, principal);
        Tienda tienda = bloquearTiendaActiva(idTienda);
        exigirVinculo(idTienda, principal);
        Map<String, Object> antes = datosTienda(tienda);
        aplicarConfiguracion(tienda, dto.nombreTienda(), dto.giroDeNegocio(),
                dto.telefonoTienda(), dto.correoTienda());
        guardarCambios(tienda, antes, principal);
        return ResponseTiendaDetalleDTO.de(tienda, List.of());
    }

    private void exigirAdministradorSistema(UsuarioPrincipal principal) {
        if (principal == null || !principal.isEnabled() || !"ADMIN_SISTEMA".equals(principal.getRol())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Solo el administrador del sistema puede administrar tiendas");
        }
    }

    private void exigirVinculo(Long idTienda, UsuarioPrincipal principal) {
        if (principal == null || !principal.isEnabled() || !"ADMIN_NEGOCIO".equals(principal.getRol())
                || !vinculos.tieneVinculoActivo(principal.getId(), idTienda)) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tienes acceso a esta tienda");
        }
    }

    private Tienda buscarTienda(Long idTienda) {
        return tiendas.findById(idTienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
    }

    private Tienda bloquearTiendaActiva(Long idTienda) {
        Tienda tienda = tiendas.bloquearParaBaja(idTienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        if (!"ACTIVO".equals(tienda.getEstadoTienda())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "La tienda está inactiva");
        }
        return tienda;
    }

    private void aplicarConfiguracion(Tienda tienda, String nombre, String giro, String telefono, String correo) {
        tienda.setNombreTienda(nombre.trim());
        tienda.setGiroDeNegocio(giro.trim());
        tienda.setTelefonoTienda(normalizarOpcional(telefono));
        tienda.setCorreoTienda(normalizarOpcional(correo));
    }

    private String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Map<String, Object> datosTienda(Tienda tienda) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre_tienda", tienda.getNombreTienda());
        datos.put("razon_social", tienda.getRazonSocial());
        datos.put("documento_tienda", tienda.getDocumentoTienda());
        datos.put("giro_de_negocio", tienda.getGiroDeNegocio());
        datos.put("direccion", tienda.getDireccion());
        datos.put("telefono_tienda", tienda.getTelefonoTienda());
        datos.put("correo_tienda", tienda.getCorreoTienda());
        datos.put("estado_tienda", tienda.getEstadoTienda());
        return datos;
    }

    private void guardarCambios(Tienda tienda, Map<String, Object> antes, UsuarioPrincipal principal) {
        Map<String, Object> despues = datosTienda(tienda);
        if (antes.equals(despues)) return;
        Usuario responsable = usuarios.findById(principal.getId())
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Administrador no encontrado"));
        tiendas.saveAndFlush(tienda);
        auditorias.save(nuevaAuditoria(responsable, tienda, "UPDATE", antes, despues));
    }

    private ResponseTiendaDetalleDTO detalle(Tienda tienda) {
        return ResponseTiendaDetalleDTO.de(tienda, vinculos.listarAdministradores(List.of(tienda.getId()))
                .stream().map(this::datosAdministrador).toList());
    }

    private ResponseTiendaDetalleDTO.Administrador datosAdministrador(UsuarioTienda vinculo) {
        Usuario usuario = vinculo.getIdUsuario();
        return new ResponseTiendaDetalleDTO.Administrador(usuario.getId(), usuario.getNombreUsuario(),
                usuario.getCorreoUsuario(), usuario.getTelefonoUsuario(), Boolean.TRUE.equals(usuario.getActivo()));
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
