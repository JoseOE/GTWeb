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

function initRoutines() {
    const modalEl = document.getElementById('routineModal');
    const modal = new bootstrap.Modal(modalEl);
    const form = document.getElementById('routine-form');
    const alertBox = document.getElementById('routine-alert');
    const saveBtn = document.getElementById('routine-save-btn');

    document.getElementById('routine-new-btn').addEventListener('click', () => {
        form.reset();
        document.getElementById('routine-id').value = '';
        document.getElementById('routineModalLabel').textContent = 'Nueva rutina';
        alertBox.classList.add('d-none');
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
            document.getElementById('routine-ejercicios').value = (r.ejercicios || []).join('\n');
            document.getElementById('routine-dia').value = r.dia || '';
            document.getElementById('routine-coach').value = r.coach || '';
            document.getElementById('routine-nivel').value = r.nivel || '';
            document.getElementById('routineModalLabel').textContent = 'Editar rutina';
            alertBox.classList.add('d-none');
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
        const payload = {
            nombre: document.getElementById('routine-nombre').value.trim(),
            categoria: document.getElementById('routine-categoria').value.trim(),
            duracion: document.getElementById('routine-duracion').value.trim(),
            descripcion: document.getElementById('routine-descripcion').value.trim(),
            ejercicios: document.getElementById('routine-ejercicios').value
                .split('\n').map(s => s.trim()).filter(Boolean),
            dia: document.getElementById('routine-dia').value,
            coach: document.getElementById('routine-coach').value.trim(),
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
                                ${r.coach ? '<small class="text-body-secondary d-block mb-1"><i class="bx bx-user-voice"></i> Coach ' + escapeHtml(r.coach) + '</small>' : ''}
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
