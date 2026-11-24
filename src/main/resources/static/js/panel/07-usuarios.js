/* Panel del dueño (bienvenido.html): Mis Usuarios. */

/* ═══════════════════════════════════════
   MIS USUARIOS — conectado a /api/gyms/{id}/members
═══════════════════════════════════════ */
function initMembers() {
    const modalEl = document.getElementById('memberModal');
    const modal = new bootstrap.Modal(modalEl);
    const form = document.getElementById('member-form');
    const alertBox = document.getElementById('member-alert');
    const saveBtn = document.getElementById('member-save-btn');

    document.getElementById('member-new-btn').addEventListener('click', () => {
        form.reset();
        alertBox.classList.add('d-none');
        modal.show();
    });

    document.getElementById('simulacion-otra').addEventListener('click', () => {
        if (simulacionSocio) simularEntrada(simulacionSocio.id, simulacionSocio.nombre);
    });

    document.getElementById('members-tbody').addEventListener('click', (e) => {
        const simular = e.target.closest('[data-simular-user]');
        if (simular) {
            simularEntrada(simular.dataset.simularUser, simular.dataset.simularName);
            return;
        }
        const btn = e.target.closest('[data-member-action]');
        if (!btn) return;
        const gymId = localStorage.getItem('gymId');
        const action = btn.dataset.memberAction;
        const url = '/api/gyms/' + gymId + '/members/' + btn.dataset.userId;

        // "remove" desvincula del gimnasio; el resto solo cambia el estado.
        if (action === 'remove') {
            if (!confirm('Se desvinculará del gimnasio. Conserva su cuenta y podrá volver a pedir acceso con el código. ¿Continuar?')) return;
            btn.disabled = true;
            fetch(url, { method: 'DELETE' }).then(() => loadMembers(gymId));
            return;
        }

        btn.disabled = true;
        fetch(url, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: action })
        })
        .then(() => loadMembers(gymId));
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const gymId = localStorage.getItem('gymId');
        const payload = {
            nombre: document.getElementById('member-nombre').value.trim(),
            email: document.getElementById('member-email').value.trim()
        };
        saveBtn.disabled = true;
        fetch('/api/gyms/' + gymId + '/members', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(res => res.json().then(data => ({ status: res.status, body: data })))
        .then(response => {
            saveBtn.disabled = false;
            if (response.status === 200) {
                modal.hide();
                loadMembers(gymId);
            } else {
                alertBox.textContent = response.body.error || 'No se pudo dar de alta al usuario.';
                alertBox.classList.remove('d-none');
            }
        })
        .catch(() => {
            saveBtn.disabled = false;
            alertBox.textContent = 'Error de conexión con el servidor.';
            alertBox.classList.remove('d-none');
        });
    });

    const gymId = localStorage.getItem('gymId');
    if (gymId) {
        document.getElementById('member-new-btn').disabled = false;
        loadMembers(gymId);
    }
    // Las solicitudes llegan desde la app en cualquier momento: al abrir la
    // pestaña se vuelve a pedir la lista.
    document.addEventListener('panel:mostrado', (e) => {
        if (e.detail === 'usuarios') loadMembers(localStorage.getItem('gymId'));
    });
}

/* ─── Simular entrada (prueba, mientras se termina la puerta RFID) ───
   "Pasa la tarjeta" de un socio con las mismas reglas que el lector: con la
   membresía vencida se niega y le llega un push para renovar desde la app (al
   pagar, vuelve a pasar la tarjeta); al día, entra y el lector prende el verde.
   Se apaga en el servidor con ACCESO_SIMULACION=false. */
let simulacionAcceso = null;   // {activa, lector: {nombre, enLinea}} o null mientras se pregunta
let simulacionSocio = null;    // {id, nombre} de la última simulación, para "Simular otra vez"

function cargarSimulacion(gymId) {
    return fetch('/api/gyms/' + gymId + '/acceso/simulacion')
        .then(res => res.ok ? res.json() : { activa: false })
        .catch(() => ({ activa: false }))
        .then(s => {
            simulacionAcceso = s;
            const aviso = document.getElementById('members-simulacion');
            aviso.classList.toggle('d-none', !s.activa);
            if (s.activa) {
                document.getElementById('members-simulacion-texto').innerHTML = s.lector
                    ? '"Simular entrada" hace como si el socio pasara su tarjeta en <strong>' + escapeHtml(s.lector.nombre) + '</strong> ('
                        + (s.lector.enLinea ? 'conectado: el lector responde de verdad' : 'desconectado: solo queda registrado') + '). '
                        + 'Con la membresía vencida se niega y le llega un aviso a su teléfono para renovar; al pagar, vuelve a pasar su tarjeta para entrar.'
                    : 'Para simular entradas, primero agrega el lector de la puerta en Accesos RFID.';
            }
            return s;
        });
}

