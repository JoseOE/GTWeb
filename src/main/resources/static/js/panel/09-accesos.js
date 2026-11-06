/* Panel del dueño (bienvenido.html): Accesos RFID. */

/* ═══════════════════════════════════════
   ACCESOS RFID — /api/gyms/{id}/acceso
   El lector manda cada tag al servidor; aquí se ve el resultado. Mientras la
   pestaña está abierta se actualiza cada 4 s (para atender las alertas
   mientras la persona sigue en la puerta); fuera de ella, cada 15 s, solo
   para el contador de alertas del menú.
═══════════════════════════════════════ */
function initAccesos() {
    const contenido = document.getElementById('accesos-contenido');
    const badgeNav = document.getElementById('accesos-nav-alertas');
    let datos = null;
    let llaveNueva = null;          // la llave de un lector recién creado: se muestra una vez
    let abierta = false;
    let cargando = false;

    const TIPOS = {
        ENTRADA: ['Entrada', 'bg-success-subtle text-success-emphasis', 'bx-log-in-circle'],
        SALIDA: ['Salida', 'bg-info-subtle text-info-emphasis', 'bx-log-out-circle'],
        NEGADO: ['Negado', 'bg-danger-subtle text-danger-emphasis', 'bx-block']
    };
    const ESTADOS = {
        PENDIENTE: ['Esperando tu decisión', 'bg-warning-subtle text-warning-emphasis'],
        APROBADA: ['Abriste la puerta', 'bg-success-subtle text-success-emphasis'],
        RECHAZADA: ['Negaste el acceso', 'bg-secondary-subtle text-secondary-emphasis'],
        EXPIRADA: ['Expiró', 'bg-secondary-subtle text-secondary-emphasis']
    };

    const hora = f => new Date(f).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const fechaHora = f => new Date(f).toLocaleDateString('es-MX', { day: 'numeric', month: 'short' }) + ' · ' + hora(f);
    const hace = f => {
        const s = Math.round((Date.now() - new Date(f).getTime()) / 1000);
        return s < 60 ? 'hace ' + Math.max(s, 0) + ' s' : s < 3600 ? 'hace ' + Math.round(s / 60) + ' min' : fechaHora(f);
    };
    const esHoy = f => new Date(f).toDateString() === new Date().toDateString();
    const opcionesSocios = () => datos.socios.map(s => `<option value="${escapeHtml(s.id)}">${escapeHtml(s.nombre)}</option>`).join('');

    function tarjetaKpi(valor, texto, sub, icono, color) {
        return `
            <div class="col-6 col-xl-3">
                <div class="kpi-tile h-100">
                    <div class="icon-circle ${color} rounded-circle d-flex align-items-center justify-content-center mb-3" style="width:42px;height:42px">
                        <i class='bx ${icono}' aria-hidden="true"></i>
                    </div>
                    <div class="ventas-dinero fw-bold">${valor}</div>
                    <div class="small fw-semibold">${texto}</div>
                    <small class="text-body-secondary text-truncate d-block">${sub}</small>
                </div>
            </div>`;
    }

    function htmlLector() {
        if (!datos.lectores.length) {
            return `
                <div class="d-flex flex-wrap align-items-center gap-3 p-4 rounded-4 bg-body-tertiary border mb-4">
                    <div class="icon-circle icon-danger rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:48px;height:48px"><i class='bx bx-wifi-off fs-4'></i></div>
                    <div class="flex-grow-1">
                        <span class="fw-semibold d-block">Lector RFID: no configurado</span>
                        <small class="text-body-secondary">Registra el lector de tu puerta para obtener su llave y cargarla en la LILYGO.</small>
                    </div>
                    <button type="button" class="btn btn-primary btn-sm rounded-pill px-3" data-accion="crear-lector"><i class='bx bx-plus'></i> Registrar lector</button>
                </div>`;
        }
        return datos.lectores.map(l => `
            <div class="d-flex align-items-center gap-3 p-3 rounded-4 bg-body-tertiary border mb-3">
                <div class="icon-circle ${l.enLinea ? 'icon-success' : 'icon-danger'} rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:44px;height:44px">
                    <i class='bx ${l.enLinea ? 'bx-wifi' : 'bx-wifi-off'} fs-4'></i>
                </div>
                <div>
                    <span class="fw-semibold d-block">${escapeHtml(l.nombre)}: <span class="${l.enLinea ? 'text-success' : 'text-danger'}">${l.enLinea ? 'en línea' : 'sin conexión'}</span></span>
                    <small class="text-body-secondary">${l.ultimaConexion ? 'Última conexión ' + hace(l.ultimaConexion) : 'Todavía no se ha conectado'}</small>
                </div>
            </div>`).join('') + (datos.voz ? `
            <div class="d-flex align-items-center gap-3 p-3 rounded-4 bg-body-tertiary border mb-3">
                <div class="icon-circle ${datos.voz.encendido ? 'icon-warning' : 'icon-brand'} rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:44px;height:44px">
                    <i class='bx ${datos.voz.encendido ? 'bxs-bulb' : 'bx-bulb'} fs-4'></i>
                </div>
                <div>
                    <span class="fw-semibold d-block">Acceso por voz (XIAO): <span class="${datos.voz.encendido ? 'text-warning-emphasis' : 'text-body-secondary'}">${datos.voz.encendido ? 'alguien dentro · LED encendido' : 'nadie dentro · LED apagado'}</span></span>
                    <small class="text-body-secondary"><i class="bx bx-microphone" aria-hidden="true"></i> ${escapeHtml(datos.voz.detalle || '')} · ${hace(datos.voz.fecha)}</small>
                </div>
            </div>` : '') + (llaveNueva ? `
            <div class="alert alert-warning rounded-4 mb-4" role="alert">
                <div class="fw-semibold mb-1"><i class='bx bx-key'></i> Llave del lector (cópiala ahora: no se vuelve a mostrar)</div>
                <code class="user-select-all d-block text-break">${escapeHtml(llaveNueva)}</code>
                <small class="d-block mt-1">Va en <code>LECTOR_LLAVE</code> del archivo <code>secrets.h</code> de la LILYGO.</small>
            </div>` : '');
    }

    function htmlAlertas() {
        const pendientes = datos.solicitudes.filter(s => s.estado === 'PENDIENTE');
        const atendidas = datos.solicitudes.filter(s => s.estado !== 'PENDIENTE').slice(0, 6);
        const tarjeta = (s, pendiente) => {
            const [estado, claseEstado] = ESTADOS[s.estado] || [s.estado, 'bg-secondary-subtle'];
            const desconocido = !s.nombre;
            return `
                <div class="col-12 col-xl-6">
                    <div class="p-3 rounded-4 border h-100 ${pendiente ? 'border-warning bg-warning bg-opacity-10' : ''}">
                        <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
                            <div>
                                <div class="fw-semibold">${desconocido ? 'Tag desconocido' : escapeHtml(s.nombre)}</div>
                                <div class="small text-body-secondary">${escapeHtml(s.motivo || '')} · ${hace(s.creadaEn)}</div>
                                <div class="small text-body-secondary font-monospace text-break">${escapeHtml(s.epc)}</div>
                            </div>
                            <span class="badge rounded-pill ${claseEstado}">${estado}</span>
                        </div>
                        ${pendiente ? `
                        <div class="d-flex flex-wrap gap-2">
                            <button type="button" class="btn btn-success btn-sm rounded-pill px-3" data-accion="abrir" data-id="${s.id}"><i class='bx bx-lock-open-alt'></i> Dar acceso</button>
                            <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-accion="negar" data-id="${s.id}"><i class='bx bx-block'></i> Negar</button>
                        </div>` : ''}
                        ${desconocido && datos.socios.length && !datos.tarjetas.some(t => t.epc === s.epc) ? `
                        <div class="d-flex flex-wrap gap-2 align-items-center mt-3 pt-3 border-top">
                            <small class="text-body-secondary">¿Es de un socio?</small>
                            <select class="form-select form-select-sm w-auto" data-socio-de="${escapeHtml(s.epc)}" aria-label="Socio dueño del tag">${opcionesSocios()}</select>
                            <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-accion="ligar" data-epc="${escapeHtml(s.epc)}"
                                title="${pendiente ? 'Le asigna el tag y le abre la puerta' : 'Le asigna el tag'}"><i class='bx bx-link'></i> ${pendiente ? 'Registrar y dar acceso' : 'Registrar tag'}</button>
                        </div>` : ''}
                    </div>
                </div>`;
        };
        return `
            <h5 class="fw-bold mb-3"><i class='bx bx-bell text-warning'></i> Alertas de acceso${pendientes.length ? ' <span class="badge rounded-pill bg-danger align-middle">' + pendientes.length + '</span>' : ''}</h5>
            ${pendientes.length ? '<div class="row g-3 mb-3">' + pendientes.map(s => tarjeta(s, true)).join('') + '</div>'
                : '<div class="dash-empty text-center text-body-secondary py-4 mb-3"><i class="bx bx-check-shield fs-1 d-block mb-2 opacity-50"></i>Nadie está esperando en la puerta.</div>'}
            ${atendidas.length ? '<details class="mb-4"><summary class="small text-body-secondary mb-2">Alertas anteriores</summary><div class="row g-3">' + atendidas.map(s => tarjeta(s, false)).join('') + '</div></details>' : ''}`;
    }

    function htmlTarjetas() {
        const filas = datos.tarjetas.length ? datos.tarjetas.map(t => `
            <tr>
                <td class="ps-4 fw-semibold">${escapeHtml(t.nombre)}</td>
                <td class="font-monospace small text-break">${escapeHtml(t.epc)}</td>
                <td>${t.membresiaVigente ? '<span class="badge rounded-pill bg-success-subtle text-success-emphasis">Al corriente</span>' : '<span class="badge rounded-pill bg-danger-subtle text-danger-emphasis">Vencida</span>'}</td>
                <td>${t.dentro ? '<span class="badge rounded-pill bg-primary-subtle text-primary-emphasis">Dentro</span>' : '<span class="text-body-secondary small">Fuera</span>'}</td>
                <td class="pe-4 text-end"><button type="button" class="btn btn-link btn-sm text-danger" data-accion="quitar" data-id="${t.id}" aria-label="Quitar el tag de ${escapeHtml(t.nombre)}"><i class='bx bx-trash'></i></button></td>
            </tr>`).join('')
            : '<tr><td colspan="5" class="text-center text-body-secondary py-4">Ningún socio tiene tag todavía. Usa "Registrar tag nuevo" y acerca el tag al lector.</td></tr>';
        return `
            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
                <h5 class="fw-bold mb-0"><i class='bx bx-purchase-tag-alt text-primary'></i> Tags de los socios</h5>
                ${datos.lectores.length && datos.socios.length && !datos.lectores[0].registroHasta && !datos.lectores[0].epcCapturado ? `
                <button type="button" class="btn btn-primary btn-sm rounded-pill px-3" data-accion="registro"><i class='bx bx-scan'></i> Registrar tag nuevo</button>` : ''}
            </div>
            ${htmlRegistro()}
            <div class="dash-empty text-start p-0 overflow-hidden mb-4">
                <div class="table-responsive">
                    <table class="table dash-table mb-0">
                        <thead><tr><th class="ps-4">Socio</th><th>EPC</th><th>Membresía</th><th>Ahora</th><th class="pe-4"></th></tr></thead>
                        <tbody>${filas}</tbody>
                    </table>
                </div>
            </div>`;
    }

    // "Registrar tag nuevo": el lector captura el siguiente tag que le acerquen
    // y aquí se elige a qué socio se le asigna. Nadie escribe el EPC a mano.
    function htmlRegistro() {
        const l = datos.lectores[0];
        if (!l) return '';
        if (l.registroHasta) {
            const quedan = Math.max(0, Math.round((new Date(l.registroHasta).getTime() - Date.now()) / 1000));
            return `
                <div class="d-flex flex-wrap align-items-center gap-3 p-3 rounded-4 border border-primary bg-primary bg-opacity-10 mb-3" role="status">
                    <span class="spinner-grow spinner-grow-sm text-primary" aria-hidden="true"></span>
                    <div class="flex-grow-1">
                        <div class="fw-semibold">Acerca el tag al lector "${escapeHtml(l.nombre)}"</div>
                        <small class="text-body-secondary">Esperando el tag · quedan ${quedan} s</small>
                    </div>
                    <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-accion="cancelar-registro">Cancelar</button>
                </div>`;
        }
        if (l.epcCapturado) {
            return `
                <div class="p-3 rounded-4 border border-success bg-success bg-opacity-10 mb-3">
                    <div class="fw-semibold mb-1"><i class='bx bx-check-circle text-success'></i> Tag leído</div>
                    <div class="small font-monospace text-break mb-1">${escapeHtml(l.epcCapturado)}</div>
                    ${l.epcCapturadoDe ? '<div class="small text-warning-emphasis mb-2">Este tag ya es de ' + escapeHtml(l.epcCapturadoDe) + ': si eliges a otro socio, pasa a ser suyo.</div>' : '<div class="small text-body-secondary mb-2">¿De quién es?</div>'}
                    <div class="d-flex flex-wrap gap-2 align-items-center">
                        <select class="form-select form-select-sm w-auto" data-socio-de="${escapeHtml(l.epcCapturado)}" aria-label="Socio dueño del tag">${opcionesSocios()}</select>
                        <button type="button" class="btn btn-success btn-sm rounded-pill px-3" data-accion="ligar" data-epc="${escapeHtml(l.epcCapturado)}"><i class='bx bx-link'></i> Asignar</button>
                        <button type="button" class="btn btn-outline-secondary btn-sm rounded-pill px-3" data-accion="cancelar-registro">Descartar</button>
                    </div>
                </div>`;
        }
        return '';
    }

    function htmlHistorial() {
        const filas = datos.accesos.length ? datos.accesos.map(a => {
            const [texto, clase, icono] = TIPOS[a.tipo] || [a.tipo, 'bg-secondary-subtle', 'bx-circle'];
            return `
                <tr>
                    <td class="ps-4 text-nowrap">${hora(a.fecha)}<div class="small text-body-secondary">${new Date(a.fecha).toLocaleDateString('es-MX', { day: 'numeric', month: 'short' })}</div></td>
                    <td><span class="fw-semibold">${escapeHtml(a.nombre || 'Tag desconocido')}</span><div class="small text-body-secondary font-monospace text-break">${escapeHtml(a.epc || '')}</div></td>
                    <td class="pe-4"><span class="badge rounded-pill ${clase}"><i class='bx ${icono}'></i> ${texto}</span>${a.motivo ? '<div class="small text-body-secondary">' + escapeHtml(a.motivo) + '</div>' : ''}</td>
                </tr>`;
        }).join('') : '<tr><td colspan="3" class="text-center text-body-secondary py-5"><i class="bx bx-scan fs-1 d-block mb-2 opacity-50"></i>Aquí aparece cada pase por el lector en cuanto ocurre.</td></tr>';
        return `
            <h5 class="fw-bold mb-3"><i class='bx bx-history text-primary'></i> Historial de la puerta</h5>
            <div class="dash-empty text-start p-0 overflow-hidden">
                <div class="table-responsive">
                    <table class="table dash-table mb-0">
                        <thead><tr><th class="ps-4">Hora</th><th>Socio</th><th class="pe-4">Resultado</th></tr></thead>
                        <tbody>${filas}</tbody>
                    </table>
                </div>
            </div>`;
    }

    function pintar() {
        const hoy = datos.accesos.filter(a => esHoy(a.fecha));
        const pendientes = datos.solicitudes.filter(s => s.estado === 'PENDIENTE').length;
        // No se repinta encima de lo que el dueño está escribiendo.
        const enFoco = document.activeElement && contenido.contains(document.activeElement)
            && ['INPUT', 'SELECT'].includes(document.activeElement.tagName);
        if (enFoco) return;
        const abiertas = contenido.querySelector('details[open]') != null;
        // El socio elegido en cada lista sobrevive a la actualización automática.
        const elegidos = {};
        contenido.querySelectorAll('select[data-socio-de]').forEach(s => { elegidos[s.dataset.socioDe] = s.value; });
        contenido.innerHTML = htmlLector()
            + '<div class="row g-3 mb-4">'
            + tarjetaKpi(datos.dentro.length, 'Dentro ahora', datos.dentro.join(', ') || 'Nadie', 'bx-group', 'icon-brand')
            + tarjetaKpi(hoy.filter(a => a.tipo === 'ENTRADA').length, 'Entradas hoy', 'Con su tag o abiertas por ti', 'bx-log-in-circle', 'icon-success')
            + tarjetaKpi(hoy.filter(a => a.tipo === 'NEGADO').length, 'Accesos negados hoy', 'Tag desconocido o membresía vencida', 'bx-block', 'icon-danger')
            + tarjetaKpi(pendientes, 'Esperando en la puerta', pendientes ? 'Revisa las alertas' : 'Sin pendientes', 'bx-bell', 'icon-warning')
            + '</div>'
            + htmlAlertas() + htmlTarjetas() + htmlHistorial();
        if (abiertas) contenido.querySelector('details')?.setAttribute('open', '');
        contenido.querySelectorAll('select[data-socio-de]').forEach(s => { if (elegidos[s.dataset.socioDe]) s.value = elegidos[s.dataset.socioDe]; });
    }

    function actualizarBadge() {
        const n = datos ? datos.solicitudes.filter(s => s.estado === 'PENDIENTE').length : 0;
        badgeNav.textContent = n;
        badgeNav.classList.toggle('d-none', n === 0);
    }

    function cargar(silencioso) {
        const gymId = localStorage.getItem('gymId');
        if (!gymId) {
            contenido.innerHTML = avisoSinGimnasio('Configura primero "Mi Gimnasio" para conectar tu lector RFID.', 'bx-id-card');
            return;
        }
        if (cargando) return;
        cargando = true;
        if (!silencioso && !datos) {
            contenido.innerHTML = '<div class="text-center text-body-secondary py-5"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Cargando accesos…</div>';
        }
        apiTienda('/api/gyms/' + gymId + '/acceso')
            .then(r => { datos = r; actualizarBadge(); if (abierta) pintar(); })
            .catch(err => { if (!silencioso) estadoError(contenido, err.message, () => cargar()); })
            .finally(() => { cargando = false; });
    }

    function accion(promesa, boton) {
        if (boton) boton.disabled = true;
        return promesa
            .then(() => cargar(true))
            .catch(err => { alert(err.message); if (boton) boton.disabled = false; });
    }

    contenido.addEventListener('click', e => {
        const b = e.target.closest('[data-accion]');
        if (!b) return;
        const gymId = localStorage.getItem('gymId');
        const base = '/api/gyms/' + gymId + '/acceso';
        if (b.dataset.accion === 'crear-lector') {
            b.disabled = true;
            apiTienda(base + '/lectores', { method: 'POST', body: JSON.stringify({ nombre: 'Puerta principal' }) })
                .then(r => { llaveNueva = r.llave; cargar(true); })
                .catch(err => { alert(err.message); b.disabled = false; });
        } else if (b.dataset.accion === 'abrir' || b.dataset.accion === 'negar') {
            accion(apiTienda(base + '/solicitudes/' + b.dataset.id + '/' + b.dataset.accion, { method: 'POST' }), b);
        } else if (b.dataset.accion === 'ligar') {
            const select = b.parentElement.querySelector('select');
            accion(apiTienda(base + '/tarjetas', { method: 'POST', body: JSON.stringify({ epc: b.dataset.epc, userId: select.value }) }), b);
        } else if (b.dataset.accion === 'quitar') {
            if (!confirm('¿Quitar este tag? El socio ya no podrá entrar con él.')) return;
            accion(apiTienda(base + '/tarjetas/' + b.dataset.id, { method: 'DELETE' }), b);
        } else if (b.dataset.accion === 'registro') {
            accion(apiTienda(base + '/lectores/' + datos.lectores[0].id + '/registro', { method: 'POST' }), b);
        } else if (b.dataset.accion === 'cancelar-registro') {
            accion(apiTienda(base + '/lectores/' + datos.lectores[0].id + '/registro', { method: 'DELETE' }), b);
        }
    });

    document.getElementById('accesos-actualizar').addEventListener('click', () => cargar());
    document.addEventListener('panel:mostrado', e => {
        abierta = e.detail === 'accesos';
        if (abierta) { if (datos) pintar(); cargar(); }
    });
    setInterval(() => {
        if (document.hidden) return;
        if (abierta || Date.now() % 15000 < 4000) cargar(true);
    }, 4000);
    cargar(true);
}
