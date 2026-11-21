package com.upc.jadesbackend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "cliente")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tienda", nullable = false)
    private Tienda idTienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario idUsuario;

    @Size(max = 200)
    @NotNull
    @Column(name = "nombre_cliente", nullable = false, length = 200)
    private String nombreCliente;

    @Size(max = 8)
    @NotNull
    @Column(name = "dni_cliente", nullable = false, length = 8)
    private String dniCliente;

    @Size(max = 15)
    @Column(name = "telefono_cliente", length = 15)
    private String telefonoCliente;

    @Size(max = 200)
    @Column(name = "correo_cliente", length = 200)
    private String correoCliente;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado_cliente", nullable = false, length = 10)
    private String estadoCliente;


}