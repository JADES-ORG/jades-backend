package com.upc.jadesbackend.services;

import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.upc.jadesbackend.dtos.RequestVentaDTO;
import com.upc.jadesbackend.dtos.ResponseVentaDTO;
import com.upc.jadesbackend.dtos.ResponseSimulacionVentaDTO;
import com.upc.jadesbackend.dtos.RequestSimularCronogramaDTO;
import com.upc.jadesbackend.dtos.ResponseCronogramaDTO;
import com.upc.jadesbackend.util.CalculoFinancieroUtil;
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
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;


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

    private final CronogramaService cronogramas;
    private final PagoUnicoService pagosUnicos;
    private final TasaInteresRepositorio tasasInteres;
    private final TasaMoratoriaRepositorio tasasMora;

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
        venta.setGraciaAdicional(0);
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

        BigDecimal total = calcularDetalles(venta, tienda, dto, condicion.getMonedaCredito());

        evaluacion.validarLimite(cliente.getId(), condicion, total);   // HU09 - criterio 1

        try {
            // flush inmediato: si otra petición con la misma clave ganó la carrera, el UNIQUE lo detecta aquí.
            ventas.saveAndFlush(venta);
        } catch (DataIntegrityViolationException e) {
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            if (clave != null && causa.contains("uk_venta_clave_idempotencia"))
                throw new NegocioException(HttpStatus.CONFLICT,
                        "Esta venta ya fue enviada y se está registrando; consulta las ventas del cliente antes de reintentar");
            throw e;
        }
        if ("MENSUAL".equals(venta.getModalidadPago())) {
            cronogramas.generar(venta.getId(), usuario.getId());
            if (evaluacion.creditoUtilizado(cliente.getId()).compareTo(condicion.getLimiteCredito()) > 0)
                throw new NegocioException(HttpStatus.CONFLICT, "El capital con la gracia supera el límite de crédito");
        } else {
            pagosUnicos.generar(venta.getId(), usuario);
        }
        return ResponseVentaDTO.de(venta);
    }

    @Transactional(readOnly = true)
    public ResponseVentaDTO consultar(Long tienda, Long id, UsuarioPrincipal usuario) {
        acceso.exigirParaConsulta(tienda, usuario);
        return ResponseVentaDTO.de(ventas.findByIdAndIdCliente_IdTienda_Id(id, tienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada en esta tienda")));
    }

    @Transactional(readOnly = true)
    public List<ResponseVentaDTO> listar(Long tienda, Long idCliente, UsuarioPrincipal usuario) {
        acceso.exigirParaConsulta(tienda, usuario);
        List<Venta> lista = idCliente == null
                ? ventas.findAllByIdCliente_IdTienda_IdOrderByFechaVentaDescHoraVentaDesc(tienda)
                : ventas.findAllByIdCliente_IdAndIdCliente_IdTienda_IdOrderByFechaVentaDescHoraVentaDesc(idCliente, tienda);
        return lista.stream().map(ResponseVentaDTO::de).toList();
    }

    /**
     * Cotización sin persistencia. Los precios, las condiciones y el reloj son del servidor.
     * La confirmación vuelve a validar los datos bajo los bloqueos de registrar().
     */
    @Transactional
    public ResponseSimulacionVentaDTO simular(Long tienda, RequestVentaDTO dto, UsuarioPrincipal usuario) {
        Tienda t = acceso.exigir(tienda, usuario);
        if (!"ACTIVO".equals(t.getEstadoTienda()))
            throw new NegocioException(HttpStatus.CONFLICT, "La tienda no está activa");
        Cliente cliente = clientes.findByIdAndIdTienda_Id(dto.idCliente(), tienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
        if (!"ACTIVO".equals(cliente.getEstadoCliente()))
            throw new NegocioException(HttpStatus.CONFLICT, "No se pueden registrar ventas a un cliente inactivo");
        CondicionCredito condicion = condiciones.findByIdCliente_Id(cliente.getId()).orElseThrow(() ->
                new NegocioException(HttpStatus.CONFLICT, "El cliente no tiene condiciones crediticias configuradas"));
        validarCuotas(dto);
        evaluacion.validarPlazo(condicion, dto.numeroCuotas());

        // Entidad transitoria para compartir la lógica de líneas; nunca se guarda.
        Venta borrador = new Venta();
        BigDecimal capital = calcularDetalles(borrador, tienda, dto, condicion.getMonedaCredito());
        if (capital.signum() <= 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El capital de la venta debe ser mayor a cero");
        BigDecimal utilizado = evaluacion.creditoUtilizado(cliente.getId());
        BigDecimal disponible = evaluacion.creditoDisponible(condicion, utilizado);
        if (capital.compareTo(disponible) > 0)
            throw new NegocioException(HttpStatus.CONFLICT, "El capital supera el crédito disponible");

        TasaInteres tasa = tasasInteres.findByIdCondicion_Id(condicion.getId()).orElseThrow(() ->
                new NegocioException(HttpStatus.CONFLICT, "El cliente no tiene tasa de interés configurada"));
        TasaMoratoria mora = tasasMora.findByIdCondicion_Id(condicion.getId()).orElseThrow(() ->
                new NegocioException(HttpStatus.CONFLICT, "Falta la tasa moratoria pactada"));

        LocalDateTime ahora = LocalDateTime.now(ZONA_NEGOCIO).truncatedTo(ChronoUnit.SECONDS);
        var ciclo = asignacionCicloService.asignar(ahora.toLocalDate(), ahora.toLocalTime(),
                dto.modalidadPago(), 0, condicion);
        ResponseCronogramaDTO cronograma = null;
        ResponseSimulacionVentaDTO.PagoUnico pagoUnico = null;
        BigDecimal reserva;
        BigDecimal totalPagar;
        try {
            // Comprobar también que la tasa histórica de mora podrá construirse al registrar.
            CalculoFinancieroUtil.tasaEfectivaDiaria(mora.getTipoTasaMora(), mora.getTasaMoratoria(),
                    mora.getPeriodoTasaMora(), mora.getCapitalizacionMora());
            if ("MENSUAL".equals(dto.modalidadPago())) {
                cronograma = cronogramas.simular(new RequestSimularCronogramaDTO(
                        capital, tasa.getTipoTasa(), tasa.getTasaInteres(), tasa.getPeriodoTasa(),
                        tasa.getCapitalizacionTasa(), dto.numeroCuotas(), ahora.toLocalDate(),
                        ahora.toLocalTime(), condicion.getDiaCorte(), condicion.getHoraCorte(),
                        condicion.getDiaPago(), 0, condicion.getMonedaCredito()));
                reserva = cronograma.capitalCapitalizado();
                totalPagar = cronograma.totalFinanciamiento();
            } else {
                var calculo = CalculoFinancieroUtil.calcularPagoUnico(capital, ahora.toLocalDate(),
                        ciclo.fechaPago(), tasa.getTipoTasa(), tasa.getTasaInteres(),
                        tasa.getPeriodoTasa(), tasa.getCapitalizacionTasa());
                pagoUnico = new ResponseSimulacionVentaDTO.PagoUnico(
                        calculo.capital(), calculo.diasFinanciamiento(), calculo.tasaEfectivaDiaria(),
                        calculo.tasaEfectivaPeriodo(), calculo.interesCompensatorio(), calculo.totalPagar());
                reserva = calculo.capital();
                totalPagar = calculo.totalPagar();
            }
        } catch (IllegalArgumentException ex) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (reserva.compareTo(disponible) > 0)
            throw new NegocioException(HttpStatus.CONFLICT, "El capital con la gracia supera el crédito disponible");
        return new ResponseSimulacionVentaDTO(cliente.getId(), cliente.getNombreCliente(), dto.modalidadPago(),
                dto.numeroCuotas(), condicion.getMonedaCredito(), ahora.toLocalDate(), ahora.toLocalTime(),
                ciclo.fechaCorte(), ciclo.fechaPago(), capital, reserva, totalPagar,
                condicion.getLimiteCredito(), utilizado, disponible, disponible.subtract(reserva),
                borrador.getDetalles().stream().map(ResponseVentaDTO.Detalle::de).toList(), cronograma, pagoUnico);
    }

    /** Calcula los mismos precios y subtotales para venta y simulación. No persiste. */
    private BigDecimal calcularDetalles(Venta venta, Long tienda, RequestVentaDTO dto, String moneda) {
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
            detalle.setNombreProducto(producto.getNombreProducto());
            detalle.setUnidadMedida(producto.getUnidadMedida());
            detalle.setCantidad(linea.cantidad());
            BigDecimal precio = switch (moneda) {
                case "PEN" -> producto.getPrecioCredito();
                case "USD" -> producto.getPrecioCreditoUsd();
                default -> throw new NegocioException(HttpStatus.BAD_REQUEST, "Moneda de crédito no admitida");
            };
            if (precio == null || precio.signum() <= 0)
                throw new NegocioException(HttpStatus.CONFLICT, "El producto no tiene un precio a crédito válido en " + moneda);
            detalle.setPrecioUnitario(precio);
            detalle.setSubtotal(precio.multiply(linea.cantidad()).setScale(2, RoundingMode.HALF_UP));
            venta.agregarDetalle(detalle);
            if (detalle.getSubtotal().signum() <= 0)
                throw new NegocioException(HttpStatus.BAD_REQUEST, "El subtotal de cada producto debe ser al menos 0.01");
            total = total.add(detalle.getSubtotal());
        }
        if (total.signum() <= 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El capital de la venta debe ser mayor a cero");
        venta.setTotalVenta(total);
        return total;
    }

    // Reglas del negocio
    private void validarCuotas(RequestVentaDTO dto) {
        if (dto.graciaAdicional() != null && dto.graciaAdicional() != 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "No se permiten meses adicionales de gracia");
        if ("UNICO".equals(dto.modalidadPago()) && dto.numeroCuotas() != 1)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "La modalidad UNICO se paga en una sola cuota");
    }

    private void validarProducto(Producto producto, RequestVentaDTO.Detalle linea, String modalidadVenta) {
        if (!"ACTIVO".equals(producto.getEstadoProducto()))
            throw new NegocioException(HttpStatus.CONFLICT, "El producto '" + producto.getNombreProducto() + "' está inactivo");
        if (!"AMBAS".equals(producto.getModalidad()) && !modalidadVenta.equals(producto.getModalidad()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El producto '" + producto.getNombreProducto()
                    + "' solo admite la modalidad " + producto.getModalidad());
        if (!Set.of("KILOGRAMO", "GRAMO", "LITRO", "MILILITRO", "METRO", "CENTIMETRO", "METRO_CUADRADO", "HORA")
                .contains(producto.getUnidadMedida()) && linea.cantidad().stripTrailingZeros().scale() > 0)
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
                && previa.getIdCliente().getId().equals(dto.idCliente())
                && Objects.equals(previa.getModalidadPago(), dto.modalidadPago())
                && Objects.equals(previa.getNumeroCuotas(), dto.numeroCuotas())
                && Objects.equals(previa.getGraciaAdicional(), dto.graciaAdicional() == null ? 0 : dto.graciaAdicional())
                && mismosDetalles(previa, dto);
        if (!mismaOperacion)
            throw new NegocioException(HttpStatus.CONFLICT, "La Idempotency-Key ya se usó para otra venta");
        return ResponseVentaDTO.de(previa);
    }

    private boolean mismosDetalles(Venta venta, RequestVentaDTO dto) {
        if (venta.getDetalles().size() != dto.detalles().size()) return false;
        Map<Long, BigDecimal> cantidades = new HashMap<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            if (cantidades.put(detalle.getIdProducto().getId(), detalle.getCantidad()) != null) return false;
        }
        Set<Long> vistos = new HashSet<>();
        for (RequestVentaDTO.Detalle detalle : dto.detalles()) {
            BigDecimal cantidad = cantidades.get(detalle.idProducto());
            if (!vistos.add(detalle.idProducto()) || cantidad == null || detalle.cantidad() == null
                    || cantidad.compareTo(detalle.cantidad()) != 0) return false;
        }
        return true;
    }
}
