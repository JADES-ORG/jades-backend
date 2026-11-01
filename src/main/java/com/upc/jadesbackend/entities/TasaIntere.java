package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "tasa_interes")
public class TasaIntere {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tasa_interes", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_condicion", nullable = false)
    private CondicionCredito idCondicion;

    @Size(max = 10)
    @NotNull
    @Column(name = "tipo_tasa", nullable = false, length = 10)
    private String tipoTasa;

    @NotNull
    @Column(name = "tasa_interes", nullable = false, precision = 20, scale = 12)
    private BigDecimal tasaInteres;

    @Size(max = 15)
    @NotNull
    @Column(name = "periodo_tasa", nullable = false, length = 15)
    private String periodoTasa;

    @Size(max = 15)
    @Column(name = "capitalizacion_tasa", length = 15)
    private String capitalizacionTasa;


}