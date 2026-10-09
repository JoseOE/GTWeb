package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Copia local de un pedido de la tienda. El pedido de verdad vive en Medusa;
// aquí queda lo que Spring necesita sin preguntarle a Medusa cada vez: de quién
// es (para no enseñarle a nadie pedidos ajenos), de qué gimnasio, su estado y
// las partidas para los recibos y el dashboard de ventas.
//
// Se escribe al completar el checkout y se vuelve a sincronizar con cada aviso
// de Medusa (PedidoService.sincronizar), así que siempre refleja el último estado.
@Document(collection = "pedidos")
public class Pedido {

    public static final String ESTADO_PENDIENTE_PAGO = "pendiente_pago";
    public static final String ESTADO_PAGADO = "pagado";
    public static final String ESTADO_CANCELADO = "cancelado";
    public static final String ESTADO_REEMBOLSADO = "reembolsado";

    public static final String CANAL_WEB = "web";
    public static final String CANAL_APP = "app";
    public static final String CANAL_MOSTRADOR = "mostrador";

    @Id
    private String id;
    // Id del pedido en Medusa (order_...).
    @Indexed(unique = true)
    private String orderId;
    // Folio corto que ve el cliente (#12).
    private Long folio;
    @Indexed
    private String gymId;
    // null en ventas de mostrador a "Público en general".
    @Indexed(sparse = true)
    private String userId;
    private String email;
    private String canal;
    private String estado;
    // Proveedor de pago de Medusa (pp_sim-stripe_default, pp_system_default...).
    private String proveedorPago;
    // Lo que cada simulador guardó del pago, sin nada sensible: marca y últimos 4
    // de la tarjeta, lo recibido y el cambio en efectivo, la referencia Paynet...
    // Es lo que muestran el ticket y el recibo.
    private Map<String, Object> datosPago = new LinkedHashMap<>();
    // Nombre del cliente en ventas de mostrador ("Público en general" si no es miembro).
    private String cliente;
    // Dueño que cobró en el mostrador.
    private String vendedorId;
    private Double total;
    private Double subtotal;
    private Double iva;
    private List<Partida> partidas = new ArrayList<>();
    private Instant creadoEn;
    private Instant pagadoEn;
    private Instant canceladoEn;
    // true cuando ya se extendió la membresía por el plan de este pedido.
    private boolean planAplicado;
    private Instant sincronizadoEn;
    // Piezas que este pedido tomó del inventario y cómo. Con esto se regresan,
    // una sola vez, si se cancela, vence o se reembolsa (InventarioService).
    private Inventario inventario;

    public Pedido() {}

    public boolean incluyePlan() {
        return partidas.stream().anyMatch(Partida::esPlan);
    }

    public static class Inventario {
        // Tienda y app: vendidas o por pagar (Paynet), todavía sin entregar.
        public static final String APARTADO = "apartado";
        // Mostrador: entregadas en el acto.
        public static final String DESCONTADO = "descontado";
        // Ya regresaron al inventario (cancelado, vencido o reembolsado).
        public static final String DEVUELTO = "devuelto";
        // Nada que mover: solo llevaba variantes sin inventario (scoops, planes).
        public static final String SIN_PIEZAS = "sin_piezas";

        private String estado;
        private List<Pieza> piezas = new ArrayList<>();
        private Instant devueltoEn;

        public String getEstado() { return estado; }
        public void setEstado(String estado) { this.estado = estado; }

        public List<Pieza> getPiezas() { return piezas; }
        public void setPiezas(List<Pieza> piezas) { this.piezas = piezas; }

        public Instant getDevueltoEn() { return devueltoEn; }
        public void setDevueltoEn(Instant devueltoEn) { this.devueltoEn = devueltoEn; }
    }

    public static class Pieza {
        private String productoId;
        private String varianteId;
        private int cantidad;

        public String getProductoId() { return productoId; }
        public void setProductoId(String productoId) { this.productoId = productoId; }

        public String getVarianteId() { return varianteId; }
        public void setVarianteId(String varianteId) { this.varianteId = varianteId; }

        public int getCantidad() { return cantidad; }
        public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    }

    public static class Partida {
        private String productoId;
        private String varianteId;
        private String titulo;
        private String variante;
        // "producto" o "membresia" (tipo de producto en Medusa).
        private String tipo;
        private Integer cantidad;
        private Double precioUnitario;
        private Double total;

        public boolean esPlan() {
            return "membresia".equals(tipo);
        }

        public String getProductoId() { return productoId; }
        public void setProductoId(String productoId) { this.productoId = productoId; }

        public String getVarianteId() { return varianteId; }
        public void setVarianteId(String varianteId) { this.varianteId = varianteId; }

        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }

        public String getVariante() { return variante; }
        public void setVariante(String variante) { this.variante = variante; }

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

        public Double getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(Double precioUnitario) { this.precioUnitario = precioUnitario; }

        public Double getTotal() { return total; }
        public void setTotal(Double total) { this.total = total; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getFolio() { return folio; }
    public void setFolio(Long folio) { this.folio = folio; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getProveedorPago() { return proveedorPago; }
    public void setProveedorPago(String proveedorPago) { this.proveedorPago = proveedorPago; }

    public Map<String, Object> getDatosPago() { return datosPago; }
    public void setDatosPago(Map<String, Object> datosPago) { this.datosPago = datosPago; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getVendedorId() { return vendedorId; }
    public void setVendedorId(String vendedorId) { this.vendedorId = vendedorId; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }

    public Double getIva() { return iva; }
    public void setIva(Double iva) { this.iva = iva; }

    public List<Partida> getPartidas() { return partidas; }
    public void setPartidas(List<Partida> partidas) { this.partidas = partidas; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getPagadoEn() { return pagadoEn; }
    public void setPagadoEn(Instant pagadoEn) { this.pagadoEn = pagadoEn; }

    public Instant getCanceladoEn() { return canceladoEn; }
    public void setCanceladoEn(Instant canceladoEn) { this.canceladoEn = canceladoEn; }

    public boolean isPlanAplicado() { return planAplicado; }
    public void setPlanAplicado(boolean planAplicado) { this.planAplicado = planAplicado; }

    public Instant getSincronizadoEn() { return sincronizadoEn; }
    public void setSincronizadoEn(Instant sincronizadoEn) { this.sincronizadoEn = sincronizadoEn; }

    public Inventario getInventario() { return inventario; }
    public void setInventario(Inventario inventario) { this.inventario = inventario; }
}
