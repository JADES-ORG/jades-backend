package com.upc.jadesbackend.services;

import com.upc.jadesbackend.dtos.RequestCondicionCrediticiaDTO;
import com.upc.jadesbackend.dtos.ResponseCondicionCrediticiaDTO;
import com.upc.jadesbackend.entities.Cliente;
import com.upc.jadesbackend.entities.CondicionCredito;
import com.upc.jadesbackend.entities.TasaInteres;
import com.upc.jadesbackend.entities.TasaMoratoria;
import com.upc.jadesbackend.exceptions.NegocioException;
import com.upc.jadesbackend.repositories.ClienteRepositorio;
import com.upc.jadesbackend.repositories.CondicionCrediticiaRepositorio;
import com.upc.jadesbackend.repositories.TasaInteresRepositorio;
import com.upc.jadesbackend.repositories.TasaMoratoriaRepositorio;
import com.upc.jadesbackend.security.principal.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;

// HU-07 (David K.) Se han adicionado los servicios
@Service
@RequiredArgsConstructor
@Transactional
public class CondicionCreditoService {
    private static final LocalTime HORA_CORTE_DEFECTO = LocalTime.of(18, 0);

    /** Días por periodo según el año comercial 360/30, usado para validar la capitalización. */
    private static final Map<String, Integer> DIAS_PERIODO = Map.of(
            "DIARIA", 1, "QUINCENAL", 15, "MENSUAL", 30, "BIMESTRAL", 60,
            "TRIMESTRAL", 90, "CUATRIMESTRAL", 120, "SEMESTRAL", 180, "ANUAL", 360);

    private final CondicionCrediticiaRepositorio condiciones;
    private final TasaInteresRepositorio tasasInteres;
    private final TasaMoratoriaRepositorio tasasMoratorias;
    private final ClienteRepositorio clientes;
    private final AccesoNegocioService acceso;
    private final EvaluacionCreditoService evaluacion;

    public ResponseCondicionCrediticiaDTO registrar(Long tienda, Long idCliente, RequestCondicionCrediticiaDTO dto, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        Cliente cliente = buscarCliente(tienda, idCliente);
        if (!"ACTIVO".equals(cliente.getEstadoCliente()))
            throw new NegocioException(HttpStatus.CONFLICT, "No se pueden configurar condiciones para un cliente inactivo");
        if (condiciones.existsByIdCliente_Id(idCliente))
            throw new NegocioException(HttpStatus.CONFLICT, "El cliente ya tiene condiciones crediticias; usa la actualización");
        validarReglas(dto);

        CondicionCredito c = new CondicionCredito();
        c.setIdCliente(cliente);
        aplicarCondicion(c, dto);
        c = condiciones.save(c);

        TasaInteres ti = new TasaInteres();
        ti.setIdCondicion(c);
        aplicarTasaInteres(ti, dto);

        TasaMoratoria tm = new TasaMoratoria();
        tm.setIdCondicion(c);
        aplicarTasaMoratoria(tm, dto);

        return ResponseCondicionCrediticiaDTO.de(c, tasasInteres.save(ti), tasasMoratorias.save(tm));
    }

    public ResponseCondicionCrediticiaDTO actualizar(Long tienda, Long idCliente, RequestCondicionCrediticiaDTO dto, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        buscarCliente(tienda, idCliente);
        validarReglas(dto);
        CondicionCredito c = condiciones.bloquearPorCliente(idCliente).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Condiciones crediticias no encontradas"));
        if (!dto.monedaCredito().equals(c.getMonedaCredito()))
            throw new NegocioException(HttpStatus.CONFLICT, "La moneda del crédito no puede modificarse después de su configuración inicial");
        var utilizado = evaluacion.creditoUtilizado(idCliente);
        if (dto.limiteCredito().compareTo(utilizado) < 0)
            throw new NegocioException(HttpStatus.CONFLICT, "El nuevo límite es inferior al crédito utilizado");
        TasaInteres ti = buscarTasaInteres(c);
        TasaMoratoria tm = buscarTasaMoratoria(c);

        aplicarCondicion(c, dto);
        aplicarTasaInteres(ti, dto);
        aplicarTasaMoratoria(tm, dto);
        return ResponseCondicionCrediticiaDTO.de(condiciones.save(c), tasasInteres.save(ti), tasasMoratorias.save(tm));
    }

