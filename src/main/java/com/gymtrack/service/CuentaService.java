package com.gymtrack.service;

import com.gymtrack.model.TokenCuenta;
import com.gymtrack.model.User;
import com.gymtrack.repository.TokenCuentaRepository;
import com.gymtrack.repository.UserRepository;
import com.gymtrack.util.TokenUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// Reglas de los códigos y enlaces que llegan por correo: cuánto duran, cuántos
// intentos se permiten y cada cuánto se puede pedir otro.
@Service
public class CuentaService {

    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(15);
    static final Duration ESPERA_REENVIO = Duration.ofSeconds(60);
    static final int MAX_INTENTOS = 5;

    private final UserRepository userRepository;
    private final TokenCuentaRepository tokenRepository;
    private final CorreoService correoService;

    public CuentaService(UserRepository userRepository, TokenCuentaRepository tokenRepository, CorreoService correoService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.correoService = correoService;
    }

    // ─── Verificación del correo ───

    // Manda un código nuevo (el anterior deja de servir). Devuelve false si el
    // correo no salió: la cuenta ya existe y puede pedir otro desde verificar.html.
    public boolean enviarCodigoDeVerificacion(User user) {
        Emitido emitido = emitir(user, TokenCuenta.VERIFICAR_CORREO, VIGENCIA_CODIGO, null);
        return correoService.verificacion(user, emitido.codigo(),
                correoService.enlace("verificar.html?token=" + emitido.enlace()), VIGENCIA_CODIGO.toMinutes());
    }

    // Reenvío pedido desde verificar.html. Si el correo no existe o ya está
    // verificado no se hace nada, para no revelar qué cuentas hay.
    public void reenviarVerificacion(String email) {
        User user = userRepository.findByEmail(limpiar(email)).orElse(null);
        if (user == null || !user.correoPendienteDeVerificar()) return;
        if (!enviarCodigoDeVerificacion(user)) {
            throw new CuentaException(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos enviar el correo. Inténtalo en unos minutos.");
        }
    }

    public User verificarConCodigo(String email, String codigo) {
        User user = userRepository.findByEmail(limpiar(email))
                .orElseThrow(() -> new CuentaException(HttpStatus.BAD_REQUEST, "El código es incorrecto o ya venció."));
        if (!user.correoPendienteDeVerificar()) return user;
        comprobarCodigo(tokenVigente(user.getId(), TokenCuenta.VERIFICAR_CORREO), codigo);
        return marcarVerificado(user);
    }

    public User verificarConEnlace(String enlace) {
        TokenCuenta token = tokenPorEnlace(enlace, TokenCuenta.VERIFICAR_CORREO,
                "El enlace no es válido o ya venció. Pide un código nuevo.");
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new CuentaException(HttpStatus.BAD_REQUEST, "El enlace no es válido o ya venció. Pide un código nuevo."));
        return marcarVerificado(user);
    }

    private User marcarVerificado(User user) {
        boolean eraPendiente = user.correoPendienteDeVerificar();
        user.setEmailVerificado(true);
        userRepository.save(user);
        tokenRepository.deleteByUserIdAndTipo(user.getId(), TokenCuenta.VERIFICAR_CORREO);
        if (eraPendiente) correoService.bienvenida(user);
        return user;
    }

    // ─── Tokens ───

    private record Emitido(String codigo, String enlace) {}

    // Crea el código y el enlace de un trámite. Solo puede haber uno vigente por
    // tipo y hay que esperar un minuto entre uno y otro.
    private Emitido emitir(User user, String tipo, Duration vigencia, String dato) {
        tokenRepository.findFirstByUserIdAndTipoOrderByCreadoDesc(user.getId(), tipo).ifPresent(anterior -> {
            long espera = ESPERA_REENVIO.minus(Duration.between(anterior.getCreado(), Instant.now())).toSeconds();
            if (espera > 0) {
                throw new CuentaException(HttpStatus.TOO_MANY_REQUESTS, "Espera " + espera + " segundos para pedir otro correo.");
            }
        });
        tokenRepository.deleteByUserIdAndTipo(user.getId(), tipo);

        String codigo = TokenUtil.codigo();
        String enlace = TokenUtil.enlace();
        Instant ahora = Instant.now();
        TokenCuenta token = new TokenCuenta();
        token.setUserId(user.getId());
        token.setTipo(tipo);
        token.setCodigoHash(TokenUtil.sha256(codigo));
        token.setEnlaceHash(TokenUtil.sha256(enlace));
        token.setDato(dato);
        token.setCreado(ahora);
        token.setExpira(ahora.plus(vigencia));
        tokenRepository.save(token);
        return new Emitido(codigo, enlace);
    }

    private TokenCuenta tokenVigente(String userId, String tipo) {
        return tokenRepository.findFirstByUserIdAndTipoOrderByCreadoDesc(userId, tipo)
                .filter(TokenCuenta::vigente)
                .orElseThrow(() -> new CuentaException(HttpStatus.BAD_REQUEST, "El código ya venció. Pide uno nuevo."));
    }

    private TokenCuenta tokenPorEnlace(String enlace, String tipo, String error) {
        if (enlace == null || enlace.isBlank()) throw new CuentaException(HttpStatus.BAD_REQUEST, error);
        return tokenRepository.findByEnlaceHashAndTipo(TokenUtil.sha256(enlace.trim()), tipo)
                .filter(TokenCuenta::vigente)
                .orElseThrow(() -> new CuentaException(HttpStatus.BAD_REQUEST, error));
    }

    // Cada intento fallido cuenta: al quinto el código deja de servir.
    private void comprobarCodigo(TokenCuenta token, String codigo) {
        if (token.getIntentos() >= MAX_INTENTOS) {
            throw new CuentaException(HttpStatus.BAD_REQUEST, "Demasiados intentos. Pide un código nuevo.");
        }
        String escrito = codigo == null ? "" : codigo.replaceAll("\\s", "");
        if (TokenUtil.coincide(escrito, token.getCodigoHash())) return;

        token.setIntentos(token.getIntentos() + 1);
        tokenRepository.save(token);
        int restantes = MAX_INTENTOS - token.getIntentos();
        throw new CuentaException(HttpStatus.BAD_REQUEST, restantes > 0
                ? "El código es incorrecto. Te quedan " + restantes + (restantes == 1 ? " intento." : " intentos.")
                : "Demasiados intentos. Pide un código nuevo.");
    }

    private static String limpiar(String email) {
        return email == null ? "" : email.trim();
    }
}
