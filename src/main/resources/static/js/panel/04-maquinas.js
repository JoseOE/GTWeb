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

    // Una fila por ejercicio. El id y las fotos viajan ocultos para no perder
    // la referencia de ejercicios que ya existían al editar la máquina.
    function addExerciseRow(ej) {
        ej = ej || {};
        const row = document.createElement('div');
        row.className = 'row g-2 align-items-end exercise-row';
        const fotos = Array.isArray(ej.imagenes) ? ej.imagenes : [];
        row.innerHTML = `
            <input type="hidden" class="ex-id" value="${escapeHtml(ej.id || '')}">
            <input type="hidden" class="ex-imagenes" value="${escapeHtml(JSON.stringify(fotos))}">
            <div class="col-md-4 d-flex align-items-center gap-2">
                ${fotos.length ? `<img src="${escapeHtml(fotos[0])}" alt="" class="ejercicio-foto ejercicio-foto-sm" loading="lazy">` : ''}
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

    // Una máquina que no está en el catálogo: el formulario a mano (desde
    // "¿No la encuentras? Crear una a mano" en Agregar máquina).
    function nuevaAMano() {
        form.reset();
        rows.innerHTML = '';
        document.getElementById('machine-id').value = '';
        document.getElementById('machine-cantidad').value = 1;
        document.getElementById('machineModalLabel').textContent = 'Nueva máquina';
        alertBox.classList.add('d-none');
        addExerciseRow();
        modal.show();
    }

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
            instrucciones: r.querySelector('.ex-instrucciones').value.trim() || null,
            imagenes: JSON.parse(r.querySelector('.ex-imagenes').value || '[]')
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

    initAgregarMaquina(nuevaAMano);

    const gymId = localStorage.getItem('gymId');
    if (gymId) document.getElementById('machine-catalogo-btn').disabled = false;
    // Sin gimnasio, loadMachines pinta el aviso para configurarlo.
    loadMachines(gymId);
}

/* ─── Agregar máquina — /api/gyms/{id}/machines/catalogo ───
   Tres pasos sobre el catálogo de máquinas (todas con sus ilustraciones):
     1. El músculo (Pecho, Tríceps, Hombro…) o buscar por nombre.
     2. La máquina: todas las que tienen algún ejercicio para ese músculo.
     3. Los ejercicios que sí se hacen en ella, con casillas: vienen marcados
        los de ese músculo y se pueden marcar los demás.
   La máquina se agrega (o se completa si ya la tenía) y sus ejercicios son
   los que luego se eligen en las rutinas. Si no está en el catálogo,
   "Crear una a mano" abre el formulario de siempre. */

const MUSCULOS = ['Pecho', 'Espalda', 'Hombro', 'Bíceps', 'Tríceps', 'Pierna', 'Glúteo', 'Core', 'Cardio'];

// Las fotos de cómo se hace un ejercicio. Las ilustraciones traen 2 cuadros
// (inicio y final), 3 (con uno intermedio) o 1 (planchas y estiramientos):
// se muestran el primero y el último.
function fotosEjercicio(e) {
    const todas = e.imagenes || [];
    const fotos = todas.length > 2 ? [todas[0], todas[todas.length - 1]] : todas;
    if (!fotos.length) return '';
    const pasos = fotos.length === 1 ? ['Posición'] : ['Inicio', 'Final'];
    return `<span class="ejercicio-fotos">${fotos.map((src, i) => `
        <figure class="mb-0">
            <img src="${escapeHtml(src)}" alt="${escapeHtml(e.nombre)}: ${pasos[i].toLowerCase()}" class="ejercicio-foto" loading="lazy" width="120" height="96">
            <figcaption>${pasos[i]}</figcaption>
        </figure>`).join('')}</span>`;
}

const sinAcento = t => String(t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();

function initAgregarMaquina(crearAMano) {
    const modalEl = document.getElementById('catalogoModal');
    const modal = new bootstrap.Modal(modalEl);
    const contenido = document.getElementById('agregar-contenido');
    const pie = document.getElementById('agregar-pie');
    const pasos = document.getElementById('agregar-pasos');
    const alerta = document.getElementById('catalogo-alert');
    let datos = null;
    let paso = 1;
    let musculo = null;     // null: vino de la búsqueda, se marcan todos
    let maquina = null;     // la máquina del catálogo elegida
    let busqueda = '';

    const avisar = (texto, tipo) => {
        alerta.className = 'alert py-2 small alert-' + tipo;
        alerta.textContent = texto;
    };
    const ocultarAviso = () => { alerta.className = 'alert d-none py-2 small'; };
    const suave = () => matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
    const loTiene = m => new Set((m.ejerciciosQueTienes || []).map(sinAcento));
    const ejerciciosDe = (m, mu) => m.ejercicios.filter(e => e.musculo === mu);
    const linkAMano = '<button type="button" class="btn btn-link btn-sm px-0" data-a-mano>¿No la encuentras? Crear una a mano</button>';

    function cargar() {
        const gymId = localStorage.getItem('gymId');
        contenido.innerHTML = '<div class="text-center text-body-secondary py-5"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Cargando máquinas…</div>';
        pie.innerHTML = '';
        return fetch('/api/gyms/' + gymId + '/machines/catalogo')
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(d => { datos = d; pintar(); })
            .catch(() => { contenido.innerHTML = '<div class="text-danger small">No se pudieron cargar las máquinas. Cierra y vuelve a intentarlo.</div>'; });
    }

    function irA(n) {
        paso = n;
        ocultarAviso();
        pintar();
        modalEl.querySelector('.modal-body').scrollTo({ top: 0, behavior: suave() });
    }

    function marcarPasos() {
        pasos.querySelectorAll('li').forEach(li => {
            const n = Number(li.dataset.paso);
            li.classList.toggle('es-actual', n === paso);
            li.classList.toggle('es-hecho', n < paso);
            if (n === paso) li.setAttribute('aria-current', 'step'); else li.removeAttribute('aria-current');
        });
    }

    function pintar() {
        marcarPasos();
        if (paso === 1) pintarMusculos();
        else if (paso === 2) pintarMaquinas();
        else pintarEjercicios();
    }

    // La tarjeta de una máquina: su ilustración, nombre y cuántos ejercicios tiene para el músculo.
    function tarjetaMaquina(m, mu) {
        const tiene = loTiene(m);
        const completa = m.machineId && m.ejercicios.every(e => tiene.has(sinAcento(e.nombre)));
        const delMusculo = mu ? ejerciciosDe(m, mu) : m.ejercicios;
        const portada = (delMusculo.find(e => (e.imagenes || []).length) || m.ejercicios.find(e => (e.imagenes || []).length));
        return `
            <div class="col-sm-6 col-lg-4">
                <button type="button" class="agregar-maquina" data-maquina="${escapeHtml(m.nombre)}">
                    ${portada ? `<img src="${escapeHtml(portada.imagenes[0])}" alt="" class="agregar-portada" loading="lazy">` : '<span class="agregar-portada ejercicio-sin-foto" aria-hidden="true"><i class="bx bx-dumbbell"></i></span>'}
                    <span class="agregar-maquina-texto">
                        <span class="fw-bold d-block">${escapeHtml(m.nombre)}</span>
                        <span class="small text-body-secondary d-block">${mu
                            ? delMusculo.length + ' ' + (delMusculo.length === 1 ? 'ejercicio' : 'ejercicios') + ' para ' + escapeHtml(mu) + (m.ejercicios.length > delMusculo.length ? ' · ' + m.ejercicios.length + ' en total' : '')
                            : m.ejercicios.length + ' ' + (m.ejercicios.length === 1 ? 'ejercicio' : 'ejercicios')}</span>
                        ${m.machineId ? '<span class="badge rounded-pill bg-success-subtle text-success-emphasis mt-2">' + (completa ? 'Ya la tienes' : 'La tienes · faltan ejercicios') + '</span>' : ''}
                    </span>
                </button>
            </div>`;
    }

    /* Paso 1: el músculo (o buscar por nombre). */
    function pintarMusculos() {
        const q = sinAcento(busqueda);
        const resultados = q ? datos.maquinas.filter(m => sinAcento(m.nombre + ' ' + m.ejercicios.map(e => e.nombre + ' ' + e.musculo).join(' ')).includes(q)) : [];
        contenido.innerHTML = `
            <p class="text-body-secondary mb-3">¿Qué músculo se trabaja en la máquina que quieres agregar?</p>
            <div class="agregar-musculos" role="list">
                ${MUSCULOS.map(mu => {
                    const maquinas = datos.maquinas.filter(m => ejerciciosDe(m, mu).length);
                    const ej = maquinas.flatMap(m => ejerciciosDe(m, mu)).find(e => (e.imagenes || []).length);
                    return `<button type="button" class="agregar-musculo" data-musculo="${escapeHtml(mu)}" role="listitem" ${maquinas.length ? '' : 'disabled'}>
                        ${ej ? `<img src="${escapeHtml(ej.imagenes[0])}" alt="" loading="lazy">` : '<span class="ejercicio-sin-foto" aria-hidden="true"><i class="bx bx-dumbbell"></i></span>'}
                        <span class="agregar-musculo-nombre">${escapeHtml(mu)}</span>
                        <span class="agregar-musculo-conteo">${maquinas.length} ${maquinas.length === 1 ? 'máquina' : 'máquinas'}</span>
                    </button>`;
                }).join('')}
            </div>
            <div class="mt-4">
                <label for="agregar-buscar" class="form-label small fw-semibold">O búscala por nombre</label>
                <input type="search" class="form-control" id="agregar-buscar" placeholder="Ej. poleas, prensa, Smith, sentadilla" value="${escapeHtml(busqueda)}" autocomplete="off">
            </div>
            ${q ? (resultados.length
                ? '<div class="row g-3 mt-1">' + resultados.map(m => tarjetaMaquina(m, null)).join('') + '</div>'
                : '<p class="small text-body-secondary mt-3 mb-0">No hay máquinas con ese nombre. Puedes crearla a mano.</p>') : ''}`;
        pie.innerHTML = linkAMano + '<span></span>';
        const campo = document.getElementById('agregar-buscar');
        if (busqueda) { campo.focus(); campo.setSelectionRange(campo.value.length, campo.value.length); }
    }

    /* Paso 2: las máquinas que trabajan ese músculo. */
    function pintarMaquinas() {
        const maquinas = datos.maquinas
            .filter(m => ejerciciosDe(m, musculo).length)
            .sort((a, b) => ejerciciosDe(b, musculo).length - ejerciciosDe(a, musculo).length || a.nombre.localeCompare(b.nombre));
        contenido.innerHTML = `
            <p class="text-body-secondary mb-3">Máquinas con ejercicios para <strong>${escapeHtml(musculo)}</strong>. Elige la que tienes en tu gimnasio.</p>
            <div class="row g-3">${maquinas.map(m => tarjetaMaquina(m, musculo)).join('')}</div>`;
        pie.innerHTML = `<button type="button" class="btn btn-outline-secondary rounded-pill px-3" data-atras><i class="bx bx-chevron-left" aria-hidden="true"></i> Músculo</button>${linkAMano}`;
    }

    /* Paso 3: los ejercicios, con casillas. */
    function pintarEjercicios() {
        const m = datos.maquinas.find(x => x.nombre === maquina);
        const tiene = loTiene(m);
        const musculos = [...new Set(m.ejercicios.map(e => e.musculo))];
        contenido.innerHTML = `
            <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
                <div class="min-w-0">
                    <h6 class="fw-bold mb-1 fs-5">${escapeHtml(m.nombre)}</h6>
                    <p class="text-body-secondary small mb-2">${escapeHtml(m.descripcion || '')}</p>
                    <div class="d-flex flex-wrap gap-1">${musculos.map(mu => '<span class="badge rounded-pill bg-body-secondary text-body-secondary">' + escapeHtml(mu) + '</span>').join('')}</div>
                </div>
                <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-todos></button>
            </div>
            <p class="small text-body-secondary mb-2">Marca los ejercicios que sí se hacen en tu gimnasio${musculo ? '; ya vienen marcados los de ' + escapeHtml(musculo) : ''}.</p>
            <div class="catalogo-ejercicios small">
                ${m.ejercicios.map((e, i) => {
                    const ya = tiene.has(sinAcento(e.nombre));
                    const marcado = ya || !musculo || e.musculo === musculo;
                    const id = 'agregar-ej-' + i;
                    return `<div class="form-check catalogo-ejercicio${ya ? ' es-propio' : ''}">
                        <input class="form-check-input" type="checkbox" id="${id}" data-ej="${escapeHtml(e.nombre)}" ${marcado ? 'checked' : ''} ${ya ? 'disabled' : ''}>
                        <label class="form-check-label d-block" for="${id}">
                            <span class="fw-semibold">${escapeHtml(e.nombre)}</span>
                            <span class="text-body-secondary"> · ${escapeHtml(e.musculo)} · ${e.series} × ${escapeHtml(e.repeticiones)}${ya ? ' · ya la tienes' : ''}</span>
                            ${fotosEjercicio(e)}
                            <span class="d-block text-body-secondary">${escapeHtml(e.instrucciones || '')}</span>
                        </label>
                    </div>`;
                }).join('')}
            </div>`;
        pie.innerHTML = `<button type="button" class="btn btn-outline-secondary rounded-pill px-3" data-atras><i class="bx bx-chevron-left" aria-hidden="true"></i> ${musculo ? 'Máquinas' : 'Buscar'}</button>
            <button type="button" class="btn btn-primary rounded-pill px-4" data-agregar></button>`;
        actualizarConteo();
    }

    function actualizarConteo() {
        const libres = [...contenido.querySelectorAll('input[data-ej]:not(:disabled)')];
        const marcados = libres.filter(i => i.checked).length;
        const boton = pie.querySelector('[data-agregar]');
        const todos = contenido.querySelector('[data-todos]');
        if (boton) {
            boton.disabled = marcados === 0;
            boton.innerHTML = '<i class="bx bx-plus" aria-hidden="true"></i> ' + (marcados
                ? 'Agregar ' + marcados + ' ' + (marcados === 1 ? 'ejercicio' : 'ejercicios')
                : (libres.length ? 'Marca al menos uno' : 'Ya tienes todos'));
        }
        if (todos) {
            todos.hidden = libres.length < 2;
            todos.textContent = marcados === libres.length ? 'Quitar todos' : 'Marcar todos';
        }
    }

    function agregar(boton) {
        const ejercicios = [...contenido.querySelectorAll('input[data-ej]:checked:not(:disabled)')].map(i => i.dataset.ej);
        if (!ejercicios.length) return;
        boton.disabled = true;
        boton.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Agregando…';
        const gymId = localStorage.getItem('gymId');
        fetch('/api/gyms/' + gymId + '/machines/catalogo', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nombre: maquina, ejercicios })
        })
            .then(res => res.json().then(body => ({ ok: res.ok, body })))
            .then(({ ok, body }) => {
                if (!ok) throw new Error(body.error || 'No se pudo agregar.');
                loadMachines(gymId);
                return fetch('/api/gyms/' + gymId + '/machines/catalogo').then(r => r.json()).then(d => {
                    datos = d;
                    paso = 3;
                    marcarPasos();
                    pasos.querySelectorAll('li').forEach(li => li.classList.add('es-hecho'));
                    contenido.innerHTML = `
                        <div class="agregar-listo" role="status">
                            <span class="agregar-listo-icono" aria-hidden="true"><i class="bx bx-check"></i></span>
                            <h6 class="fw-bold fs-5 mb-1">${escapeHtml(body.maquina.nombre)} ya está en tu gimnasio</h6>
                            <p class="text-body-secondary mb-0">Se ${body.agregados === 1 ? 'agregó 1 ejercicio' : 'agregaron ' + body.agregados + ' ejercicios'}. Ya puedes usarlos en tus rutinas.</p>
                        </div>`;
                    pie.innerHTML = `<button type="button" class="btn btn-outline-secondary rounded-pill px-3" data-otra><i class="bx bx-plus" aria-hidden="true"></i> Agregar otra máquina</button>
                        <button type="button" class="btn btn-primary rounded-pill px-4" data-bs-dismiss="modal">Listo</button>`;
                });
            })
            .catch(err => {
                avisar(err.message || 'Error de conexión con el servidor.', 'danger');
                actualizarConteo();
            });
    }

    contenido.addEventListener('click', e => {
        const mu = e.target.closest('[data-musculo]');
        if (mu) { musculo = mu.dataset.musculo; irA(2); return; }
        const maq = e.target.closest('[data-maquina]');
        if (maq) {
            maquina = maq.dataset.maquina;
            if (paso === 1) musculo = null;   // vino de la búsqueda
            irA(3);
            return;
        }
        if (e.target.closest('[data-todos]')) {
            const libres = [...contenido.querySelectorAll('input[data-ej]:not(:disabled)')];
            const todos = libres.every(i => i.checked);
            libres.forEach(i => { i.checked = !todos; });
            actualizarConteo();
        }
    });
    contenido.addEventListener('change', e => { if (e.target.matches('input[data-ej]')) actualizarConteo(); });
    contenido.addEventListener('input', e => {
        if (e.target.id !== 'agregar-buscar') return;
        busqueda = e.target.value;
        pintarMusculos();
    });

    pie.addEventListener('click', e => {
        if (e.target.closest('[data-atras]')) { irA(paso === 3 && musculo ? 2 : 1); return; }
        const boton = e.target.closest('[data-agregar]');
        if (boton) { agregar(boton); return; }
        if (e.target.closest('[data-otra]')) { musculo = null; maquina = null; busqueda = ''; irA(1); return; }
        if (e.target.closest('[data-a-mano]')) {
            modalEl.addEventListener('hidden.bs.modal', () => crearAMano(), { once: true });
            modal.hide();
        }
    });

    document.getElementById('machine-catalogo-btn').addEventListener('click', () => {
        paso = 1;
        musculo = null;
        maquina = null;
        busqueda = '';
        ocultarAviso();
        marcarPasos();
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
                container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-cog fs-1 d-block mb-2 opacity-50"></i>Aún no registras máquinas. Usa "Agregar máquina": eliges el músculo, la máquina y los ejercicios que se hacen en ella.</div>';
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
                                            <div class="maquina-ejercicio">
                                                ${(ej.imagenes || []).length
                                                    ? `<img src="${escapeHtml(ej.imagenes[0])}" alt="" class="ejercicio-foto ejercicio-foto-sm" loading="lazy">`
                                                    : '<span class="ejercicio-foto ejercicio-foto-sm ejercicio-sin-foto" aria-hidden="true"><i class="bx bx-dumbbell"></i></span>'}
                                                <span class="small flex-grow-1">${escapeHtml(ej.nombre)}
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
