package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.model.Producto;
import com.gymtrack.repository.ProductoRepository;
import com.gymtrack.util.Ids;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.gymtrack.service.TiendaGymService.CATEGORIA_MEMBRESIAS;
import static com.gymtrack.service.TiendaGymService.TIPO_MEMBRESIA;
import static com.gymtrack.service.TiendaGymService.TIPO_PRODUCTO;

// Lo que el dueño vende: productos (con variantes, precio y stock) y planes de
// membresía, con el formato sencillo que usan el panel y la app.
//
// Todo vive en MongoDB (colección productos), cada producto con sus variantes
// adentro y el precio en MXN con IVA incluido:
//  - "No controlar inventario" (scoops) = la variante nunca se agota.
//  - Un plan es un producto de tipo "membresia" con una sola variante "Plan",
//    sin inventario, y su duración y beneficios en Producto.plan.
@Service
public class CatalogoService {

    // Nombre de la única variante de un plan (los recibos lo ocultan).
    static final String VARIANTE_PLAN = "Plan";
    private static final int MAX_VARIANTES = 30;
    private static final int MAX_REINTENTOS = 3;

    private final TiendaGymService tiendas;
    private final ProductoRepository productos;

    public CatalogoService(TiendaGymService tiendas, ProductoRepository productos) {
        this.tiendas = tiendas;
        this.productos = productos;
    }

    // ═══════════════════════════ PRODUCTOS ═══════════════════════════

    public List<Map<String, Object>> listarProductos(String gymId) {
        tiendas.exigirGimnasio(gymId);
        return productos.findByGymIdAndTipo(gymId, TIPO_PRODUCTO).stream()
                .sorted(POR_ORDEN_Y_NOMBRE)
                .map(CatalogoService::vistaProducto)
                .toList();
    }

    public Map<String, Object> obtenerProducto(String gymId, String productoId) {
        return vistaProducto(exigirDelGimnasio(gymId, productoId, TIPO_PRODUCTO));
    }

    public Map<String, Object> crearProducto(String gymId, ProductoRequest r) {
        List<VarianteLimpia> variantes = validar(r);
        String categoria = categoriaDeProducto(r.getCategoria());
        tiendas.exigirGimnasio(gymId);

        Producto p = new Producto();
        p.setId(Ids.nuevo("prod"));
        p.setGymId(gymId);
        p.setTipo(TIPO_PRODUCTO);
        p.setCreadoEn(Instant.now());
        datosGenerales(p, r, categoria);
        List<Producto.Variante> nuevas = new ArrayList<>();
        for (int i = 0; i < variantes.size(); i++) nuevas.add(nuevaVariante(variantes.get(i), i));
        p.setVariantes(nuevas);
        return vistaProducto(productos.insert(p));
    }

    // Las variantes que traen id se actualizan, las nuevas se crean y las que ya
    // no vienen se borran (los pedidos viejos conservan su copia). Las piezas
    // apartadas en pedidos se respetan: disponible = existencias - apartadas.
    public Map<String, Object> actualizarProducto(String gymId, String productoId, ProductoRequest r) {
        List<VarianteLimpia> variantes = validar(r);
        String categoria = categoriaDeProducto(r.getCategoria());
        return vistaProducto(conReintentos(() -> {
            Producto p = exigirDelGimnasio(gymId, productoId, TIPO_PRODUCTO);
            datosGenerales(p, r, categoria);
            Map<String, Producto.Variante> actuales = new HashMap<>();
            p.getVariantes().forEach(v -> actuales.put(v.getId(), v));
            List<Producto.Variante> nuevas = new ArrayList<>();
            for (int i = 0; i < variantes.size(); i++) {
                VarianteLimpia limpia = variantes.get(i);
                Producto.Variante actual = limpia.id() == null ? null : actuales.get(limpia.id());
                if (actual == null) {
                    nuevas.add(nuevaVariante(limpia, i));
                    continue;
                }
                actual.setPresentacion(limpia.presentacion());
                actual.setSabor(limpia.sabor());
                actual.setPrecio(limpia.precio());
                actual.setOrden(i);
                fijarExistencias(actual, limpia);
                nuevas.add(actual);
            }
            p.setVariantes(nuevas);
            p.setActualizadoEn(Instant.now());
            return productos.save(p);
        }));
    }

