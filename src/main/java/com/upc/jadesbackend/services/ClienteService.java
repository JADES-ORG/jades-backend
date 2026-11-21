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
        aplicar(tienda, e, new RequestClienteDTO(dto.nombreCliente(), dto.dniCliente(), dto.telefonoCliente(), correo));
        if (usuarios.existsByCorreoUsuarioIgnoreCase(correo))
            throw new NegocioException(HttpStatus.CONFLICT, "El correo de acceso ya está registrado");
        Usuario cuenta = new Usuario();
        cuenta.setRol("CLIENTE");
        cuenta.setActivo(true);
        cuenta.setNombreUsuario(dto.nombreCliente());
        cuenta.setCorreoUsuario(correo);
        cuenta.setTelefonoUsuario(dto.telefonoCliente());
        cuenta.setPasswordHash(encoder.encode(dto.contrasenaTemporal()));
        e.setIdUsuario(usuarios.save(cuenta));
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    public ResponseClienteDTO actualizar(Long tienda, Long id, RequestClienteDTO dto, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente e = buscar(tienda, id);
        aplicar(tienda, e, dto);
        Usuario cuenta = e.getIdUsuario();
        if (cuenta != null) {
            if (dto.correoCliente() == null || dto.correoCliente().isBlank() || dto.correoCliente().length() > 150)
                throw new NegocioException(HttpStatus.BAD_REQUEST, "Se requiere un correo válido para la cuenta vinculada");
            String correo = dto.correoCliente().trim().toLowerCase(Locale.ROOT);
            if (usuarios.findByCorreoUsuarioIgnoreCase(correo).filter(u -> !u.getId().equals(cuenta.getId())).isPresent())
                throw new NegocioException(HttpStatus.CONFLICT, "El correo de acceso ya está registrado");
            cuenta.setCorreoUsuario(correo);
            e.setCorreoCliente(correo);
        }
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    public ResponseClienteDTO desactivar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente e = buscar(tienda, id);
        e.setEstadoCliente("INACTIVO");
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    @Transactional(readOnly=true)
    public List<ResponseClienteDTO> listar(Long tienda, boolean soloActivos, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        return (soloActivos ? repositorio.findByIdTienda_IdAndEstadoClienteOrderByIdAsc(tienda, "ACTIVO") : repositorio.findByIdTienda_IdOrderByIdAsc(tienda))
                .stream().map(ResponseClienteDTO::de).toList();
    }
    @Transactional(readOnly=true)
    public ResponseClienteDTO consultar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        return ResponseClienteDTO.de(buscar(tienda, id));
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
        e.setCorreoCliente(dto.correoCliente());
    }
}