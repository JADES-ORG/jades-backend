package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "credito")
public class Credito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credito", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta", nullable = false)
    private Venta idVenta;

    @NotNull
    @Column(name = "capital_venta", nullable = false, precision = 18, scale = 2)
    private BigDecimal capitalVenta;

    @Size(max = 20)
    @NotNull
    @Column(name = "codigo_credito", nullable = false, length = 20)
    private String codigoCredito;

    @NotNull
    @Column(name = "capital_capitalizado", nullable = false, precision = 18, scale = 2)
    private BigDecimal capitalCapitalizado;

    @NotNull
    @Column(name = "total_financiamiento", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalFinanciamiento;

    @NotNull
    @Column(name = "saldo_credito", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoCredito;

    @NotNull
    @Column(name = "fecha_corte", nullable = false)
    private Instant fechaCorte;

    @NotNull
    @Column(name = "fecha_primer_pago", nullable = false)
    private LocalDate fechaPrimerPago;

    @Column(name = "cuota_mensual", precision = 18, scale = 2)
    private BigDecimal cuotaMensual;

    @NotNull
    @Column(name = "intereses_totales", nullable = false, precision = 18, scale = 2)
    private BigDecimal interesesTotales;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'PENDIENTE'")
    @Column(name = "estado_credito", nullable = false, length = 10)
    private String estadoCredito;

    @NotNull
    @Column(name = "tasa_efectiva_diaria_aplicada", nullable = false, precision = 20, scale = 12)
    private BigDecimal tasaEfectivaDiariaAplicada;

    @NotNull
    @Column(name = "tasa_mora_diaria_aplicada", nullable = false, precision = 20, scale = 12)
    private BigDecimal tasaMoraDiariaAplicada;


}