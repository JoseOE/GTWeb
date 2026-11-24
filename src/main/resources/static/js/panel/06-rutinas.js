/* Panel del dueño (bienvenido.html): Rutinas. */

/* ═══════════════════════════════════════
   RUTINAS — conectado a /api/gyms/{id}/routines
═══════════════════════════════════════ */
const CATEGORY_ICONS = {
    espalda: 'bx-body', pecho: 'bx-run', pierna: 'bx-walk', hombro: 'bx-dumbbell',
    brazo: 'bx-dumbbell', core: 'bx-analyse', cardio: 'bx-heart', movilidad: 'bx-body'
};
function iconForCategory(cat) {
    return CATEGORY_ICONS[(cat || '').trim().toLowerCase()] || 'bx-dumbbell';
}

const MUSCULOS_RUTINA = ['Pecho', 'Espalda', 'Pierna', 'Hombro', 'Brazo', 'Core', 'Cardio', 'Movilidad', 'Otro'];

// "Press banca plano — 4 x 10" → { nombre, series, repeticiones }. Sirve para
// pasar a la lista las rutinas que se capturaron como texto.
function leerEjercicioDeTexto(texto) {
    const m = /^(.*?)\s*[—–-]\s*(\d+)\s*[x×]\s*(.+)$/i.exec(texto.trim());
    return m ? { nombre: m[1].trim(), series: Number(m[2]), repeticiones: m[3].trim() } : { nombre: texto.trim() };
}

