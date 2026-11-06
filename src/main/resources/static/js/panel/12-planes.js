/* Panel del dueño (bienvenido.html): Planes de membresía — /api/gyms/{id}/planes. */

/* ─── Planes de membresía ─── */
function initPlanes() {
    const modal = new bootstrap.Modal(document.getElementById('planModal'));
    const form = document.getElementById('plan-form');
    const alertBox = document.getElementById('plan-alert');
    const saveBtn = document.getElementById('plan-guardar-btn');
    const camposDuracion = document.getElementById('plan-duracion-campos');
    const tipoUnico = document.getElementById('plan-tipo-unico');

    const sincronizarTipo = () => camposDuracion.classList.toggle('d-none', tipoUnico.checked);
    document.querySelectorAll('input[name="plan-tipo"]').forEach(r => r.addEventListener('change', sincronizarTipo));

    function abrir(p, esNuevo) {
        form.reset();
        alertBox.classList.add('d-none');
        document.getElementById('plan-id').value = !esNuevo && p ? p.id : '';
        document.getElementById('planModalLabel').textContent = !esNuevo && p ? 'Editar plan' : 'Nuevo plan';
        document.getElementById('plan-nombre').value = p ? p.nombre : '';
        document.getElementById('plan-precio').value = p && p.precio != null ? p.precio : '';
        document.getElementById('plan-duracion-cantidad').value = p && p.duracionCantidad ? p.duracionCantidad : 1;
        document.getElementById('plan-duracion-unidad').value = p && p.duracionUnidad ? p.duracionUnidad : 'mes';
        document.getElementById('plan-beneficios').value = p ? (p.beneficios || []).join('\n') : '';
        document.getElementById('plan-descripcion').value = p && p.descripcion ? p.descripcion : '';
        document.getElementById('plan-destacado').checked = !!(p && p.destacado);
        document.getElementById('plan-activo').checked = p && p.activo != null ? p.activo : true;
        tipoUnico.checked = !!(p && p.pagoUnico);
        document.getElementById('plan-tipo-duracion').checked = !tipoUnico.checked;
        sincronizarTipo();
        modal.show();
    }

    document.getElementById('plan-nuevo-btn').addEventListener('click', () => abrir(null, true));
    document.getElementById('planes-presets').addEventListener('click', (e) => {
        const btn = e.target.closest('[data-preset]');
        if (btn) abrir(presetsCache[parseInt(btn.dataset.preset, 10)], true);
    });

    document.getElementById('planes-container').addEventListener('click', (e) => {
        const editar = e.target.closest('[data-editar-plan]');
        const borrar = e.target.closest('[data-borrar-plan]');
        if (editar) {
            abrir(planesCache.find(p => p.id === editar.dataset.editarPlan), false);
        } else if (borrar) {
            const p = planesCache.find(x => x.id === borrar.dataset.borrarPlan);
            if (!confirm('¿Eliminar el plan "' + p.nombre + '"? Quien ya lo pagó conserva su membresía.')) return;
            borrar.disabled = true;
            const gymId = localStorage.getItem('gymId');
            apiTienda('/api/gyms/' + gymId + '/planes/' + p.id, { method: 'DELETE' })
                .then(() => loadPlanes(gymId))
                .catch(err => { borrar.disabled = false; alert(err.message); });
        }
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        alertBox.classList.add('d-none');
        const gymId = localStorage.getItem('gymId');
        const id = document.getElementById('plan-id').value;
        const precio = parseFloat(document.getElementById('plan-precio').value);
        const fallar = (msg) => { alertBox.textContent = msg; alertBox.classList.remove('d-none'); };
        if (!document.getElementById('plan-nombre').value.trim()) return fallar('Escribe el nombre del plan.');
        if (!(precio > 0)) return fallar('Ponle un precio mayor a $0.');

        saveBtn.disabled = true;
        saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span> Guardando...';
        apiTienda(id ? '/api/gyms/' + gymId + '/planes/' + id : '/api/gyms/' + gymId + '/planes', {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify({
                nombre: document.getElementById('plan-nombre').value.trim(),
                descripcion: document.getElementById('plan-descripcion').value.trim(),
                precio,
                pagoUnico: tipoUnico.checked,
                duracionUnidad: document.getElementById('plan-duracion-unidad').value,
                duracionCantidad: parseInt(document.getElementById('plan-duracion-cantidad').value, 10) || null,
                beneficios: document.getElementById('plan-beneficios').value.split('\n').map(s => s.trim()).filter(Boolean),
                destacado: document.getElementById('plan-destacado').checked,
                activo: document.getElementById('plan-activo').checked
            })
        })
            .then(() => { modal.hide(); loadPlanes(gymId); })
            .catch(err => fallar(err.message))
            .finally(() => { saveBtn.disabled = false; saveBtn.innerHTML = '<i class="bx bx-save"></i> Guardar plan'; });
    });

    const gymId = localStorage.getItem('gymId');
    if (gymId) document.getElementById('plan-nuevo-btn').disabled = false;
    loadPlanes(gymId);
}

