package com.gymtrack.service;

import com.gymtrack.model.Gym;
import com.gymtrack.repository.GymRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Lo que comparten las tiendas de todos los gimnasios: tipos de producto y
// categorías fijas. Cada producto y cada pedido se guardan con su gymId, así
// que un gimnasio nuevo no necesita preparar nada para vender.
@Service
public class TiendaGymService {

    public static final String MONEDA = "mxn";
    public static final String TIPO_PRODUCTO = "producto";
    public static final String TIPO_MEMBRESIA = "membresia";
    public static final String CATEGORIA_MEMBRESIAS = "membresias";

    // Categorías de la tienda, iguales para todos los gimnasios y en el orden en
    // que se muestran. El id es el mismo handle.
    private static final List<Categoria> CATEGORIAS = List.of(
            new Categoria("suplementos", "suplementos", "Suplementos"),
            new Categoria("bebidas", "bebidas", "Bebidas"),
            new Categoria("snacks", "snacks", "Snacks"),
            new Categoria("accesorios", "accesorios", "Accesorios"),
            new Categoria(CATEGORIA_MEMBRESIAS, CATEGORIA_MEMBRESIAS, "Membresías"));

    private final GymRepository gymRepository;

    public TiendaGymService(GymRepository gymRepository) {
        this.gymRepository = gymRepository;
    }

    public record Categoria(String id, String handle, String nombre) {}

    public List<Categoria> categorias() {
        return CATEGORIAS;
    }

    public Optional<Categoria> categoria(String handle) {
        return CATEGORIAS.stream().filter(c -> c.handle().equals(handle)).findFirst();
    }

    // El gimnasio de la tienda. Ya no hay nada que preparar la primera vez:
    // sus productos y pedidos se guardan con su gymId.
    public Gym exigirGimnasio(String gymId) {
        return gymRepository.findById(gymId)
                .orElseThrow(() -> new TiendaException(HttpStatus.NOT_FOUND, "Gimnasio no encontrado."));
    }
}
