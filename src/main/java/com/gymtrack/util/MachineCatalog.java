package com.gymtrack.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymtrack.model.Machine;

import java.io.IOException;
import java.text.Normalizer;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Catálogo base que un gimnasio puede importar de un clic para no empezar de cero.
// Es solo un punto de partida: al importarlo se crean copias propias del gimnasio,
// que luego puede editar, borrar o completar con sus ejercicios reales.
//
// Además está el catálogo grande (catalogo-maquinas.json): máquinas comunes de
// gimnasio clasificadas (Pecho, Espalda, Hombro, Bíceps, Tríceps, Pierna, Glúteo,
// Core, Cardio, Multifuncional, Peso libre), cada una con los ejercicios que se
// hacen en ella. En el panel, el dueño elige la máquina y qué ejercicios agregar.
public final class MachineCatalog {

    private MachineCatalog() {}

    // Orden en que el panel muestra las categorías.
    public static final List<String> CATEGORIAS = List.of(
            "Pecho", "Espalda", "Hombro", "Bíceps", "Tríceps", "Pierna", "Glúteo",
            "Core", "Cardio", "Multifuncional", "Peso libre");

    // Una máquina del catálogo grande (no es del gimnasio hasta que la agrega).
    public static class MaquinaCatalogo {
        public String nombre;
        public String categoria;
        public String zona;
        public String descripcion;
        public List<Machine.Exercise> ejercicios = new ArrayList<>();
    }

    private static final List<MaquinaCatalogo> CATALOGO = leerCatalogo();
    // Cómo se hace cada ejercicio del catálogo grande, por nombre en minúsculas.
    private static final Map<String, String> INSTRUCCIONES_CATALOGO = indexarInstrucciones();
    // Fotos de cada ejercicio del catálogo grande, por nombre en minúsculas.
    private static final Map<String, List<String>> IMAGENES_CATALOGO = indexarImagenes();