function loadPlanes(gymId) {
    const container = document.getElementById('planes-container');
    const presets = document.getElementById('planes-presets');
    if (!gymId) {
        container.innerHTML = '';
        return;
    }
    estadoCargando(container, 'Cargando planes…');
    apiTienda('/api/gyms/' + gymId + '/planes')
        .then(planes => {
            planesCache = planes;
            resumenConteos.planes = planes.filter(p => !p.pagoUnico).length;
            pintarPasos();
            pintarPresets();
            if (!planes.length) {
                container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-purchase-tag-alt fs-1 d-block mb-2 opacity-50"></i>Aún no tienes planes. Empieza con uno de "Agregar rápido".</div>';
                return;
            }
            container.innerHTML = '<div class="row g-3">' + planes.map(p => `
                <div class="col-sm-6 col-xl-4">
                    <div class="dash-empty text-start h-100 d-flex flex-column${p.destacado ? ' border border-primary' : ''}">
                        <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                            <h4 class="fw-bold fs-5 mb-0">${escapeHtml(p.nombre)}</h4>
                            <div class="d-flex flex-wrap gap-1 justify-content-end">
                                ${p.destacado ? '<span class="badge rounded-pill bg-primary bg-opacity-10 text-primary"><i class="bx bx-star"></i> Destacado</span>' : ''}
                                ${p.activo ? '' : '<span class="badge rounded-pill bg-secondary bg-opacity-10 text-body-secondary"><i class="bx bx-hide"></i> Oculto</span>'}
                            </div>
                        </div>
                        <div class="fs-3 fw-bold">${dinero(p.precio)}<small class="fs-6 text-body-secondary fw-normal"> · ${escapeHtml(p.duracionTexto)}</small></div>
                        ${p.descripcion ? '<p class="text-body-secondary small mb-0">' + escapeHtml(p.descripcion) + '</p>' : ''}
                        <ul class="list-unstyled small text-body-secondary my-3">
                            ${(p.beneficios || []).map(b => '<li class="d-flex gap-1"><i class="bx bx-check text-success"></i>' + escapeHtml(b) + '</li>').join('')}
                        </ul>
                        <div class="mt-auto d-flex gap-2">
                            <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-editar-plan="${escapeHtml(p.id)}">Editar</button>
                            <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-borrar-plan="${escapeHtml(p.id)}">Eliminar</button>
                        </div>
                    </div>
                </div>`).join('') + '</div>';
        })
        .catch(err => estadoError(container, err.message, () => loadPlanes(gymId)));

    apiTienda('/api/gyms/' + gymId + '/planes/presets')
        .then(lista => { presetsCache = lista; pintarPresets(); })
        .catch(() => { presets.innerHTML = '<span class="text-body-secondary small">No se pudieron cargar las sugerencias.</span>'; });
}

// Las sugerencias que ya tienes (mismo nombre, o misma duración) se marcan
// en lugar de ofrecerse otra vez: así no quedan dos "Mensual".
function pintarPresets() {
    if (!presetsCache.length) return;
    const normal = (s) => String(s || '').trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
    const yaLoTienes = (p) => planesCache.some(x => normal(x.nombre) === normal(p.nombre)
        || (!p.pagoUnico && !x.pagoUnico && x.duracionUnidad === p.duracionUnidad && x.duracionCantidad === p.duracionCantidad));
    document.getElementById('planes-presets').innerHTML = presetsCache.map((p, i) => yaLoTienes(p)
        ? '<span class="preset-creado"><i class="bx bx-check" aria-hidden="true"></i> ' + escapeHtml(p.nombre) + ' · ya lo tienes</span>'
        : '<button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-preset="' + i + '"><i class="bx bx-plus" aria-hidden="true"></i> '
            + escapeHtml(p.nombre) + (p.precio != null ? ' · ' + dinero(p.precio) : '') + '</button>').join('');
}
