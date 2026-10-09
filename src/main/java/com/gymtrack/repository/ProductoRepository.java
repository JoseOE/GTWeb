package com.gymtrack.repository;

import com.gymtrack.model.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends MongoRepository<Producto, String> {
    // Catálogo del panel: productos o planes de un gimnasio (publicados u ocultos).
    List<Producto> findByGymIdAndTipo(String gymId, String tipo);
    // Tienda y mostrador: solo lo publicado.
    List<Producto> findByGymIdAndActivoTrue(String gymId);
    Optional<Producto> findByIdAndGymId(String id, String gymId);
    // Producto de un gimnasio que contiene esa variante.
    Optional<Producto> findByGymIdAndVariantesId(String gymId, String varianteId);
}
