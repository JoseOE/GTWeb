package com.gymtrack.service;

import com.gymtrack.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

// Lo que el comprador acepta antes de pagar, como pide la ley de comercio
// electrónico: los términos y condiciones de compra vigentes y, si lleva
// suplementos con advertencia sanitaria, esa advertencia. El checkout lo manda
// después de "¿Confirmas tu compra?" y el pedido guarda qué se aceptó y cuándo.
//
// En la web se exige siempre. La app todavía no lo manda: mientras se
// actualiza, en el canal "app" solo se exige con EXIGIR_CONSENTIMIENTO_APP=true
// (igual que EXIGIR_TOKEN); sin él, el pedido queda marcado como "no enviado".
@Service
public class ConsentimientoService {

    private static final Logger log = LoggerFactory.getLogger(ConsentimientoService.class);

    // Versión vigente de terminos.html (js/legal.js: Legal.VERSION). Si el texto
    // cambia, se sube en los dos lugares y quien tenga la versión vieja abierta
    // tiene que volver a aceptarla.
    public static final String VERSION_TERMINOS = "1";

    private final boolean exigirEnApp;

    public ConsentimientoService(@Value("${tienda.exigir-consentimiento-app:false}") boolean exigirEnApp) {
        this.exigirEnApp = exigirEnApp;
    }

    // Lo que manda el checkout.
    public static class Solicitud {
        private String terminosVersion;
        private Boolean aceptaTerminos;
        private Boolean aceptaAdvertencia;

        public String getTerminosVersion() { return terminosVersion; }
        public void setTerminosVersion(String terminosVersion) { this.terminosVersion = terminosVersion; }

        public Boolean getAceptaTerminos() { return aceptaTerminos; }
        public void setAceptaTerminos(Boolean aceptaTerminos) { this.aceptaTerminos = aceptaTerminos; }

        public Boolean getAceptaAdvertencia() { return aceptaAdvertencia; }
        public void setAceptaAdvertencia(Boolean aceptaAdvertencia) { this.aceptaAdvertencia = aceptaAdvertencia; }
    }

    // Revisa el consentimiento antes de cobrar y devuelve lo que guarda el
    // pedido. conAdvertencia: los productos del carrito que piden aceptar la
    // advertencia sanitaria. Responde 400 si falta algo y 409 si aceptó una
    // versión vieja de los términos.
    public Pedido.Consentimiento revisar(String canal, Solicitud s, List<String> conAdvertencia) {
        boolean exigir = Pedido.CANAL_WEB.equals(canal) || exigirEnApp;
        boolean mando = s != null && Boolean.TRUE.equals(s.getAceptaTerminos());
        if (!mando) {
            if (exigir) {
                throw new TiendaException(HttpStatus.BAD_REQUEST, "Acepta los términos y condiciones de compra para continuar.");
            }
            log.warn("Compra desde la app sin consentimiento (EXIGIR_CONSENTIMIENTO_APP=false): el pedido queda marcado como no enviado.");
            Pedido.Consentimiento c = new Pedido.Consentimiento();
            c.setEnviado(false);
            c.setProductosConAdvertencia(conAdvertencia);
            c.setRegistradoEn(Instant.now());
            return c;
        }
        if (!VERSION_TERMINOS.equals(s.getTerminosVersion())) {
            throw new TiendaException(HttpStatus.CONFLICT, "Los términos de compra cambiaron. Revísalos y vuelve a aceptarlos.");
        }
        boolean aceptaAdvertencia = Boolean.TRUE.equals(s.getAceptaAdvertencia());
        if (!conAdvertencia.isEmpty() && !aceptaAdvertencia && exigir) {
            throw new TiendaException(HttpStatus.BAD_REQUEST, "Lee y acepta la advertencia sanitaria de: " + String.join(", ", conAdvertencia) + ".");
        }
        Pedido.Consentimiento c = new Pedido.Consentimiento();
        c.setEnviado(true);
        c.setTerminosVersion(VERSION_TERMINOS);
        c.setAceptaTerminos(true);
        c.setAceptaAdvertencia(!conAdvertencia.isEmpty() && aceptaAdvertencia);
        c.setProductosConAdvertencia(conAdvertencia);
        c.setRegistradoEn(Instant.now());
        return c;
    }
}
