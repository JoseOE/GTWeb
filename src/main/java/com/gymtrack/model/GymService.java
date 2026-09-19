package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "servicios")
public class GymService {
    @Id
    private String id;
    private String nombre;
    private String descripcion;
    private Double precio;
    private String icono;
    private String imagen;
    private String categoria;
    private String duracion;
    private Boolean destacado;
    private Boolean activo;

    public GymService() {}

    public GymService(String id, String nombre, String descripcion, Double precio, String icono,
                      String imagen, String categoria, String duracion, Boolean destacado, Boolean activo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.icono = icono;
        this.imagen = imagen;
        this.categoria = categoria;
        this.duracion = duracion;
        this.destacado = destacado;
        this.activo = activo;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }
    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
    public Boolean getDestacado() { return destacado; }
    public void setDestacado(Boolean destacado) { this.destacado = destacado; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
