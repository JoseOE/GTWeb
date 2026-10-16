package com.gymtrack.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Freno a quien intenta adivinar una contraseña: después de MAX_FALLOS
// contraseñas incorrectas para el mismo correo dentro de VENTANA, ese correo
// espera a que pase la ventana. Entrar bien borra la cuenta de fallos.
//
// Vive en memoria: Render corre una sola instancia, y si se reinicia el
// contador vuelve a cero (no es un problema: solo frena, no bloquea cuentas).
@Service
public class IntentosLoginService {

    static final int MAX_FALLOS = 10;
    static final Duration VENTANA = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();

    // Minutos que faltan para poder intentar otra vez; 0 si puede intentar ya.
    public long minutosDeEspera(String email) {
        Deque<Instant> lista = fallos.get(clave(email));
        if (lista == null) return 0;
        synchronized (lista) {
            limpiar(lista, Instant.now());
            if (lista.size() < MAX_FALLOS) return 0;
            Duration falta = Duration.between(Instant.now(), lista.peekFirst().plus(VENTANA));
            return Math.max(1, (falta.toSeconds() + 59) / 60);
        }
    }

    public void fallo(String email) {
        Deque<Instant> lista = fallos.computeIfAbsent(clave(email), k -> new ArrayDeque<>());
        synchronized (lista) {
            Instant ahora = Instant.now();
            limpiar(lista, ahora);
            lista.addLast(ahora);
        }
    }

    public void exito(String email) {
        fallos.remove(clave(email));
    }

    // Cada media hora se olvidan los correos sin fallos recientes.
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    void olvidarViejos() {
        Instant ahora = Instant.now();
        fallos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                limpiar(e.getValue(), ahora);
                return e.getValue().isEmpty();
            }
        });
    }

    private static void limpiar(Deque<Instant> lista, Instant ahora) {
        while (!lista.isEmpty() && lista.peekFirst().plus(VENTANA).isBefore(ahora)) lista.pollFirst();
    }

    private static String clave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
