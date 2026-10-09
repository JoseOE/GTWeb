package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// El carrito de cada miembro, completo, en MongoDB: uno por usuario, gimnasio
// y canal. Así, si alguien agrega algo en la computadora y luego abre la
// página en su celular, encuentra el mismo carrito: la clave no depende del
// dispositivo. localStorage queda solo como caché para pintar rápido el contador.
//
// Al pagarse, el carrito se vacía y cartId vuelve a null: el siguiente
// producto que agregue abre uno nuevo.
@Document(collection = "carritos")
@CompoundIndex(name = "usuario_gimnasio_canal", def = "{'userId': 1, 'gymId': 1, 'canal': 1}", unique = true)
public class Carrito {

    @Id
    private String id;
    private String userId;
    private String gymId;
    // "web" o "app".
    private String canal;
    // Id que ven la página y la app (cart_...). null = todavía no agrega nada.
    private String cartId;
    private List<Partida> items = new ArrayList<>();
    // Mientras se cobra (un doble clic, dos dispositivos), nadie más lo cobra.
    private Instant cobrandoDesde;
    private Instant creadoEn;
    private Instant actualizadoEn;
    // Dos dispositivos que cambian el carrito a la vez no se pisan.
    @Version
    private Long version;

    public Carrito() {}

    public Carrito(String userId, String gymId, String canal) {
        this.userId = userId;
        this.gymId = gymId;
        this.canal = canal;
        this.creadoEn = Instant.now();
        this.actualizadoEn = this.creadoEn;
    }

    // Una partida con lo que se vio al agregarla. El precio se revisa contra
    // el catálogo antes de mostrarla y antes de cobrar.
    public static class Partida {
        // cali_...
        private String id;
        private String productoId;
        private String varianteId;
        private String titulo;
        // "Bote 1 kg · Vainilla" (en un plan, su presentación interna "Plan").
        private String variante;
        private String imagen;
        private boolean esPlan;
        // Duración del plan al agregarlo: el pedido la conserva aunque el plan cambie.
        private String duracionUnidad;
        private Integer duracionCantidad;
        private int cantidad;
        private double precioUnitario;
        private Instant agregadoEn;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getProductoId() { return productoId; }
        public void setProductoId(String productoId) { this.productoId = productoId; }

        public String getVarianteId() { return varianteId; }
        public void setVarianteId(String varianteId) { this.varianteId = varianteId; }

        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }

        public String getVariante() { return variante; }
        public void setVariante(String variante) { this.variante = variante; }

        public String getImagen() { return imagen; }
        public void setImagen(String imagen) { this.imagen = imagen; }

        public boolean isEsPlan() { return esPlan; }
        public void setEsPlan(boolean esPlan) { this.esPlan = esPlan; }

        public String getDuracionUnidad() { return duracionUnidad; }
        public void setDuracionUnidad(String duracionUnidad) { this.duracionUnidad = duracionUnidad; }

        public Integer getDuracionCantidad() { return duracionCantidad; }
        public void setDuracionCantidad(Integer duracionCantidad) { this.duracionCantidad = duracionCantidad; }

        public int getCantidad() { return cantidad; }
        public void setCantidad(int cantidad) { this.cantidad = cantidad; }

        public double getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; }

        public Instant getAgregadoEn() { return agregadoEn; }
        public void setAgregadoEn(Instant agregadoEn) { this.agregadoEn = agregadoEn; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }

    public List<Partida> getItems() { return items; }
    public void setItems(List<Partida> items) { this.items = items; }

    public Instant getCobrandoDesde() { return cobrandoDesde; }
    public void setCobrandoDesde(Instant cobrandoDesde) { this.cobrandoDesde = cobrandoDesde; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
