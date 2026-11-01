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
@Table(name = "tasa_moratoria")
public class TasaMoratoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tasa_moratoria", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_condicion", nullable = false)
    private CondicionCredito idCondicion;

    @NotNull
    @Column(name = "tasa_moratoria", nullable = false, precision = 20, scale = 12)
    private BigDecimal tasaMoratoria;

    @Size(max = 10)
    @NotNull
    @Column(name = "tipo_tasa_mora", nullable = false, length = 10)
    private String tipoTasaMora;

    @Size(max = 15)
    @NotNull
    @Column(name = "periodo_tasa_mora", nullable = false, length = 15)
    private String periodoTasaMora;

    @Size(max = 15)
    @Column(name = "capitalizacion_mora", length = 15)
    private String capitalizacionMora;


}