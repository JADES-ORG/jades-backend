package com.upc.jadesbackend.services;
import com.upc.jadesbackend.dtos.*;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {
    private final ClienteRepositorio repositorio;
    private final AccesoNegocioService acceso;
    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;

    public ResponseClienteDTO registrar(Long tienda, RequestAltaClienteDTO dto, UsuarioPrincipal usuario) {
        Cliente e = new Cliente();
        e.setIdTienda(acceso.exigir(tienda, usuario));
        e.setEstadoCliente("ACTIVO");
        String correo = dto.correoCliente().trim().toLowerCase(Locale.ROOT);
        aplicar(tienda, e, new RequestClienteDTO(dto.nombreCliente(), dto.dniCliente(), dto.telefonoCliente()));
        e.setCorreoCliente(correo);
        Usuario cuenta = usuarios.findByCorreoUsuarioIgnoreCase(correo).orElse(null);
        if (cuenta != null) {
            if (!"CLIENTE".equals(cuenta.getRol()) || !Boolean.TRUE.equals(cuenta.getActivo()))
                throw new NegocioException(HttpStatus.CONFLICT, "No es posible vincular esa cuenta");
            List<Cliente> fichas = repositorio.findAllByIdUsuario_IdOrderByIdAsc(cuenta.getId());
            if (fichas.isEmpty() || fichas.stream().anyMatch(f -> !dto.dniCliente().equals(f.getDniCliente())))
                throw new NegocioException(HttpStatus.CONFLICT, "El correo y DNI no corresponden a la misma cuenta");
            if (repositorio.findByIdUsuario_IdAndIdTienda_Id(cuenta.getId(), tienda).isPresent())
                throw new NegocioException(HttpStatus.CONFLICT, "La cuenta ya está vinculada a esta tienda");
        } else {
            if (dto.contrasenaTemporal() == null || dto.contrasenaTemporal().isBlank())
                throw new NegocioException(HttpStatus.BAD_REQUEST, "La contraseña temporal es obligatoria para una cuenta nueva");
            cuenta = new Usuario();
            cuenta.setRol("CLIENTE");
            cuenta.setActivo(true);
            cuenta.setNombreUsuario(dto.nombreCliente());
            cuenta.setCorreoUsuario(correo);
            cuenta.setTelefonoUsuario(dto.telefonoCliente());
            cuenta.setPasswordHash(encoder.encode(dto.contrasenaTemporal()));
            cuenta = usuarios.save(cuenta);
        }
        e.setIdUsuario(cuenta);
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    public ResponseClienteDTO actualizar(Long tienda, Long id, RequestClienteDTO dto, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente e = repositorio.bloquearPorIdYTienda(id, tienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
        if (e.getIdUsuario() != null && !e.getDniCliente().equals(dto.dniCliente()))
            throw new NegocioException(HttpStatus.CONFLICT, "No se puede modificar el DNI de una cuenta vinculada");
        aplicar(tienda, e, dto);
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    public ResponseClienteDTO desactivar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente e = repositorio.bloquearPorIdYTienda(id, tienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
        e.setEstadoCliente("INACTIVO");
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    public ResponseClienteDTO reactivar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente e = repositorio.bloquearPorIdYTienda(id, tienda)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
        e.setEstadoCliente("ACTIVO");
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    @Transactional(readOnly=true)
    public List<ResponseClienteDTO> listar(Long tienda, boolean soloActivos, UsuarioPrincipal usuario) {
        acceso.exigirParaConsulta(tienda, usuario);
        return (soloActivos ? repositorio.findByIdTienda_IdAndEstadoClienteOrderByIdAsc(tienda, "ACTIVO") : repositorio.findByIdTienda_IdOrderByIdAsc(tienda))
                .stream().map(ResponseClienteDTO::de).toList();
    }
    @Transactional(readOnly=true)
    public ResponseClienteDTO consultar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigirParaConsulta(tienda, usuario);
        return ResponseClienteDTO.de(buscar(tienda, id));
    }
    @Transactional(readOnly = true)
    public Cliente consultarFichaPropia(Long idUsuario, Long idTienda) {
        if (idTienda != null) {
            return repositorio.findByIdUsuario_IdAndIdTienda_Id(idUsuario, idTienda)
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "No tienes una cuenta en esta tienda"));
        }
        List<Cliente> fichas = repositorio.findAllByIdUsuario_IdOrderByIdAsc(idUsuario);
        if (fichas.isEmpty())
            throw new NegocioException(HttpStatus.NOT_FOUND, "No existe un cliente vinculado al usuario autenticado");
        if (fichas.size() > 1)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Selecciona la tienda que deseas consultar");
        return fichas.get(0);
    }

    private Cliente buscar(Long tienda, Long id) {
        return repositorio.findByIdAndIdTienda_Id(id, tienda).orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
    }
    private void aplicar(Long tienda, Cliente e, RequestClienteDTO dto) {
        if (repositorio.existsByIdTienda_IdAndDniClienteAndIdNot(tienda, dto.dniCliente(), e.getId() == null ? -1L : e.getId()))
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe un cliente con este DNI en la tienda");
        e.setNombreCliente(dto.nombreCliente());
        e.setDniCliente(dto.dniCliente());
        e.setTelefonoCliente(dto.telefonoCliente());

    }
}