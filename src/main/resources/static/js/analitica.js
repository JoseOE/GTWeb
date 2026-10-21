/* ═══════════════════════════════════════
   Analítica (Google Analytics 4) con aviso de cookies
   ───────────────────────────────────────
   Google Analytics solo se carga si la persona elige "Aceptar" en el aviso:
   con "Rechazar" no se descarga nada de Google ni se guardan cookies de
   medición. La elección vive en este navegador (localStorage) y se cambia
   desde el Aviso de privacidad ("Cambiar mis preferencias de cookies").

   Las páginas de la tienda mandan eventos de comercio electrónico con
   Analitica.evento(nombre, datos): view_item, add_to_cart, begin_checkout
   y purchase. Nunca se mandan nombres, correos ni datos de pago.

   Se carga en el <head> de las páginas públicas y de la tienda (no en el
   panel del dueño ni en el simulador de PayPal).
═══════════════════════════════════════ */
(function () {
    const ID = 'G-G24WE0GZ4K';
    const CLAVE = 'gymtrack.cookies';
    const ACEPTADAS = 'aceptadas';
    const RECHAZADAS = 'rechazadas';

    window.dataLayer = window.dataLayer || [];
    function gtag() { window.dataLayer.push(arguments); }
    let cargado = false;

    function leer() {
        try { return localStorage.getItem(CLAVE); } catch (e) { return null; }
    }

    function escribir(valor) {
        try { localStorage.setItem(CLAVE, valor); } catch (e) { /* sin almacenamiento: se vuelve a preguntar */ }
    }

    function cargar() {
        if (cargado) return;
        cargado = true;
        window['ga-disable-' + ID] = false;
        gtag('js', new Date());
        gtag('config', ID, { anonymize_ip: true });
        const s = document.createElement('script');
        s.async = true;
        s.src = 'https://www.googletagmanager.com/gtag/js?id=' + ID;
        document.head.appendChild(s);
    }

    // Al rechazar después de haber aceptado: se apaga la medición y se borran sus cookies.
    function apagar() {
        window['ga-disable-' + ID] = true;
        const dominio = location.hostname;
        document.cookie.split(';').map(c => c.split('=')[0].trim()).filter(n => /^_ga/.test(n)).forEach(n => {
            [dominio, '.' + dominio, ''].forEach(d => {
                document.cookie = n + '=; Max-Age=0; path=/' + (d ? '; domain=' + d : '');
            });
        });
    }

    function elegir(valor) {
        escribir(valor);
        quitarAviso();
        if (valor === ACEPTADAS) cargar();
        else apagar();
    }

    function quitarAviso() {
        const aviso = document.getElementById('aviso-cookies');
        if (aviso) aviso.remove();
    }

    function mostrarAviso() {
        if (document.getElementById('aviso-cookies')) return;
        const aviso = document.createElement('div');
        aviso.id = 'aviso-cookies';
        aviso.setAttribute('role', 'dialog');
        aviso.setAttribute('aria-live', 'polite');
        aviso.setAttribute('aria-label', 'Aviso de cookies');
        aviso.style.cssText = 'position:fixed;left:16px;right:16px;bottom:16px;z-index:2000;max-width:560px;margin:0 auto;'
            + 'background:#20221f;color:#f5f3ee;border-radius:14px;padding:16px 18px;box-shadow:0 10px 30px rgba(0,0,0,.25);'
            + 'font:14px/1.45 system-ui,-apple-system,"Segoe UI",sans-serif';
        aviso.innerHTML = '<p style="margin:0 0 12px">Usamos Google Analytics para saber cómo se usa la página y mejorarla. '
            + 'Solo se activa si aceptas. <a href="privacidad.html#almacenamiento" style="color:#f7a37f">Aviso de privacidad</a></p>'
            + '<div style="display:flex;gap:8px;justify-content:flex-end;flex-wrap:wrap">'
            + '<button type="button" data-cookies="rechazar" style="border:1px solid #8a8c82;background:transparent;color:#f5f3ee;border-radius:999px;padding:8px 16px;font:inherit;cursor:pointer">Rechazar</button>'
            + '<button type="button" data-cookies="aceptar" style="border:0;background:#f45124;color:#fff;border-radius:999px;padding:8px 16px;font:inherit;font-weight:600;cursor:pointer">Aceptar</button>'
            + '</div>';
        aviso.addEventListener('click', e => {
            const b = e.target.closest('[data-cookies]');
            if (b) elegir(b.dataset.cookies === 'aceptar' ? ACEPTADAS : RECHAZADAS);
        });
        document.body.appendChild(aviso);
    }

    window.Analitica = {
        // Manda un evento solo si la persona aceptó la medición.
        evento(nombre, datos) {
            if (leer() !== ACEPTADAS) return;
            cargar();
            gtag('event', nombre, datos || {});
        },
        // Vuelve a mostrar el aviso para cambiar la elección.
        preferencias() {
            mostrarAviso();
        }
    };

    const eleccion = leer();
    if (eleccion === ACEPTADAS) cargar();
    else if (eleccion !== RECHAZADAS) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mostrarAviso);
        else mostrarAviso();
    }
})();
