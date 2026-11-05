/* ═══════════════════════════════════════
   Anuncios del gimnasio (pestaña Anuncios del panel)
   ───────────────────────────────────────
   El dueño publica avisos para sus miembros ("Cerramos el lunes",
   "2×1 en proteína"). La app los muestra en Inicio mientras están vigentes.
   Con "Avisar a mis miembros" sale además una notificación, una sola vez.
   API: /api/gyms/{gymId}/anuncios (ver AnuncioController).
═══════════════════════════════════════ */
(function () {
    const panel = document.getElementById('panel-anuncios');
    if (!panel) return;

    const lista = document.getElementById('anuncios-lista');
    const nuevoBtn = document.getElementById('anuncio-nuevo-btn');
    const modalEl = document.getElementById('anuncioModal');
    const modal = new bootstrap.Modal(modalEl);
    const form = document.getElementById('anuncio-form');
    const alerta = document.getElementById('anuncio-alerta');
    const guardarBtn = document.getElementById('anuncio-guardar-btn');
    const campos = {
        titulo: document.getElementById('anuncio-titulo'),
        texto: document.getElementById('anuncio-texto'),
        tipo: document.getElementById('anuncio-tipo'),
        desde: document.getElementById('anuncio-desde'),
        hasta: document.getElementById('anuncio-hasta'),
        avisar: document.getElementById('anuncio-avisar')
    };
    const contador = document.getElementById('anuncio-texto-contador');
    const avisarAyuda = document.getElementById('anuncio-avisar-ayuda');
    const titulo = document.getElementById('anuncioModalLabel');

    const TIPOS = {
        aviso: { texto: 'Aviso', icono: 'bx-info-circle', clase: 'text-info' },
        promocion: { texto: 'Promoción', icono: 'bx-purchase-tag-alt', clase: 'text-success' },
        cierre: { texto: 'Cierre', icono: 'bx-calendar-x', clase: 'text-warning' }
    };
    const ESTADOS = {
        vigente: { texto: 'Vigente', clase: 'bg-success-subtle text-success-emphasis' },
        programado: { texto: 'Programado', clase: 'bg-info-subtle text-info-emphasis' },
        pausado: { texto: 'Pausado', clase: 'bg-warning-subtle text-warning-emphasis' },
        vencido: { texto: 'Vencido', clase: 'bg-secondary-subtle text-secondary-emphasis' }
    };
    const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

    let anuncios = [];
    let editando = null;

    const gymId = () => localStorage.getItem('gymId');
    const escapar = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    // Hoy en México, como AAAA-MM-DD (lo mismo que usa el servidor).
    function hoy() {
        return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Mexico_City' }).format(new Date());
    }
    function fechaCorta(iso) {
        if (!iso) return '';
        const [, m, d] = iso.split('-').map(Number);
        return d + ' ' + MESES[m - 1];
    }
    function vigencia(a) {
        if (a.hasta) return a.desde === a.hasta ? 'Solo el ' + fechaCorta(a.desde) : 'Del ' + fechaCorta(a.desde) + ' al ' + fechaCorta(a.hasta);
        return 'Desde el ' + fechaCorta(a.desde) + ', sin fecha de fin';
    }

    // sesion.js agrega el token; X-User-Id se manda como en el resto del panel.
    function api(ruta, opciones) {
        opciones = opciones || {};
        const headers = { 'X-User-Id': localStorage.getItem('userId') || '' };
        if (opciones.body) headers['Content-Type'] = 'application/json';
        return fetch(ruta, { ...opciones, headers })
            .catch(() => { throw new Error('Error de conexión con el servidor.'); })
            .then(res => res.json().catch(() => ({})).then(body => {
                if (!res.ok) throw new Error(body.error || 'No se pudo completar la operación.');
                return body;
            }));
    }

    function pintar() {
        if (!gymId()) {
            lista.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-store-alt fs-1 d-block mb-2 opacity-50" aria-hidden="true"></i>Configura primero tu gimnasio para publicar anuncios.<div class="mt-3"><button type="button" class="btn btn-primary btn-sm rounded-pill px-3" data-goto="gimnasio">Configurar mi gimnasio</button></div></div>';
            nuevoBtn.disabled = true;
            return;
        }
        nuevoBtn.disabled = false;
        if (!anuncios.length) {
            lista.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-broadcast fs-1 d-block mb-2 opacity-50" aria-hidden="true"></i>Todavía no publicas anuncios. Úsalos para avisar cierres, cambios de horario o promociones.</div>';
            return;
        }
        lista.innerHTML = '<div class="row g-3">' + anuncios.map(a => {
            const tipo = TIPOS[a.tipo] || TIPOS.aviso;
            const estado = ESTADOS[a.estado] || ESTADOS.vigente;
            return '<div class="col-md-6"><div class="dash-empty text-start h-100 p-4 d-flex flex-column gap-2">'
                + '<div class="d-flex align-items-center gap-2 flex-wrap">'
                + '<span class="badge rounded-pill ' + estado.clase + '">' + estado.texto + '</span>'
                + '<span class="small fw-semibold ' + tipo.clase + '"><i class="bx ' + tipo.icono + '" aria-hidden="true"></i> ' + tipo.texto + '</span>'
                + (a.avisadoEn ? '<span class="small text-body-secondary"><i class="bx bx-bell" aria-hidden="true"></i> Se avisó a los miembros</span>' : '')
                + '</div>'
                + '<h4 class="fw-bold fs-6 mb-0">' + escapar(a.titulo) + '</h4>'
                + '<p class="mb-0 small" style="white-space:pre-line">' + escapar(a.texto) + '</p>'
                + '<small class="text-body-secondary">' + vigencia(a) + '</small>'
                + '<div class="d-flex gap-2 mt-auto pt-2 flex-wrap">'
                + '<button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-editar="' + a.id + '"><i class="bx bx-edit"></i> Editar</button>'
                + '<button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-pausar="' + a.id + '" data-pausado="' + (a.pausado ? '1' : '0') + '">'
                + (a.pausado ? '<i class="bx bx-play"></i> Reanudar' : '<i class="bx bx-pause"></i> Pausar') + '</button>'
                + '<button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-borrar="' + a.id + '"><i class="bx bx-trash"></i> Borrar</button>'
                + '</div></div></div>';
        }).join('') + '</div>';
    }

    function cargar() {
        if (!gymId()) return pintar();
        api('/api/gyms/' + gymId() + '/anuncios')
            .then(datos => { anuncios = datos; pintar(); })
            .catch(err => { lista.innerHTML = '<div class="alert alert-danger small">' + escapar(err.message) + '</div>'; });
    }

    // "Avisar" solo tiene sentido con un anuncio que ya se ve y que no se avisó antes.
    function actualizarAvisar() {
        const yaAvisado = editando && editando.avisadoEn;
        const futuro = campos.desde.value && campos.desde.value > hoy();
        campos.avisar.disabled = !!yaAvisado || futuro;
        if (campos.avisar.disabled) campos.avisar.checked = false;
        avisarAyuda.textContent = yaAvisado
            ? 'Ya se avisó a los miembros de este anuncio.'
            : futuro
                ? 'Empieza después de hoy: podrás avisar cuando ya esté vigente.'
                : 'Les llega una notificación al teléfono (solo a quienes tienen la app y su membresía activa).';
    }

    function abrir(a) {
        editando = a || null;
        titulo.textContent = a ? 'Editar anuncio' : 'Nuevo anuncio';
        campos.titulo.value = a ? a.titulo : '';
        campos.texto.value = a ? a.texto : '';
        campos.tipo.value = a ? a.tipo : 'aviso';
        campos.desde.value = a ? a.desde : hoy();
        campos.hasta.value = a && a.hasta ? a.hasta : '';
        campos.avisar.checked = false;
        alerta.classList.add('d-none');
        contador.textContent = campos.texto.value.length + '/400';
        actualizarAvisar();
        modal.show();
    }

    nuevoBtn.addEventListener('click', () => abrir(null));
    campos.desde.addEventListener('input', actualizarAvisar);
    campos.texto.addEventListener('input', () => { contador.textContent = campos.texto.value.length + '/400'; });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        alerta.classList.add('d-none');
        guardarBtn.disabled = true;
        const cuerpo = {
            titulo: campos.titulo.value.trim(),
            texto: campos.texto.value.trim(),
            tipo: campos.tipo.value,
            desde: campos.desde.value,
            hasta: campos.hasta.value || null,
            avisar: campos.avisar.checked
        };
        const ruta = '/api/gyms/' + gymId() + '/anuncios' + (editando ? '/' + editando.id : '');
        api(ruta, { method: editando ? 'PUT' : 'POST', body: JSON.stringify(cuerpo) })
            .then(r => {
                modal.hide();
                cargar();
                if (cuerpo.avisar) {
                    const aviso = document.getElementById('anuncios-aviso');
                    aviso.textContent = r.avisados === 1 ? 'Se avisó a 1 miembro.' : 'Se avisó a ' + r.avisados + ' miembros.';
                    aviso.classList.remove('d-none');
                    clearTimeout(aviso._ocultar);
                    aviso._ocultar = setTimeout(() => aviso.classList.add('d-none'), 5000);
                }
            })
            .catch(err => {
                alerta.textContent = err.message;
                alerta.classList.remove('d-none');
            })
            .finally(() => { guardarBtn.disabled = false; });
    });

    lista.addEventListener('click', (e) => {
        const boton = e.target.closest('button');
        if (!boton) return;
        if (boton.dataset.editar) {
            abrir(anuncios.find(a => a.id === boton.dataset.editar));
        } else if (boton.dataset.pausar) {
            boton.disabled = true;
            api('/api/gyms/' + gymId() + '/anuncios/' + boton.dataset.pausar + '/pausa', {
                method: 'POST', body: JSON.stringify({ pausado: boton.dataset.pausado !== '1' })
            }).then(cargar).catch(err => { boton.disabled = false; alert(err.message); });
        } else if (boton.dataset.borrar) {
            const a = anuncios.find(x => x.id === boton.dataset.borrar);
            if (!confirm('¿Borrar el anuncio «' + (a ? a.titulo : '') + '»? Ya no lo verán tus miembros.')) return;
            api('/api/gyms/' + gymId() + '/anuncios/' + boton.dataset.borrar, { method: 'DELETE' })
                .then(cargar).catch(err => alert(err.message));
        }
    });

    document.addEventListener('panel:mostrado', e => { if (e.detail === 'anuncios') cargar(); });
    pintar();
})();
