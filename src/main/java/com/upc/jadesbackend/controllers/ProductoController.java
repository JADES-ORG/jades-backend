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
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tiendas/{tienda}/productos")
@PreAuthorize("hasRole('ADMIN_NEGOCIO')")
public class ProductoController {
    private final ProductoService servicio;
    @PostMapping
    public ResponseEntity<ResponseProductoDTO> registrar(@PathVariable Long tienda, @Valid @RequestBody RequestProductoDTO dto, @AuthenticationPrincipal UsuarioPrincipal usuario) {
        var resultado = servicio.registrar(tienda, dto, usuario);
        return ResponseEntity.created(URI.create("/api/v1/tiendas/" + tienda + "/productos/" + resultado.id())).body(resultado);
    }
    @GetMapping
    public List<ResponseProductoDTO> listar(@PathVariable Long tienda, @RequestParam(defaultValue="true") boolean soloActivos, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.listar(tienda, soloActivos, usuario); }
    @GetMapping("/{id}")
    public ResponseProductoDTO consultar(@PathVariable Long tienda, @PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.consultar(tienda, id, usuario); }
    @PutMapping("/{id}")
    public ResponseProductoDTO actualizar(@PathVariable Long tienda, @PathVariable Long id, @Valid @RequestBody RequestProductoDTO dto, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.actualizar(tienda, id, dto, usuario); }
    @PatchMapping("/{id}/desactivar")
    public ResponseProductoDTO desactivar(@PathVariable Long tienda, @PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario) { return servicio.desactivar(tienda, id, usuario); }
}