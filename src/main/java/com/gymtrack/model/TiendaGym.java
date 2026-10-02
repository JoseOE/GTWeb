package com.gymtrack.model;

import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

// Lo que cada gimnasio tiene dentro de Medusa. Se crea la primera vez que el
// gimnasio usa la tienda (TiendaGymService) y se guarda pieza por pieza: si
// Medusa falla a la mitad, el siguiente intento continúa donde se quedó en
// lugar de duplicar canales o almacenes.
public class TiendaGym {

    // Canal de venta: separa los productos y pedidos de este gimnasio de los demás.
    // Indexado porque cada pedido que llega de Medusa se asigna a su gimnasio por aquí.
    @Indexed(sparse = true)
    private String salesChannelId;
    // Almacén del gimnasio: ahí vive el stock de sus productos.
    private String stockLocationId;
    // "Recoger en el gimnasio": conjunto de entrega, su zona (México) y la opción de envío de $0.
    private String fulfillmentSetId;
    private String serviceZoneId;
    private String shippingOptionId;
    // Llave publicable de la Store API, ligada solo al canal de este gimnasio.
    private String publishableKeyId;
    private String publishableKey;
    // Fecha en que quedó lista; null mientras falte alguna pieza.
    private Instant lista;

    public boolean estaCompleta() {
        return salesChannelId != null && stockLocationId != null && fulfillmentSetId != null
                && serviceZoneId != null && shippingOptionId != null && publishableKeyId != null
                && publishableKey != null;
    }

    public String getSalesChannelId() { return salesChannelId; }
    public void setSalesChannelId(String salesChannelId) { this.salesChannelId = salesChannelId; }

    public String getStockLocationId() { return stockLocationId; }
    public void setStockLocationId(String stockLocationId) { this.stockLocationId = stockLocationId; }

    public String getFulfillmentSetId() { return fulfillmentSetId; }
    public void setFulfillmentSetId(String fulfillmentSetId) { this.fulfillmentSetId = fulfillmentSetId; }

    public String getServiceZoneId() { return serviceZoneId; }
    public void setServiceZoneId(String serviceZoneId) { this.serviceZoneId = serviceZoneId; }

    public String getShippingOptionId() { return shippingOptionId; }
    public void setShippingOptionId(String shippingOptionId) { this.shippingOptionId = shippingOptionId; }

    public String getPublishableKeyId() { return publishableKeyId; }
    public void setPublishableKeyId(String publishableKeyId) { this.publishableKeyId = publishableKeyId; }

    public String getPublishableKey() { return publishableKey; }
    public void setPublishableKey(String publishableKey) { this.publishableKey = publishableKey; }

    public Instant getLista() { return lista; }
    public void setLista(Instant lista) { this.lista = lista; }
}
