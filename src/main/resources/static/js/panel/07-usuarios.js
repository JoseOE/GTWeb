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

    document.getElementById('members-tbody').addEventListener('click', (e) => {
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

function loadMembers(gymId) {
    const tbody = document.getElementById('members-tbody');
    const countLabel = document.getElementById('members-count');
    if (!gymId) return;
    fetch('/api/gyms/' + gymId + '/members')
        .then(res => res.json())
        .then(members => {
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
                active: {
                    badge: '<span class="badge rounded-pill bg-success bg-opacity-10 text-success px-3 py-2"><i class="bx bx-check-circle"></i> Activo</span>',
                    acciones: (id) =>
                        '<button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-member-action="inactive" data-user-id="' + id + '">Dar de baja</button>'
                },
                inactive: {
                    badge: '<span class="badge rounded-pill bg-danger bg-opacity-10 text-danger px-3 py-2"><i class="bx bx-block"></i> Dado de baja</span>',
                    acciones: (id) =>
                        '<button type="button" class="btn btn-outline-success btn-sm rounded-pill px-3" data-member-action="active" data-user-id="' + id + '">Dar de alta</button>'
                        + ' <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-member-action="remove" data-user-id="' + id + '">Quitar</button>'
                }
            };

            tbody.innerHTML = members.map(m => {
                const estado = ESTADOS[m.membershipStatus] || ESTADOS.active;
                // Una solicitud todavía no es miembro: ni estado de pago ni botón de cobro.
                const esSolicitud = m.membershipStatus === 'pending';
                const pago = estadoDePago(m);
                return `
                <tr${esSolicitud ? ' class="table-warning bg-opacity-10"' : ''}>
                    <td class="ps-4 fw-semibold">${escapeHtml(m.nombre)}</td>
                    <td class="text-body-secondary">${escapeHtml(m.email)}</td>
                    <td>${estado.badge}</td>
                    <td>
                        ${esSolicitud ? '<span class="text-body-secondary small">Al aprobarla</span>' : `
                        <span class="badge rounded-pill ${pago.clase} px-3 py-2"><i class="bx ${pago.icono}"></i> ${pago.texto}</span>
                        ${m.fechaProximoPago ? '<div class="text-body-secondary small mt-1">Vence el ' + escapeHtml(fechaCorta(m.fechaProximoPago)) + '</div>' : ''}`}
                    </td>
                    <td class="pe-4 text-end">
                        ${esSolicitud ? '' : `<button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3 mb-1" data-pay-user="${m.id}" data-pay-name="${escapeHtml(m.nombre)}">
                            <i class='bx bx-dollar'></i> Pago
                        </button>`}
                        ${estado.acciones(m.id)}
                    </td>
                </tr>`;
            }).join('');
        });
}
