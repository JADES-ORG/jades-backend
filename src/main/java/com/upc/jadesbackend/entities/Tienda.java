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
@Table(name = "tienda")
public class Tienda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tienda", nullable = false)
    private Long id;

    @Size(max = 120)
    @NotNull
    @Column(name = "nombre_tienda", nullable = false, length = 120)
    private String nombreTienda;

    @Size(max = 150)
    @NotNull
    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Size(max = 11)
    @NotNull
    @Column(name = "documento_tienda", nullable = false, length = 11)
    private String documentoTienda;

    @Size(max = 50)
    @NotNull
    @Column(name = "giro_de_negocio", nullable = false, length = 50)
    private String giroDeNegocio;

    @Size(max = 200)
    @NotNull
    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @Size(max = 15)
    @Column(name = "telefono_tienda", length = 15)
    private String telefonoTienda;

    @Size(max = 150)
    @Column(name = "correo_tienda", length = 150)
    private String correoTienda;

    @Size(max = 10)
    @NotNull
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado_tienda", nullable = false, length = 10)
    private String estadoTienda;


}