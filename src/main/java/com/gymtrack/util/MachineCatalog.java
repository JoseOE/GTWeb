package com.gymtrack.util;

import com.gymtrack.model.Machine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Catálogo base que un gimnasio puede importar de un clic para no empezar de cero.
// Es solo un punto de partida: al importarlo se crean copias propias del gimnasio,
// que luego puede editar, borrar o completar con sus ejercicios reales.
public final class MachineCatalog {

    private MachineCatalog() {}

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
        return new Machine.Exercise(UUID.randomUUID().toString(), nombre, musculo, series, reps);
    }
}
