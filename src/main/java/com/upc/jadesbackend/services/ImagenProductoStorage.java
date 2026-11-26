package com.upc.jadesbackend.services;

import com.upc.jadesbackend.exceptions.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImagenProductoStorage {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELES = 20_000_000L;

    private final Path directorio;
    private final String baseUrl;

    public ImagenProductoStorage(
            @Value("${app.productos.imagenes.directorio:./uploads/productos}")
            String directorio,
            @Value("${app.productos.imagenes.base-url:http://localhost:8080}")
            String baseUrl) {

        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "");

        try {
            var uri = java.net.URI.create(this.baseUrl);
            if (uri.getHost() == null
                    || !("http".equals(uri.getScheme())
                    || "https".equals(uri.getScheme()))
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "La URL base de imágenes debe ser HTTP o HTTPS", e);
        }
    }

    public String guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException(
                    HttpStatus.BAD_REQUEST, "Selecciona una imagen");
        }

        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "La imagen no debe superar los 5 MB");
        }

        BufferedImage imagen;

        // Comprueba el contenido real, no solo la extensión o el Content-Type.
        try (ImageInputStream entrada =
                     ImageIO.createImageInputStream(archivo.getInputStream())) {

            if (entrada == null) {
                throw imagenInvalida();
            }

            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);

            if (!lectores.hasNext()) {
                throw imagenInvalida();
            }

            ImageReader lector = lectores.next();

            try {
                lector.setInput(entrada, true, true);

                String formato = lector.getFormatName()
                        .toLowerCase(Locale.ROOT);

                if (!formato.equals("png")
                        && !formato.equals("jpeg")
                        && !formato.equals("jpg")) {
                    throw imagenInvalida();
                }

                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);

                if (ancho <= 0 || alto <= 0
                        || (long) ancho * alto > MAX_PIXELES) {
                    throw new NegocioException(
                            HttpStatus.BAD_REQUEST,
                            "La imagen debe tener como máximo 20 megapíxeles");
                }

                imagen = lector.read(0);

                if (imagen == null) {
                    throw imagenInvalida();
                }
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw imagenInvalida();
        }

        // Se genera el nombre y se reescribe como PNG.
        // No se utiliza el nombre enviado por el usuario.
        String nombre = UUID.randomUUID() + ".png";
        Path destino = resolver(nombre);

        try {
            Files.createDirectories(directorio);

            if (!ImageIO.write(imagen, "png", destino.toFile())) {
                throw new IOException("No hay codificador PNG disponible");
            }

            return nombre;
        } catch (IOException e) {
            eliminar(nombre);

            throw new NegocioException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar la imagen");
        }
    }

    public ByteArrayResource leer(String nombre) {
        Path archivo = resolver(nombre);

        if (!Files.isRegularFile(archivo)) {
            throw new NegocioException(
                    HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }

        try {
            return new ByteArrayResource(Files.readAllBytes(archivo));
        } catch (IOException e) {
            throw new NegocioException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer la imagen");
        }
    }

    public String url(Long tienda, Long producto, String nombre) {
        resolver(nombre);

        return baseUrl + "/api/v1/tiendas/" + tienda
                + "/productos/" + producto + "/imagenes/" + nombre;
    }

    public boolean corresponde(
            String urlGuardada, Long tienda, Long producto, String nombre) {

        resolver(nombre);

        // Se compara la ruta para permitir cambiar el dominio al desplegar.
        try {
            return urlGuardada != null
                    && java.net.URI.create(urlGuardada).getPath()
                    .equals(java.net.URI.create(
                            url(tienda, producto, nombre)).getPath());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void eliminar(String nombre) {
        try {
            Files.deleteIfExists(resolver(nombre));
        } catch (IOException e) {
            // Un fallo de limpieza no debe ocultar el error original.
            System.getLogger(getClass().getName()).log(
                    System.Logger.Level.WARNING,
                    "No se pudo limpiar una imagen de producto", e);
        }
    }

    private Path resolver(String nombre) {
        if (nombre == null || !nombre.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}"
                        + "-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) {
            throw new NegocioException(
                    HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }

        Path archivo = directorio.resolve(nombre).normalize();

        if (!archivo.startsWith(directorio)) {
            throw new NegocioException(
                    HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }

        return archivo;
    }

    private NegocioException imagenInvalida() {
        return new NegocioException(
                HttpStatus.BAD_REQUEST,
                "El archivo debe ser una imagen PNG o JPEG válida");
    }
}