package com.gymtrack.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "gyms")
public class Gym {

    @Id
    private String id;
    private String nombre;
    private String telefono;
    private String direccion;
    private String horario;
    // Notas libres de equipamiento (campo original, se conserva para no perder datos)
    private String equipamiento;
    // Inventario de máquinas, una por entrada: "Prensa de piernas x2"
    private List<String> maquinas = new ArrayList<>();
    // Código que el usuario teclea en la app para solicitar unirse a este gimnasio
    @Indexed(unique = true, sparse = true)
    private String codigo;
    private String ownerId;

    // ─── Identidad visual del gimnasio ───
    // Logo como data URI (data:image/png;base64,...). El panel lo redimensiona
    // antes de subirlo, así no hace falta contratar almacenamiento aparte.
    private String logo;
    // Foto del gimnasio (/api/imagenes/{id}): sale con las opiniones de la página de inicio.
    private String foto;
    // Color de acento del gimnasio en hex (#RRGGBB). La app tiñe su interfaz con él.
    private String colorPrimario;
    // Si aparece en el directorio público de gimnasios de la app.
    private Boolean enDirectorio = false;

    // ─── Cobranza ───
    // Cuota mensual sugerida, para prellenar el registro de pagos.
    private Double cuotaMensual;

    // ─── Datos del vendedor ───
    // Quién vende en la tienda del gimnasio. La ley de comercio electrónico pide
    // mostrarlo antes de comprar: aparece en la tienda, los términos y los recibos.
    // Nombre o razón social; si falta, se usa el nombre del gimnasio.
    private String razonSocial;
    // Opcional: no todos los gimnasios facturan.
    private String rfc;
    // Correo para dudas, cancelaciones y aclaraciones de compras.
    private String correoContacto;

    public Gym() {}

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public String getEquipamiento() { return equipamiento; }
    public void setEquipamiento(String equipamiento) { this.equipamiento = equipamiento; }

    public List<String> getMaquinas() { return maquinas; }
    public void setMaquinas(List<String> maquinas) { this.maquinas = maquinas; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getColorPrimario() { return colorPrimario; }
    public void setColorPrimario(String colorPrimario) { this.colorPrimario = colorPrimario; }

    public Boolean getEnDirectorio() { return enDirectorio; }
    public void setEnDirectorio(Boolean enDirectorio) { this.enDirectorio = enDirectorio; }

    public Double getCuotaMensual() { return cuotaMensual; }
    public void setCuotaMensual(Double cuotaMensual) { this.cuotaMensual = cuotaMensual; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getRfc() { return rfc; }
    public void setRfc(String rfc) { this.rfc = rfc; }

    public String getCorreoContacto() { return correoContacto; }
    public void setCorreoContacto(String correoContacto) { this.correoContacto = correoContacto; }

}
