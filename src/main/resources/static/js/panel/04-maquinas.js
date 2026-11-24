/* Panel del dueño (bienvenido.html): Máquinas. */

/* ═══════════════════════════════════════
   MÁQUINAS Y EJERCICIOS — /api/gyms/{id}/machines
═══════════════════════════════════════ */
function initMachines() {
    const modal = new bootstrap.Modal(document.getElementById('machineModal'));
    const form = document.getElementById('machine-form');
    const alertBox = document.getElementById('machine-alert');
    const saveBtn = document.getElementById('machine-save-btn');
    const rows = document.getElementById('exercise-rows');

    // Una fila por ejercicio. El id viaja oculto para no perder la
    // referencia de ejercicios que ya existían al editar la máquina.
    function addExerciseRow(ej) {
        ej = ej || {};
        const row = document.createElement('div');
        row.className = 'row g-2 align-items-end exercise-row';
        row.innerHTML = `
            <input type="hidden" class="ex-id" value="${escapeHtml(ej.id || '')}">
            <div class="col-md-4">
                <input type="text" class="form-control form-control-sm ex-nombre" placeholder="Ejercicio" value="${escapeHtml(ej.nombre || '')}">
            </div>
            <div class="col-md-3">
                <input type="text" class="form-control form-control-sm ex-musculo" list="routine-categoria-options" placeholder="Músculo" value="${escapeHtml(ej.musculo || '')}">
            </div>
            <div class="col-md-2">
                <input type="number" min="1" class="form-control form-control-sm ex-series" placeholder="Series" value="${ej.series != null ? ej.series : ''}">
            </div>
            <div class="col-md-2">
                <input type="text" class="form-control form-control-sm ex-reps" placeholder="Reps" value="${escapeHtml(ej.repeticiones || '')}">
            </div>
            <div class="col-md-1 text-end">
                <button type="button" class="btn btn-outline-danger btn-sm rounded-circle ex-remove" title="Quitar"><i class='bx bx-x'></i></button>
            </div>
            <div class="col-12">
                <input type="text" class="form-control form-control-sm ex-instrucciones" maxlength="300" placeholder="Cómo se hace (opcional): la app se lo muestra al miembro al entrenar" value="${escapeHtml(ej.instrucciones || '')}">
            </div>`;
        row.querySelector('.ex-remove').addEventListener('click', () => row.remove());
        rows.appendChild(row);
    }

    document.getElementById('exercise-add-btn').addEventListener('click', () => addExerciseRow());

    document.getElementById('machine-new-btn').addEventListener('click', () => {
        form.reset();
        rows.innerHTML = '';
        document.getElementById('machine-id').value = '';
        document.getElementById('machine-cantidad').value = 1;
        document.getElementById('machineModalLabel').textContent = 'Nueva máquina';
        alertBox.classList.add('d-none');
        addExerciseRow();
        modal.show();
    });

    document.getElementById('machine-import-btn').addEventListener('click', () => {
        const gymId = localStorage.getItem('gymId');
        if (!confirm('Se agregarán las máquinas más comunes con sus ejercicios. Las que ya tengas no se duplican. ¿Continuar?')) return;
        fetch('/api/gyms/' + gymId + '/machines/import', { method: 'POST' })
            .then(res => res.json())
            .then(data => { alert(data.message); loadMachines(gymId); });
    });

    document.getElementById('machines-container').addEventListener('click', (e) => {
        const editBtn = e.target.closest('[data-edit-machine]');
        const delBtn = e.target.closest('[data-delete-machine]');
        if (editBtn) {
            const m = JSON.parse(editBtn.dataset.editMachine);
            document.getElementById('machine-id').value = m.id;
            document.getElementById('machine-nombre').value = m.nombre || '';
            document.getElementById('machine-zona').value = m.zona || '';
            document.getElementById('machine-cantidad').value = m.cantidad || 1;
            document.getElementById('machine-descripcion').value = m.descripcion || '';
            document.getElementById('machineModalLabel').textContent = 'Editar máquina';
            rows.innerHTML = '';
            (m.ejercicios || []).forEach(addExerciseRow);
            if (!(m.ejercicios || []).length) addExerciseRow();
            alertBox.classList.add('d-none');
            modal.show();
        } else if (delBtn) {
            if (!confirm('¿Eliminar esta máquina y sus ejercicios?')) return;
            fetch('/api/machines/' + delBtn.dataset.deleteMachine, { method: 'DELETE' })
                .then(() => loadMachines(localStorage.getItem('gymId')));
        }
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const gymId = localStorage.getItem('gymId');
        const id = document.getElementById('machine-id').value;
        const ejercicios = [...rows.querySelectorAll('.exercise-row')].map(r => ({
            id: r.querySelector('.ex-id').value || null,
            nombre: r.querySelector('.ex-nombre').value.trim(),
            musculo: r.querySelector('.ex-musculo').value.trim(),
            series: r.querySelector('.ex-series').value ? parseInt(r.querySelector('.ex-series').value, 10) : null,
            repeticiones: r.querySelector('.ex-reps').value.trim(),
            instrucciones: r.querySelector('.ex-instrucciones').value.trim() || null
        })).filter(ej => ej.nombre);

        const payload = {
            nombre: document.getElementById('machine-nombre').value.trim(),
            zona: document.getElementById('machine-zona').value.trim() || 'Sin zona',
            cantidad: parseInt(document.getElementById('machine-cantidad').value, 10) || 1,
            descripcion: document.getElementById('machine-descripcion').value.trim(),
            ejercicios
        };

        saveBtn.disabled = true;
        fetch(id ? '/api/machines/' + id : '/api/gyms/' + gymId + '/machines', {
            method: id ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(res => res.json().then(data => ({ status: res.status, body: data })))
        .then(response => {
            saveBtn.disabled = false;
            if (response.status === 200) { modal.hide(); loadMachines(gymId); }
            else { alertBox.textContent = response.body.error || 'No se pudo guardar la máquina.'; alertBox.classList.remove('d-none'); }
        })
        .catch(() => {
            saveBtn.disabled = false;
            alertBox.textContent = 'Error de conexión con el servidor.';
            alertBox.classList.remove('d-none');
        });
    });

    initCatalogoMaquinas();

    const gymId = localStorage.getItem('gymId');
    if (gymId) {
        document.getElementById('machine-new-btn').disabled = false;
        document.getElementById('machine-import-btn').disabled = false;
        document.getElementById('machine-catalogo-btn').disabled = false;
    }
    // Sin gimnasio, loadMachines pinta el aviso para configurarlo.
    loadMachines(gymId);
}

/* ─── Catálogo de máquinas — /api/gyms/{id}/machines/catalogo ───
   Máquinas comunes por categoría (Pecho, Espalda, Hombro, Bíceps…). Cada una
   trae sus ejercicios con músculo, series y cómo se hacen; el dueño marca los
   que sí se hacen en su gimnasio y la máquina se agrega (o se completa si ya
   la tenía). Esos ejercicios son los que luego elige en las rutinas. */
function initCatalogoMaquinas() {
    const modal = new bootstrap.Modal(document.getElementById('catalogoModal'));
    const chips = document.getElementById('catalogo-categorias');
    const lista = document.getElementById('catalogo-lista');
    const buscar = document.getElementById('catalogo-buscar');
    const alerta = document.getElementById('catalogo-alert');
    let datos = null;
    let categoria = null;
    let abierta = null;     // nombre de la máquina con sus ejercicios desplegados

    const avisar = (texto, tipo) => {
        alerta.className = 'alert py-2 small alert-' + tipo;
        alerta.textContent = texto;
    };

    function cargar() {
        const gymId = localStorage.getItem('gymId');
        lista.innerHTML = '<div class="text-center text-body-secondary py-5"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Cargando catálogo…</div>';
        return fetch('/api/gyms/' + gymId + '/machines/catalogo')
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(d => { datos = d; categoria = categoria || d.categorias[0]; pintar(); })
            .catch(() => { lista.innerHTML = '<div class="text-danger small">No se pudo cargar el catálogo.</div>'; });
    }

    function pintar() {
        const q = buscar.value.trim().toLowerCase();
        chips.innerHTML = datos.categorias.map(c => {
            const n = datos.maquinas.filter(m => m.categoria === c).length;
            const activa = !q && c === categoria;
            return `<button type="button" class="btn btn-sm rounded-pill ${activa ? 'btn-primary' : 'btn-outline-secondary'}" data-cat="${escapeHtml(c)}" role="tab" aria-selected="${activa}">${escapeHtml(c)} <span class="opacity-75">${n}</span></button>`;
        }).join('');
        // Con búsqueda se ve todo el catálogo; si no, la categoría elegida.
        const visibles = datos.maquinas.filter(m => q
            ? (m.nombre + ' ' + m.categoria + ' ' + m.ejercicios.map(e => e.nombre + ' ' + e.musculo).join(' ')).toLowerCase().includes(q)
            : m.categoria === categoria);
        if (!visibles.length) {
            lista.innerHTML = '<div class="col-12 text-body-secondary small">No hay máquinas que coincidan.</div>';
            return;
        }
        lista.innerHTML = visibles.map(m => {
            const tiene = new Set((m.ejerciciosQueTienes || []).map(n => n.trim().toLowerCase()));
            const completa = m.machineId && m.ejercicios.every(e => tiene.has(e.nombre.trim().toLowerCase()));
            const musculos = [...new Set(m.ejercicios.map(e => e.musculo))];
            const abiertaAqui = abierta === m.nombre;
            return `
                <div class="col-md-6 col-xl-4">
                    <div class="border rounded-4 p-3 h-100 d-flex flex-column ${m.machineId ? 'border-success-subtle' : ''}">
                        <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                            <h6 class="fw-bold mb-0">${escapeHtml(m.nombre)}</h6>
                            ${m.machineId ? '<span class="badge rounded-pill bg-success-subtle text-success-emphasis flex-shrink-0">' + (completa ? 'Ya la tienes' : 'La tienes · faltan ejercicios') + '</span>' : ''}
                        </div>
                        <p class="text-body-secondary small mb-2">${escapeHtml(m.descripcion || '')}</p>
                        <div class="d-flex flex-wrap gap-1 mb-3">${musculos.map(mu => '<span class="badge rounded-pill bg-body-secondary text-body-secondary">' + escapeHtml(mu) + '</span>').join('')}</div>
                        ${abiertaAqui ? `
                            <div class="mb-3 small">
                                ${m.ejercicios.map((e, i) => {
                                    const ya = tiene.has(e.nombre.trim().toLowerCase());
                                    const id = 'cat-' + btoa(unescape(encodeURIComponent(m.nombre))).replace(/[^a-z0-9]/gi, '') + '-' + i;
                                    return `<div class="form-check border-bottom py-1">
                                        <input class="form-check-input" type="checkbox" id="${id}" data-ej="${escapeHtml(e.nombre)}" ${ya ? 'checked disabled' : 'checked'}>
                                        <label class="form-check-label d-block" for="${id}">
                                            <span class="fw-semibold">${escapeHtml(e.nombre)}</span>
                                            <span class="text-body-secondary"> · ${escapeHtml(e.musculo)} · ${e.series} × ${escapeHtml(e.repeticiones)}${ya ? ' · ya la tienes' : ''}</span>
                                            <span class="d-block text-body-secondary fst-italic">${escapeHtml(e.instrucciones || '')}</span>
                                        </label>
                                    </div>`;
                                }).join('')}
                            </div>
                            <div class="mt-auto d-flex gap-2">
                                <button type="button" class="btn btn-primary btn-sm rounded-pill px-3" data-agregar="${escapeHtml(m.nombre)}" ${completa ? 'disabled' : ''}>
                                    <i class='bx bx-plus'></i> ${m.machineId ? 'Agregar los marcados' : 'Agregar al gimnasio'}
                                </button>
                                <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-cerrar>Cerrar</button>
                            </div>` : `
                            <ul class="small text-body-secondary ps-3 mb-3">${m.ejercicios.slice(0, 4).map(e => '<li>' + escapeHtml(e.nombre) + '</li>').join('')}${m.ejercicios.length > 4 ? '<li>y ' + (m.ejercicios.length - 4) + ' más</li>' : ''}</ul>
                            <div class="mt-auto">
                                <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-abrir="${escapeHtml(m.nombre)}">
                                    ${completa ? 'Ver ejercicios' : 'Elegir ejercicios'}
                                </button>
                            </div>`}
                    </div>
                </div>`;
        }).join('');
    }

    chips.addEventListener('click', e => {
        const b = e.target.closest('[data-cat]');
        if (!b) return;
        categoria = b.dataset.cat;
        buscar.value = '';
        abierta = null;
        pintar();
    });
    buscar.addEventListener('input', () => { abierta = null; pintar(); });

    lista.addEventListener('click', e => {
        const abrir = e.target.closest('[data-abrir]');
        if (abrir) { abierta = abrir.dataset.abrir; pintar(); return; }
        if (e.target.closest('[data-cerrar]')) { abierta = null; pintar(); return; }
        const agregar = e.target.closest('[data-agregar]');
        if (!agregar) return;
        const tarjeta = agregar.closest('.border');
        const ejercicios = [...tarjeta.querySelectorAll('input[data-ej]:checked:not(:disabled)')].map(i => i.dataset.ej);
        if (!ejercicios.length) { avisar('Marca al menos un ejercicio.', 'warning'); return; }
        agregar.disabled = true;
        const gymId = localStorage.getItem('gymId');
        fetch('/api/gyms/' + gymId + '/machines/catalogo', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nombre: agregar.dataset.agregar, ejercicios })
        })
            .then(res => res.json().then(body => ({ ok: res.ok, body })))
            .then(({ ok, body }) => {
                if (!ok) throw new Error(body.error || 'No se pudo agregar.');
                avisar(body.maquina.nombre + ': ' + body.agregados + ' ejercicio(s) agregados. Ya puedes usarlos en tus rutinas.', 'success');
                abierta = null;
                loadMachines(gymId);
                return cargar();
            })
            .catch(err => { avisar(err.message || 'Error de conexión con el servidor.', 'danger'); agregar.disabled = false; });
    });

    document.getElementById('machine-catalogo-btn').addEventListener('click', () => {
        alerta.className = 'alert d-none py-2 small';
        buscar.value = '';
        abierta = null;
        modal.show();
        cargar();
    });
}

