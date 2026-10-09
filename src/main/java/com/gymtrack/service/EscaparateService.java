package com.gymtrack.service;

import com.gymtrack.model.Producto;
import com.gymtrack.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.gymtrack.service.TiendaGymService.TIPO_MEMBRESIA;

// Lo que ve el miembro en la tienda de su gimnasio: productos y planes
// publicados, con el precio vigente y lo disponible (existencias menos lo
// apartado por pedidos que aún no se entregan).
//
// Sale de la misma colección que el catálogo del dueño (CatalogoService); aquí
// solo entra lo publicado de ese gimnasio, con una sola consulta indexada.
@Service
public class EscaparateService {

    // Los planes primero (destacados arriba) y luego los productos por nombre.
    private static final Comparator<Producto> EN_ESCAPARATE = Comparator
            .comparing((Producto p) -> p.esPlan() ? 0 : 1)
            .thenComparing(p -> p.esPlan() && p.getPlan() != null && p.getPlan().isDestacado() ? 0 : 1)
            .thenComparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER);

    private final ProductoRepository productos;

    public EscaparateService(ProductoRepository productos) {
        this.productos = productos;
    }

    // GET /api/tienda/{gymId}/productos: todo lo publicado, filtrado por
    // categoría y por texto.
    public List<Map<String, Object>> productos(String gymId, String categoria, String busqueda) {
        String texto = normalizar(busqueda);
        return productos.findByGymIdAndActivoTrue(gymId).stream()
                .filter(p -> categoria == null || categoria.isBlank() || categoria.equals(p.getCategoria()))
                .filter(p -> texto.isEmpty() || coincide(p, texto))
                .sorted(EN_ESCAPARATE)
                .map(EscaparateService::vista)
                .toList();
    }

    // Un producto oculto, borrado o de otro gimnasio responde igual: ya no se vende.
    public Map<String, Object> producto(String gymId, String productoId) {
        return productos.findByIdAndGymId(productoId, gymId)
                .filter(Producto::isActivo)
                .map(EscaparateService::vista)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Ese producto ya no está a la venta."));
    }

    static Map<String, Object> vista(Producto p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("nombre", p.getNombre());
        v.put("descripcion", vacio(p.getDescripcion()));
        v.put("imagen", vacio(p.getImagen()));
        v.put("tipo", p.esPlan() ? TIPO_MEMBRESIA : TiendaGymService.TIPO_PRODUCTO);
        v.put("categoria", CatalogoService.vistaCategoria(p.getCategoria()));

        List<Map<String, Object>> variantes = new ArrayList<>();
        Double desde = null;
        boolean todoAgotado = true;
        for (Producto.Variante var : CatalogoService.enOrden(p.getVariantes())) {
            Integer disponible = var.isControlarInventario() ? Math.max(0, var.getDisponible()) : null;
            boolean agotado = disponible != null && disponible <= 0;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", var.getId());
            x.put("nombre", var.etiqueta());
            x.put("presentacion", var.getPresentacion());
            x.put("sabor", vacio(var.getSabor()));
            x.put("precio", var.getPrecio());
            x.put("controlarInventario", disponible != null);
            x.put("disponible", disponible);
            x.put("agotado", agotado);
            variantes.add(x);
            if (desde == null || var.getPrecio() < desde) desde = var.getPrecio();
            if (!agotado) todoAgotado = false;
        }
        v.put("variantes", variantes);
        v.put("precioDesde", desde);
        v.put("agotado", todoAgotado);

        Map<String, Object> plan = null;
        if (p.esPlan()) {
            Producto.Plan m = p.getPlan() == null ? new Producto.Plan() : p.getPlan();
            Optional<PlanPagado> duracion = CatalogoService.plan(p);
            plan = new LinkedHashMap<>();
            plan.put("pagoUnico", m.isPagoUnico());
            plan.put("duracionUnidad", duracion.map(PlanPagado::unidad).orElse(null));
            plan.put("duracionCantidad", duracion.map(PlanPagado::cantidad).orElse(null));
            plan.put("duracionTexto", duracion.map(CatalogoService::duracionTexto).orElse("Pago único"));
            plan.put("beneficios", m.getBeneficios() == null ? List.of() : m.getBeneficios());
            plan.put("destacado", m.isDestacado());
        }
        v.put("plan", plan);
        return v;
    }

    // Busca en el nombre, la descripción y las variantes, sin importar acentos ni mayúsculas.
    private static boolean coincide(Producto p, String texto) {
        StringBuilder todo = new StringBuilder(p.getNombre()).append(' ')
                .append(p.getDescripcion() == null ? "" : p.getDescripcion());
        p.getVariantes().forEach(x -> todo.append(' ').append(x.etiqueta()));
        return normalizar(todo.toString()).contains(texto);
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
