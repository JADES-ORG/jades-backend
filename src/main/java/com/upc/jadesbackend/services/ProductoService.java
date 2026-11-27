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
import java.util.Objects;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
@Service
@RequiredArgsConstructor
@Transactional
public class ProductoService {
    private final ProductoRepositorio repositorio;
    private final AccesoNegocioService acceso;
    private final ImagenProductoStorage imagenes;
    public ResponseProductoDTO registrar(Long tienda, RequestProductoDTO dto, MultipartFile archivo, UsuarioPrincipal usuario) {
        Producto e = new Producto();
        e.setIdTienda(acceso.exigir(tienda, usuario));
        e.setEstadoProducto("ACTIVO");
        aplicar(tienda, e, dto);
        if (archivo == null || archivo.isEmpty())
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Selecciona una imagen");
        repositorio.saveAndFlush(e);
        reemplazarImagen(e, tienda, archivo);
        return ResponseProductoDTO.de(repositorio.saveAndFlush(e));
    }
    public ResponseProductoDTO actualizar(
            Long tienda,
            Long id,
            RequestProductoDTO dto,
            MultipartFile archivo,
            UsuarioPrincipal usuario) {

        acceso.exigir(tienda, usuario);

        Producto producto = bloquear(tienda, id);

        aplicar(tienda, producto, dto);

        if (archivo != null) {
            reemplazarImagen(producto, tienda, archivo);
        } else if (producto.getImagenProducto() == null || producto.getImagenProducto().isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Selecciona una imagen");
        }

        return ResponseProductoDTO.de(
                repositorio.saveAndFlush(producto)
        );
    }
    public ResponseProductoDTO desactivar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Producto e = bloquear(tienda, id);
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
    private Producto bloquear(Long tienda, Long id) {
        return repositorio.bloquearPorIdYTienda(id, tienda).orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Producto no encontrado en esta tienda"));
    }
    private void aplicar(Long tienda, Producto e, RequestProductoDTO dto) {
        if ("SERVICIO".equals(dto.tipoProducto()) && (dto.proveedor() != null && !dto.proveedor().isBlank() || dto.marca() != null && !dto.marca().isBlank()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Proveedor y marca solo corresponden a productos");
        if (dto.imagenProducto() != null && !dto.imagenProducto().isBlank()
                && !Objects.equals(dto.imagenProducto(), e.getImagenProducto())) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La imagen se modifica mediante la carga de un archivo");
        }
        e.setNombreProducto(dto.nombreProducto());
        e.setDescripcion(dto.descripcion());
        e.setPrecioContado(dto.precioContado());
        e.setPrecioCredito(dto.precioCredito());
        e.setTipoProducto(dto.tipoProducto());
        e.setProveedor(dto.proveedor());
        e.setMarca(dto.marca());
        e.setUnidadMedida(dto.unidadMedida());
        e.setModalidad(dto.modalidad());
    }

    //subir imagen
    public ResponseProductoDTO subirImagen(
            Long tienda,
            Long id,
            MultipartFile archivo,
            UsuarioPrincipal usuario) {

        acceso.exigir(tienda, usuario);

        Producto producto = bloquear(tienda, id);
        reemplazarImagen(producto, tienda, archivo);

        return ResponseProductoDTO.de(repositorio.saveAndFlush(producto));
    }

    private void reemplazarImagen(Producto producto, Long tienda, MultipartFile archivo) {
        String urlAnterior = producto.getImagenProducto();
        String anterior = imagenes.nombreGuardado(urlAnterior, tienda, producto.getId());
        boolean eliminarAnterior = anterior != null
                && !repositorio.existsByImagenProductoAndIdNot(urlAnterior, producto.getId());
        String nombre = imagenes.guardar(archivo);

        // Si la transacción de base de datos falla, elimina el archivo nuevo.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            imagenes.eliminar(nombre);
                        } else if (status == STATUS_COMMITTED && eliminarAnterior) {
                            imagenes.eliminar(anterior);
                        }
                    }
                }
        );

        producto.setImagenProducto(imagenes.url(tienda, producto.getId(), nombre));
    }

    @Transactional(readOnly = true)
    public ByteArrayResource consultarImagen(
            Long tienda,
            Long id,
            String nombre,
            UsuarioPrincipal usuario) {

        acceso.exigirParaConsulta(tienda, usuario);

        Producto producto = buscar(tienda, id);

        if (!imagenes.corresponde(
                producto.getImagenProducto(), tienda, id, nombre)) {
            throw new NegocioException(
                    HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }

        return imagenes.leer(nombre);
    }
}