function loadMachines(gymId) {
    const container = document.getElementById('machines-container');
    if (!gymId) {
        container.innerHTML = avisoSinGimnasio('Configura primero "Mi Gimnasio" para registrar tus máquinas.', 'bx-cog');
        return;
    }
    fetch('/api/gyms/' + gymId + '/machines')
        .then(res => res.json())
        .then(machines => {
            resumenConteos.maquinas = machines.length;
            pintarPasos();
            if (!machines.length) {
                container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-cog fs-1 d-block mb-2 opacity-50"></i>Aún no registras máquinas. Usa "Catálogo de máquinas" para elegirlas por categoría con sus ejercicios, o "Importar básicas" para empezar rápido.</div>';
                return;
            }
            const zonas = {};
            machines.forEach(m => { (zonas[m.zona || 'Sin zona'] = zonas[m.zona || 'Sin zona'] || []).push(m); });

            container.innerHTML = Object.keys(zonas).sort().map(zona => `
                <h6 class="text-uppercase fw-bold text-body-secondary small mb-3 mt-2" style="letter-spacing:0.06em">${escapeHtml(zona)}</h6>
                <div class="row g-3 mb-4">
                    ${zonas[zona].map(m => `
                        <div class="col-md-6 col-xl-4">
                            <div class="dash-empty text-start h-100 d-flex flex-column">
                                <div class="d-flex align-items-start justify-content-between gap-2 mb-2">
                                    <h4 class="fw-bold fs-6 mb-0">${escapeHtml(m.nombre)}</h4>
                                    ${m.cantidad > 1 ? '<span class="badge rounded-pill bg-secondary bg-opacity-10 text-body-secondary">x' + m.cantidad + '</span>' : ''}
                                </div>
                                ${m.descripcion ? '<p class="text-body-secondary small mb-2">' + escapeHtml(m.descripcion) + '</p>' : ''}
                                <div class="mb-3">
                                    ${(m.ejercicios || []).length
                                        ? (m.ejercicios || []).map(ej => `
                                            <div class="d-flex justify-content-between align-items-center border-bottom py-1">
                                                <span class="small">${escapeHtml(ej.nombre)}
                                                    ${ej.musculo ? '<span class="text-body-secondary"> · ' + escapeHtml(ej.musculo) + '</span>' : ''}
                                                </span>
                                                <span class="text-body-secondary small flex-shrink-0 ps-2">${ej.series || '-'}${ej.repeticiones ? ' x ' + escapeHtml(ej.repeticiones) : ''}</span>
                                            </div>`).join('')
                                        : '<span class="text-body-secondary small fst-italic">Sin ejercicios capturados todavía.</span>'}
                                </div>
                                <div class="mt-auto d-flex gap-2">
                                    <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-edit-machine='${JSON.stringify(m).replace(/'/g, "&#39;")}'>Editar</button>
                                    <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-delete-machine="${m.id}">Eliminar</button>
                                </div>
                            </div>
                        </div>`).join('')}
                </div>`).join('');
        });
}