function botonSimular(m) {
    if (!simulacionAcceso || !simulacionAcceso.activa || !simulacionAcceso.lector) return '';
    const vencida = m.diasParaVencer != null && m.diasParaVencer < 0;
    // Va dentro del menú "⋯" de cada socio.
    return `<button type="button" class="dropdown-item ${vencida ? 'text-danger' : ''}"
                data-simular-user="${m.id}" data-simular-name="${escapeHtml(m.nombre)}" title="Prueba: como si pasara su tarjeta">
                <i class='bx bx-door-open me-2'></i>Simular entrada${vencida ? ' (vencida)' : ''}
            </button>`;
}

function simularEntrada(socioId, nombre) {
    const gymId = localStorage.getItem('gymId');
    const cuerpo = document.getElementById('simulacion-cuerpo');
    const modalEl = document.getElementById('simulacionModal');
    simulacionSocio = { id: socioId, nombre };
    document.getElementById('simulacionModalLabel').textContent = 'Simular entrada de ' + nombre;
    cuerpo.innerHTML = '<div class="py-4 text-body-secondary"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Pasando la tarjeta…</div>';
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
    apiTienda('/api/gyms/' + gymId + '/acceso/simular', { method: 'POST', body: JSON.stringify({ userId: socioId }) })
        .then(r => { cuerpo.innerHTML = vistaSimulacion(r); })
        .catch(err => {
            cuerpo.innerHTML = '<div class="py-3"><i class="bx bx-error-circle fs-1 text-danger d-block mb-2"></i>' + escapeHtml(err.message) + '</div>';
        });
}

function vistaSimulacion(r) {
    const lector = r.lector ? escapeHtml(r.lector.nombre) : 'el lector';
    const puerta = r.puertaAbierta
        ? '<i class="bx bx-wifi me-1"></i><strong>' + lector + '</strong> está abriendo la puerta.'
        : '<i class="bx bx-wifi-off me-1"></i><strong>' + lector + '</strong> no está conectado: la puerta no se movió, pero quedó registrado.';
    const tarjeta = (icono, color, titulo, texto, extra) => `
        <div class="py-2">
            <div class="d-inline-flex align-items-center justify-content-center rounded-circle bg-${color} bg-opacity-10 text-${color} mb-3" style="width:72px;height:72px">
                <i class="bx ${icono}" style="font-size:2.4rem" aria-hidden="true"></i>
            </div>
            <h5 class="fw-bold mb-2">${titulo}</h5>
            <p class="mb-2">${texto}</p>
            ${extra ? '<p class="small text-body-secondary mb-0">' + extra + '</p>' : ''}
        </div>`;
    const nombre = escapeHtml(r.nombre || '');
    if (r.resultado === 'ENTRADA') {
        return tarjeta('bx-door-open', 'success', 'Bienvenido, ' + nombre, 'Su membresía está al día: entrada registrada.', puerta);
    }
    if (r.resultado === 'SALIDA') {
        return tarjeta('bx-log-out', 'primary', 'Hasta luego, ' + nombre, 'Ya estaba dentro, así que este pase cuenta como su salida.', puerta);
    }
    if (r.resultado === 'NEGADO') {
        const aviso = r.avisoRenovar
            ? '<i class="bx bx-mobile-alt me-1"></i>Le llegó una notificación a su teléfono para renovar desde la app.'
            : r.alertaRepetida
                ? 'Ya tenía una alerta de hace menos de 2 minutos, así que no se le volvió a avisar.'
                : 'No tiene la app con las notificaciones activas, así que no le llegó el aviso; puede renovar desde la app.';
        return tarjeta('bx-block', 'danger', 'Acceso negado: ' + escapeHtml(r.motivo || 'sin permiso'), aviso,
            'Cuando pague desde el teléfono, vuelve a pasar su tarjeta (o "Simular entrada") y ya entra. También aparece en Accesos RFID como alerta.');
    }
    return tarjeta('bx-time-five', 'secondary', 'Espera un momento', escapeHtml(r.motivo || 'Muy pronto desde su último pase') + ': vuelve a intentar en 10 segundos.', '');
}

