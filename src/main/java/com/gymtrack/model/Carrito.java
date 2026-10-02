package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Qué carrito de Medusa le toca a cada usuario. El carrito de verdad (partidas,
// precios, totales) vive en Medusa; aquí solo se guarda su id por usuario,
// gimnasio y canal. Así, si alguien agrega algo en la computadora y luego abre
// la página en su celular, encuentra el mismo carrito: la clave no depende del
// dispositivo. localStorage queda solo como caché para pintar rápido el contador.
//
// Al completar el pedido cartId vuelve a null y el siguiente producto que agregue
// abre un carrito nuevo.
@Document(collection = "carritos")
@CompoundIndex(name = "usuario_gimnasio_canal", def = "{'userId': 1, 'gymId': 1, 'canal': 1}", unique = true)
public class Carrito {

    @Id
    private String id;
    private String userId;
    private String gymId;
    // "web" o "app" (el mostrador lleva su propio carrito por dueño).
    private String canal;
    // Id del carrito en Medusa (cart_...). null = todavía no agrega nada.
    private String cartId;
    private Instant creadoEn;
    private Instant actualizadoEn;

    public Carrito() {}

    public Carrito(String userId, String gymId, String canal) {
        this.userId = userId;
        this.gymId = gymId;
        this.canal = canal;
        this.creadoEn = Instant.now();
        this.actualizadoEn = this.creadoEn;
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

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
