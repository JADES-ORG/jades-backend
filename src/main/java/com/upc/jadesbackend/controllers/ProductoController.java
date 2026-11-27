package com.upc.jadesbackend.controllers;
import com.upc.jadesbackend.dtos.*;
import com.upc.jadesbackend.services.ProductoService;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tiendas/{tienda}/productos")
@PreAuthorize("hasRole('ADMIN_NEGOCIO')")
public class ProductoController {
    private final ProductoService servicio;
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseProductoDTO> registrar(@PathVariable Long tienda,
            @Valid @RequestPart("producto") RequestProductoDTO dto,
            @RequestPart("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        var resultado = servicio.registrar(tienda, dto, archivo, usuario);
        return ResponseEntity.created(URI.create("/api/v1/tiendas/" + tienda + "/productos/" + resultado.id())).body(resultado);
    }
    @GetMapping
    public List<ResponseProductoDTO> listar(@PathVariable Long tienda, @RequestParam(defaultValue="true") boolean soloActivos, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.listar(tienda, soloActivos, usuario); }
    @GetMapping("/{id}")
    public ResponseProductoDTO consultar(@PathVariable Long tienda, @PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.consultar(tienda, id, usuario); }
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseProductoDTO actualizar(@PathVariable Long tienda, @PathVariable Long id, @Valid @RequestBody RequestProductoDTO dto, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.actualizar(tienda, id, dto, null, usuario); }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseProductoDTO actualizarConImagen(@PathVariable Long tienda, @PathVariable Long id,
            @Valid @RequestPart("producto") RequestProductoDTO dto,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return servicio.actualizar(tienda, id, dto, archivo, usuario);
    }
    @PatchMapping("/{id}/desactivar")
    public ResponseProductoDTO desactivar(@PathVariable Long tienda, @PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.desactivar(tienda, id, usuario); }


    @PostMapping(
        value = "/{id}/imagen",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseProductoDTO subirImagen(
            @PathVariable Long tienda,
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {

        return servicio.subirImagen(tienda, id, archivo, usuario);
    }

    @GetMapping("/{id}/imagenes/{nombre}")
    public ResponseEntity<ByteArrayResource> consultarImagen(
            @PathVariable Long tienda,
            @PathVariable Long id,
            @PathVariable String nombre,
            @AuthenticationPrincipal UsuarioPrincipal usuario) 
    {

        var imagen = servicio.consultarImagen(tienda, id, nombre, usuario);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(imagen.contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(imagen);
    }
}