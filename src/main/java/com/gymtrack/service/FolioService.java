package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;

// Folio corto de cada pedido (#312), el que ve el cliente en el recibo. Sigue
// la numeración que llevaba Medusa (display_id): un contador en la colección
// contadores ({_id: "folio", valor}) que se incrementa de forma atómica, así
// dos pedidos a la vez nunca reciben el mismo folio.
@Service
public class FolioService {

    private static final String COLECCION = "contadores";

    private final MongoTemplate mongo;
    private volatile boolean alineado;

    public FolioService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public long siguiente() {
        if (!alineado) alinear();
        Document d = mongo.getCollection(COLECCION).findOneAndUpdate(
                new Document("_id", "folio"),
                new Document("$inc", new Document("valor", 1L)),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return ((Number) d.get("valor")).longValue();
    }

    // La primera vez, el contador arranca después del folio más alto que ya
    // existe (los pedidos que dejó Medusa o la migración).
    private synchronized void alinear() {
        if (alineado) return;
        Pedido ultimo = mongo.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "folio")).limit(1), Pedido.class);
        long maximo = ultimo == null || ultimo.getFolio() == null ? 0 : ultimo.getFolio();
        mongo.getCollection(COLECCION).updateOne(new Document("_id", "folio"),
                new Document("$max", new Document("valor", maximo)), new UpdateOptions().upsert(true));
        alineado = true;
    }
}
