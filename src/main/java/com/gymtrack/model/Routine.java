package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private List<String> ejercicios;
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

    public String getDia() { return dia; }
    public void setDia(String dia) { this.dia = dia; }

    public String getCoach() { return coach; }
    public void setCoach(String coach) { this.coach = coach; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
}
