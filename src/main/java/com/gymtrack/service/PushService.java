package com.gymtrack.service;

import com.gymtrack.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Envía notificaciones push a través del servicio de Expo.
// No necesita llaves: Expo acepta el token del dispositivo directamente.
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient restClient = RestClient.create();

    // Un push nunca debe tumbar la operación que lo disparó: si Expo no responde,
    // el alta o el pago ya quedaron guardados y solo se pierde el aviso.
    public void enviar(User destinatario, String titulo, String cuerpo, Map<String, Object> data) {
        String token = destinatario.getPushToken();
        if (token == null || token.isBlank()) return;

        try {
            restClient.post()
                    .uri(EXPO_PUSH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "to", token,
                            "title", titulo,
                            "body", cuerpo,
                            "sound", "default",
                            "data", data == null ? Map.of() : data
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Push enviado a {}: {}", destinatario.getEmail(), titulo);
        } catch (Exception e) {
            log.warn("No se pudo enviar el push a {}: {}", destinatario.getEmail(), e.getMessage());
        }
    }

    public void enviarATodos(List<User> destinatarios, String titulo, String cuerpo, Map<String, Object> data) {
        destinatarios.forEach(u -> enviar(u, titulo, cuerpo, data));
    }
}