    public void eliminarProducto(String gymId, String productoId) {
        productos.delete(exigirDelGimnasio(gymId, productoId, TIPO_PRODUCTO));
    }

    // ═══════════════════════════ PLANES ═══════════════════════════

    public List<Map<String, Object>> listarPlanes(String gymId) {
        tiendas.exigirGimnasio(gymId);
        return productos.findByGymIdAndTipo(gymId, TIPO_MEMBRESIA).stream()
                .sorted(POR_ORDEN_Y_NOMBRE)
                .map(CatalogoService::vistaPlan)
                .toList();
    }

    public Map<String, Object> crearPlan(String gymId, PlanRequest r) {
        validar(r);
        tiendas.exigirGimnasio(gymId);

        Producto p = new Producto();
        p.setId(Ids.nuevo("prod"));
        p.setGymId(gymId);
        p.setTipo(TIPO_MEMBRESIA);
        p.setCategoria(CATEGORIA_MEMBRESIAS);
        p.setCreadoEn(Instant.now());
        datosDelPlan(p, r);
        Producto.Variante v = new Producto.Variante();
        v.setId(Ids.nuevo("variant"));
        v.setPresentacion(VARIANTE_PLAN);
        v.setPrecio(r.getPrecio());
        v.setControlarInventario(false);
        p.setVariantes(new ArrayList<>(List.of(v)));
        return vistaPlan(productos.insert(p));
    }

    public Map<String, Object> actualizarPlan(String gymId, String planId, PlanRequest r) {
        validar(r);
        return vistaPlan(conReintentos(() -> {
            Producto p = exigirDelGimnasio(gymId, planId, TIPO_MEMBRESIA);
            datosDelPlan(p, r);
            if (p.getVariantes().isEmpty()) {
                Producto.Variante v = new Producto.Variante();
                v.setId(Ids.nuevo("variant"));
                v.setPresentacion(VARIANTE_PLAN);
                p.getVariantes().add(v);
            }
            p.getVariantes().get(0).setPrecio(r.getPrecio());
            p.setActualizadoEn(Instant.now());
            return productos.save(p);
        }));
    }

    public void eliminarPlan(String gymId, String planId) {
        productos.delete(exigirDelGimnasio(gymId, planId, TIPO_MEMBRESIA));
    }

    // Plan con el que se registra un pago a mano desde el panel.
    public PlanPagado planParaPago(String gymId, String planId) {
        return plan(exigirDelGimnasio(gymId, planId, TIPO_MEMBRESIA))
                .orElseThrow(() -> new TiendaException(HttpStatus.BAD_REQUEST,
                        "Ese concepto es de pago único: no extiende la membresía."));
    }

    // Duración de un plan. Vacío si no es un plan, si es de pago único
    // (inscripción) o si no tiene una duración válida.
    public static Optional<PlanPagado> plan(Producto p) {
        Producto.Plan m = p.getPlan();
        if (!p.esPlan() || m == null || m.isPagoUnico() || !PlanPagado.UNIDADES.contains(m.getDuracionUnidad())
                || m.getDuracionCantidad() == null || m.getDuracionCantidad() < 1) {
            return Optional.empty();
        }
        return Optional.of(new PlanPagado(p.getId(), p.getNombre(), m.getDuracionUnidad(), m.getDuracionCantidad()));
    }

