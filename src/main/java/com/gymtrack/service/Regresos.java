package com.gymtrack.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// A dónde vuelve el comprador después de pagar fuera de la tienda (Stripe,
// PayPal): una página de esta web (relativa o con su dominio) o un enlace de la
// app (gymtrack://... en una build, exp://... en Expo Go). Nunca otro sitio web:
// el pago no debe servir para mandar a alguien a una página ajena que se haga
// pasar por la tienda.
@Component
public class Regresos {

    // Los enlaces de la app: gymtrack:// en una build y exp:// (o exps://) en Expo Go.
    static final Set<String> ESQUEMAS_APP = Set.of("gymtrack", "exp", "exps");

    private final String urlPublica;

    public Regresos(@Value("${app.url-publica:http://localhost:8080}") String urlPublica) {
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    public String validar(String url, String campo, String hostPropio) {
        String valor = url == null ? "" : url.trim();
        if (valor.isEmpty() || valor.length() > 2000) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Falta " + campo + ": la dirección a la que vuelve el comprador.");
        }
        URI uri;
        try {
            uri = URI.create(valor);
        } catch (IllegalArgumentException e) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es una dirección válida.");
        }
        String esquema = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (esquema == null) {
            if (valor.startsWith("//")) throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es una dirección válida.");
            return valor;
        }
        if (ESQUEMAS_APP.contains(esquema)) return valor;
        if (!esquema.equals("http") && !esquema.equals("https")) {
            // javascript:, data:, intent:... nada que no sea una página o la app.
            throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " no es una dirección válida.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String hostApp = URI.create(urlPublica).getHost();
        if (!host.equals(hostPropio == null ? "" : hostPropio.toLowerCase(Locale.ROOT)) && !host.equalsIgnoreCase(hostApp)) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, campo + " debe ser una página de GymTrack o un enlace de la app.");
        }
        return valor;
    }

    // Stripe y PayPal solo regresan a direcciones http(s) completas. Una ruta
    // relativa se completa con la URL pública, y un enlace de la app pasa por
    // regreso-app.html, que reenvía a la app con lo que agregue el proveedor.
    public String paraProveedor(String validada) {
        String esquema = esquema(validada);
        if (esquema == null) return urlPublica + (validada.startsWith("/") ? "" : "/") + validada;
        if (ESQUEMAS_APP.contains(esquema)) {
            return urlPublica + "/regreso-app.html?a=" + URLEncoder.encode(validada, StandardCharsets.UTF_8);
        }
        return validada;
    }

    public static String conParametros(String url, Map<String, String> params) {
        String fragmento = "";
        int almohadilla = url.indexOf('#');
        if (almohadilla >= 0) {
            fragmento = url.substring(almohadilla);
            url = url.substring(0, almohadilla);
        }
        StringBuilder sb = new StringBuilder(url);
        char separador = url.contains("?") ? '&' : '?';
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(separador).append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            separador = '&';
        }
        return sb + fragmento;
    }

    private static String esquema(String url) {
        try {
            String s = URI.create(url).getScheme();
            return s == null ? null : s.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
