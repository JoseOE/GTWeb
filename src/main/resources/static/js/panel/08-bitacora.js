/* Panel del dueño (bienvenido.html): Bitácora. */

/* ═══════════════════════════════════════
   BITÁCORA — /api/gyms/{id}/bitacora
   Solo lectura: la escribe el servidor (BitacoraService).
═══════════════════════════════════════ */
let bitacoraEventos = [];

function initBitacora() {
    const tbody = document.getElementById('bitacora-tbody');
    const POR_PAGINA = 25;
    let visibles = POR_PAGINA;
    const CUENTA = ['LOGIN_EXITOSO', 'LOGIN_FALLIDO', 'LOGIN_BLOQUEADO', 'CONTRASENA_CAMBIADA', 'CONTRASENA_RESTABLECIDA', 'CORREO_CAMBIADO'];
    const ALERTA = ['LOGIN_FALLIDO', 'LOGIN_BLOQUEADO', 'ACCESO_DENEGADO'];
    // Ícono y color de cada tipo de evento (los colores de las tarjetas del panel).
    const ICONOS = {
        LOGIN_EXITOSO: ['bx-log-in', 'icon-success'], LOGIN_FALLIDO: ['bx-error', 'icon-danger'],
        LOGIN_BLOQUEADO: ['bx-lock-alt', 'icon-danger'], ACCESO_DENEGADO: ['bx-block', 'icon-danger'],
        CONTRASENA_CAMBIADA: ['bx-key', 'icon-warning'], CONTRASENA_RESTABLECIDA: ['bx-key', 'icon-warning'],
        CORREO_CAMBIADO: ['bx-envelope', 'icon-info'], GIMNASIO_CREADO: ['bx-store-alt', 'icon-brand'],
        CODIGO_GIMNASIO: ['bx-qr', 'icon-brand'], MIEMBRO_ALTA: ['bx-user-plus', 'icon-brand'],
        MIEMBRO_ESTADO: ['bx-user-check', 'icon-brand'], MIEMBRO_BAJA: ['bx-user-minus', 'icon-brand'],
        PAGO_REGISTRADO: ['bx-credit-card', 'icon-success'], VENTA_MOSTRADOR: ['bx-store', 'icon-success'],
        PEDIDO_REEMBOLSADO: ['bx-undo', 'icon-warning'], PEDIDO_CANCELADO: ['bx-x-circle', 'icon-warning'],
        PEDIDO_ENTREGADO: ['bx-package', 'icon-info'], PRODUCTO_CAMBIADO: ['bx-box', 'icon-info'],
        PLAN_CAMBIADO: ['bx-purchase-tag-alt', 'icon-info']
    };
    // Para decir en palabras qué se intentó en un acceso negado.
    const SECCIONES = {
        members: 'miembros', payments: 'pagos', productos: 'productos', planes: 'planes', mostrador: 'el mostrador',
        ventas: 'ventas', codigo: 'el código de invitación', bitacora: 'la bitácora', routines: 'rutinas',
        machines: 'máquinas', resenas: 'reseñas', imagenes: 'imágenes', users: 'una cuenta'
    };
    const VERBOS = { GET: 'ver', POST: 'cambiar', PUT: 'editar', PATCH: 'editar', DELETE: 'borrar' };

    function filtro() {
        return document.querySelector('input[name="bitacora-filtro"]:checked').value;
    }

    function dispositivo(agente) {
        if (!agente) return 'Desconocido';
        if (/okhttp|CFNetwork|Expo|Dalvik/i.test(agente)) return 'App GymTrack';
        const so = /Android/i.test(agente) ? 'Android' : /iPhone|iPad/i.test(agente) ? 'iOS' : /Windows/i.test(agente) ? 'Windows' : /Mac OS/i.test(agente) ? 'macOS' : /Linux/i.test(agente) ? 'Linux' : '';
        const nav = /Edg\//.test(agente) ? 'Edge' : /OPR\//.test(agente) ? 'Opera' : /Chrome\//.test(agente) ? 'Chrome' : /Firefox\//.test(agente) ? 'Firefox' : /Safari\//.test(agente) ? 'Safari' : /node/i.test(agente) ? 'Script' : 'Otro';
        return nav + (so ? ' en ' + so : '');
    }

    function ip(valor) {
        if (!valor) return '';
        return /^(::1|0:0:0:0:0:0:0:1|127\.)/.test(valor) ? 'Este equipo' : valor;
    }

    // La línea de abajo del evento: con qué correo, o qué se intentó o cambió.
    function detalle(e) {
        if (e.email) return e.email;
        const partes = (e.ruta || '').split('/').filter(Boolean);
        const seccion = partes.slice().reverse().map(p => SECCIONES[p]).find(Boolean);
        if (e.tipo === 'ACCESO_DENEGADO') {
            return 'Intentó ' + (VERBOS[e.metodo] || 'usar') + ' ' + (seccion || 'algo que no es suyo');
        }
        return seccion ? 'En ' + seccion : '';
    }

    function pintarKpis() {
        const desde = Date.now() - 7 * 24 * 3600 * 1000;
        const semana = bitacoraEventos.filter(e => new Date(e.fecha).getTime() >= desde);
        const cuantos = tipos => semana.filter(e => tipos.includes(e.tipo)).length;
        const tiles = [
            { valor: cuantos(['LOGIN_EXITOSO']), texto: 'Inicios de sesión', icono: 'bx-log-in', color: 'icon-success' },
            { valor: cuantos(['LOGIN_FALLIDO', 'LOGIN_BLOQUEADO']), texto: 'Contraseñas incorrectas', icono: 'bx-error', color: 'icon-danger' },
            { valor: cuantos(['ACCESO_DENEGADO']), texto: 'Accesos negados', icono: 'bx-block', color: 'icon-warning' },
            { valor: semana.filter(e => !CUENTA.includes(e.tipo) && !ALERTA.includes(e.tipo)).length, texto: 'Cambios en el panel', icono: 'bx-edit', color: 'icon-brand' }
        ];
        document.getElementById('bitacora-kpis').innerHTML = tiles.map(t => `
            <div class="col-6 col-xl-3">
                <div class="kpi-tile h-100${t.color === 'icon-danger' && t.valor ? ' es-alerta' : ''}">
                    <div class="kpi-cabeza"><span class="kpi-titulo">${t.texto}</span><i class="bx ${t.icono}" aria-hidden="true"></i></div>
                    <div class="kpi-value">${t.valor}</div>
                    <small class="kpi-detalle">Últimos 7 días</small>
                </div>
            </div>`).join('');
    }

    function pintar() {
        const tipo = filtro();
        const eventos = bitacoraEventos.filter(e => !tipo
            || (tipo === 'sesion' && CUENTA.includes(e.tipo))
            || (tipo === 'alertas' && ALERTA.includes(e.tipo))
            || (tipo === 'panel' && !CUENTA.includes(e.tipo) && !ALERTA.includes(e.tipo)));
        document.getElementById('bitacora-conteo').textContent = eventos.length
            ? (eventos.length === 1 ? '1 evento' : eventos.length + ' eventos') + (eventos.length > visibles ? ' · mostrando los ' + visibles + ' más recientes' : '')
            : '';
        document.getElementById('bitacora-mas-caja').classList.toggle('d-none', eventos.length <= visibles);
        if (!eventos.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-body-secondary py-5 px-4"><i class="bx bx-shield fs-1 d-block mb-2 opacity-50"></i>'
                + (tipo === 'alertas' ? 'Sin alertas: nadie ha fallado la contraseña ni intentado entrar donde no debe.' : 'No hay eventos que mostrar.') + '</td></tr>';
            return;
        }
        tbody.innerHTML = eventos.slice(0, visibles).map(e => {
            const [icono, color] = ICONOS[e.tipo] || ['bx-shield', 'icon-brand'];
            const fecha = new Date(e.fecha);
            const sub = detalle(e);
            return `
                <tr${e.ruta ? ' title="' + escapeHtml((e.metodo || '') + ' ' + e.ruta) + '"' : ''}>
                    <td class="ps-4">
                        <div class="d-flex align-items-center gap-3">
                            <div class="icon-circle ${color} rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:36px;height:36px">
                                <i class='bx ${icono}' aria-hidden="true"></i>
                            </div>
                            <div style="min-width:0">
                                <div class="fw-semibold">${escapeHtml(e.descripcion)}${ALERTA.includes(e.tipo) ? ' <span class="badge rounded-pill bg-danger-subtle text-danger-emphasis ms-1">Alerta</span>' : ''}</div>
                                ${sub ? '<div class="small text-body-secondary text-truncate" style="max-width:320px">' + escapeHtml(sub) + '</div>' : ''}
                            </div>
                        </div>
                    </td>
                    <td class="text-nowrap">
                        ${fecha.toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' })}
                        <div class="small text-body-secondary">${fecha.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })}</div>
                    </td>
                    <td class="text-nowrap">
                        ${escapeHtml(dispositivo(e.agente))}
                        <div class="small text-body-secondary">${escapeHtml(ip(e.ip))}</div>
                    </td>
                    <td class="pe-4">${e.propio
                        ? '<span class="badge rounded-pill bg-success-subtle text-success-emphasis">Tú</span>'
                        : '<span class="badge rounded-pill bg-secondary-subtle text-secondary-emphasis">Otra cuenta</span>'}</td>
                </tr>`;
        }).join('');
    }

    function cargar() {
        const gymId = localStorage.getItem('gymId');
        const kpis = document.getElementById('bitacora-kpis');
        if (!gymId) {
            kpis.innerHTML = '<div class="col-12">' + avisoSinGimnasio('Configura primero "Mi Gimnasio" para ver su bitácora.', 'bx-shield-quarter') + '</div>';
            tbody.innerHTML = '';
            return;
        }
        visibles = POR_PAGINA;
        kpis.innerHTML = '';
        tbody.innerHTML = '<tr><td colspan="4" class="text-center text-body-secondary py-5"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Cargando la bitácora…</td></tr>';
        apiTienda('/api/gyms/' + gymId + '/bitacora?limite=500')
            .then(eventos => { bitacoraEventos = eventos; pintarKpis(); pintar(); })
            .catch(err => {
                tbody.innerHTML = '<tr><td colspan="4" id="bitacora-error"></td></tr>';
                estadoError(document.getElementById('bitacora-error'), err.message, cargar);
            });
    }

    document.querySelectorAll('input[name="bitacora-filtro"]').forEach(r => r.addEventListener('change', () => { visibles = POR_PAGINA; pintar(); }));
    document.getElementById('bitacora-mas').addEventListener('click', () => { visibles += POR_PAGINA; pintar(); });
    document.getElementById('bitacora-actualizar').addEventListener('click', cargar);
    document.addEventListener('panel:mostrado', e => { if (e.detail === 'bitacora') cargar(); });
}
