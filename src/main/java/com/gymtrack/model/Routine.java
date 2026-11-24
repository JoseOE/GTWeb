package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "routines")
public class Routine {

    @Id
    private String id;
    private String gymId;
    private String nombre;
    // Grupo muscular / categoría libre: "Espalda", "Pecho", "Pierna", "Cardio"...
    // Texto libre (no catálogo fijo) para que el dueño organice como quiera.
    private String categoria;
    private String descripcion;
    private String duracion;
    // Texto de cada ejercicio ("Press banca plano — 4 x 10"). Con items, el
    // servidor lo arma solo, para las versiones de la app que no conocen items.
    private List<String> ejercicios;
    // Ejercicios elegidos de las máquinas del gimnasio, en orden. Con ellos la
    // app sabe el músculo de cada uno y puede ofrecer reemplazos.
    private List<RoutineItem> items = new ArrayList<>();
    // Día de la semana que el coach asigna a esta rutina ("Lunes"..."Domingo").
    // Vacío = rutina disponible siempre, no atada a un día concreto.
    private String dia;
    // Nombre del coach que la publicó, para que el miembro sepa de quién viene.
    private String coach;
    // "Principiante" | "Intermedio" | "Avanzado" — vacío = sin especificar.
    private String nivel;

    public Routine() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }

    public List<String> getEjercicios() { return ejercicios; }
    public void setEjercicios(List<String> ejercicios) { this.ejercicios = ejercicios; }

    public List<RoutineItem> getItems() { return items; }
    public void setItems(List<RoutineItem> items) { this.items = items == null ? new ArrayList<>() : items; }

    public String getDia() { return dia; }
    public void setDia(String dia) { this.dia = dia; }

    public String getCoach() { return coach; }
    public void setCoach(String coach) { this.coach = coach; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }

    // Un ejercicio de la rutina. machineId + exerciseId apuntan al ejercicio de la
    // máquina; sin ellos es un ejercicio libre (peso corporal, mancuernas).
    public static class RoutineItem {
        private String machineId;
        private String exerciseId;
        private String machineNombre;
        private String nombre;
        private String musculo;
        private Integer series;
        private String repeticiones;
        // Descanso entre series, en segundos.
        private Integer descansoSegundos;

        public RoutineItem() {}

        public String getMachineId() { return machineId; }
        public void setMachineId(String machineId) { this.machineId = machineId; }

        public String getExerciseId() { return exerciseId; }
        public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }

        public String getMachineNombre() { return machineNombre; }
        public void setMachineNombre(String machineNombre) { this.machineNombre = machineNombre; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getMusculo() { return musculo; }
        public void setMusculo(String musculo) { this.musculo = musculo; }

        public Integer getSeries() { return series; }
        public void setSeries(Integer series) { this.series = series; }

        public String getRepeticiones() { return repeticiones; }
        public void setRepeticiones(String repeticiones) { this.repeticiones = repeticiones; }

        public Integer getDescansoSegundos() { return descansoSegundos; }
        public void setDescansoSegundos(Integer descansoSegundos) { this.descansoSegundos = descansoSegundos; }
    }
}
