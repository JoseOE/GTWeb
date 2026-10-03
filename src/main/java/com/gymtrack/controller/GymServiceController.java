package com.gymtrack.controller;

import com.gymtrack.model.GymService;
import com.gymtrack.repository.GymServiceRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/servicios")
public class GymServiceController {

    private static final String COLECCION = "servicios";

    private final GymServiceRepository repository;
    private final MongoTemplate mongoTemplate;

    public GymServiceController(GymServiceRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    // GET /api/servicios → Todos los servicios de la colección "servicios".
    //
    // Se leen los documentos tal cual están en Mongo en vez de usar findAll():
    // así, si alguien agrega un servicio a mano desde MongoDB Atlas con un campo
    // de más, uno de menos o un tipo distinto (precio como texto, por ejemplo),
    // ese servicio aparece igual en la página y, sobre todo, no tumba a los demás.
    // Con findAll(), un solo documento raro hacía fallar todo el catálogo.
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> obtenerServicios() {
        List<Map<String, Object>> servicios = new ArrayList<>();
        for (Document doc : mongoTemplate.getCollection(COLECCION).find()) {
            Map<String, Object> servicio = normalizar(doc);
            // activo:false oculta un servicio sin borrarlo. Si el campo no existe, se muestra.
            if (Boolean.FALSE.equals(servicio.get("activo"))) continue;
            servicios.add(servicio);
        }
        // Sin caché: un servicio recién agregado debe verse en cuanto se recarga la página.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicios);
    }

    // Convierte un documento de Mongo a la forma que espera la página, aceptando
    // los nombres de campo en español (los del proyecto) o en inglés.
    private Map<String, Object> normalizar(Document doc) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", doc.get("_id") == null ? null : doc.get("_id").toString());
        s.put("nombre", texto(doc, "nombre", "name", "titulo", "title"));
        s.put("descripcion", texto(doc, "descripcion", "description", "detalle"));
        s.put("precio", numero(doc, "precio", "price", "costo"));
        s.put("icono", texto(doc, "icono", "icon"));
        s.put("imagen", texto(doc, "imagen", "image", "img", "foto"));
        s.put("categoria", texto(doc, "categoria", "category", "tipo"));
        s.put("duracion", texto(doc, "duracion", "duration", "plan"));
        s.put("destacado", booleano(doc, "destacado", "featured"));
        s.put("activo", booleano(doc, "activo", "active"));
        return s;
    }

    private String texto(Document doc, String... campos) {
        for (String campo : campos) {
            Object v = doc.get(campo);
            if (v != null && !v.toString().isBlank()) return v.toString().trim();
        }
        return null;
    }

    // Acepta 3500, 3500.0, "3500", "$3,500" o "3500 MXN". Si no se puede leer, null.
    private Double numero(Document doc, String... campos) {
        for (String campo : campos) {
            Object v = doc.get(campo);
            if (v instanceof Number n) return n.doubleValue();
            if (v != null) {
                String limpio = v.toString().replaceAll("[^0-9.\\-]", "");
                if (!limpio.isEmpty()) {
                    try {
                        return Double.parseDouble(limpio);
                    } catch (NumberFormatException ignored) {
                        // se intenta con el siguiente nombre de campo
                    }
                }
            }
        }
        return null;
    }

    // Acepta true/false, "true"/"false", "si"/"no", 1/0. Si no existe, null.
    private Boolean booleano(Document doc, String... campos) {
        for (String campo : campos) {
            Object v = doc.get(campo);
            if (v instanceof Boolean b) return b;
            if (v instanceof Number n) return n.intValue() != 0;
            if (v != null) {
                String t = v.toString().trim().toLowerCase();
                if (t.equals("true") || t.equals("si") || t.equals("sí") || t.equals("1")) return true;
                if (t.equals("false") || t.equals("no") || t.equals("0")) return false;
            }
        }
        return null;
    }

    // GET /api/servicios/{id} → Obtener un servicio por ID
    @GetMapping("/{id}")
    public Optional<GymService> obtenerServicioPorId(@PathVariable String id) {
        return repository.findById(id);
    }
}
