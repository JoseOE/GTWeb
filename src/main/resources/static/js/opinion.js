/* ═══════════════════════════════════════
   Tu opinión sobre GymTrack
   ───────────────────────────────────────
   Formulario de estrellas y comentario que usan el panel del dueño
   (bienvenido.html) y Mi cuenta (cuenta.html). Una opinión por cuenta:
   GET, PUT y DELETE /api/opiniones/mia. Con 4★ o más sale en el
   carrusel de la página de inicio con la foto del gimnasio.

   Uso: Opinion.montar(document.getElementById('...'));
   La sesión la pone js/sesion.js en cada fetch.
═══════════════════════════════════════ */
window.Opinion = (function () {
    const MIN = 10, MAX = 300;
    const ETIQUETAS = ['', 'Mala', 'Regular', 'Buena', 'Muy buena', 'Excelente'];

    function api(metodo, cuerpo) {
        return fetch('/api/opiniones/mia', {
            method: metodo,
            headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '' },
            body: cuerpo ? JSON.stringify(cuerpo) : undefined
        }).catch(() => { throw new Error('Error de conexión con el servidor.'); })
            .then(r => r.json().catch(() => ({})).then(b => {
                if (!r.ok) throw new Error(b.error || 'No se pudo guardar tu opinión.');
                return b;
            }));
    }

    function montar(el) {
        if (!el) return;
        const id = 'op' + Math.random().toString(36).slice(2, 7);
        el.innerHTML = `
            <fieldset class="mb-3">
                <legend class="form-label fw-semibold small mb-1">Tu calificación</legend>
                <div class="d-flex align-items-center gap-1 flex-wrap" role="radiogroup" aria-label="Calificación de 1 a 5 estrellas">
                    ${[1, 2, 3, 4, 5].map(n => `
                        <input type="radio" class="btn-check" name="${id}-estrellas" id="${id}-e${n}" value="${n}">
                        <label class="btn btn-link p-0 fs-3 lh-1 text-decoration-none opinion-estrella" style="color:#F5A524" for="${id}-e${n}" data-n="${n}" title="${ETIQUETAS[n]}">
                            <i class="bx bx-star" aria-hidden="true"></i><span class="visually-hidden">${n} ${n === 1 ? 'estrella' : 'estrellas'}</span>
                        </label>`).join('')}
                    <span class="small text-body-secondary ms-2" id="${id}-etiqueta"></span>
                </div>
            </fieldset>
            <div class="mb-2">
                <label for="${id}-texto" class="form-label fw-semibold small">Tu opinión</label>
                <textarea id="${id}-texto" class="form-control" rows="3" maxlength="${MAX}" placeholder="¿Qué te ha gustado de GymTrack? ¿Qué mejorarías?"></textarea>
                <div class="d-flex justify-content-between mt-1">
                    <small class="text-body-secondary">Con 4 estrellas o más aparece en la página de inicio, con tu nombre corto y la foto de tu gimnasio.</small>
                    <small class="text-body-secondary text-nowrap ms-2" id="${id}-cuenta">0/${MAX}</small>
                </div>
            </div>
            <div class="alert d-none small py-2" role="status" id="${id}-aviso"></div>
            <div class="d-flex flex-wrap gap-2">
                <button type="button" class="btn btn-primary rounded-pill px-4" id="${id}-guardar"><i class="bx bx-send"></i> Publicar opinión</button>
                <button type="button" class="btn btn-outline-danger rounded-pill px-3 d-none" id="${id}-borrar">Borrar</button>
            </div>`;

        const $ = s => el.querySelector('#' + id + '-' + s);
        const texto = $('texto'), aviso = $('aviso'), guardar = $('guardar'), borrar = $('borrar');
        let estrellas = 0;

        function pintarEstrellas(n) {
            el.querySelectorAll('.opinion-estrella').forEach(l => {
                l.querySelector('i').className = 'bx ' + (Number(l.dataset.n) <= n ? 'bxs-star' : 'bx-star');
            });
            $('etiqueta').textContent = ETIQUETAS[n] || '';
        }
        function avisar(msg, tipo) {
            aviso.className = 'alert small py-2 alert-' + tipo + (msg ? '' : ' d-none');
            aviso.textContent = msg || '';
        }
        function contar() {
            $('cuenta').textContent = texto.value.trim().length + '/' + MAX;
        }
        function cargar(o) {
            estrellas = o && o.estrellas ? o.estrellas : 0;
            const radio = estrellas && el.querySelector('#' + id + '-e' + estrellas);
            el.querySelectorAll('input[type=radio]').forEach(r => { r.checked = r === radio; });
            texto.value = o && o.texto ? o.texto : '';
            pintarEstrellas(estrellas);
            contar();
            borrar.classList.toggle('d-none', !(o && o.texto));
            guardar.innerHTML = o && o.texto ? '<i class="bx bx-save"></i> Actualizar opinión' : '<i class="bx bx-send"></i> Publicar opinión';
        }

        el.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener('change', () => {
            estrellas = Number(r.value);
            pintarEstrellas(estrellas);
        }));
        el.querySelectorAll('.opinion-estrella').forEach(l => {
            l.addEventListener('mouseenter', () => pintarEstrellas(Number(l.dataset.n)));
            l.addEventListener('mouseleave', () => pintarEstrellas(estrellas));
        });
        texto.addEventListener('input', contar);

        guardar.addEventListener('click', () => {
            const t = texto.value.trim();
            if (!estrellas) return avisar('Elige de 1 a 5 estrellas.', 'warning');
            if (t.length < MIN) return avisar('Escribe al menos ' + MIN + ' caracteres.', 'warning');
            guardar.disabled = true;
            api('PUT', { estrellas, texto: t })
                .then(o => {
                    cargar(o);
                    avisar(o.estrellas >= 4 ? '¡Gracias! Tu opinión ya aparece en la página de inicio.' : 'Gracias por tu opinión. Nos ayuda a mejorar GymTrack.', 'success');
                })
                .catch(err => avisar(err.message, 'danger'))
                .finally(() => { guardar.disabled = false; });
        });
        borrar.addEventListener('click', () => {
            if (!confirm('¿Borrar tu opinión? Dejará de aparecer en la página de inicio.')) return;
            api('DELETE').then(() => { cargar(null); avisar('Tu opinión se borró.', 'secondary'); })
                .catch(err => avisar(err.message, 'danger'));
        });

        api('GET').then(cargar).catch(() => cargar(null));
    }

    return { montar };
})();
