package com.upc.jadesbackend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Getter
@Embeddable
public class CondicionesAplicadas {
    @Column(name = "aplicado_dia_corte")
    private Integer diaCorte;

    @Column(name = "aplicado_dia_pago")
    private Integer diaPago;

    @Column(name = "aplicado_limite", precision = 18, scale = 2)
    private BigDecimal limite;

    @Column(name = "aplicado_plazo")
    private Integer plazo;

    @Column(name = "aplicado_tipo", length = 15)
    private String tipo;

    @Column(name = "aplicado_valor", precision = 20, scale = 12)
    private BigDecimal valor;

    @Column(name = "aplicado_periodo", length = 15)
    private String periodo;

    @Column(name = "aplicado_capitalizacion", length = 15)
    private String capitalizacion;

    @Column(name = "aplicado_tipo_mora", length = 15)
    private String tipoMora;

    @Column(name = "aplicado_valor_mora", precision = 20, scale = 12)
    private BigDecimal valorMora;

    @Column(name = "aplicado_periodo_mora", length = 15)
    private String periodoMora;

    @Column(name = "aplicado_capitalizacion_mora", length = 15)
    private String capitalizacionMora;

    @Column(name = "aplicado_fin_gracia")
    private LocalDate finGracia;

    @Column(name = "aplicado_dias_gracia")
    private Long diasGracia;

    protected CondicionesAplicadas() {}

    public CondicionesAplicadas(CondicionCredito c, TasaInteres i, TasaMoratoria m,
                                LocalDate finGracia, Long diasGracia) {
        this.diaCorte = c.getDiaCorte();
        this.diaPago = c.getDiaPago();
        this.limite = c.getLimiteCredito();
        this.plazo = c.getPlazoMaximo();
        this.tipo = i.getTipoTasa();
        this.valor = i.getTasaInteres();
        this.periodo = i.getPeriodoTasa();
        this.capitalizacion = i.getCapitalizacionTasa();
        this.tipoMora = m == null ? null : m.getTipoTasaMora();
        this.valorMora = m == null ? null : m.getTasaMoratoria();
        this.periodoMora = m == null ? null : m.getPeriodoTasaMora();
        this.capitalizacionMora = m == null ? null : m.getCapitalizacionMora();
        this.finGracia = finGracia;
        this.diasGracia = diasGracia;
    }
}