// Editor de los ejercicios de una rutina: una fila por ejercicio, en orden.
// Los de máquina llevan machineId + exerciseId; los libres, nombre y músculo.
function crearEditorDeEjercicios() {
    const lista = document.getElementById('routine-items');
    const select = document.getElementById('routine-item-select');
    let items = [];
    let maquinas = [];

    function opciones() {
        const grupos = maquinas
            .filter(m => (m.ejercicios || []).length)
            .map(m => '<optgroup label="' + escapeHtml(m.nombre) + '">'
                + m.ejercicios.map(e => '<option value="m|' + escapeHtml(m.id) + '|' + escapeHtml(e.id) + '">'
                    + escapeHtml(e.nombre) + (e.musculo ? ' · ' + escapeHtml(e.musculo) : '') + '</option>').join('')
                + '</optgroup>').join('');
        select.innerHTML = '<option value="">' + (maquinas.length ? 'Elige un ejercicio…' : 'Aún no tienes máquinas: agrega ejercicios libres') + '</option>'
            + grupos + '<optgroup label="Sin máquina"><option value="libre">Ejercicio libre…</option></optgroup>';
    }

    function buscar(machineId, exerciseId) {
        const m = maquinas.find(x => x.id === machineId);
        const e = m && (m.ejercicios || []).find(x => x.id === exerciseId);
        return m && e ? { m, e } : null;
    }

    function pintar() {
        if (!items.length) {
            lista.innerHTML = '<div class="text-body-secondary small border rounded-3 p-3 text-center">Agrega el primer ejercicio con la lista de abajo.</div>';
            return;
        }
        lista.innerHTML = items.map((it, i) => {
            const libre = !it.machineId;
            const titulo = libre
                ? '<input type="text" class="form-control form-control-sm ri-nombre" placeholder="Nombre del ejercicio" maxlength="80" value="' + escapeHtml(it.nombre || '') + '">'
                  + '<select class="form-select form-select-sm ri-musculo mt-1" aria-label="Músculo">'
                  + MUSCULOS_RUTINA.map(mu => '<option' + (mu === (it.musculo || 'Otro') ? ' selected' : '') + '>' + mu + '</option>').join('') + '</select>'
                : '<div class="fw-semibold small">' + escapeHtml(it.nombre) + '</div><div class="text-body-secondary" style="font-size:.75rem">'
                  + escapeHtml(it.machineNombre || '') + (it.musculo ? ' · ' + escapeHtml(it.musculo) : '') + '</div>';
            return '<div class="border rounded-3 p-2 d-flex flex-wrap align-items-center gap-2" data-i="' + i + '">'
                + '<span class="badge rounded-pill bg-primary bg-opacity-10 text-primary">' + (i + 1) + '</span>'
                + '<div class="flex-grow-1" style="min-width:180px">' + titulo + '</div>'
                + '<div class="d-flex gap-1 align-items-center">'
                + '<input type="number" min="1" max="10" class="form-control form-control-sm ri-series" style="width:64px" title="Series" aria-label="Series" value="' + (it.series || 3) + '">'
                + '<span class="small text-body-secondary">×</span>'
                + '<input type="text" maxlength="20" class="form-control form-control-sm ri-reps" style="width:84px" title="Repeticiones" aria-label="Repeticiones" placeholder="Reps" value="' + escapeHtml(it.repeticiones || '') + '">'
                + '<input type="number" min="0" max="600" step="15" class="form-control form-control-sm ri-descanso" style="width:76px" title="Descanso en segundos" aria-label="Descanso en segundos" value="' + (it.descansoSegundos != null ? it.descansoSegundos : 90) + '">'
                + '<span class="small text-body-secondary">s</span>'
                + '</div>'
                + '<div class="btn-group btn-group-sm">'
                + '<button type="button" class="btn btn-outline-secondary ri-subir" title="Subir" aria-label="Subir"' + (i === 0 ? ' disabled' : '') + '><i class="bx bx-chevron-up"></i></button>'
                + '<button type="button" class="btn btn-outline-secondary ri-bajar" title="Bajar" aria-label="Bajar"' + (i === items.length - 1 ? ' disabled' : '') + '><i class="bx bx-chevron-down"></i></button>'
                + '<button type="button" class="btn btn-outline-danger ri-quitar" title="Quitar" aria-label="Quitar"><i class="bx bx-x"></i></button>'
                + '</div></div>';
        }).join('');
    }

    // Guarda lo que se escribió en las filas antes de repintarlas.
    function leerFilas() {
        lista.querySelectorAll('[data-i]').forEach(fila => {
            const it = items[Number(fila.dataset.i)];
            if (!it) return;
            const nombre = fila.querySelector('.ri-nombre');
            const musculo = fila.querySelector('.ri-musculo');
            if (nombre) it.nombre = nombre.value.trim();
            if (musculo) it.musculo = musculo.value;
            it.series = Number(fila.querySelector('.ri-series').value) || null;
            it.repeticiones = fila.querySelector('.ri-reps').value.trim();
            const d = fila.querySelector('.ri-descanso').value;
            it.descansoSegundos = d === '' ? null : Number(d);
        });
    }

    lista.addEventListener('click', (e) => {
        const fila = e.target.closest('[data-i]');
        if (!fila) return;
        const i = Number(fila.dataset.i);
        if (e.target.closest('.ri-quitar')) { leerFilas(); items.splice(i, 1); }
        else if (e.target.closest('.ri-subir') && i > 0) { leerFilas(); [items[i - 1], items[i]] = [items[i], items[i - 1]]; }
        else if (e.target.closest('.ri-bajar') && i < items.length - 1) { leerFilas(); [items[i + 1], items[i]] = [items[i], items[i + 1]]; }
        else return;
        pintar();
    });

    document.getElementById('routine-item-add').addEventListener('click', () => {
        const v = select.value;
        if (!v) return;
        leerFilas();
        if (v === 'libre') {
            items.push({ nombre: '', musculo: 'Otro', series: 3, repeticiones: '10', descansoSegundos: 90 });
        } else {
            const [, machineId, exerciseId] = v.split('|');
            const hallado = buscar(machineId, exerciseId);
            if (!hallado) return;
            items.push({
                machineId, exerciseId, machineNombre: hallado.m.nombre, nombre: hallado.e.nombre, musculo: hallado.e.musculo,
                series: hallado.e.series || 3, repeticiones: hallado.e.repeticiones || '10', descansoSegundos: 90
            });
        }
        select.value = '';
        pintar();
        if (v === 'libre') {
            const nuevo = lista.querySelector('[data-i="' + (items.length - 1) + '"] .ri-nombre');
            if (nuevo) nuevo.focus();
        }
    });

    return {
        // Abre el editor con los ejercicios de una rutina. Las viejas, capturadas
        // como texto, se convierten: si el nombre coincide con un ejercicio de
        // una máquina se enlazan con él; si no, quedan como ejercicios libres.
        abrir(rutina, gymId) {
            items = [];
            pintar();
            select.innerHTML = '<option value="">Cargando tus máquinas…</option>';
            return fetch('/api/gyms/' + gymId + '/machines')
                .then(res => res.ok ? res.json() : [])
                .catch(() => [])
                .then(data => {
                    maquinas = Array.isArray(data) ? data : [];
                    opciones();
                    if (rutina && rutina.items && rutina.items.length) {
                        items = rutina.items.map(it => ({ ...it }));
                    } else if (rutina && rutina.ejercicios) {
                        items = rutina.ejercicios.filter(Boolean).map(texto => {
                            const leido = leerEjercicioDeTexto(texto);
                            const clave = leido.nombre.toLowerCase();
                            for (const m of maquinas) {
                                const e = (m.ejercicios || []).find(x => (x.nombre || '').toLowerCase() === clave);
                                if (e) return { machineId: m.id, exerciseId: e.id, machineNombre: m.nombre, nombre: e.nombre, musculo: e.musculo,
                                    series: leido.series || e.series || 3, repeticiones: leido.repeticiones || e.repeticiones || '10', descansoSegundos: 90 };
                            }
                            return { nombre: leido.nombre, musculo: MUSCULOS_RUTINA.includes(rutina.categoria) ? rutina.categoria : 'Otro',
                                series: leido.series || 3, repeticiones: leido.repeticiones || '10', descansoSegundos: 90 };
                        });
                    }
                    pintar();
                });
        },
        valor() {
            leerFilas();
            return items.map(it => it.machineId
                ? { machineId: it.machineId, exerciseId: it.exerciseId, series: it.series, repeticiones: it.repeticiones, descansoSegundos: it.descansoSegundos }
                : { nombre: it.nombre, musculo: it.musculo, series: it.series, repeticiones: it.repeticiones, descansoSegundos: it.descansoSegundos });
        }
    };
}

