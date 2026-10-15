/* ═══════════════════════════════════════
   Reseñas de productos (producto.html y el carrusel de tienda.html)
   ───────────────────────────────────────
   API (ResenaController):
     GET    /api/tienda/{gymId}/productos/{id}/resenas?pagina=0
     PUT    /api/tienda/{gymId}/productos/{id}/resenas/mia  {estrellas, comentario}
     DELETE /api/tienda/{gymId}/productos/{id}/resenas/mia
     GET    /api/tienda/{gymId}/resenas/destacadas
   Solo califica quien compró el producto; es una reseña por persona y
   producto, y volver a calificar la edita.
   Depende de Tienda (js/tienda.js).
═══════════════════════════════════════ */
const Resenas = (() => {
    const MAX_COMENTARIO = 500;
    const e = (s) => Tienda.escapar(s);

    // ★★★★☆ con medias estrellas. El número va aparte, en texto, para lectores de pantalla.
    function estrellas(valor, clase) {
        const v = Math.max(0, Math.min(5, Number(valor) || 0));
        let html = '';
        for (let i = 1; i <= 5; i++) {
            const icono = v >= i ? 'bxs-star' : (v >= i - 0.5 ? 'bxs-star-half' : 'bx-star');
            html += '<i class="bx ' + icono + '" aria-hidden="true"></i>';
        }
        return '<span class="estrellas ' + (clase || '') + '" role="img" aria-label="' + v.toFixed(1).replace('.0', '') + ' de 5 estrellas">' + html + '</span>';
    }

    // "4.6 · 12 reseñas" junto a las estrellas; nada si no hay calificación.
    function resumenCorto(calificacion, enlace) {
        if (!calificacion || !calificacion.total) return '';
        const texto = '<strong>' + calificacion.promedio.toFixed(1) + '</strong> · ' + calificacion.total + (calificacion.total === 1 ? ' reseña' : ' reseñas');
        return '<span class="calificacion-corta">' + estrellas(calificacion.promedio) + (enlace
            ? '<a href="' + enlace + '">' + texto + '</a>'
            : '<span>' + texto + '</span>') + '</span>';
    }

    function fechaCorta(iso) {
        return iso ? new Date(iso).toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
    }

    /* ─── Sección de reseñas del producto ─── */
    function montarSeccion(contenedor, gymId, productoId, alCambiar) {
        const base = '/api/tienda/' + encodeURIComponent(gymId) + '/productos/' + encodeURIComponent(productoId) + '/resenas';
        let pagina = 0;
        let datos = null;
        let editando = false;

        function cargar() {
            pagina = 0;
            return Tienda.api(base).then(d => { datos = d; pintar(); })
                .catch(err => Tienda.pintarEstado(contenedor, 'error', err.message, cargar));
        }

        function pintar() {
            const c = datos.calificacion;
            const mia = datos.mia;
            const filas = [5, 4, 3, 2, 1].map(n => {
                const cuantas = c.porEstrellas[n] || 0;
                const pct = c.total ? Math.round(cuantas / c.total * 100) : 0;
                return '<li><span>' + n + ' <i class="bx bxs-star" aria-hidden="true"></i></span>'
                    + '<span class="resenas-barra" aria-hidden="true"><span style="width:' + pct + '%"></span></span>'
                    + '<span class="text-body-secondary">' + cuantas + '</span>'
                    + '<span class="visually-hidden"> reseñas de ' + n + ' estrellas</span></li>';
            }).join('');

            contenedor.innerHTML = `
                <h2 class="seccion-titulo">Reseñas</h2>
                <div class="row g-4">
                    <div class="col-md-4">
                        <div class="resenas-resumen">
                            ${c.total ? `
                                <div class="resenas-promedio">${c.promedio.toFixed(1)}</div>
                                ${estrellas(c.promedio, 'grandes')}
                                <p class="small text-body-secondary mt-1 mb-3">${c.total} ${c.total === 1 ? 'reseña' : 'reseñas'} de compras verificadas</p>
                                <ul class="resenas-distribucion">${filas}</ul>`
                                : '<p class="mb-0 text-body-secondary">Todavía nadie califica este producto.</p>'}
                        </div>
                        <div class="mt-3" id="resena-mia">${pintarMia(mia)}</div>
                    </div>
                    <div class="col-md-8">
                        <ul class="resenas-lista" id="resenas-lista">${datos.resenas.map(pintarResena).join('')}</ul>
                        ${datos.hayMas ? '<button type="button" class="btn btn-outline-primary tocable px-4" id="resenas-mas">Ver más reseñas</button>' : ''}
                    </div>
                </div>`;
        }

        function pintarResena(r) {
            return `<li class="resena">
                <div class="d-flex flex-wrap align-items-center gap-2">
                    ${estrellas(r.estrellas)}
                    <strong>${e(r.autor)}</strong>
                    ${r.propia ? '<span class="badge rounded-pill text-bg-light border">Tu reseña</span>' : ''}
                </div>
                <div class="small text-body-secondary mt-1"><i class="bx bx-check-shield" aria-hidden="true"></i> Compra verificada · ${fechaCorta(r.fecha)}${r.editada ? ' · editada' : ''}</div>
                ${r.comentario ? '<p class="mt-2 mb-0">' + e(r.comentario) + '</p>' : ''}
            </li>`;
        }

        function pintarMia(mia) {
            if (Tienda.sesion.role === 'owner') return '';
            const r = mia.resena;
            if (!mia.puede && !r) {
                return '<p class="small text-body-secondary mb-0"><i class="bx bx-info-circle" aria-hidden="true"></i> Solo califican quienes compraron este producto.</p>';
            }
            if (r && !editando) {
                return `<div class="resena-propia">
                    <div class="small fw-semibold mb-1">Tu reseña${r.oculta ? ' <span class="text-warning-emphasis">· el gimnasio la ocultó</span>' : ''}</div>
                    ${estrellas(r.estrellas)}
                    ${r.comentario ? '<p class="small mt-2 mb-2">' + e(r.comentario) + '</p>' : ''}
                    <div class="d-flex gap-2 mt-2">
                        <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3 tocable" data-resena="editar">Editar</button>
                        <button type="button" class="btn btn-link btn-sm text-danger tocable" data-resena="borrar">Borrar</button>
                    </div>
                </div>`;
            }
            const actual = r ? r.estrellas : 0;
            return `<form class="resena-form" id="resena-form" novalidate>
                <fieldset>
                    <legend class="small fw-semibold mb-1">${r ? 'Edita tu reseña' : '¿Qué te pareció?'}</legend>
                    <div class="selector-estrellas" id="selector-estrellas">
                        ${[1, 2, 3, 4, 5].map(n => `<input type="radio" class="visually-hidden" name="estrellas" id="estrella-${n}" value="${n}" ${n === actual ? 'checked' : ''}>
                            <label for="estrella-${n}" data-n="${n}"><i class="bx bx-star" aria-hidden="true"></i><span class="visually-hidden">${n} ${n === 1 ? 'estrella' : 'estrellas'}</span></label>`).join('')}
                    </div>
                </fieldset>
                <label for="resena-comentario" class="small fw-semibold mt-2 mb-1">Comentario <span class="fw-normal text-body-secondary">(opcional)</span></label>
                <textarea class="form-control" id="resena-comentario" rows="3" maxlength="${MAX_COMENTARIO}" placeholder="¿Cómo te fue con el producto?">${r && r.comentario ? e(r.comentario) : ''}</textarea>
                <div class="d-flex justify-content-between small text-body-secondary mt-1"><span id="resena-error" class="text-danger" role="alert"></span><span id="resena-contador"></span></div>
                <div class="d-flex gap-2 mt-2">
                    <button type="submit" class="btn btn-primary rounded-pill px-4 tocable">${r ? 'Guardar' : 'Publicar reseña'}</button>
                    ${r ? '<button type="button" class="btn btn-link tocable" data-resena="cancelar">Cancelar</button>' : ''}
                </div>
            </form>`;
        }

        // Ilumina las estrellas hasta la elegida (o la que está bajo el puntero).
        function iluminar(n) {
            contenedor.querySelectorAll('#selector-estrellas label').forEach(l => {
                const activa = Number(l.dataset.n) <= n;
                l.classList.toggle('activa', activa);
                l.querySelector('i').className = 'bx ' + (activa ? 'bxs-star' : 'bx-star');
            });
        }
        function elegida() {
            const r = contenedor.querySelector('input[name="estrellas"]:checked');
            return r ? Number(r.value) : 0;
        }
        function contar() {
            const t = contenedor.querySelector('#resena-comentario');
            const c = contenedor.querySelector('#resena-contador');
            if (t && c) c.textContent = t.value.length + '/' + MAX_COMENTARIO;
        }

        contenedor.addEventListener('change', (ev) => { if (ev.target.name === 'estrellas') iluminar(elegida()); });
        contenedor.addEventListener('mouseover', (ev) => { const l = ev.target.closest('#selector-estrellas label'); if (l) iluminar(Number(l.dataset.n)); });
        contenedor.addEventListener('mouseout', (ev) => { if (ev.target.closest('#selector-estrellas')) iluminar(elegida()); });
        contenedor.addEventListener('input', (ev) => { if (ev.target.id === 'resena-comentario') contar(); });

        contenedor.addEventListener('click', (ev) => {
            const accion = ev.target.closest('[data-resena]');
            if (ev.target.closest('#resenas-mas')) {
                const boton = ev.target.closest('#resenas-mas');
                boton.disabled = true;
                Tienda.api(base + '?pagina=' + (pagina + 1)).then(d => {
                    pagina++;
                    datos.resenas = datos.resenas.concat(d.resenas);
                    datos.hayMas = d.hayMas;
                    pintar();
                }).catch(err => { boton.disabled = false; Tienda.avisar(err.message); });
                return;
            }
            if (!accion) return;
            if (accion.dataset.resena === 'editar') { editando = true; pintar(); iluminar(elegida()); contar(); contenedor.querySelector('#selector-estrellas input:checked, #selector-estrellas input')?.focus(); }
            if (accion.dataset.resena === 'cancelar') { editando = false; pintar(); }
            if (accion.dataset.resena === 'borrar') {
                if (!confirm('¿Borrar tu reseña?')) return;
                Tienda.api(base + '/mia', { method: 'DELETE' })
                    .then(() => { editando = false; Tienda.avisar('Borramos tu reseña.'); return cargar(); })
                    .then(() => alCambiar && alCambiar(datos.calificacion))
                    .catch(err => Tienda.avisar(err.message));
            }
        });

        contenedor.addEventListener('submit', (ev) => {
            if (ev.target.id !== 'resena-form') return;
            ev.preventDefault();
            const error = contenedor.querySelector('#resena-error');
            const n = elegida();
            if (!n) { error.textContent = 'Elige de 1 a 5 estrellas.'; contenedor.querySelector('#estrella-1').focus(); return; }
            const boton = ev.target.querySelector('[type="submit"]');
            boton.disabled = true;
            Tienda.api(base + '/mia', { method: 'PUT', body: JSON.stringify({ estrellas: n, comentario: contenedor.querySelector('#resena-comentario').value }) })
                .then(() => { editando = false; Tienda.avisar('¡Gracias! Publicamos tu reseña.'); return cargar(); })
                .then(() => alCambiar && alCambiar(datos.calificacion))
                .catch(err => { error.textContent = err.message; boton.disabled = false; });
        });

        return cargar().then(() => { iluminar(elegida()); contar(); });
    }

    /* ─── Carrusel de la tienda: productos con más de 3.8 estrellas ─── */
    function montarCarrusel(contenedor, gymId) {
        return Tienda.api('/api/tienda/' + encodeURIComponent(gymId) + '/resenas/destacadas')
            .then(lista => {
                if (!lista.length) { contenedor.hidden = true; return; }
                contenedor.hidden = false;
                contenedor.innerHTML = `
                    <div class="d-flex align-items-end justify-content-between gap-2 mb-2">
                        <div>
                            <h2 class="seccion-titulo mb-0" id="carrusel-titulo">Lo mejor calificado</h2>
                            <p class="small text-body-secondary mb-0">Más de 3.8 estrellas, según quienes los compraron</p>
                        </div>
                        <div class="carrusel-flechas d-none d-md-flex gap-1">
                            <button type="button" class="icono-redondo" data-carrusel="-1" aria-label="Anteriores"><i class="bx bx-chevron-left"></i></button>
                            <button type="button" class="icono-redondo" data-carrusel="1" aria-label="Siguientes"><i class="bx bx-chevron-right"></i></button>
                        </div>
                    </div>
                    <ul class="carrusel-resenas" id="carrusel-pista" aria-labelledby="carrusel-titulo">
                        ${lista.map(p => `<li class="carrusel-item">
                            <a class="carrusel-tarjeta" href="producto.html?id=${encodeURIComponent(p.id)}#resenas">
                                <div class="carrusel-foto">${p.imagen ? '<img src="' + e(p.imagen) + '" alt="" loading="lazy">' : '<i class="bx bx-package" aria-hidden="true"></i>'}</div>
                                <div class="carrusel-cuerpo">
                                    <span class="nombre">${e(p.nombre)}</span>
                                    ${resumenCorto(p.calificacion)}
                                    ${p.resena ? '<blockquote>“' + e(p.resena.comentario) + '”<cite>' + e(p.resena.autor) + '</cite></blockquote>' : ''}
                                    <span class="precio">${p.variantes.length > 1 ? 'desde ' : ''}${Tienda.dinero(p.precioDesde)}</span>
                                </div>
                            </a>
                        </li>`).join('')}
                    </ul>`;
                const pista = contenedor.querySelector('#carrusel-pista');
                contenedor.querySelectorAll('[data-carrusel]').forEach(b => b.addEventListener('click', () => {
                    pista.scrollBy({ left: Number(b.dataset.carrusel) * pista.clientWidth * 0.9, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
                }));
            })
            .catch(() => { contenedor.hidden = true; });
    }

    return { estrellas, resumenCorto, montarSeccion, montarCarrusel };
})();
