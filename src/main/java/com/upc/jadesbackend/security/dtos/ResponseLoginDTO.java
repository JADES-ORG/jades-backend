package com.upc.jadesbackend.security.dtos;

public record ResponseLoginDTO(String tokenAcceso, Long id, String correo, String rol) {}
