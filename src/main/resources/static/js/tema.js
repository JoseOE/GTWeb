/* ═══════════════════════════════════════
   Tema claro / oscuro de los paneles (bienvenido, admin y Mi cuenta)
   ───────────────────────────────────────
   Se carga en el <head>, antes de pintar, para que la página no destelle
   en claro antes de pasar a oscuro. Si la persona no ha elegido, se sigue
   el tema del sistema (y cambia solo si el sistema cambia). El botón con
   data-tema-alternar cambia entre claro y oscuro y lo recuerda en este
   navegador (localStorage "tema").
   Bootstrap 5.3 hace el resto con data-bs-theme; los colores propios del
   panel están en css/tema-oscuro.css.
═══════════════════════════════════════ */
(function () {
    const CLAVE = 'tema';
    const raiz = document.documentElement;
    const sistema = window.matchMedia('(prefers-color-scheme: dark)');

    function elegido() {
        try {
            const t = localStorage.getItem(CLAVE);
            return t === 'oscuro' || t === 'claro' ? t : null;
        } catch (e) { return null; }
    }

    function actual() {
        return elegido() || (sistema.matches ? 'oscuro' : 'claro');
    }

    function aplicar(tema) {
        raiz.setAttribute('data-bs-theme', tema === 'oscuro' ? 'dark' : 'light');
        raiz.style.colorScheme = tema === 'oscuro' ? 'dark' : 'light';
        document.querySelectorAll('[data-tema-alternar]').forEach(b => {
            const oscuro = tema === 'oscuro';
            b.setAttribute('aria-label', oscuro ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');
            b.setAttribute('title', oscuro ? 'Tema claro' : 'Tema oscuro');
            const icono = b.querySelector('.bx');
            if (icono) {
                icono.classList.toggle('bx-sun', oscuro);
                icono.classList.toggle('bx-moon', !oscuro);
            }
        });
        document.dispatchEvent(new CustomEvent('tema-cambio', { detail: { tema } }));
    }

    aplicar(actual());

    // Sin elección propia, el panel sigue al sistema.
    sistema.addEventListener('change', () => { if (!elegido()) aplicar(actual()); });

    // Cambiar de tema con una transición corta de colores (sin movimiento):
    // evita el salto brusco de brillo. Con movimiento reducido es inmediato.
    function cambiar() {
        const siguiente = actual() === 'oscuro' ? 'claro' : 'oscuro';
        try { localStorage.setItem(CLAVE, siguiente); } catch (e) { /* solo dura esta visita */ }
        const suave = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (suave) raiz.classList.add('tema-cambiando');
        aplicar(siguiente);
        if (suave) setTimeout(() => raiz.classList.remove('tema-cambiando'), 260);
    }

    // Un solo escucha para toda la página: también sirve para los botones que
    // se agregan después (la barra de la tienda se arma con JavaScript).
    document.addEventListener('click', e => {
        if (e.target.closest('[data-tema-alternar]')) cambiar();
    });
    document.addEventListener('DOMContentLoaded', () => aplicar(actual()));

    // refrescar(): pone el ícono correcto a botones agregados después.
    window.Tema = { actual, cambiar, refrescar: () => aplicar(actual()) };
})();