    // Planes sugeridos, con el precio calculado desde la cuota mensual del
    // gimnasio. El dueño los edita antes de guardarlos.
    public List<Map<String, Object>> presets(Gym gym) {
        Double cuota = gym.getCuotaMensual();
        List<Map<String, Object>> lista = new ArrayList<>();
        lista.add(preset("Visita", "dia", 1, precioSugerido(cuota, 0.10), false, false,
                List.of("Acceso por un día", "Uso de todas las áreas")));
        lista.add(preset("Semana", "semana", 1, precioSugerido(cuota, 0.35), false, false,
                List.of("7 días de acceso", "Ideal para probar el gimnasio")));
        lista.add(preset("Mensual", "mes", 1, precioSugerido(cuota, 1), true, false,
                List.of("Acceso ilimitado todo el mes", "Rutinas en la app GymTrack")));
        lista.add(preset("Trimestral", "mes", 3, precioSugerido(cuota, 3 * 0.90), false, false,
                List.of("3 meses de acceso", "Ahorras 10 % contra el mensual", "Rutinas en la app GymTrack")));
        lista.add(preset("Semestral", "mes", 6, precioSugerido(cuota, 6 * 0.85), false, false,
                List.of("6 meses de acceso", "Ahorras 15 % contra el mensual", "Rutinas en la app GymTrack")));
        lista.add(preset("Anual", "mes", 12, precioSugerido(cuota, 12 * 0.80), false, false,
                List.of("12 meses de acceso", "Ahorras 20 % contra el mensual", "Rutinas en la app GymTrack")));
        lista.add(preset("Inscripción", null, null, precioSugerido(cuota, 0.5), false, true,
                List.of("Pago único al registrarte", "Credencial de acceso")));
        return lista;
    }

    // ═══════════════════════════ VISTAS ═══════════════════════════

    // Forma en que el panel (y la app) reciben un producto.
    static Map<String, Object> vistaProducto(Producto p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("nombre", p.getNombre());
        v.put("descripcion", vacio(p.getDescripcion()));
        v.put("imagen", vacio(p.getImagen()));
        v.put("activo", p.isActivo());
        v.put("categoria", vistaCategoria(p.getCategoria()));

        List<Map<String, Object>> variantes = new ArrayList<>();
        Double desde = null;
        for (Producto.Variante var : enOrden(p.getVariantes())) {
            boolean controlar = var.isControlarInventario();
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", var.getId());
            x.put("nombre", var.etiqueta());
            x.put("presentacion", var.getPresentacion());
            x.put("sabor", vacio(var.getSabor()));
            x.put("precio", var.getPrecio());
            x.put("controlarInventario", controlar);
            x.put("existencias", controlar ? var.getExistencias() : null);
            x.put("disponible", controlar ? Math.max(0, var.getDisponible()) : null);
            variantes.add(x);
            if (desde == null || var.getPrecio() < desde) desde = var.getPrecio();
        }
        v.put("variantes", variantes);
        v.put("precioDesde", desde);
        return v;
    }

    static Map<String, Object> vistaPlan(Producto p) {
        Producto.Plan m = p.getPlan() == null ? new Producto.Plan() : p.getPlan();
        Producto.Variante variante = p.getVariantes().isEmpty() ? null : p.getVariantes().get(0);
        Optional<PlanPagado> plan = plan(p);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("varianteId", variante == null ? null : variante.getId());
        v.put("nombre", p.getNombre());
        v.put("descripcion", vacio(p.getDescripcion()));
        v.put("precio", variante == null ? null : variante.getPrecio());
        v.put("pagoUnico", m.isPagoUnico());
        v.put("duracionUnidad", plan.map(PlanPagado::unidad).orElse(null));
        v.put("duracionCantidad", plan.map(PlanPagado::cantidad).orElse(null));
        v.put("duracionTexto", plan.map(CatalogoService::duracionTexto).orElse("Pago único"));
        v.put("beneficios", m.getBeneficios() == null ? List.of() : m.getBeneficios());
        v.put("destacado", m.isDestacado());
        v.put("activo", p.isActivo());
        return v;
    }

    // "1 día", "7 días", "1 semana", "3 meses"...
    public static String duracionTexto(PlanPagado p) {
        int n = p.cantidad();
        return switch (p.unidad()) {
            case "dia" -> n + (n == 1 ? " día" : " días");
            case "semana" -> n + (n == 1 ? " semana" : " semanas");
            default -> n + (n == 1 ? " mes" : " meses");
        };
    }

