package com.gymtrack.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Folio corto de cada pedido (#12), el que ve el cliente en el recibo. Cada
// gimnasio lleva su propia numeración (#1, #2, #3…): lo que venden los demás
// no le salta folios. Es un contador por gimnasio en la colección contadores
// ({_id: "folio:<gymId>", valor}) que se incrementa de forma atómica, así dos
// pedidos a la vez nunca reciben el mismo folio.
//
// Antes había un solo contador para todos los gimnasios. La primera vez que
// arranca esta versión, renumera los pedidos que ya existen, gimnasio por
// gimnasio y en el orden en que se crearon, y deja la marca
// {_id: "folios-por-gimnasio"} para no volver a hacerlo.
@Service
public class FolioService {

    private static final Logger log = LoggerFactory.getLogger(FolioService.class);
    private static final String COLECCION = "contadores";
    private static final String MARCA = "folios-por-gimnasio";

    private final MongoTemplate mongo;
    private final Set<String> alineados = ConcurrentHashMap.newKeySet();
    private volatile boolean migrado;

    public FolioService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public static String contador(String gymId) {
        return "folio:" + gymId;
    }

    public long siguiente(String gymId) {
        if (!migrado) migrar();
        if (!alineados.contains(gymId)) alinear(gymId);
        Document d = contadores().findOneAndUpdate(
                new Document("_id", contador(gymId)),
                new Document("$inc", new Document("valor", 1L)),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return ((Number) d.get("valor")).longValue();
    }

    // La primera vez por gimnasio, el contador arranca después del folio más
    // alto que ya tiene ese gimnasio (por si se perdió el contador).
    private void alinear(String gymId) {
        Document ultimo = pedidos().find(new Document("gymId", gymId))
                .projection(new Document("folio", 1)).sort(new Document("folio", -1)).limit(1).first();
        long maximo = ultimo == null || ultimo.get("folio") == null ? 0 : ((Number) ultimo.get("folio")).longValue();
        contadores().updateOne(new Document("_id", contador(gymId)),
                new Document("$max", new Document("valor", maximo)), new UpdateOptions().upsert(true));
        alineados.add(gymId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            migrar();
        } catch (RuntimeException e) {
            // Si Mongo no respondió, se reintenta con la primera venta.
            log.error("No se pudieron renumerar los folios por gimnasio; se reintentará con la primera venta.", e);
        }
    }

    // Una sola vez: renumera los pedidos de cada gimnasio del 1 en adelante,
    // del más viejo al más nuevo, y deja cada contador en su último folio.
    // Es synchronized: el primer pedido que llegue mientras corre, espera.
    synchronized void migrar() {
        if (migrado) return;
        if (contadores().find(new Document("_id", MARCA)).first() != null) {
            migrado = true;
            return;
        }
        int gimnasios = 0;
        long renumerados = 0;
        for (String gymId : pedidos().distinct("gymId", String.class)) {
            if (gymId == null) continue;
            List<WriteModel<Document>> cambios = new ArrayList<>();
            long folio = 0;
            for (Document p : pedidos().find(new Document("gymId", gymId))
                    .projection(new Document("_id", 1))
                    .sort(new Document("creadoEn", 1).append("folio", 1).append("_id", 1))) {
                folio++;
                cambios.add(new UpdateOneModel<>(new Document("_id", p.get("_id")),
                        new Document("$set", new Document("folio", folio))));
            }
            if (!cambios.isEmpty()) pedidos().bulkWrite(cambios);
            contadores().updateOne(new Document("_id", contador(gymId)),
                    new Document("$set", new Document("valor", folio)), new UpdateOptions().upsert(true));
            gimnasios++;
            renumerados += folio;
        }
        contadores().updateOne(new Document("_id", MARCA),
                new Document("$set", new Document("hecho", true).append("en", new java.util.Date())), new UpdateOptions().upsert(true));
        migrado = true;
        if (renumerados > 0) log.info("Folios por gimnasio: se renumeraron {} pedidos de {} gimnasios.", renumerados, gimnasios);
    }

    private MongoCollection<Document> contadores() {
        return mongo.getCollection(COLECCION);
    }

    private MongoCollection<Document> pedidos() {
        return mongo.getCollection("pedidos");
    }
}
