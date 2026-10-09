package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Lo que vende cada gimnasio: productos (suplementos, bebidas…) y planes de
// membresía. Las variantes van dentro del producto: así leer la tienda es una
// sola consulta y el stock de cada presentación se cambia con una actualización
// atómica sobre este mismo documento (InventarioService).
@Document(collection = "productos")
@CompoundIndexes({
        // La tienda y el panel siempre filtran por gimnasio, tipo y si está publicado.
        @CompoundIndex(name = "gimnasio_tipo_activo", def = "{'gymId': 1, 'tipo': 1, 'activo': 1}"),
        // El carrito y el mostrador llegan con el id de la variante.
        @CompoundIndex(name = "variante", def = "{'variantes.id': 1}")
})
public class Producto {

    public static final String TIPO_PRODUCTO = "producto";
    public static final String TIPO_MEMBRESIA = "membresia";

    @Id
    private String id;
    private String gymId;
    // "producto" o "membresia".
    private String tipo;
    // handle de la categoría: suplementos | bebidas | snacks | accesorios | membresias
    private String categoria;
    private String nombre;
    private String descripcion;
    // URL de /api/imagenes/{id}
    private String imagen;
    // false = oculto: no aparece en la tienda ni en el mostrador.
    private boolean activo = true;
    // Posición para ordenar a mano (0 = por nombre).
    private int orden;
    // Solo en los planes de membresía.
    private Plan plan;
    private List<Variante> variantes = new ArrayList<>();
    private Instant creadoEn;
    private Instant actualizadoEn;
    // Cambia con cada escritura (también las de stock): dos ediciones a la vez
    // no se pisan.
    @Version
    private Long version;

    public boolean esPlan() {
        return TIPO_MEMBRESIA.equals(tipo);
    }

    public Variante variante(String varianteId) {
        if (varianteId == null) return null;
        for (Variante v : variantes) {
            if (varianteId.equals(v.getId())) return v;
        }
        return null;
    }

    // Duración y beneficios de un plan de membresía.
    public static class Plan {
        // true = inscripción u otro cobro que no extiende la membresía.
        private boolean pagoUnico;
        // dia | semana | mes (null si es de pago único)
        private String duracionUnidad;
        private Integer duracionCantidad;
        private List<String> beneficios = new ArrayList<>();
        private boolean destacado;

        public boolean isPagoUnico() { return pagoUnico; }
        public void setPagoUnico(boolean pagoUnico) { this.pagoUnico = pagoUnico; }

        public String getDuracionUnidad() { return duracionUnidad; }
        public void setDuracionUnidad(String duracionUnidad) { this.duracionUnidad = duracionUnidad; }

        public Integer getDuracionCantidad() { return duracionCantidad; }
        public void setDuracionCantidad(Integer duracionCantidad) { this.duracionCantidad = duracionCantidad; }

        public List<String> getBeneficios() { return beneficios; }
        public void setBeneficios(List<String> beneficios) { this.beneficios = beneficios; }

        public boolean isDestacado() { return destacado; }
        public void setDestacado(boolean destacado) { this.destacado = destacado; }
    }

    // Una presentación con su precio y su stock.
    //
    // Con inventario se cumple siempre disponible = existencias - apartadas:
    //  - existencias: piezas físicas en el gimnasio (lo que captura el dueño);
    //  - apartadas: vendidas o por pagar (Paynet) que todavía no se entregan;
    //  - disponible: lo que todavía se puede vender. Las compras lo descuentan
    //    con una actualización condicionada (disponible >= cantidad).
    public static class Variante {
        private String id;
        // "Bote 1 kg", "Scoop"… (en kilos, como sugiere el panel)
        private String presentacion;
        private String sabor;
        // MXN con IVA incluido.
        private double precio;
        // false = no se lleva inventario (scoops, planes): nunca se agota.
        private boolean controlarInventario;
        private int existencias;
        private int apartadas;
        private int disponible;
        // Orden en que el dueño capturó las presentaciones.
        private int orden;

        // "Bote 1 kg · Vainilla", o solo la presentación si no tiene sabor.
        public String etiqueta() {
            return sabor == null || sabor.isBlank() ? presentacion : presentacion + " · " + sabor;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPresentacion() { return presentacion; }
        public void setPresentacion(String presentacion) { this.presentacion = presentacion; }

        public String getSabor() { return sabor; }
        public void setSabor(String sabor) { this.sabor = sabor; }

        public double getPrecio() { return precio; }
        public void setPrecio(double precio) { this.precio = precio; }

        public boolean isControlarInventario() { return controlarInventario; }
        public void setControlarInventario(boolean controlarInventario) { this.controlarInventario = controlarInventario; }

        public int getExistencias() { return existencias; }
        public void setExistencias(int existencias) { this.existencias = existencias; }

        public int getApartadas() { return apartadas; }
        public void setApartadas(int apartadas) { this.apartadas = apartadas; }

        public int getDisponible() { return disponible; }
        public void setDisponible(int disponible) { this.disponible = disponible; }

        public int getOrden() { return orden; }
        public void setOrden(int orden) { this.orden = orden; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }

    public List<Variante> getVariantes() { return variantes; }
    public void setVariantes(List<Variante> variantes) { this.variantes = variantes; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
