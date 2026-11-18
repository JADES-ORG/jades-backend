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
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {
    private final ClienteRepositorio repositorio;
    private final AccesoNegocioService acceso;
    public ResponseClienteDTO registrar(Long tienda, RequestClienteDTO dto, UsuarioPrincipal usuario) {
        Cliente e = new Cliente();
        e.setIdTienda(acceso.exigir(tienda, usuario));
        e.setEstadoCliente("ACTIVO");
        aplicar(tienda, e, dto);
        return ResponseClienteDTO.de(repositorio.save(e));
    }
    public ResponseClienteDTO actualizar(Long tienda, Long id, RequestClienteDTO dto, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente e = buscar(tienda, id);
        aplicar(tienda, e, dto);
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