    static Map<String, Object> vistaCategoria(String handle) {
        if (handle == null) return null;
        String nombre = CATEGORIAS_FIJAS.getOrDefault(handle, handle);
        // En este orden, como lo daba Medusa.
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("handle", handle);
        v.put("nombre", nombre);
        return v;
    }

    private static final Map<String, String> CATEGORIAS_FIJAS = Map.of(
            "suplementos", "Suplementos", "bebidas", "Bebidas", "snacks", "Snacks",
            "accesorios", "Accesorios", CATEGORIA_MEMBRESIAS, "Membresías");

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    // Primero el orden que puso el dueño y luego por nombre, sin importar
    // mayúsculas ni acentos.
    static final Comparator<Producto> POR_ORDEN_Y_NOMBRE = Comparator
            .comparingInt(Producto::getOrden)
            .thenComparing(p -> EscaparateService.normalizar(p.getNombre()));

    // Orden en que el dueño capturó las presentaciones.
    static List<Producto.Variante> enOrden(List<Producto.Variante> variantes) {
        return variantes.stream().sorted(Comparator.comparingInt(Producto.Variante::getOrden)).toList();
    }

    // Un producto de otro gimnasio responde igual que uno que no existe.
    private Producto exigirDelGimnasio(String gymId, String productoId, String tipo) {
        return productos.findByIdAndGymId(productoId, gymId)
                .filter(p -> tipo.equals(p.getTipo()))
                .orElseThrow(() -> noEncontrado(tipo));
    }

    private TiendaException noEncontrado(String tipo) {
        return new TiendaException(HttpStatus.NOT_FOUND,
                TIPO_MEMBRESIA.equals(tipo) ? "Plan no encontrado en tu gimnasio." : "Producto no encontrado en tu gimnasio.");
    }

    // Dos ediciones del mismo producto a la vez (o una edición y una venta, que
    // también cambia su versión): la segunda se repite con lo más reciente.
    private static Producto conReintentos(Supplier<Producto> cambio) {
        for (int intento = 1; ; intento++) {
            try {
                return cambio.get();
            } catch (OptimisticLockingFailureException e) {
                if (intento >= MAX_REINTENTOS) {
                    throw new TiendaException(HttpStatus.CONFLICT, "El producto cambió mientras lo guardabas. Intenta de nuevo.");
                }
            }
        }
    }

    private static void datosGenerales(Producto p, ProductoRequest r, String categoria) {
        p.setNombre(r.getNombre().trim());
        p.setDescripcion(r.getDescripcion() == null || r.getDescripcion().isBlank() ? null : r.getDescripcion().trim());
        p.setImagen(r.getImagen() == null || r.getImagen().isBlank() ? null : r.getImagen());
        p.setActivo(!Boolean.FALSE.equals(r.getActivo()));
        p.setCategoria(categoria);
    }

    private static Producto.Variante nuevaVariante(VarianteLimpia limpia, int orden) {
        Producto.Variante v = new Producto.Variante();
        v.setId(Ids.nuevo("variant"));
        v.setPresentacion(limpia.presentacion());
        v.setSabor(limpia.sabor());
        v.setPrecio(limpia.precio());
        v.setOrden(orden);
        fijarExistencias(v, limpia);
        return v;
    }

    // El dueño captura las piezas que tiene; lo apartado en pedidos se respeta.
    // Una variante que deja de llevar inventario olvida lo apartado: ya no hay
    // existencias que cuidar.
    private static void fijarExistencias(Producto.Variante v, VarianteLimpia limpia) {
        if (!limpia.controlar()) {
            v.setControlarInventario(false);
            v.setExistencias(0);
            v.setApartadas(0);
            v.setDisponible(0);
            return;
        }
        if (!v.isControlarInventario()) v.setApartadas(0);
        v.setControlarInventario(true);
        v.setExistencias(limpia.existencias());
        v.setDisponible(limpia.existencias() - v.getApartadas());
    }

