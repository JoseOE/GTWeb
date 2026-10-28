/* ═══════════════════════════════════════
   Opiniones en la página de inicio
   ───────────────────────────────────────
   Carrusel con las opiniones de 4★ o más de dueños y miembros
   (GET /api/opiniones/destacadas, pública). Cada tarjeta lleva la foto del
   gimnasio de quien opina. Si no hay ninguna, la sección no se muestra.

   Avanza solo cada 6 segundos (no si el usuario prefiere menos movimiento,
   ni mientras el puntero o el foco están dentro), y se puede mover con las
   flechas, deslizando o con el teclado.
═══════════════════════════════════════ */
(function () {
    const seccion = document.getElementById('opiniones');
    if (!seccion) return;
    const pista = document.getElementById('opiniones-pista');
    const anterior = document.getElementById('opiniones-anterior');
    const siguiente = document.getElementById('opiniones-siguiente');
    const puntos = document.getElementById('opiniones-puntos');
    const quieto = matchMedia('(prefers-reduced-motion: reduce)').matches;
    // Si el gimnasio no tiene foto: una de gimnasio genérica.
    const FOTO_GENERICA = 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=800&q=70';

    function escapar(s) {
        const d = document.createElement('div');
        d.textContent = s == null ? '' : String(s);
        return d.innerHTML;
    }

    function estrellas(n) {
        let h = '';
        for (let i = 1; i <= 5; i++) h += '<i class="bx ' + (i <= n ? 'bxs-star' : 'bx-star') + '" aria-hidden="true"></i>';
        return '<div class="opinion-estrellas" role="img" aria-label="' + n + ' de 5 estrellas">' + h + '</div>';
    }

    function tarjeta(o, i, total) {
        const g = o.gimnasio || {};
        const foto = g.foto || FOTO_GENERICA;
        return '<article class="opinion-tarjeta" role="group" aria-roledescription="opinión" aria-label="' + (i + 1) + ' de ' + total + '">'
            + '<figure class="opinion-foto"><img src="' + escapar(foto) + '" alt="' + escapar(g.nombre) + '" loading="lazy" width="800" height="500">'
            + '<figcaption>' + (g.logo ? '<img class="opinion-logo" src="' + escapar(g.logo) + '" alt="">' : '') + '<span>' + escapar(g.nombre) + '</span></figcaption></figure>'
            + '<div class="opinion-cuerpo">' + estrellas(o.estrellas)
            + '<blockquote>“' + escapar(o.texto) + '”</blockquote>'
            + '<p class="opinion-autor"><strong>' + escapar(o.autor) + '</strong><span>' + escapar(o.rol) + ' · ' + escapar(g.nombre) + '</span></p>'
            + '</div></article>';
    }

    function porVista() {
        const t = pista.querySelector('.opinion-tarjeta');
        return t ? Math.max(1, Math.round(pista.clientWidth / (t.getBoundingClientRect().width + 24))) : 1;
    }

    function paginaActual() {
        const t = pista.querySelector('.opinion-tarjeta');
        if (!t) return 0;
        return Math.round(pista.scrollLeft / ((t.getBoundingClientRect().width + 24) * porVista()));
    }

    function paginas() {
        return Math.max(1, Math.ceil(pista.children.length / porVista()));
    }

    function ir(pagina) {
        const total = paginas();
        const destino = ((pagina % total) + total) % total;
        const t = pista.querySelector('.opinion-tarjeta');
        if (!t) return;
        pista.scrollTo({ left: destino * (t.getBoundingClientRect().width + 24) * porVista(), behavior: quieto ? 'auto' : 'smooth' });
    }

    function pintarPuntos() {
        const total = paginas();
        const actual = paginaActual();
        puntos.innerHTML = total > 1 ? Array.from({ length: total }, (_, i) =>
            '<button type="button" class="' + (i === actual ? 'activo' : '') + '" aria-label="Ir a la página ' + (i + 1) + ' de opiniones"' + (i === actual ? ' aria-current="true"' : '') + '></button>').join('') : '';
        anterior.hidden = siguiente.hidden = total <= 1;
    }

    let reloj = null;
    function arrancar() {
        if (quieto || reloj) return;
        reloj = setInterval(() => ir(paginaActual() + 1), 6000);
    }
    function detener() {
        clearInterval(reloj);
        reloj = null;
    }

    fetch('/api/opiniones/destacadas?limite=12')
        .then(r => r.ok ? r.json() : [])
        .then(lista => {
            if (!Array.isArray(lista) || !lista.length) return;
            pista.innerHTML = lista.map((o, i) => tarjeta(o, i, lista.length)).join('');
            seccion.hidden = false;
            pintarPuntos();
            arrancar();
        })
        .catch(() => { /* sin opiniones, la sección se queda oculta */ });

    anterior.addEventListener('click', () => ir(paginaActual() - 1));
    siguiente.addEventListener('click', () => ir(paginaActual() + 1));
    puntos.addEventListener('click', e => {
        const b = e.target.closest('button');
        if (b) ir([...puntos.children].indexOf(b));
    });
    let espera = null;
    pista.addEventListener('scroll', () => { clearTimeout(espera); espera = setTimeout(pintarPuntos, 120); });
    pista.addEventListener('keydown', e => {
        if (e.key === 'ArrowRight') { e.preventDefault(); ir(paginaActual() + 1); }
        if (e.key === 'ArrowLeft') { e.preventDefault(); ir(paginaActual() - 1); }
    });
    seccion.addEventListener('mouseenter', detener);
    seccion.addEventListener('mouseleave', arrancar);
    seccion.addEventListener('focusin', detener);
    seccion.addEventListener('focusout', arrancar);
    addEventListener('resize', () => { clearTimeout(espera); espera = setTimeout(pintarPuntos, 150); });
})();
