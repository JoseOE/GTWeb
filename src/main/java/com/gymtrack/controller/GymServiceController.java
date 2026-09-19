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

    // GET /api/servicios/seed → Repoblar la base de datos (como /seed del code folder)
    @GetMapping("/seed")
    public String seedDatabase() {
        repository.deleteAll();
        repository.saveAll(List.of(
            new GymService(null, "Plataforma Web",
                "Panel de control centralizado en la nube para gestionar clientes, finanzas y configuración de tu gimnasio.",
                0.0, "bx-laptop",
                "https://images.unsplash.com/photo-1460925895917-afdab827c52f?q=80&w=600&auto=format&fit=crop",
                "software", "SaaS", true, true),
            new GymService(null, "Control de Acceso IoT",
                "Módulo de hardware inteligente que se conecta a tu torniquete para validar entradas mediante RFID.",
                3500.0, "bx-chip",
                "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop",
                "hardware", "Pago Único", true, true),
            new GymService(null, "App Móvil para Clientes",
                "Aplicación nativa para que tus usuarios consulten su membresía, rutinas y progreso.",
                0.0, "bx-mobile-alt",
                "https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?q=80&w=600&auto=format&fit=crop",
                "software", "SaaS", true, true),
            new GymService(null, "Gestión de Cobros",
                "Sistema automatizado que bloquea el acceso físico a usuarios morosos y notifica vencimientos.",
                0.0, "bx-credit-card-front",
                "https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?q=80&w=600&auto=format&fit=crop",
                "módulo", "SaaS", false, true),
            new GymService(null, "Creador de Rutinas",
                "Herramienta para diseñar rutinas personalizadas basadas en el equipamiento físico de tu sucursal.",
                0.0, "bx-dumbbell",
                "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop",
                "módulo", "SaaS", false, true),
            new GymService(null, "Analíticas y Reportes",
                "Gráficas sobre asistencia, horarios pico, ingresos mensuales y retención de clientes.",
                0.0, "bx-bar-chart-alt-2",
                "https://images.unsplash.com/photo-1551288049-bebda4e38f71?q=80&w=600&auto=format&fit=crop",
                "software", "SaaS", false, true),
            new GymService(null, "Credenciales RFID",
                "Tarjetas o pulseras RFID que funcionan como llave de acceso única y segura para tus clientes.",
                50.0, "bx-id-card",
                "https://images.unsplash.com/photo-1563013544-824ae1b704d3?q=80&w=600&auto=format&fit=crop",
                "hardware", "Por unidad", false, true),
            new GymService(null, "Respaldo en la Nube",
                "Tus datos seguros con arquitectura Multi-tenant, respaldos automáticos y soporte técnico.",
                0.0, "bx-cloud-upload",
                "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=600&auto=format&fit=crop",
                "servicio", "24/7", false, true)
        ));
        return "✅ Base de datos poblada exitosamente con 8 módulos de GymTrack SaaS.";
    }
}
