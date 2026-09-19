package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

// Una máquina concreta de un gimnasio concreto. Dos gimnasios pueden tener una
// "prensa de piernas" y no permitir los mismos ejercicios, así que los ejercicios
// viven dentro de la máquina y no en un catálogo global.
@Document(collection = "machines")
public class Machine {

    @Id
    private String id;
    private String gymId;
    private String nombre;
    // Zona del gimnasio: "Peso libre", "Cardio", "Funcional"...
    private String zona;
    private String descripcion;
    private Integer cantidad = 1;
    private List<Exercise> ejercicios = new ArrayList<>();

    public Machine() {}

    public Machine(String gymId, String nombre, String zona) {
        this.gymId = gymId;
        this.nombre = nombre;
        this.zona = zona;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getZona() { return zona; }
    public void setZona(String zona) { this.zona = zona; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public List<Exercise> getEjercicios() { return ejercicios; }
    public void setEjercicios(List<Exercise> ejercicios) { this.ejercicios = ejercicios; }

    // Ejercicio que se puede hacer en ESTA máquina de ESTE gimnasio.
    public static class Exercise {
        private String id;
        private String nombre;
        // Grupo muscular principal: "Espalda", "Pecho", "Pierna"...
        private String musculo;
        private Integer series;
        private String repeticiones;
        private String instrucciones;

        public Exercise() {}

        public Exercise(String id, String nombre, String musculo, Integer series, String repeticiones) {
            this.id = id;
            this.nombre = nombre;
            this.musculo = musculo;
            this.series = series;
            this.repeticiones = repeticiones;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getMusculo() { return musculo; }
        public void setMusculo(String musculo) { this.musculo = musculo; }

        public Integer getSeries() { return series; }
        public void setSeries(Integer series) { this.series = series; }

        public String getRepeticiones() { return repeticiones; }
        public void setRepeticiones(String repeticiones) { this.repeticiones = repeticiones; }

        public String getInstrucciones() { return instrucciones; }
        public void setInstrucciones(String instrucciones) { this.instrucciones = instrucciones; }
    }
}
