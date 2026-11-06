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
            repeticiones: r.querySelector('.ex-reps').value.trim()
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

    const gymId = localStorage.getItem('gymId');
    if (gymId) {
        document.getElementById('machine-new-btn').disabled = false;
        document.getElementById('machine-import-btn').disabled = false;
    }
    // Sin gimnasio, loadMachines pinta el aviso para configurarlo.
    loadMachines(gymId);
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
                container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-cog fs-1 d-block mb-2 opacity-50"></i>Aún no registras máquinas. Usa "Importar catálogo base" para empezar rápido y luego ajústalas.</div>';
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
