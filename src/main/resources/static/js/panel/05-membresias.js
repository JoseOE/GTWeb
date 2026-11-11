/* Panel del dueño (bienvenido.html): Membresías y Pagos. */

/* ═══════════════════════════════════════
   MEMBRESÍAS Y PAGOS — /api/gyms/{id}/members/{uid}/payments
═══════════════════════════════════════ */
function initBilling() {
    const modal = new bootstrap.Modal(document.getElementById('paymentModal'));
    const form = document.getElementById('payment-form');
    const alertBox = document.getElementById('payment-alert');
    const saveBtn = document.getElementById('payment-save-btn');
    const fechaInput = document.getElementById('payment-fecha');
    const preview = document.getElementById('payment-preview');
    const planSelect = document.getElementById('payment-plan');

    // Le enseña al dueño hasta cuándo quedará cubierto ANTES de guardar,
    // porque el corte respeta el día de pago original, no la fecha capturada.
    function actualizarPreview() {
        if (!fechaInput.value) { preview.textContent = ''; return; }
        const plan = planesCache.find(p => p.id === planSelect.value);
        const unidad = plan ? plan.duracionUnidad : 'mes';
        const cantidad = plan ? plan.duracionCantidad : 1;
        const hasta = new Date(fechaInput.value + 'T00:00:00');
        if (unidad === 'dia') hasta.setDate(hasta.getDate() + cantidad);
        else if (unidad === 'semana') hasta.setDate(hasta.getDate() + 7 * cantidad);
        else hasta.setMonth(hasta.getMonth() + cantidad);
        preview.innerHTML = '<i class="bx bx-info-circle"></i> Con este pago la membresía quedará cubierta aproximadamente hasta el <strong>'
            + hasta.toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' })
            + '</strong>. Si el usuario ya tenía saldo, se le suma.';
    }
    fechaInput.addEventListener('change', actualizarPreview);
    planSelect.addEventListener('change', () => {
        const plan = planesCache.find(p => p.id === planSelect.value);
        const cuota = document.getElementById('gym-cuota').value;
        document.getElementById('payment-monto').value = plan ? plan.precio : (cuota || '');
        actualizarPreview();
    });

    // Solo los planes que extienden la membresía; la inscripción se cobra en la tienda o el mostrador.
    function llenarPlanes() {
        planSelect.innerHTML = '<option value="">Sin plan (cubre 1 mes)</option>'
            + planesCache.filter(p => !p.pagoUnico).map(p =>
                '<option value="' + escapeHtml(p.id) + '">' + escapeHtml(p.nombre) + ' · ' + escapeHtml(p.duracionTexto)
                + ' · $' + Number(p.precio).toFixed(2) + (p.activo ? '' : ' (oculto)') + '</option>').join('');
    }

    document.body.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-pay-user]');
        if (!btn) return;
        form.reset();
        alertBox.classList.add('d-none');
        document.getElementById('payment-user-id').value = btn.dataset.payUser;
        document.getElementById('payment-user-label').textContent = 'Pago de ' + btn.dataset.payName;
        fechaInput.value = fechaMx(new Date());
        llenarPlanes();
        const cuota = document.getElementById('gym-cuota').value;
        if (cuota) document.getElementById('payment-monto').value = cuota;
        actualizarPreview();
        modal.show();
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const gymId = localStorage.getItem('gymId');
        const userId = document.getElementById('payment-user-id').value;
        saveBtn.disabled = true;
        fetch('/api/gyms/' + gymId + '/members/' + userId + '/payments', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                monto: document.getElementById('payment-monto').value ? parseFloat(document.getElementById('payment-monto').value) : null,
                metodo: document.getElementById('payment-metodo').value,
                fechaPago: fechaInput.value,
                nota: document.getElementById('payment-nota').value.trim(),
                planId: planSelect.value || null
            })
        })
        .then(res => res.json().then(data => ({ status: res.status, body: data })))
        .then(response => {
            saveBtn.disabled = false;
            if (response.status === 200) {
                modal.hide();
                loadBilling(gymId);
                loadMembers(gymId);
            } else {
                alertBox.textContent = response.body.error || 'No se pudo registrar el pago.';
                alertBox.classList.remove('d-none');
            }
        })
        .catch(() => {
            saveBtn.disabled = false;
            alertBox.textContent = 'Error de conexión con el servidor.';
            alertBox.classList.remove('d-none');
        });
    });

    document.getElementById('payments-tbody').addEventListener('click', (e) => {
        const boton = e.target.closest('[data-recibo-pago]');
        if (!boton) return;
        // fetch y no un enlace: el recibo exige el encabezado X-User-Id.
        boton.disabled = true;
        fetch('/api/recibos/pagos/' + encodeURIComponent(boton.dataset.reciboPago) + '.pdf', { headers: { 'X-User-Id': localStorage.getItem('userId') || '' } })
            .then(res => res.ok ? res.blob() : Promise.reject(new Error('No se pudo generar el recibo.')))
            .then(blob => {
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'recibo-membresia.pdf';
                document.body.appendChild(a);
                a.click();
                a.remove();
                setTimeout(() => URL.revokeObjectURL(url), 10000);
            })
            .catch(err => alert(err.message))
            .finally(() => { boton.disabled = false; });
    });

    const gymId = localStorage.getItem('gymId');
    if (gymId) loadBilling(gymId);
    // Los pagos de la tienda y del mostrador llegan solos: al abrir la
    // pestaña se vuelve a pedir todo.
    document.addEventListener('panel:mostrado', (e) => {
        if (e.detail === 'membresias') loadBilling(localStorage.getItem('gymId'));
    });
}

