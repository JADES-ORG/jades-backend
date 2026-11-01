package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "condicion_credito")
public class CondicionCredito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condicion", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente idCliente;

    @NotNull
    @Column(name = "limite_credito", nullable = false, precision = 18, scale = 2)
    private BigDecimal limiteCredito;

    @NotNull
    @Column(name = "plazo_maximo", nullable = false)
    private Integer plazoMaximo;

    @NotNull
    @Column(name = "dia_corte", nullable = false)
    private Integer diaCorte;

    @NotNull
    @Column(name = "dia_pago", nullable = false)
    private Integer diaPago;

    @Size(max = 3)
    @NotNull
    @ColumnDefault("'PEN'")
    @Column(name = "moneda_credito", nullable = false, length = 3)
    private String monedaCredito;

    @NotNull
    @ColumnDefault("'18:00:00'")
    @Column(name = "hora_corte", nullable = false)
    private LocalTime horaCorte;


}