    private static void datosDelPlan(Producto p, PlanRequest r) {
        boolean pagoUnico = Boolean.TRUE.equals(r.getPagoUnico());
        p.setNombre(r.getNombre().trim());
        p.setDescripcion(r.getDescripcion() == null || r.getDescripcion().isBlank() ? null : r.getDescripcion().trim());
        p.setActivo(!Boolean.FALSE.equals(r.getActivo()));
        Producto.Plan m = new Producto.Plan();
        m.setPagoUnico(pagoUnico);
        // Un plan que pasa a ser de pago único no conserva su duración anterior.
        m.setDuracionUnidad(pagoUnico ? null : r.getDuracionUnidad());
        m.setDuracionCantidad(pagoUnico ? null : r.getDuracionCantidad());
        m.setBeneficios(limpiarBeneficios(r.getBeneficios()));
        m.setDestacado(Boolean.TRUE.equals(r.getDestacado()));
        p.setPlan(m);
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private String categoriaDeProducto(String handle) {
        if (handle == null || CATEGORIA_MEMBRESIAS.equals(handle) || tiendas.categoria(handle).isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige la categoría del producto.");
        }
        return handle;
    }

    private static Map<String, Object> preset(String nombre, String unidad, Integer cantidad, Double precio,
                                              boolean destacado, boolean pagoUnico, List<String> beneficios) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nombre", nombre);
        p.put("duracionUnidad", unidad);
        p.put("duracionCantidad", cantidad);
        p.put("precio", precio);
        p.put("destacado", destacado);
        p.put("pagoUnico", pagoUnico);
        p.put("beneficios", beneficios);
        return p;
    }

    // Redondea a múltiplos de $5 para que el precio sugerido se vea "de mostrador".
    private static Double precioSugerido(Double cuota, double factor) {
        if (cuota == null || cuota <= 0) return null;
        return Math.round(cuota * factor / 5.0) * 5.0;
    }

    private static List<String> limpiarBeneficios(List<String> beneficios) {
        if (beneficios == null) return List.of();
        return beneficios.stream().filter(b -> b != null && !b.isBlank()).map(String::trim).limit(10).toList();
    }

    // ─── Validación ───

    record VarianteLimpia(String id, String presentacion, String sabor, double precio,
                          boolean controlar, int existencias) {
        String etiqueta() {
            return sabor == null ? presentacion : presentacion + " · " + sabor;
        }
    }

