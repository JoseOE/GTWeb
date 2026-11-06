package com.gymtrack.config;

import com.mongodb.MongoCommandException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Crea los índices que piden los modelos (@Indexed, @CompoundIndex) al arrancar,
// antes de que el servidor reciba peticiones. Reemplaza el
// auto-index-creation de Spring Data (apagado en application.properties).
//
// La diferencia: si en la base ya hay un índice con las mismas llaves pero
// otras opciones, lo borra y lo crea como lo pide el modelo, en lugar de
// impedir que la aplicación arranque. Pasó al subir a Spring Boot 3.5: la
// versión anterior ignoraba @Indexed(expireAfter = "0s"), así que en Atlas
// "sesiones.expiraEn" y "enlaces_recibo.venceEn" quedaron sin TTL, y la nueva
// pedía crearlos con TTL; Mongo responde IndexOptionsConflict y Render no
// podía desplegar.
@Component
public class IndicesMongo implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(IndicesMongo.class);

    // IndexOptionsConflict e IndexKeySpecsConflict: mismo índice, otras opciones.
    private static final int OPCIONES_DISTINTAS = 85;
    private static final int LLAVES_DISTINTAS = 86;

    private final MongoTemplate mongo;
    private final MongoMappingContext modelos;

    public IndicesMongo(MongoTemplate mongo, MongoMappingContext modelos) {
        this.mongo = mongo;
        this.modelos = modelos;
    }

    @Override
    public void afterSingletonsInstantiated() {
        IndexResolver resolver = IndexResolver.create(modelos);
        List<MongoPersistentEntity<?>> entidades = new ArrayList<>(modelos.getPersistentEntities());
        for (MongoPersistentEntity<?> entidad : entidades) {
            if (!entidad.isAnnotationPresent(org.springframework.data.mongodb.core.mapping.Document.class)) continue;
            IndexOperations ops = mongo.indexOps(entidad.getType());
            for (IndexDefinition indice : resolver.resolveIndexFor(entidad.getType())) {
                crear(ops, entidad.getCollection(), indice);
            }
        }
    }

    private void crear(IndexOperations ops, String coleccion, IndexDefinition indice) {
        try {
            ops.createIndex(indice);
        } catch (DataAccessException e) {
            if (!esConflicto(e)) throw e;
            // Con las mismas llaves (otras opciones), o con el mismo nombre (otras llaves).
            String viejo = existente(ops, indice.getIndexKeys());
            if (viejo == null) viejo = indice.getIndexOptions().getString("name");
            if (viejo == null) throw e;
            log.warn("Índice {}.{} con otras opciones: se vuelve a crear como lo pide el modelo ({}).",
                    coleccion, viejo, indice.getIndexOptions().toJson());
            ops.dropIndex(viejo);
            ops.createIndex(indice);
        }
    }

    private static boolean esConflicto(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MongoCommandException m
                    && (m.getErrorCode() == OPCIONES_DISTINTAS || m.getErrorCode() == LLAVES_DISTINTAS)) {
                return true;
            }
        }
        return false;
    }

    // El nombre del índice que ya existe con esas mismas llaves (en el mismo orden).
    private static String existente(IndexOperations ops, Document llaves) {
        List<String> buscadas = new ArrayList<>(llaves.keySet());
        for (IndexInfo info : ops.getIndexInfo()) {
            List<String> campos = info.getIndexFields().stream().map(IndexField::getKey).toList();
            if (Objects.equals(campos, buscadas)) return info.getName();
        }
        return null;
    }
}
