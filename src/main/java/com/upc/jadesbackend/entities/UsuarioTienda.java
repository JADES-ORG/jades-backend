package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "usuario_tienda")
public class UsuarioTienda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario_tienda", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario idUsuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tienda", nullable = false)
    private Tienda idTienda;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado_acceso", nullable = false, length = 10)
    private String estadoAcceso;

    @NotNull
    @ColumnDefault("CURRENT_DATE")
    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDate fechaAsignacion;


}