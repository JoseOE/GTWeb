package com.gymtrack.controller;

import com.gymtrack.model.Imagen;
import com.gymtrack.repository.ImagenRepository;
import com.gymtrack.service.AccesoService;
import com.gymtrack.service.TiendaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Imágenes de los productos. El panel las reduce en el navegador (como el
// logo) y las manda como data URI; aquí se guardan en Mongo y se sirven con
// una URL fija que es la que se guarda como imagen del producto.
@RestController
public class ImagenController {

    private static final Pattern DATA_URI = Pattern.compile("^data:(image/(?:png|jpeg|webp));base64,(.+)$");
    // Una foto de 800 px en JPEG pesa ~100 KB; 1.5 MB deja margen sin llenar Mongo.
    private static final int MAX_BYTES = 1_500_000;

    private final ImagenRepository imagenRepository;
    private final AccesoService acceso;
    private final String urlPublica;

    public ImagenController(ImagenRepository imagenRepository, AccesoService acceso,
                            @Value("${app.url-publica:http://localhost:8080}") String urlPublica) {
        this.imagenRepository = imagenRepository;
        this.acceso = acceso;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    // POST /api/gyms/{gymId}/imagenes  {"dataUrl": "data:image/jpeg;base64,..."} → {id, url}
    @PostMapping("/api/gyms/{gymId}/imagenes")
    public Map<String, Object> subir(@PathVariable String gymId,
                                     @RequestHeader(value = AccesoService.ENCABEZADO, required = false) String userId,
                                     @RequestBody Map<String, String> body) {
        acceso.exigirDueno(gymId, userId);
        Matcher m = DATA_URI.matcher(body.getOrDefault("dataUrl", ""));
        if (!m.matches()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La imagen debe ser PNG, JPG o WebP.");
        }
        byte[] datos;
        try {
            datos = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "No pudimos leer esa imagen.");
        }
        if (datos.length > MAX_BYTES) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La imagen es demasiado pesada (máximo 1.5 MB).");
        }

        Imagen imagen = new Imagen();
        imagen.setGymId(gymId);
        imagen.setTipoContenido(m.group(1));
        imagen.setDatos(datos);
        imagen.setCreadaEn(Instant.now());
        imagen = imagenRepository.save(imagen);
        return Map.of("id", imagen.getId(), "url", urlPublica + "/api/imagenes/" + imagen.getId());
    }

    // GET /api/imagenes/{id} → la imagen tal cual. Nunca cambia (una imagen
    // nueva tiene otro id), así que el navegador y la app la guardan en caché un año.
    @GetMapping("/api/imagenes/{id}")
    public ResponseEntity<byte[]> ver(@PathVariable String id) {
        return imagenRepository.findById(id)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getTipoContenido()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(img.getDatos()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
