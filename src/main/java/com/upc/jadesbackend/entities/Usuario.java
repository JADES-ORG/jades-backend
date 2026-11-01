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
@Table(name = "usuario")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario", nullable = false)
    private Long id;

    @Size(max = 20)
    @NotNull
    @Column(name = "rol", nullable = false, length = 20)
    private String rol;

    @Size(max = 200)
    @NotNull
    @Column(name = "nombre_usuario", nullable = false, length = 200)
    private String nombreUsuario;

    @Size(max = 150)
    @NotNull
    @Column(name = "correo_usuario", nullable = false, length = 150)
    private String correoUsuario;

    @Size(max = 15)
    @Column(name = "telefono_usuario", length = 15)
    private String telefonoUsuario;

    @Size(max = 255)
    @NotNull
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "activo", nullable = false)
    private Boolean activo;


}