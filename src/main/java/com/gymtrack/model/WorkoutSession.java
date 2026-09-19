package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Un entrenamiento real que el usuario registró desde la app.
// Pertenece al usuario, no al gimnasio: si se cambia de gimnasio o se queda sin
// uno, su historial sigue siendo suyo. gymId solo deja constancia de dónde entrenó.
@Document(collection = "workouts")
public class WorkoutSession {

    @Id
    private String id;
    private String userId;
    private String gymId;
    private String nombre;
    private Instant fecha;
    private Integer duracionMinutos;
    private String notas;
    private List<LoggedExercise> ejercicios = new ArrayList<>();

    public WorkoutSession() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGymId() { return gymId; }
    public void setGymId(String gymId) { this.gymId = gymId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }

    public Integer getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(Integer duracionMinutos) { this.duracionMinutos = duracionMinutos; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public List<LoggedExercise> getEjercicios() { return ejercicios; }
    public void setEjercicios(List<LoggedExercise> ejercicios) { this.ejercicios = ejercicios; }

    // Volumen total movido: la métrica que alimenta "kg movidos" en Progreso.
    public double volumenTotal() {
        double total = 0;
        for (LoggedExercise e : ejercicios) {
            for (LoggedSet s : e.getSeries()) {
                if (s.getPeso() != null && s.getRepeticiones() != null && Boolean.TRUE.equals(s.getCompletada())) {
                    total += s.getPeso() * s.getRepeticiones();
                }
            }
        }
        return total;
    }

    public static class LoggedExercise {
        // Referencia opcional: un ejercicio puede venir de una máquina del gimnasio
        // o ser libre (peso corporal, mancuernas, una rutina de GymTrack).
        private String machineId;
        private String machineNombre;
        private String nombre;
        private String musculo;
        private List<LoggedSet> series = new ArrayList<>();

        public String getMachineId() { return machineId; }
        public void setMachineId(String machineId) { this.machineId = machineId; }

        public String getMachineNombre() { return machineNombre; }
        public void setMachineNombre(String machineNombre) { this.machineNombre = machineNombre; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getMusculo() { return musculo; }
        public void setMusculo(String musculo) { this.musculo = musculo; }

        public List<LoggedSet> getSeries() { return series; }
        public void setSeries(List<LoggedSet> series) { this.series = series; }
    }

    public static class LoggedSet {
        private Integer repeticiones;
        private Double peso;
        private Boolean completada = true;

        public Integer getRepeticiones() { return repeticiones; }
        public void setRepeticiones(Integer repeticiones) { this.repeticiones = repeticiones; }

        public Double getPeso() { return peso; }
        public void setPeso(Double peso) { this.peso = peso; }

        public Boolean getCompletada() { return completada; }
        public void setCompletada(Boolean completada) { this.completada = completada; }
    }
}