// Traduce los días restantes a algo que el dueño pueda leer de un vistazo.
function estadoDePago(m) {
    if (m.diasParaVencer == null) {
        return { texto: 'Sin pagos', clase: 'bg-secondary bg-opacity-10 text-body-secondary', icono: 'bx-minus-circle', orden: 2 };
    }
    if (m.diasParaVencer < 0) {
        return { texto: 'Vencida hace ' + Math.abs(m.diasParaVencer) + ' d', clase: 'bg-danger bg-opacity-10 text-danger', icono: 'bx-error-circle', orden: 0 };
    }
    if (m.diasParaVencer <= 5) {
        return { texto: 'Vence en ' + m.diasParaVencer + ' d', clase: 'bg-warning bg-opacity-10 text-warning', icono: 'bx-time-five', orden: 1 };
    }
    return { texto: 'Al corriente', clase: 'bg-success bg-opacity-10 text-success', icono: 'bx-check-circle', orden: 3 };
}

// "2026-11-03" → "3 nov 2026": las fechas de la base, en español.
function fechaCorta(iso) {
    if (!iso) return '';
    const d = new Date(String(iso).slice(0, 10) + 'T12:00:00Z');
    return isNaN(d) ? String(iso) : d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}

function loadBilling(gymId) {
    const tbody = document.getElementById('billing-tbody');
    const kpis = document.getElementById('billing-kpis');
    if (!gymId) return;
    cargarPagosRecientes(gymId);

    fetch('/api/gyms/' + gymId + '/members')
        .then(res => res.json())
        // Las solicitudes pendientes todavía no son miembros: no cuentan en la cobranza.
        .then(todos => todos.filter(m => m.membershipStatus !== 'pending'))
        .then(members => {
            const vencidos = members.filter(m => m.diasParaVencer != null && m.diasParaVencer < 0).length;
            const porVencer = members.filter(m => m.diasParaVencer != null && m.diasParaVencer >= 0 && m.diasParaVencer <= 5).length;
            const alCorriente = members.filter(m => m.diasParaVencer != null && m.diasParaVencer > 5).length;
            const sinPagos = members.filter(m => m.diasParaVencer == null).length;

            kpis.innerHTML = [
                { n: vencidos, l: 'Vencidas', i: 'bx-error-circle', d: 'Hay que cobrarles', alerta: true },
                { n: porVencer, l: 'Vencen pronto', i: 'bx-time-five', d: 'En los próximos 5 días' },
                { n: alCorriente, l: 'Al corriente', i: 'bx-check-circle', d: 'Con más de 5 días' },
                { n: sinPagos, l: 'Sin pagos registrados', i: 'bx-minus-circle', d: 'Falta su primer pago' }
            ].map(k => `
                <div class="col-6 col-lg-3">
                    <div class="kpi-tile h-100${k.alerta && k.n ? ' es-alerta' : ''}">
                        <div class="kpi-cabeza"><span class="kpi-titulo">${k.l}</span><i class="bx ${k.i}" aria-hidden="true"></i></div>
                        <div class="kpi-value">${k.n}</div>
                        <small class="kpi-detalle">${k.d}</small>
                    </div>
                </div>`).join('');

            if (!members.length) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-body-secondary py-5 px-4">Todavía no tienes usuarios.</td></tr>';
                return;
            }

            // Los problemas primero: vencidos, luego por vencer.
            const ordenados = [...members].sort((a, b) => estadoDePago(a).orden - estadoDePago(b).orden);
            tbody.innerHTML = ordenados.map(m => {
                const est = estadoDePago(m);
                return `
                <tr>
                    <td class="ps-4"><div class="persona">${avatar(m.nombre)}<div class="persona-texto"><div class="fw-semibold">${escapeHtml(m.nombre)}</div><div class="text-body-secondary small">${escapeHtml(m.email)}</div></div></div></td>
                    <td class="text-body-secondary">${m.diaDePago ? 'Día ' + m.diaDePago : '—'}</td>
                    <td class="text-body-secondary">${m.fechaProximoPago ? escapeHtml(fechaCorta(m.fechaProximoPago)) : '—'}</td>
                    <td><span class="badge rounded-pill ${est.clase} px-3 py-2"><i class="bx ${est.icono}"></i> ${est.texto}</span></td>
                    <td class="pe-4 text-end">
                        <button type="button" class="btn btn-primary btn-sm rounded-pill px-3" data-pay-user="${m.id}" data-pay-name="${escapeHtml(m.nombre)}">
                            <i class='bx bx-dollar'></i> Registrar pago
                        </button>
                    </td>
                </tr>`;
            }).join('');
        });
}