function initRoutines() {
    const modalEl = document.getElementById('routineModal');
    const modal = new bootstrap.Modal(modalEl);
    const form = document.getElementById('routine-form');
    const alertBox = document.getElementById('routine-alert');
    const saveBtn = document.getElementById('routine-save-btn');
    const editor = crearEditorDeEjercicios();

    document.getElementById('routine-new-btn').addEventListener('click', () => {
        form.reset();
        document.getElementById('routine-id').value = '';
        document.getElementById('routineModalLabel').textContent = 'Nueva rutina';
        alertBox.classList.add('d-none');
        editor.abrir(null, localStorage.getItem('gymId'));
        modal.show();
    });

    document.getElementById('routines-container').addEventListener('click', (e) => {
        const editBtn = e.target.closest('[data-edit-routine]');
        const delBtn = e.target.closest('[data-delete-routine]');
        if (editBtn) {
            const r = JSON.parse(editBtn.dataset.editRoutine);
            document.getElementById('routine-id').value = r.id;
            document.getElementById('routine-nombre').value = r.nombre || '';
            document.getElementById('routine-categoria').value = r.categoria || '';
            document.getElementById('routine-duracion').value = r.duracion || '';
            document.getElementById('routine-descripcion').value = r.descripcion || '';
            document.getElementById('routine-dia').value = r.dia || '';
            document.getElementById('routine-coach').value = sinPrefijoCoach(r.coach);
            document.getElementById('routine-nivel').value = r.nivel || '';
            document.getElementById('routineModalLabel').textContent = 'Editar rutina';
            alertBox.classList.add('d-none');
            editor.abrir(r, localStorage.getItem('gymId'));
            modal.show();
        } else if (delBtn) {
            if (!confirm('¿Eliminar esta rutina? Esta acción no se puede deshacer.')) return;
            fetch('/api/routines/' + delBtn.dataset.deleteRoutine, { method: 'DELETE' })
                .then(() => loadRoutines(localStorage.getItem('gymId')));
        }
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const gymId = localStorage.getItem('gymId');
        const id = document.getElementById('routine-id').value;
        const items = editor.valor();
        if (!items.length) {
            alertBox.textContent = 'Agrega al menos un ejercicio.';
            alertBox.classList.remove('d-none');
            return;
        }
        const payload = {
            nombre: document.getElementById('routine-nombre').value.trim(),
            categoria: document.getElementById('routine-categoria').value.trim(),
            duracion: document.getElementById('routine-duracion').value.trim(),
            descripcion: document.getElementById('routine-descripcion').value.trim(),
            items,
            dia: document.getElementById('routine-dia').value,
            coach: sinPrefijoCoach(document.getElementById('routine-coach').value),
            nivel: document.getElementById('routine-nivel').value
        };
        const url = id ? '/api/routines/' + id : '/api/gyms/' + gymId + '/routines';
        const method = id ? 'PUT' : 'POST';

        saveBtn.disabled = true;
        fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
            .then(res => res.json().then(data => ({ status: res.status, body: data })))
            .then(response => {
                saveBtn.disabled = false;
                if (response.status === 200) {
                    modal.hide();
                    loadRoutines(gymId);
                } else {
                    alertBox.textContent = response.body.error || 'No se pudo guardar la rutina.';
                    alertBox.classList.remove('d-none');
                }
            })
            .catch(() => {
                saveBtn.disabled = false;
                alertBox.textContent = 'Error de conexión con el servidor.';
                alertBox.classList.remove('d-none');
            });
    });

    const initialGymId = localStorage.getItem('gymId');
    document.getElementById('routine-new-btn').disabled = !initialGymId;
    loadRoutines(initialGymId);
}

