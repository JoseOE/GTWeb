package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Reseña de un producto de la tienda: de 1 a 5 estrellas y un comentario
// opcional. Solo la deja quien compró el producto (un pedido pagado que lo
// incluye) y es una por persona y producto: si lo vuelve a comprar, edita la
// suya en lugar de sumar otra, así nadie infla el promedio.
@Document(collection = "resenas")
@CompoundIndexes({
        // Una por miembro y producto.
        @CompoundIndex(name = "usuario_producto", def = "{'userId': 1, 'productoId': 1}", unique = true),
        // Las reseñas visibles de un producto, de la más nueva a la más vieja.
        @CompoundIndex(name = "producto_visibles", def = "{'productoId': 1, 'oculta': 1, 'actualizadoEn': -1}"),
        // El promedio de todos los productos de un gimnasio (tienda y carrusel).
        @CompoundIndex(name = "gimnasio_visibles", def = "{'gymId': 1, 'oculta': 1}")
})
public class Resena {

    @Id
    private String id;
    private String gymId;
    private String productoId;
    private String userId;
    // Cómo firma: nombre e inicial del apellido ("Ana P."), tomado al guardar.
    private String autor;
    private int estrellas;
    private String comentario;
    // El dueño puede ocultar una reseña ofensiva o que no trata del producto.
    // Oculta no cuenta en el promedio ni se muestra, pero su autor la sigue viendo.
    private boolean oculta;
    private Instant creadoEn;
    private Instant actualizadoEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }

    public int getEstrellas() { return estrellas; }
    public void setEstrellas(int estrellas) { this.estrellas = estrellas; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public boolean isOculta() { return oculta; }
    public void setOculta(boolean oculta) { this.oculta = oculta; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }

    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