// "Últimos pagos" y lo cobrado en el mes salen del historial del gimnasio
// (dos consultas, sin importar cuántos miembros haya).
function cargarPagosRecientes(gymId) {
    const paymentsBody = document.getElementById('payments-tbody');
    const delMes = document.getElementById('payments-mes');
    apiTienda('/api/gyms/' + gymId + '/payments?limite=12')
        .then(pagos => {
            paymentsBody.innerHTML = pagos.length
                ? pagos.map(p => `
                    <tr>
                        <td class="ps-4 fw-semibold">${escapeHtml(p.nombre || '—')}</td>
                        <td class="text-body-secondary">${escapeHtml(fechaCorta(p.fechaPago))}</td>
                        <td class="fw-semibold">${p.monto != null ? '$' + Number(p.monto).toFixed(2) : '—'}</td>
                        <td class="text-body-secondary">${escapeHtml(p.metodo || '—')}${p.plan ? '<div class="small">' + escapeHtml(p.plan) + '</div>' : ''}</td>
                        <td class="text-body-secondary">${p.cubreHasta ? escapeHtml(fechaCorta(p.cubreHasta)) : '—'}${p.reembolsadoEn ? '<div><span class="badge rounded-pill bg-danger-subtle text-danger-emphasis">Reembolsado</span></div>' : ''}</td>
                        <td class="pe-4 text-end">
                            <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-recibo-pago="${escapeHtml(p.id)}" title="Descargar el recibo en PDF">
                                <i class='bx bx-receipt'></i> Recibo
                            </button>
                        </td>
                    </tr>`).join('')
                : '<tr><td colspan="6" class="text-center text-body-secondary py-4 px-4">Aún no registras pagos.</td></tr>';
        })
        .catch(err => {
            paymentsBody.innerHTML = '<tr><td colspan="6" class="text-center text-body-secondary py-4 px-4">' + escapeHtml(err.message) + '</td></tr>';
        });

    // Todas las membresías cobradas en el mes: a mano, en la tienda o en el mostrador.
    apiTienda('/api/gyms/' + gymId + '/payments?desde=' + fechaMx(new Date()).slice(0, 8) + '01&limite=500')
        .then(pagos => {
            const vigentes = pagos.filter(p => !p.reembolsadoEn);
            const total = vigentes.reduce((s, p) => s + (Number(p.monto) || 0), 0);
            delMes.textContent = 'Este mes: ' + dinero(total) + ' en ' + vigentes.length
                + (vigentes.length === 1 ? ' pago de membresía.' : ' pagos de membresía.');
        })
        .catch(() => { delMes.textContent = ''; });
}