function loadRoutines(gymId) {
    const container = document.getElementById('routines-container');
    if (!gymId) {
        container.innerHTML = avisoSinGimnasio('Configura primero "Mi Gimnasio" para poder crear rutinas.', 'bx-dumbbell');
        return;
    }
    fetch('/api/gyms/' + gymId + '/routines')
        .then(res => res.json())
        .then(routines => {
            if (!routines.length) {
                container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-dumbbell fs-1 d-block mb-2 opacity-50"></i>Aún no has creado rutinas. Crea la primera con "Nueva rutina".</div>';
                return;
            }
            const groups = {};
            routines.forEach(r => {
                const cat = r.categoria || 'Sin categoría';
                (groups[cat] = groups[cat] || []).push(r);
            });
            container.innerHTML = Object.keys(groups).sort().map(cat => `
                <h6 class="text-uppercase fw-bold text-body-secondary small mb-3 mt-2" style="letter-spacing:0.06em">${cat}</h6>
                <div class="row g-3 mb-4">
                    ${groups[cat].map(r => `
                        <div class="col-md-4">
                            <div class="dash-empty text-start h-100 d-flex flex-column">
                                <div class="icon-circle icon-brand rounded-circle d-flex align-items-center justify-content-center mb-3" style="width:54px;height:54px">
                                    <i class="bx ${iconForCategory(r.categoria)} fs-3"></i>
                                </div>
                                <h4 class="fw-bold fs-6">${escapeHtml(r.nombre)}</h4>
                                <div class="d-flex flex-wrap gap-1 mb-2">
                                    ${r.dia ? '<span class="badge rounded-pill bg-primary bg-opacity-10 text-primary"><i class="bx bx-calendar"></i> ' + escapeHtml(r.dia) + '</span>' : ''}
                                    ${r.nivel ? '<span class="badge rounded-pill bg-secondary bg-opacity-10 text-body-secondary">' + escapeHtml(r.nivel) + '</span>' : ''}
                                    ${r.duracion ? '<span class="badge rounded-pill bg-secondary bg-opacity-10 text-body-secondary"><i class="bx bx-time-five"></i> ' + escapeHtml(r.duracion) + '</span>' : ''}
                                </div>
                                ${r.coach ? '<small class="text-body-secondary d-block mb-1"><i class="bx bx-user-voice"></i> Coach ' + escapeHtml(sinPrefijoCoach(r.coach)) + '</small>' : ''}
                                <p class="text-body-secondary small mb-3">${escapeHtml(r.descripcion || (r.ejercicios || []).join(', '))}</p>
                                <div class="mt-auto d-flex gap-2">
                                    <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-edit-routine='${JSON.stringify(r).replace(/'/g, "&#39;")}'>Editar</button>
                                    <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-delete-routine="${r.id}">Eliminar</button>
                                </div>
                            </div>
                        </div>
                    `).join('')}
                </div>
            `).join('');
        });
}