    private static List<MaquinaCatalogo> leerCatalogo() {
        ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream in = MachineCatalog.class.getResourceAsStream("/catalogo-maquinas.json")) {
            if (in == null) throw new IllegalStateException("Falta catalogo-maquinas.json");
            return List.of(json.readValue(in, MaquinaCatalogo[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer catalogo-maquinas.json", e);
        }
    }

    private static Map<String, String> indexarInstrucciones() {
        Map<String, String> m = new HashMap<>();
        for (MaquinaCatalogo maq : CATALOGO) {
            for (Machine.Exercise e : maq.ejercicios) {
                if (e.getInstrucciones() != null) m.putIfAbsent(e.getNombre().trim().toLowerCase(), e.getInstrucciones());
            }
        }
        return m;
    }

    private static Map<String, List<String>> indexarImagenes() {
        Map<String, List<String>> m = new HashMap<>();
        for (MaquinaCatalogo maq : CATALOGO) {
            for (Machine.Exercise e : maq.ejercicios) {
                if (e.getImagenes() != null && !e.getImagenes().isEmpty()) {
                    m.putIfAbsent(clave(e.getNombre()), List.copyOf(e.getImagenes()));
                }
            }
        }
        return m;
    }

    public static List<MaquinaCatalogo> catalogo() {
        return CATALOGO;
    }

    // Nombre sin acentos, en minúsculas y con un solo espacio: "Jalón  al Pecho" = "jalon al pecho".
    static String clave(String nombre) {
        return Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().replaceAll("\\s+", " ");
    }

    // Otra forma de llamar a un ejercicio del catálogo (la app usa la misma lista,
    // en GTApp/constants/ImagenesEjercicios.ts).
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("sentadilla", "sentadilla trasera"),
            Map.entry("fondos", "fondos en paralelas"),
            Map.entry("press banca", "press banca plano"),
            Map.entry("jalon al pecho", "jalon agarre ancho"),
            Map.entry("remo en polea baja", "remo bajo en estacion"),
            Map.entry("extension de piernas", "extension a dos piernas"),
            Map.entry("pantorrilla de pie", "elevacion de talones de pie"),
            Map.entry("prensa", "prensa 45°"),
            Map.entry("prensa a 45°", "prensa 45°"),
            Map.entry("pajaros en pec deck", "pec deck inverso"),
            Map.entry("caminata inclinada", "caminata en pendiente"),
            Map.entry("remo de 500 m", "remo en remadora"),
            Map.entry("bici", "pedaleo continuo"),
            Map.entry("intervalos en bici", "sprints en bici"),
            Map.entry("escaladora", "escaladora continua"),
            Map.entry("hip thrust", "hip thrust en maquina"),
            Map.entry("curl femoral", "curl femoral acostado"));

    // El nombre del catálogo que corresponde a como lo escribió el gimnasio:
    // el mismo (sin importar acentos ni mayúsculas), un alias, el más corto que
    // empiece igual ("Curl femoral" → "Curl femoral sentado") o el más largo con
    // el que empiece ("Peso muerto convencional" → "Peso muerto"). null si nada.
    static String claveDelCatalogo(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String q = clave(nombre);
        if (IMAGENES_CATALOGO.containsKey(q)) return q;
        String alias = ALIAS.get(q);
        if (alias != null && IMAGENES_CATALOGO.containsKey(alias)) return alias;
        String parecido = IMAGENES_CATALOGO.keySet().stream()
                .filter(k -> k.startsWith(q + " "))
                .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .orElse(null);
        if (parecido != null) return parecido;
        return IMAGENES_CATALOGO.keySet().stream()
                .filter(k -> q.startsWith(k + " "))
                .max(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .orElse(null);
    }

    // Ilustraciones de un ejercicio por su nombre, si está (o se parece a uno) en el catálogo; si no, null.
    public static List<String> imagenes(String nombre) {
        String k = claveDelCatalogo(nombre);
        return k == null ? null : IMAGENES_CATALOGO.get(k);
    }

    // Las fotos de personas reales que se usaron antes (free-exercise-db). Si
    // una máquina las guardó, ya no se muestran.
    static final String FOTOS_ANTERIORES = "free-exercise-db";

    // Las ilustraciones de cada ejercicio de una máquina del gimnasio: las del
    // catálogo con su nombre (aunque la máquina haya guardado otras antes) o,
    // si no está en el catálogo, las que trae, salvo las fotos anteriores. No
    // se guarda: solo cambia lo que se responde, así se actualizan también las
    // máquinas capturadas antes.
    public static void completarImagenes(Machine m) {
        if (m.getEjercicios() == null) return;
        for (Machine.Exercise e : m.getEjercicios()) {
            List<String> delCatalogo = imagenes(e.getNombre());
            if (delCatalogo != null) {
                e.setImagenes(delCatalogo);
            } else if (e.getImagenes() != null && e.getImagenes().stream().anyMatch(u -> u != null && u.contains(FOTOS_ANTERIORES))) {
                e.setImagenes(null);
            }
        }
    }

    // Cómo se hace un ejercicio, si está en alguno de los catálogos.
    public static String instrucciones(String nombre) {
        if (nombre == null) return null;
        String i = INSTRUCCIONES.get(nombre.trim());
        return i != null ? i : INSTRUCCIONES_CATALOGO.get(nombre.trim().toLowerCase());
    }

    // Copia de un ejercicio del catálogo, con id propio, para guardarlo en una máquina.
    public static Machine.Exercise copia(Machine.Exercise e) {
        Machine.Exercise c = new Machine.Exercise(UUID.randomUUID().toString(), e.getNombre(), e.getMusculo(),
                e.getSeries(), e.getRepeticiones());
        c.setInstrucciones(e.getInstrucciones());
        c.setImagenes(e.getImagenes());
        return c;
    }

    public static List<Machine> plantillaPara(String gymId) {
        List<Machine> maquinas = new ArrayList<>();

        maquinas.add(maquina(gymId, "Prensa de piernas", "Peso libre",
                ej("Prensa 45°", "Pierna", 4, "10-12"),
                ej("Prensa pies juntos", "Pierna", 3, "12"),
                ej("Prensa a una pierna", "Pierna", 3, "10 por lado"),
                ej("Elevación de talones en prensa", "Pierna", 4, "15-20")));

        maquinas.add(maquina(gymId, "Jalón al pecho", "Máquinas",
                ej("Jalón agarre ancho", "Espalda", 4, "10-12"),
                ej("Jalón agarre cerrado", "Espalda", 3, "12"),
                ej("Jalón supino", "Espalda", 3, "10-12"),
                ej("Jalón a una mano", "Espalda", 3, "12 por lado")));

        maquinas.add(maquina(gymId, "Press banca", "Peso libre",
                ej("Press banca plano", "Pecho", 4, "8-10"),
                ej("Press banca inclinado", "Pecho", 4, "10"),
                ej("Press cerrado", "Brazo", 3, "10-12")));

        maquinas.add(maquina(gymId, "Remo sentado", "Máquinas",
                ej("Remo agarre neutro", "Espalda", 4, "10-12"),
                ej("Remo agarre ancho", "Espalda", 3, "12"),
                ej("Remo a una mano", "Espalda", 3, "12 por lado")));

        maquinas.add(maquina(gymId, "Rack de sentadillas", "Peso libre",
                ej("Sentadilla trasera", "Pierna", 5, "5-8"),
                ej("Sentadilla frontal", "Pierna", 4, "8"),
                ej("Zancadas con barra", "Pierna", 3, "10 por pierna"),
                ej("Press militar de pie", "Hombro", 4, "8-10")));

        maquinas.add(maquina(gymId, "Poleas cruzadas", "Máquinas",
                ej("Cruce de poleas alto", "Pecho", 4, "12-15"),
                ej("Cruce de poleas bajo", "Pecho", 3, "12-15"),
                ej("Extensión de tríceps en polea", "Brazo", 4, "12"),
                ej("Curl de bíceps en polea", "Brazo", 4, "12"),
                ej("Face pull", "Hombro", 3, "15")));

        maquinas.add(maquina(gymId, "Caminadora", "Cardio",
                ej("Caminata en pendiente", "Cardio", 1, "20-30 min"),
                ej("Trote continuo", "Cardio", 1, "20 min"),
                ej("Intervalos HIIT", "Cardio", 8, "40 s / 20 s")));

        maquinas.add(maquina(gymId, "Bicicleta fija", "Cardio",
                ej("Pedaleo continuo", "Cardio", 1, "25 min"),
                ej("Sprints en bici", "Cardio", 10, "30 s / 60 s")));

        maquinas.add(maquina(gymId, "Extensión de cuádriceps", "Máquinas",
                ej("Extensión a dos piernas", "Pierna", 4, "12-15"),
                ej("Extensión a una pierna", "Pierna", 3, "12 por lado")));

        maquinas.add(maquina(gymId, "Curl femoral", "Máquinas",
                ej("Curl femoral acostado", "Pierna", 4, "12"),
                ej("Curl femoral sentado", "Pierna", 3, "12-15")));

        return maquinas;
    }

    private static Machine maquina(String gymId, String nombre, String zona, Machine.Exercise... ejercicios) {
        Machine m = new Machine(gymId, nombre, zona);
        m.setEjercicios(new ArrayList<>(List.of(ejercicios)));
        return m;
    }

    private static Machine.Exercise ej(String nombre, String musculo, int series, String reps) {
        Machine.Exercise e = new Machine.Exercise(UUID.randomUUID().toString(), nombre, musculo, series, reps);
        e.setInstrucciones(INSTRUCCIONES.get(nombre));
        e.setImagenes(imagenes(nombre));
        return e;
    }

    // Cómo se hace cada ejercicio del catálogo: la app lo muestra al entrenar.
    public static final Map<String, String> INSTRUCCIONES = Map.ofEntries(
        Map.entry("Prensa 45°",
                "Pies al ancho de cadera en la plataforma. Baja hasta que las rodillas formen 90° sin despegar la espalda baja y empuja con los talones."),
        Map.entry("Prensa pies juntos",
                "Pies juntos al centro de la plataforma. Baja controlado y empuja sin bloquear las rodillas arriba."),
        Map.entry("Prensa a una pierna",
                "Un pie al centro de la plataforma. Baja hasta 90° con la rodilla alineada al pie y empuja con el talón."),
        Map.entry("Elevación de talones en prensa",
                "Apoya solo la punta de los pies en el borde. Baja los talones lo más posible y sube hasta quedar de puntas."),
        Map.entry("Jalón agarre ancho",
                "Manos más abiertas que los hombros. Pecho arriba, jala la barra hasta la parte alta del pecho llevando los codos hacia abajo."),
        Map.entry("Jalón agarre cerrado",
                "Agarre en V o manos juntas. Jala hacia el pecho con los codos pegados al cuerpo y sube controlado."),
        Map.entry("Jalón supino",
                "Palmas hacia ti, al ancho de hombros. Jala hasta la barbilla apretando la espalda y estira completo arriba."),
        Map.entry("Jalón a una mano",
                "Toma el maneral con una mano. Jala hacia el costado del pecho girando ligeramente el torso y regresa estirando."),
        Map.entry("Press banca plano",
                "Escápulas juntas y pies firmes. Baja la barra a la mitad del pecho con los codos a 45° y empuja hasta estirar los brazos."),
        Map.entry("Press banca inclinado",
                "Banco a 30-45°. Baja la barra a la parte alta del pecho y empuja en línea recta sin despegar los glúteos."),
        Map.entry("Press cerrado",
                "Manos al ancho de hombros. Baja la barra al pecho con los codos pegados al cuerpo y empuja con los tríceps."),
        Map.entry("Remo agarre neutro",
                "Pecho apoyado o espalda recta. Jala los manerales hacia el abdomen juntando las escápulas y regresa sin encorvarte."),
        Map.entry("Remo agarre ancho",
                "Manos abiertas. Jala hacia la parte baja del pecho con los codos hacia afuera y aprieta la espalda alta."),
        Map.entry("Remo a una mano",
                "Un maneral a la vez. Jala hacia la cadera sin girar el torso y estira completo al regresar."),
        Map.entry("Sentadilla trasera",
                "Barra sobre los trapecios, pies al ancho de hombros. Baja con el pecho arriba hasta que la cadera quede a la altura de las rodillas y sube empujando el piso."),
        Map.entry("Sentadilla frontal",
                "Barra al frente sobre los hombros con los codos altos. Baja con el torso vertical y sube sin dejar caer los codos."),
        Map.entry("Zancadas con barra",
                "Barra en la espalda. Da un paso largo, baja hasta que la rodilla de atrás casi toque el piso y regresa empujando con la pierna de adelante."),
        Map.entry("Press militar de pie",
                "Barra a la altura de la clavícula, abdomen firme. Empuja por encima de la cabeza sin arquear la espalda baja."),
        Map.entry("Cruce de poleas alto",
                "Poleas arriba, un paso al frente. Junta las manos frente a la cadera con los codos un poco doblados y abre controlado."),
        Map.entry("Cruce de poleas bajo",
                "Poleas abajo. Sube las manos hacia el pecho en un arco, juntándolas al frente, y baja controlado."),
        Map.entry("Extensión de tríceps en polea",
                "Codos pegados al cuerpo. Estira los brazos hacia abajo hasta bloquear y sube solo hasta 90°."),
        Map.entry("Curl de bíceps en polea",
                "Codos fijos a los costados. Sube la barra hacia los hombros apretando el bíceps y baja hasta estirar."),
        Map.entry("Face pull",
                "Cuerda a la altura de la cara. Jala hacia los ojos separando las manos y abriendo los codos hacia atrás."),
        Map.entry("Caminata en pendiente",
                "Inclinación de 8-12 % y paso firme. No te sostengas de los barandales."),
        Map.entry("Trote continuo",
                "Ritmo en el que puedas hablar con frases cortas. Pisa debajo de la cadera, sin dar zancadas largas."),
        Map.entry("Intervalos HIIT",
                "40 s a ritmo fuerte y 20 s caminando. Repite las rondas indicadas."),
        Map.entry("Pedaleo continuo",
                "Asiento a la altura de la cadera. Ritmo constante con resistencia media."),
        Map.entry("Sprints en bici",
                "Resistencia alta, pedalea a tope en los intervalos y recupera suave entre cada uno."),
        Map.entry("Extensión a dos piernas",
                "Rodillas alineadas con el eje de la máquina. Estira las piernas hasta arriba, aprieta y baja lento."),
        Map.entry("Extensión a una pierna",
                "Una pierna a la vez. Sube hasta estirar, aprieta el cuádriceps y baja en dos o tres segundos."),
        Map.entry("Curl femoral acostado",
                "Cadera pegada al banco. Lleva los talones hacia los glúteos y baja controlado sin despegar la cadera."),
        Map.entry("Curl femoral sentado",
                "Rodillas alineadas con el eje. Lleva los talones hacia abajo y atrás y regresa lento.")
    );
}
