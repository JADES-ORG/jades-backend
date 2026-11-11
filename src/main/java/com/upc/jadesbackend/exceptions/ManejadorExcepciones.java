package com.upc.jadesbackend.exceptions;

import com.upc.jadesbackend.dtos.ResponseErrorDTO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ManejadorExcepciones {
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ResponseErrorDTO> manejarNegocio(NegocioException e) {
        return construir(e.getEstado(), e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseErrorDTO> manejarValidacion(MethodArgumentNotValidException e) {
        Map<String, String> detalles = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                detalles.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return construir(HttpStatus.BAD_REQUEST, "Revisa los campos obligatorios o inválidos", detalles);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseErrorDTO> manejarIntegridad(DataIntegrityViolationException e) {
        return construir(HttpStatus.CONFLICT, "El documento, correo o vínculo ya existe, o no cumple las restricciones de la base de datos", Map.of());
    }

    private ResponseEntity<ResponseErrorDTO> construir(HttpStatus estado, String mensaje, Map<String, String> errores) {
        return ResponseEntity.status(estado).body(new ResponseErrorDTO(
                OffsetDateTime.now(ZoneOffset.UTC), estado.value(), mensaje, errores));
    }
}
