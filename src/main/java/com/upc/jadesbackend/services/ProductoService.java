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
public class ProductoService {
    private final ProductoRepositorio repositorio;
    private final AccesoNegocioService acceso;
    public ResponseProductoDTO registrar(Long tienda, RequestProductoDTO dto, UsuarioPrincipal usuario) {
        Producto e = new Producto();
        e.setIdTienda(acceso.exigir(tienda, usuario));
        e.setEstadoProducto("ACTIVO");
        aplicar(tienda, e, dto);
        return ResponseProductoDTO.de(repositorio.save(e));
    }
    public ResponseProductoDTO actualizar(
            Long tienda,
            Long id,
            RequestProductoDTO dto,
            UsuarioPrincipal usuario) {

        acceso.exigir(tienda, usuario);

        Producto producto = buscar(tienda, id);

        aplicar(tienda, producto, dto);

        return ResponseProductoDTO.de(
                repositorio.save(producto)
        );
    }
    public ResponseProductoDTO desactivar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Producto e = buscar(tienda, id);
        e.setEstadoProducto("INACTIVO");
        return ResponseProductoDTO.de(repositorio.save(e));
    }
    @Transactional(readOnly=true)
    public List<ResponseProductoDTO> listar(Long tienda, boolean soloActivos, UsuarioPrincipal usuario) {
        acceso.exigirParaConsulta(tienda, usuario);
        return (soloActivos ? repositorio.findByIdTienda_IdAndEstadoProductoOrderByIdAsc(tienda, "ACTIVO") : repositorio.findByIdTienda_IdOrderByIdAsc(tienda))
                .stream().map(ResponseProductoDTO::de).toList();
    }
    @Transactional(readOnly=true)
    public ResponseProductoDTO consultar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigirParaConsulta(tienda, usuario);
        return ResponseProductoDTO.de(buscar(tienda, id));
    }
    private Producto buscar(Long tienda, Long id) {
        return repositorio.findByIdAndIdTienda_Id(id, tienda).orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Producto no encontrado en esta tienda"));
    }
    private void aplicar(Long tienda, Producto e, RequestProductoDTO dto) {
        if ("SERVICIO".equals(dto.tipoProducto()) && (dto.proveedor() != null && !dto.proveedor().isBlank() || dto.marca() != null && !dto.marca().isBlank()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Proveedor y marca solo corresponden a productos");
        if (dto.imagenProducto() != null && !dto.imagenProducto().isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(dto.imagenProducto());
                if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "La imagen debe ser una URL HTTP o HTTPS válida");
            }
        }
        e.setNombreProducto(dto.nombreProducto());
        e.setDescripcion(dto.descripcion());
        e.setPrecioContado(dto.precioContado());
        e.setPrecioCredito(dto.precioCredito());
        e.setTipoProducto(dto.tipoProducto());
        e.setProveedor(dto.proveedor());
        e.setMarca(dto.marca());
        e.setUnidadMedida(dto.unidadMedida());
        e.setImagenProducto(dto.imagenProducto());
        e.setModalidad(dto.modalidad());
    }
}