function loadMembers(gymId) {
    const tbody = document.getElementById('members-tbody');
    const countLabel = document.getElementById('members-count');
    if (!gymId) return;
    Promise.all([fetch('/api/gyms/' + gymId + '/members').then(res => res.json()), cargarSimulacion(gymId)])
        .then(([members]) => {
            resumenConteos.miembros = members.length;
            pintarPasos();
            const pendientes = members.filter(m => m.membershipStatus === 'pending').length;
            countLabel.textContent = members.length + (members.length === 1 ? ' usuario' : ' usuarios')
                + (pendientes ? ' · ' + pendientes + (pendientes === 1 ? ' solicitud por revisar' : ' solicitudes por revisar') : '');

            if (!members.length) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-body-secondary py-5 px-4"><i class="bx bx-group fs-1 d-block mb-2 opacity-50"></i>Todavía no hay usuarios. Comparte tu código de acceso, o usa "Agregar usuario" para crear la cuenta tú mismo.</td></tr>';
                return;
            }

            // Cada estado ofrece solo las acciones que tienen sentido desde ahí.
            const ESTADOS = {
                pending: {
                    badge: '<span class="badge rounded-pill bg-warning bg-opacity-10 text-warning px-3 py-2"><i class="bx bx-time-five"></i> Solicitud pendiente</span>',
                    acciones: (id) =>
                        '<button type="button" class="btn btn-success btn-sm rounded-pill px-3" data-member-action="active" data-user-id="' + id + '">Aprobar</button>'
                        + ' <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-member-action="remove" data-user-id="' + id + '">Rechazar</button>'
                },
                // Socios: "Pago" queda a la vista y lo demás en el menú "⋯",
                // así cada fila tiene una sola acción principal.
                active: {
                    badge: '<span class="badge rounded-pill bg-success bg-opacity-10 text-success px-3 py-2"><i class="bx bx-check-circle"></i> Activo</span>',
                    menu: (id) =>
                        '<li><button type="button" class="dropdown-item text-danger" data-member-action="inactive" data-user-id="' + id + '"><i class="bx bx-user-x me-2"></i>Dar de baja</button></li>'
                },
                inactive: {
                    badge: '<span class="badge rounded-pill bg-danger bg-opacity-10 text-danger px-3 py-2"><i class="bx bx-block"></i> Dado de baja</span>',
                    menu: (id) =>
                        '<li><button type="button" class="dropdown-item" data-member-action="active" data-user-id="' + id + '"><i class="bx bx-user-check me-2"></i>Dar de alta</button></li>'
                        + '<li><button type="button" class="dropdown-item text-danger" data-member-action="remove" data-user-id="' + id + '"><i class="bx bx-unlink me-2"></i>Quitar del gimnasio</button></li>'
                }
            };

            // El menú "⋯" de un socio: simular su entrada (si está activa la prueba) y cambiar su estado.
            const menuSocio = (m, estado) => {
                const simular = botonSimular(m);
                return `<div class="dropdown d-inline-block">
                    <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-2" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Más acciones para ${escapeHtml(m.nombre)}">
                        <i class="bx bx-dots-horizontal-rounded" aria-hidden="true"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                        ${simular ? '<li>' + simular + '</li><li><hr class="dropdown-divider"></li>' : ''}
                        ${estado.menu(m.id)}
                    </ul>
                </div>`;
            };

            tbody.innerHTML = members.map(m => {
                const estado = ESTADOS[m.membershipStatus] || ESTADOS.active;
                // Una solicitud todavía no es miembro: ni estado de pago ni botón de cobro.
                const esSolicitud = m.membershipStatus === 'pending';
                const pago = estadoDePago(m);
                return `
                <tr${esSolicitud ? ' class="table-warning bg-opacity-10"' : ''}>
                    <td class="ps-4"><div class="persona">${avatar(m.nombre)}<span class="fw-semibold">${escapeHtml(m.nombre)}</span></div></td>
                    <td class="text-body-secondary">${escapeHtml(m.email)}</td>
                    <td>${estado.badge}</td>
                    <td>
                        ${esSolicitud ? '<span class="text-body-secondary small">Al aprobarla</span>' : `
                        <span class="badge rounded-pill ${pago.clase} px-3 py-2"><i class="bx ${pago.icono}"></i> ${pago.texto}</span>
                        ${m.fechaProximoPago ? '<div class="text-body-secondary small mt-1">Vence el ' + escapeHtml(fechaCorta(m.fechaProximoPago)) + '</div>' : ''}`}
                    </td>
                    <td class="pe-4 text-end text-nowrap">
                        ${esSolicitud ? estado.acciones(m.id) : `<button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-pay-user="${m.id}" data-pay-name="${escapeHtml(m.nombre)}">
                            <i class='bx bx-dollar'></i> Pago
                        </button>
                        ${menuSocio(m, estado)}`}
                    </td>
                </tr>`;
            }).join('');
        });
}