    private List<VarianteLimpia> validar(ProductoRequest r) {
        if (r == null || r.getNombre() == null || r.getNombre().isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El producto necesita un nombre.");
        }
        validarImagen(r.getImagen());
        if (r.getVariantes() == null || r.getVariantes().isEmpty()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Agrega al menos una presentación con su precio.");
        }
        if (r.getVariantes().size() > MAX_VARIANTES) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Un producto puede tener hasta " + MAX_VARIANTES + " variantes.");
        }
        List<VarianteLimpia> limpias = new ArrayList<>();
        Set<String> etiquetas = new HashSet<>();
        for (VarianteRequest v : r.getVariantes()) {
            String presentacion = v.getPresentacion() == null || v.getPresentacion().isBlank() ? "Única" : v.getPresentacion().trim();
            String sabor = v.getSabor() == null || v.getSabor().isBlank() ? null : v.getSabor().trim();
            if (v.getPrecio() == null || v.getPrecio() <= 0) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Ponle precio a \"" + presentacion + "\".");
            }
            boolean controlar = !Boolean.FALSE.equals(v.getControlarInventario());
            int existencias = v.getExistencias() == null ? 0 : v.getExistencias();
            if (controlar && existencias < 0) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Las existencias no pueden ser negativas.");
            }
            VarianteLimpia limpia = new VarianteLimpia(v.getId(), presentacion, sabor,
                    Math.round(v.getPrecio() * 100) / 100.0, controlar, existencias);
            if (!etiquetas.add(limpia.etiqueta().toLowerCase())) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "La variante \"" + limpia.etiqueta() + "\" está repetida.");
            }
            limpias.add(limpia);
        }
        return limpias;
    }

    private void validar(PlanRequest r) {
        if (r == null || r.getNombre() == null || r.getNombre().isBlank()) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "El plan necesita un nombre.");
        }
        if (r.getPrecio() == null || r.getPrecio() <= 0) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Ponle precio al plan.");
        }
        if (!Boolean.TRUE.equals(r.getPagoUnico())) {
            if (r.getDuracionUnidad() == null || !PlanPagado.UNIDADES.contains(r.getDuracionUnidad())) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Elige si el plan dura días, semanas o meses.");
            }
            if (r.getDuracionCantidad() == null || r.getDuracionCantidad() < 1 || r.getDuracionCantidad() > 36) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "La duración del plan debe estar entre 1 y 36.");
            }
        }
    }

    private void validarImagen(String imagen) {
        if (imagen != null && !imagen.isBlank() && !imagen.startsWith("https://") && !imagen.startsWith("http://")) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "La imagen debe subirse desde el panel.");
        }
    }

    // ─── Lo que manda el panel ───

    public static class ProductoRequest {
        private String nombre;
        private String descripcion;
        // handle de la categoría: suplementos | bebidas | snacks | accesorios
        private String categoria;
        // URL que devolvió POST /api/gyms/{gymId}/imagenes
        private String imagen;
        private Boolean activo;
        private List<VarianteRequest> variantes;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

        public String getCategoria() { return categoria; }
        public void setCategoria(String categoria) { this.categoria = categoria; }

        public String getImagen() { return imagen; }
        public void setImagen(String imagen) { this.imagen = imagen; }

        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }

        public List<VarianteRequest> getVariantes() { return variantes; }
        public void setVariantes(List<VarianteRequest> variantes) { this.variantes = variantes; }
    }

    public static class VarianteRequest {
        // null = variante nueva
        private String id;
        private String presentacion;
        private String sabor;
        private Double precio;
        // false = no controlar inventario (scoops): nunca se agota
        private Boolean controlarInventario;
        private Integer existencias;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPresentacion() { return presentacion; }
        public void setPresentacion(String presentacion) { this.presentacion = presentacion; }

        public String getSabor() { return sabor; }
        public void setSabor(String sabor) { this.sabor = sabor; }

        public Double getPrecio() { return precio; }
        public void setPrecio(Double precio) { this.precio = precio; }

        public Boolean getControlarInventario() { return controlarInventario; }
        public void setControlarInventario(Boolean controlarInventario) { this.controlarInventario = controlarInventario; }

        public Integer getExistencias() { return existencias; }
        public void setExistencias(Integer existencias) { this.existencias = existencias; }
    }

    public static class PlanRequest {
        private String nombre;
        private String descripcion;
        private Double precio;
        // dia | semana | mes (se ignora si es de pago único)
        private String duracionUnidad;
        private Integer duracionCantidad;
        private List<String> beneficios;
        private Boolean destacado;
        private Boolean activo;
        // true = inscripción u otro cobro que no extiende la membresía
        private Boolean pagoUnico;

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

        public Double getPrecio() { return precio; }
        public void setPrecio(Double precio) { this.precio = precio; }

        public String getDuracionUnidad() { return duracionUnidad; }
        public void setDuracionUnidad(String duracionUnidad) { this.duracionUnidad = duracionUnidad; }

        public Integer getDuracionCantidad() { return duracionCantidad; }
        public void setDuracionCantidad(Integer duracionCantidad) { this.duracionCantidad = duracionCantidad; }

        public List<String> getBeneficios() { return beneficios; }
        public void setBeneficios(List<String> beneficios) { this.beneficios = beneficios; }

        public Boolean getDestacado() { return destacado; }
        public void setDestacado(Boolean destacado) { this.destacado = destacado; }

        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }

        public Boolean getPagoUnico() { return pagoUnico; }
        public void setPagoUnico(Boolean pagoUnico) { this.pagoUnico = pagoUnico; }
    }
}
