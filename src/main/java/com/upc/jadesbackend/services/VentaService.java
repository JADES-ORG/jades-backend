package com.upc.jadesbackend.services;

import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.upc.jadesbackend.dtos.RequestVentaDTO;
import com.upc.jadesbackend.dtos.ResponseVentaDTO;
import com.upc.jadesbackend.entities.*;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.*;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Transactional
// Adiciones de la HU-08 (David K.)
public class VentaService {
    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Lima");

    private static final Pattern FORMATO_CLAVE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final VentaRepositorio ventas;
    private final ClienteRepositorio clientes;
    private final ProductoRepositorio productos;
    private final CondicionCrediticiaRepositorio condiciones;
    private final TiendaRepositorio tiendas;
    private final UsuarioRepositorio usuarios;
    private final AccesoNegocioService acceso;
    private final EvaluacionCreditoService evaluacion;   // HU09
    private final AsignacionCicloService asignacionCicloService;

    public ResponseVentaDTO registrar(Long tienda, RequestVentaDTO dto, UsuarioPrincipal usuario, String claveIdempotencia) {
        acceso.exigir(tienda, usuario);

        String clave = normalizarClave(claveIdempotencia);
        if (clave != null) {
            Optional<Venta> previa = ventas.findByClaveIdempotencia(clave);
            if (previa.isPresent()) return repetirRespuesta(previa.get(), tienda, dto);
        }

        // Bloqueo compartido: evita registrar ventas mientras la tienda se está dando de baja.
        Tienda t = tiendas.bloquearParaVenta(tienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
        if (!"ACTIVO".equals(t.getEstadoTienda()))
            throw new NegocioException(HttpStatus.CONFLICT, "La tienda no está activa");

        Cliente cliente = clientes.findByIdAndIdTienda_Id(dto.idCliente(), tienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
        if (!"ACTIVO".equals(cliente.getEstadoCliente()))
            throw new NegocioException(HttpStatus.CONFLICT, "No se pueden registrar ventas a un cliente inactivo");

        // HU09: bloqueo exclusivo de la condición del cliente. Dos ventas simultáneas del mismo cliente
        // se evalúan una después de la otra, así la segunda ya ve la deuda de la primera.
        CondicionCredito condicion = condiciones.bloquearPorCliente(cliente.getId()).orElseThrow(() ->
                new NegocioException(HttpStatus.CONFLICT, "El cliente no tiene condiciones crediticias configuradas"));

        // Si un reintento con la misma Idempotency-Key esperó el bloqueo, la venta original ya se confirmó: se devuelve esa.
        if (clave != null) {
            Optional<Venta> previa = ventas.findByClaveIdempotencia(clave);
            if (previa.isPresent()) return repetirRespuesta(previa.get(), tienda, dto);
        }

        validarCuotas(dto);
        evaluacion.validarPlazo(condicion, dto.numeroCuotas());   // HU09 - criterio 2

        LocalDateTime ahora = LocalDateTime.now(ZONA_NEGOCIO);
        Venta venta = new Venta();
        venta.setIdCliente(cliente);
        venta.setIdUsuarioRegistro(usuarios.getReferenceById(usuario.getId()));
        venta.setFechaVenta(ahora.toLocalDate());
        venta.setHoraVenta(ahora.toLocalTime().truncatedTo(ChronoUnit.SECONDS));
        venta.setModalidadPago(dto.modalidadPago());
        venta.setNumeroCuotas(dto.numeroCuotas());
        venta.setGraciaAdicional(dto.graciaAdicional() == null ? 0 : dto.graciaAdicional());
        venta.setMonedaVenta(condicion.getMonedaCredito());
        venta.setEstadoVenta("VIGENTE");
        AsignacionCicloService.ResultadoCiclo resultado =
                asignacionCicloService.asignar(
                        venta.getFechaVenta(),
                        venta.getHoraVenta(),
                        venta.getModalidadPago(),
                        venta.getGraciaAdicional(),
                        condicion
                );

        venta.setFechaCorteAsignada(resultado.fechaCorte());
        venta.setFechaPagoAsignada(resultado.fechaPago());
        venta.setClaveIdempotencia(clave);

        BigDecimal total = BigDecimal.ZERO;
        Set<Long> vistos = new HashSet<>();
        for (RequestVentaDTO.Detalle linea : dto.detalles()) {
            if (!vistos.add(linea.idProducto()))
                throw new NegocioException(HttpStatus.BAD_REQUEST,
                        "El producto " + linea.idProducto() + " está repetido; indica la cantidad total en una sola línea");
            Producto producto = productos.findByIdAndIdTienda_Id(linea.idProducto(), tienda).orElseThrow(() ->
                    new NegocioException(HttpStatus.NOT_FOUND, "Producto " + linea.idProducto() + " no encontrado en esta tienda"));
            validarProducto(producto, linea, dto.modalidadPago());

            DetalleVenta detalle = new DetalleVenta();
            detalle.setIdProducto(producto);
            detalle.setCantidad(linea.cantidad());
            detalle.setPrecioUnitario(producto.getPrecioCredito());   // precio a crédito vigente al momento de la venta
            detalle.setSubtotal(producto.getPrecioCredito().multiply(linea.cantidad()).setScale(2, RoundingMode.HALF_UP));
            venta.agregarDetalle(detalle);
            total = total.add(detalle.getSubtotal());
        }
        venta.setTotalVenta(total);

        evaluacion.validarLimite(cliente.getId(), condicion, total);   // HU09 - criterio 1

        try {
            // flush inmediato: si otra petición con la misma clave ganó la carrera, el UNIQUE lo detecta aquí.
            return ResponseVentaDTO.de(ventas.saveAndFlush(venta));
        } catch (DataIntegrityViolationException e) {
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            if (clave != null && causa.contains("uk_venta_clave_idempotencia"))
                throw new NegocioException(HttpStatus.CONFLICT,
                        "Esta venta ya fue enviada y se está registrando; consulta las ventas del cliente antes de reintentar");
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ResponseVentaDTO consultar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        return ResponseVentaDTO.de(ventas.findByIdAndIdCliente_IdTienda_Id(id, tienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada en esta tienda")));
    }

    @Transactional(readOnly = true)
    public List<ResponseVentaDTO> listar(Long tienda, Long idCliente, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        List<Venta> lista = idCliente == null
                ? ventas.findAllByIdCliente_IdTienda_IdOrderByFechaVentaDescHoraVentaDesc(tienda)
                : ventas.findAllByIdCliente_IdAndIdCliente_IdTienda_IdOrderByFechaVentaDescHoraVentaDesc(idCliente, tienda);
        return lista.stream().map(ResponseVentaDTO::de).toList();
    }

    // Reglas del negocio
    private void validarCuotas(RequestVentaDTO dto) {
        if ("UNICO".equals(dto.modalidadPago()) && dto.numeroCuotas() != 1)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La modalidad UNICO se paga en una sola cuota");
    }

    private void validarProducto(Producto producto, RequestVentaDTO.Detalle linea, String modalidadVenta) {
        if (!"ACTIVO".equals(producto.getEstadoProducto()))
            throw new NegocioException(HttpStatus.CONFLICT, "El producto '" + producto.getNombreProducto() + "' está inactivo");
        if (!"AMBAS".equals(producto.getModalidad()) && !modalidadVenta.equals(producto.getModalidad()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El producto '" + producto.getNombreProducto()
                    + "' solo admite la modalidad " + producto.getModalidad());
        if (!"KILOGRAMO".equals(producto.getUnidadMedida()) && linea.cantidad().stripTrailingZeros().scale() > 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La cantidad de '" + producto.getNombreProducto()
                    + "' debe ser un número entero (" + producto.getUnidadMedida() + ")");
    }

    private String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String limpia = clave.trim();
        if (!FORMATO_CLAVE.matcher(limpia).matches())
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key debe tener entre 8 y 64 caracteres (letras, números, '-' o '_')");
        return limpia;
    }

    private ResponseVentaDTO repetirRespuesta(Venta previa, Long tienda, RequestVentaDTO dto) {
        boolean mismaOperacion = previa.getIdCliente().getIdTienda().getId().equals(tienda)
                && previa.getIdCliente().getId().equals(dto.idCliente());
        if (!mismaOperacion)
            throw new NegocioException(HttpStatus.CONFLICT, "La Idempotency-Key ya se usó para otra venta");
        return ResponseVentaDTO.de(previa);
    }
}