    @Transactional(readOnly = true)
    public ResponseCondicionCrediticiaDTO consultar(Long tienda, Long idCliente, UsuarioPrincipal usuario) {
        acceso.exigir(tienda, usuario);
        buscarCliente(tienda, idCliente);
        CondicionCredito c = buscarCondicion(idCliente);
        return ResponseCondicionCrediticiaDTO.de(c, buscarTasaInteres(c), buscarTasaMoratoria(c));
    }

    // HU-07 Reglas de negocio adicionales

    void validarReglas(RequestCondicionCrediticiaDTO dto) {
        if (dto.tasaInteres().compareTo(BigDecimal.ONE) > 0 || dto.tasaMoratoria().compareTo(BigDecimal.ONE) > 0)
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Las tasas deben ser fracciones decimales: 18% = 0.18");
        if (dto.diaCorte().equals(dto.diaPago()))
            throw new NegocioException(HttpStatus.BAD_REQUEST, "El día de pago debe ser distinto al día de corte");
        validarTasa("compensatoria", dto.tipoTasa(), dto.periodoTasa(), dto.capitalizacionTasa());
        validarTasa("moratoria", dto.tipoTasaMora(), dto.periodoTasaMora(), dto.capitalizacionMora());
    }

    private void validarTasa(String nombre, String tipo, String periodo, String capitalizacion) {
        if ("NOMINAL".equals(tipo)) {
            if (capitalizacion == null || capitalizacion.isBlank())
                throw new NegocioException(HttpStatus.BAD_REQUEST,
                        "La tasa " + nombre + " nominal requiere el periodo de capitalización");
            if (DIAS_PERIODO.get(capitalizacion) > DIAS_PERIODO.get(periodo))
                throw new NegocioException(HttpStatus.BAD_REQUEST,
                        "La capitalización de la tasa " + nombre + " no puede ser mayor que su periodo");
        } else if (capitalizacion != null && !capitalizacion.isBlank()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La tasa " + nombre + " efectiva no lleva periodo de capitalización");
        }
    }

    // HU-07 Realización de transferencia de datos de los DTO a las entidades

    private void aplicarCondicion(CondicionCredito c, RequestCondicionCrediticiaDTO dto) {
        c.setMonedaCredito(dto.monedaCredito());
        c.setLimiteCredito(dto.limiteCredito());
        c.setPlazoMaximo(dto.plazoMaximo());
        c.setDiaCorte(dto.diaCorte());
        c.setDiaPago(dto.diaPago());
        c.setHoraCorte(dto.horaCorte() == null ? HORA_CORTE_DEFECTO : dto.horaCorte());
    }

    private void aplicarTasaInteres(TasaInteres ti, RequestCondicionCrediticiaDTO dto) {
        ti.setTipoTasa(dto.tipoTasa());
        ti.setTasaInteres(dto.tasaInteres());
        ti.setPeriodoTasa(dto.periodoTasa());
        ti.setCapitalizacionTasa("NOMINAL".equals(dto.tipoTasa()) ? dto.capitalizacionTasa() : null);
    }

    private void aplicarTasaMoratoria(TasaMoratoria tm, RequestCondicionCrediticiaDTO dto) {
        tm.setTipoTasaMora(dto.tipoTasaMora());
        tm.setTasaMoratoria(dto.tasaMoratoria());
        tm.setPeriodoTasaMora(dto.periodoTasaMora());
        tm.setCapitalizacionMora("NOMINAL".equals(dto.tipoTasaMora()) ? dto.capitalizacionMora() : null);
    }

    // HU07 Metodos de busqueda

    private Cliente buscarCliente(Long tienda, Long idCliente) {
        return clientes.findByIdAndIdTienda_Id(idCliente, tienda).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado en esta tienda"));
    }

    private CondicionCredito buscarCondicion(Long idCliente) {
        return condiciones.findByIdCliente_Id(idCliente).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "El cliente aún no tiene condiciones crediticias"));
    }

    private TasaInteres buscarTasaInteres(CondicionCredito c) {
        return tasasInteres.findByIdCondicion_Id(c.getId()).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "La condición no tiene tasa compensatoria registrada"));
    }

    private TasaMoratoria buscarTasaMoratoria(CondicionCredito c) {
        return tasasMoratorias.findByIdCondicion_Id(c.getId()).orElseThrow(() ->
                new NegocioException(HttpStatus.NOT_FOUND, "La condición no tiene tasa moratoria registrada"));
    }
}