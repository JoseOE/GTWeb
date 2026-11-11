/* ═══════════════════════════════════════
   Panel de administración del equipo de GymTrack (admin.html)
   ───────────────────────────────────────
   Todo sale de /api/admin (AdminController): solo responde a la cuenta de
   administrador, que se crea con ADMIN_EMAIL y ADMIN_PASSWORD. Aquí solo se
   lee, salvo ocultar o volver a mostrar una reseña u opinión.
═══════════════════════════════════════ */
(function () {
    // Cualquier otra cuenta va a su propio panel.
    if (localStorage.getItem('role') !== 'admin') {
        location.replace(Sesion.destino({ role: localStorage.getItem('role') }));
        return;
    }

    const POR_PAGINA = 50;
    const moneda = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });
    const numero = new Intl.NumberFormat('es-MX');
    const datos = { resumen: null, gimnasios: null, usuarios: null, resenas: null, bitacora: null };
    const visibles = { cuentas: POR_PAGINA, resenas: POR_PAGINA, bitacora: POR_PAGINA };

    // ═══════════════════════════ AYUDANTES ═══════════════════════════

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str == null ? '' : String(str);
        return div.innerHTML;
    }

    function api(url, opciones) {
        return fetch(url, opciones).then(res => res.json().catch(() => ({})).then(cuerpo => {
            if (res.status === 403) {
                // La sesión es de otra cuenta (o la cuenta dejó de ser administrador).
                throw new Error(cuerpo.error || 'Solo el equipo de GymTrack puede ver esto.');
            }
            if (!res.ok) throw new Error(cuerpo.error || 'No pudimos cargar la información. Intenta de nuevo.');
            return cuerpo;
        }));
    }

    function cargando(el, texto, columnas) {
        const html = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' + texto;
        el.innerHTML = columnas
            ? '<tr><td colspan="' + columnas + '" class="text-center text-body-secondary py-5">' + html + '</td></tr>'
            : '<div class="text-center text-body-secondary py-5">' + html + '</div>';
    }

    function error(el, mensaje, reintentar, columnas) {
        const html = '<i class="bx bx-error-circle fs-1 d-block mb-2 text-danger"></i>' + escapeHtml(mensaje)
            + '<div class="mt-3"><button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3">Reintentar</button></div>';
        el.innerHTML = columnas
            ? '<tr><td colspan="' + columnas + '" class="text-center text-body-secondary py-5">' + html + '</td></tr>'
            : '<div class="text-center text-body-secondary py-5">' + html + '</div>';
        el.querySelector('button').addEventListener('click', reintentar);
    }

    function vacio(texto, icono, columnas) {
        const html = '<i class="bx ' + icono + ' fs-1 d-block mb-2 opacity-50"></i>' + texto;
        return columnas
            ? '<tr><td colspan="' + columnas + '" class="text-center text-body-secondary py-5 px-4">' + html + '</td></tr>'
            : '<div class="dash-empty text-center text-body-secondary py-5 px-4">' + html + '</div>';
    }

    function fecha(valor, conHora) {
        if (!valor) return '<span class="text-body-secondary">—</span>';
        const f = new Date(valor);
        return f.toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' })
            + (conHora ? '<div class="small text-body-secondary">' + f.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }) + '</div>' : '');
    }

    function hace(valor) {
        if (!valor) return '<span class="text-body-secondary">Sin registro</span>';
        const dias = Math.floor((Date.now() - new Date(valor).getTime()) / 86400000);
        const texto = dias <= 0 ? 'Hoy' : dias === 1 ? 'Ayer' : 'Hace ' + dias + ' días';
        return texto + '<div class="small text-body-secondary">' + new Date(valor).toLocaleDateString('es-MX', { day: 'numeric', month: 'short' }) + '</div>';
    }

    function estrellas(n) {
        let html = '<span class="admin-estrellas" role="img" aria-label="' + n + ' de 5 estrellas">';
        for (let i = 1; i <= 5; i++) html += '<i class="bx ' + (i <= n ? 'bxs-star' : 'bx-star') + '" aria-hidden="true"></i>';
        return html + '</span>';
    }

    function calificacion(c) {
        if (!c || !c.total) return '<span class="text-body-secondary small">Sin calificar</span>';
        return '<span class="text-nowrap"><i class="bx bxs-star admin-estrella" aria-hidden="true"></i> <strong>'
            + c.promedio.toFixed(1) + '</strong> <span class="small text-body-secondary">(' + c.total + ')</span></span>';
    }

    function iniciales(nombre) {
        const p = (nombre || '').trim().split(/\s+/).filter(Boolean);
        return ((p[0] || '?')[0] + (p.length > 1 ? p[1][0] : '')).toUpperCase();
    }

    function tile(t) {
        return `
            <div class="col-6 col-xl-3">
                <div class="kpi-tile h-100${t.color === 'icon-danger' && String(t.valor) !== '0' ? ' es-alerta' : ''}">
                    <div class="kpi-cabeza"><span class="kpi-titulo">${t.texto}</span><i class="bx ${t.icono}" aria-hidden="true"></i></div>
                    <div class="kpi-value">${t.valor}</div>
                    <small class="kpi-detalle">${t.nota || ''}</small>
                </div>
            </div>`;
    }

    function elegido(nombre) {
        const el = document.querySelector('input[name="' + nombre + '"]:checked');
        return el ? el.value : '';
    }

    function normal(s) {
        return (s || '').toString().normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    }

    function contiene(texto, ...campos) {
        const q = normal(texto).trim();
        return !q || campos.some(c => normal(c).includes(q));
    }

    function avisar(texto) {
        document.getElementById('admin-aviso-texto').textContent = texto;
        bootstrap.Toast.getOrCreateInstance(document.getElementById('admin-aviso')).show();
    }

    function contar(n, uno, varios, total) {
        const base = n === 1 ? '1 ' + uno : numero.format(n) + ' ' + varios;
        return total > n ? base + ' · mostrando ' + numero.format(total) : base;
    }

    // ═══════════════════════════ PESTAÑAS ═══════════════════════════

    const navLinks = document.querySelectorAll('.dash-nav-link');
    const panels = document.querySelectorAll('.dash-panel');
    const content = document.querySelector('.dash-content');

    function mostrar(key) {
        panels.forEach(p => p.classList.toggle('active', p.dataset.panel === key));
        navLinks.forEach(l => {
            const activo = l.dataset.panel === key;
            l.classList.toggle('active', activo);
            l.setAttribute('aria-selected', String(activo));
        });
        content.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
        CARGAS[key]();
    }

    navLinks.forEach(l => l.addEventListener('click', () => mostrar(l.dataset.panel)));
    document.addEventListener('click', e => {
        const destino = e.target.closest('[data-goto]');
        if (!destino) return;
        if (destino.dataset.calificacion) {
            const radio = document.querySelector('input[name="resenas-cal"][value="' + destino.dataset.calificacion + '"]');
            if (radio) radio.checked = true;
        }
        mostrar(destino.dataset.goto);
    });

    // ═══════════════════════════ RESUMEN ═══════════════════════════

    function cargarResumen(forzar) {
        const kpis = document.getElementById('resumen-kpis');
        const malas = document.getElementById('resumen-malas');
        const top = document.getElementById('resumen-top');
        if (!forzar && datos.resumen && datos.resenas && datos.gimnasios) return pintarResumen();
        kpis.innerHTML = '<div class="col-12" id="resumen-estado"></div>';
        cargando(document.getElementById('resumen-estado'), 'Cargando la plataforma…');
        cargando(malas, 'Cargando…');
        cargando(top, 'Cargando…');
        Promise.all([api('/api/admin/resumen'), api('/api/admin/resenas'), api('/api/admin/gimnasios')])
            .then(([r, resenas, gimnasios]) => {
                datos.resumen = r;
                datos.resenas = resenas;
                datos.gimnasios = gimnasios;
                pintarResumen();
                llenarGimnasiosFiltro();
                pintarInsigniaMalas();
            })
            .catch(err => {
                kpis.innerHTML = '<div class="col-12 dash-empty" id="resumen-estado"></div>';
                error(document.getElementById('resumen-estado'), err.message, () => cargarResumen(true));
                malas.innerHTML = '';
                top.innerHTML = '';
            });
    }

    function pintarResumen() {
        const r = datos.resumen;
        const malasTotal = r.resenas.malas + r.opiniones.malas;
        document.getElementById('resumen-kpis').innerHTML = [
            { valor: numero.format(r.gimnasios), texto: 'Gimnasios', nota: numero.format(r.duenos) + ' dueños', icono: 'bx-store-alt', color: 'icon-brand' },
            { valor: numero.format(r.miembros), texto: 'Miembros', nota: numero.format(r.cuentasNuevas30) + ' cuentas nuevas en 30 días', icono: 'bx-group', color: 'icon-info' },
            { valor: moneda.format(r.ventasTotal), texto: 'Ventas totales', nota: moneda.format(r.ventas30) + ' en 30 días (' + numero.format(r.pedidos30) + ' pedidos)', icono: 'bx-line-chart', color: 'icon-success' },
            { valor: numero.format(malasTotal), texto: 'Reseñas malas', nota: 'De ' + numero.format(r.resenas.total + r.opiniones.total) + ' reseñas y opiniones', icono: 'bx-error', color: malasTotal ? 'icon-danger' : 'icon-success' },
            { valor: r.resenas.total ? r.resenas.promedio.toFixed(1) + '★' : '—', texto: 'Productos', nota: numero.format(r.resenas.total) + ' reseñas', icono: 'bx-package', color: 'icon-warning' },
            { valor: r.opiniones.total ? r.opiniones.promedio.toFixed(1) + '★' : '—', texto: 'Gimnasios', nota: numero.format(r.opiniones.total) + ' opiniones', icono: 'bx-star', color: 'icon-warning' },
            { valor: numero.format(r.pedidosPagados), texto: 'Pedidos pagados', nota: 'Tienda, app y mostrador', icono: 'bx-receipt', color: 'icon-success' },
            { valor: numero.format(r.resenas.buenas + r.opiniones.buenas), texto: 'Reseñas buenas', nota: 'De 4 y 5 estrellas', icono: 'bx-like', color: 'icon-success' }
        ].map(tile).join('');

        const malas = datos.resenas.filter(x => x.estrellas <= 2).slice(0, 5);
        document.getElementById('resumen-malas').innerHTML = malas.length
            ? '<ul class="list-unstyled mb-0">' + malas.map(x => `
                <li class="admin-fila">
                    <div class="d-flex justify-content-between gap-2">
                        <div style="min-width:0">
                            ${estrellas(x.estrellas)}
                            <span class="small text-body-secondary ms-1">${escapeHtml(x.tipo === 'producto' ? x.producto : 'Opinión del gimnasio')} · ${escapeHtml(x.gimnasio)}</span>
                            <div class="admin-texto-corto">${escapeHtml(x.texto || '(sin comentario)')}</div>
                        </div>
                        <small class="text-body-secondary text-nowrap">${new Date(x.fecha).toLocaleDateString('es-MX', { day: 'numeric', month: 'short' })}</small>
                    </div>
                </li>`).join('') + '</ul>'
            : '<div class="text-center text-body-secondary py-5 px-4"><i class="bx bx-happy fs-1 d-block mb-2 opacity-50"></i>No hay reseñas malas. 🎉</div>';

        const top = datos.gimnasios.slice(0, 5);
        const maximo = Math.max(1, ...top.map(g => g.ventas));
        document.getElementById('resumen-top').innerHTML = top.length
            ? '<ul class="list-unstyled mb-0">' + top.map((g, i) => `
                <li class="admin-fila">
                    <div class="d-flex justify-content-between align-items-baseline gap-2 mb-1">
                        <span class="fw-semibold text-truncate">${i + 1}. ${escapeHtml(g.nombre)}</span>
                        <span class="fw-semibold text-nowrap">${moneda.format(g.ventas)}</span>
                    </div>
                    <div class="progress admin-barra" role="presentation"><div class="progress-bar" style="width:${Math.round(g.ventas * 100 / maximo)}%"></div></div>
                    <small class="text-body-secondary">${numero.format(g.pedidosPagados)} pedidos · ${numero.format(g.miembros)} miembros</small>
                </li>`).join('') + '</ul>'
            : vacio('Todavía no hay gimnasios.', 'bx-store-alt');
    }

    function pintarInsigniaMalas() {
        const insignia = document.getElementById('resenas-nav-malas');
        const n = datos.resenas.filter(x => x.estrellas <= 2 && !x.oculta).length;
        insignia.textContent = n;
        insignia.classList.toggle('d-none', !n);
    }

    // ═══════════════════════════ GIMNASIOS ═══════════════════════════

    function cargarGimnasios(forzar) {
        const tbody = document.getElementById('gimnasios-tbody');
        if (!forzar && datos.gimnasios) return pintarGimnasios();
        cargando(tbody, 'Cargando los gimnasios…', 6);
        api('/api/admin/gimnasios')
            .then(g => { datos.gimnasios = g; pintarGimnasios(); llenarGimnasiosFiltro(); })
            .catch(err => error(tbody, err.message, () => cargarGimnasios(true), 6));
    }

    function pintarGimnasios() {
        const q = document.getElementById('gimnasios-buscar').value;
        const lista = datos.gimnasios.filter(g => contiene(q, g.nombre, g.dueno && g.dueno.nombre, g.dueno && g.dueno.email));
        document.getElementById('gimnasios-conteo').textContent = contar(lista.length, 'gimnasio', 'gimnasios', 0);
        document.getElementById('gimnasios-tbody').innerHTML = lista.length ? lista.map(g => `
            <tr>
                <td class="ps-4 admin-celda-principal">
                    <div class="d-flex align-items-center gap-3">
                        ${g.foto || g.logo
                            ? '<img src="' + escapeHtml(g.foto || g.logo) + '" alt="" class="admin-miniatura" loading="lazy">'
                            : '<div class="admin-miniatura d-flex align-items-center justify-content-center" style="background:' + escapeHtml(g.colorPrimario || '#6c757d') + '"><i class="bx bx-dumbbell text-white" aria-hidden="true"></i></div>'}
                        <div style="min-width:0">
                            <div class="fw-semibold">${escapeHtml(g.nombre)}</div>
                            <div class="small text-body-secondary">${g.enDirectorio ? 'En el directorio' : 'Fuera del directorio'}${g.creado ? ' · desde ' + new Date(g.creado).toLocaleDateString('es-MX', { month: 'short', year: 'numeric' }) : ''}</div>
                        </div>
                    </div>
                </td>
                <td>${g.dueno ? escapeHtml(g.dueno.nombre) + '<div class="small text-body-secondary">' + escapeHtml(g.dueno.email) + '</div>' : '<span class="text-body-secondary">Sin dueño</span>'}</td>
                <td class="text-end">${numero.format(g.miembros)}</td>
                <td class="text-end text-nowrap">${moneda.format(g.ventas)}<div class="small text-body-secondary">${numero.format(g.pedidosPagados)} pedidos</div></td>
                <td>${calificacion(g.opiniones)}</td>
                <td class="pe-4">${calificacion(g.resenas)}</td>
            </tr>`).join('') : vacio('Ningún gimnasio coincide con la búsqueda.', 'bx-search', 6);
    }

    document.getElementById('gimnasios-buscar').addEventListener('input', () => datos.gimnasios && pintarGimnasios());

    // ═══════════════════════════ CUENTAS ═══════════════════════════

    const ROLES = {
        owner: ['Dueño', 'bg-primary-subtle text-primary-emphasis'],
        member: ['Miembro', 'bg-info-subtle text-info-emphasis'],
        admin: ['Administrador', 'bg-dark text-white']
    };
    const ESTADOS = { active: 'Activo', pending: 'Solicitud pendiente', inactive: 'Dado de baja', none: 'Sin gimnasio' };

    function cargarCuentas(forzar) {
        const tbody = document.getElementById('cuentas-tbody');
        if (!forzar && datos.usuarios) return pintarCuentas();
        cargando(tbody, 'Cargando las cuentas…', 5);
        api('/api/admin/usuarios')
            .then(u => { datos.usuarios = u; pintarCuentas(); })
            .catch(err => error(tbody, err.message, () => cargarCuentas(true), 5));
    }

    function pintarCuentas() {
        const q = document.getElementById('cuentas-buscar').value;
        const rol = elegido('cuentas-rol');
        const sinDemo = document.getElementById('cuentas-sin-demo').checked;
        const lista = datos.usuarios.filter(u => (!rol || u.rol === rol) && (!sinDemo || !u.demo)
            && contiene(q, u.nombre, u.email, u.gimnasio));
        document.getElementById('cuentas-conteo').textContent = contar(Math.min(lista.length, visibles.cuentas), 'cuenta', 'cuentas', 0)
            + (lista.length > visibles.cuentas ? ' de ' + numero.format(lista.length) : '');
        document.getElementById('cuentas-mas-caja').classList.toggle('d-none', lista.length <= visibles.cuentas);
        document.getElementById('cuentas-tbody').innerHTML = lista.length ? lista.slice(0, visibles.cuentas).map(u => {
            const [etiqueta, clase] = ROLES[u.rol] || [u.rol || '—', 'bg-secondary-subtle text-secondary-emphasis'];
            return `
                <tr>
                    <td class="ps-4 admin-celda-principal">
                        <div class="fw-semibold">${escapeHtml(u.nombre)}${u.demo ? ' <span class="badge rounded-pill bg-secondary-subtle text-secondary-emphasis ms-1">Demo</span>' : ''}</div>
                        <div class="small text-body-secondary">${escapeHtml(u.email)}${u.correoVerificado ? '' : ' · <span class="text-warning-emphasis">correo sin verificar</span>'}</div>
                    </td>
                    <td><span class="badge rounded-pill ${clase}">${etiqueta}</span>${u.rol === 'member' && u.estado ? '<div class="small text-body-secondary mt-1">' + escapeHtml(ESTADOS[u.estado] || u.estado) + '</div>' : ''}</td>
                    <td>${u.gimnasio ? escapeHtml(u.gimnasio) : '<span class="text-body-secondary">—</span>'}</td>
                    <td class="text-nowrap">${fecha(u.creado)}</td>
                    <td class="pe-4 text-nowrap">${hace(u.ultimoAcceso)}</td>
                </tr>`;
        }).join('') : vacio('Ninguna cuenta coincide con los filtros.', 'bx-search', 5);
    }

    document.getElementById('cuentas-buscar').addEventListener('input', () => { visibles.cuentas = POR_PAGINA; datos.usuarios && pintarCuentas(); });
    document.querySelectorAll('input[name="cuentas-rol"], #cuentas-sin-demo').forEach(el =>
        el.addEventListener('change', () => { visibles.cuentas = POR_PAGINA; datos.usuarios && pintarCuentas(); }));
    document.getElementById('cuentas-mas').addEventListener('click', () => { visibles.cuentas += POR_PAGINA; pintarCuentas(); });

    // ═══════════════════════════ RESEÑAS ═══════════════════════════

    function llenarGimnasiosFiltro() {
        const select = document.getElementById('resenas-gym');
        if (!datos.gimnasios || select.options.length > 1) return;
        datos.gimnasios.slice().sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
            .forEach(g => select.add(new Option(g.nombre, g.id)));
    }

    function cargarResenas(forzar) {
        const lista = document.getElementById('resenas-lista');
        if (!forzar && datos.resenas) { llenarGimnasiosFiltro(); return pintarResenas(); }
        cargando(lista, 'Cargando las reseñas…');
        Promise.all([api('/api/admin/resenas'), datos.gimnasios ? Promise.resolve(datos.gimnasios) : api('/api/admin/gimnasios')])
            .then(([r, g]) => { datos.resenas = r; datos.gimnasios = g; llenarGimnasiosFiltro(); pintarResenas(); pintarInsigniaMalas(); })
            .catch(err => error(lista, err.message, () => cargarResenas(true)));
    }

    function filtrarResenas() {
        const tipo = elegido('resenas-tipo');
        const cal = elegido('resenas-cal');
        const vis = elegido('resenas-vis');
        const gym = document.getElementById('resenas-gym').value;
        const q = document.getElementById('resenas-buscar').value;
        return datos.resenas.filter(x => (!tipo || x.tipo === tipo)
            && (!cal || (cal === 'buenas' ? x.estrellas >= 4 : cal === 'regulares' ? x.estrellas === 3 : x.estrellas <= 2))
            && (!vis || (vis === 'ocultas') === x.oculta)
            && (!gym || x.gymId === gym)
            && contiene(q, x.texto, x.autor, x.email, x.producto, x.gimnasio));
    }

    function pintarKpisResenas(lista) {
        const prom = l => l.length ? (l.reduce((s, x) => s + x.estrellas, 0) / l.length).toFixed(1) + '★' : '—';
        document.getElementById('resenas-kpis').innerHTML = [
            { valor: numero.format(lista.length), texto: 'Con estos filtros', nota: 'Promedio ' + prom(lista), icono: 'bx-filter-alt', color: 'icon-brand' },
            { valor: numero.format(lista.filter(x => x.estrellas >= 4).length), texto: 'Buenas', nota: 'De 4 y 5 estrellas', icono: 'bx-like', color: 'icon-success' },
            { valor: numero.format(lista.filter(x => x.estrellas === 3).length), texto: 'Regulares', nota: 'De 3 estrellas', icono: 'bx-meh', color: 'icon-warning' },
            { valor: numero.format(lista.filter(x => x.estrellas <= 2).length), texto: 'Malas', nota: 'De 1 y 2 estrellas', icono: 'bx-dislike', color: 'icon-danger' }
        ].map(tile).join('');
    }

    function tarjetaResena(x) {
        const esProducto = x.tipo === 'producto';
        const tono = x.estrellas >= 4 ? 'buena' : x.estrellas === 3 ? 'regular' : 'mala';
        const insignias = [
            esProducto
                ? '<span class="badge rounded-pill bg-warning-subtle text-warning-emphasis"><i class="bx bx-package"></i> Producto</span>'
                : '<span class="badge rounded-pill bg-primary-subtle text-primary-emphasis"><i class="bx bx-store-alt"></i> Gimnasio</span>',
            x.oculta ? '<span class="badge rounded-pill bg-secondary"><i class="bx bx-hide"></i> Oculta</span>' : '',
            !esProducto && x.enCarrusel ? '<span class="badge rounded-pill bg-success-subtle text-success-emphasis"><i class="bx bx-carousel"></i> En la portada</span>' : ''
        ].join(' ');
        return `
            <article class="admin-resena admin-resena-${tono}${x.oculta ? ' admin-resena-oculta' : ''}" data-id="${escapeHtml(x.id)}" data-tipo="${x.tipo}">
                <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2">
                    <div>
                        ${estrellas(x.estrellas)}
                        <div class="small fw-semibold mt-1">${escapeHtml(esProducto ? x.producto : 'Opinión sobre el gimnasio')}<span class="text-body-secondary fw-normal"> · ${escapeHtml(x.gimnasio)}</span></div>
                    </div>
                    <div class="d-flex flex-wrap gap-1 justify-content-end">${insignias}</div>
                </div>
                <p class="mb-3 admin-resena-texto">${x.texto ? escapeHtml(x.texto) : '<span class="text-body-secondary fst-italic">Sin comentario, solo estrellas.</span>'}</p>
                <div class="d-flex flex-wrap justify-content-between align-items-center gap-2">
                    <div class="small text-body-secondary" style="min-width:0">
                        <i class="bx bx-user" aria-hidden="true"></i> <span class="fw-semibold text-body">${escapeHtml(x.autor)}</span>${x.rolAutor ? ' (' + escapeHtml(x.rolAutor) + ')' : ''}
                        ${x.email ? ' · ' + escapeHtml(x.email) : ''}
                        · ${new Date(x.fecha).toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' })}
                    </div>
                    <button type="button" class="btn btn-sm rounded-pill px-3 ${x.oculta ? 'btn-outline-success' : 'btn-outline-secondary'}" data-visibilidad="${x.oculta ? 'mostrar' : 'ocultar'}">
                        <i class="bx ${x.oculta ? 'bx-show' : 'bx-hide'}" aria-hidden="true"></i> ${x.oculta ? 'Volver a mostrar' : 'Ocultar'}
                    </button>
                </div>
            </article>`;
    }

    function pintarResenas() {
        const lista = filtrarResenas();
        pintarKpisResenas(lista);
        document.getElementById('resenas-conteo').textContent = lista.length
            ? (lista.length > visibles.resenas ? 'Mostrando ' + visibles.resenas + ' de ' + numero.format(lista.length) : contar(lista.length, 'resultado', 'resultados', 0)) + ', lo más reciente primero'
            : '';
        document.getElementById('resenas-mas-caja').classList.toggle('d-none', lista.length <= visibles.resenas);
        document.getElementById('resenas-lista').innerHTML = lista.length
            ? lista.slice(0, visibles.resenas).map(tarjetaResena).join('')
            : vacio('No hay reseñas con estos filtros.', 'bx-star');
    }

    document.querySelectorAll('input[name="resenas-tipo"], input[name="resenas-cal"], input[name="resenas-vis"], #resenas-gym').forEach(el =>
        el.addEventListener('change', () => { visibles.resenas = POR_PAGINA; datos.resenas && pintarResenas(); }));
    document.getElementById('resenas-buscar').addEventListener('input', () => { visibles.resenas = POR_PAGINA; datos.resenas && pintarResenas(); });
    document.getElementById('resenas-mas').addEventListener('click', () => { visibles.resenas += POR_PAGINA; pintarResenas(); });

    // Ocultar / volver a mostrar: la reseña deja de contar en el promedio del
    // producto; la opinión sale (o regresa) al carrusel de la portada.
    document.getElementById('resenas-lista').addEventListener('click', e => {
        const boton = e.target.closest('[data-visibilidad]');
        if (!boton) return;
        const tarjeta = boton.closest('.admin-resena');
        const x = datos.resenas.find(r => r.id === tarjeta.dataset.id && r.tipo === tarjeta.dataset.tipo);
        if (!x) return;
        const oculta = boton.dataset.visibilidad === 'ocultar';
        boton.disabled = true;
        const ruta = x.tipo === 'producto' ? '/api/admin/resenas/' : '/api/admin/opiniones/';
        api(ruta + encodeURIComponent(x.id) + '/visibilidad', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ oculta })
        }).then(r => {
            x.oculta = r.oculta;
            if ('enCarrusel' in r) x.enCarrusel = r.enCarrusel;
            pintarResenas();
            pintarInsigniaMalas();
            avisar(oculta
                ? (x.tipo === 'producto' ? 'Reseña oculta: ya no cuenta en el promedio del producto.' : 'Opinión oculta: ya no sale en la portada.')
                : (x.tipo === 'producto' ? 'La reseña se volvió a mostrar.' : 'La opinión se volvió a mostrar' + (x.enCarrusel ? ' y regresa a la portada.' : '.')));
        }).catch(err => { boton.disabled = false; avisar(err.message); });
    });

    // ═══════════════════════════ BITÁCORA ═══════════════════════════

    const SESION = ['LOGIN_EXITOSO', 'LOGIN_FALLIDO', 'LOGIN_BLOQUEADO', 'CONTRASENA_CAMBIADA', 'CONTRASENA_RESTABLECIDA', 'CORREO_CAMBIADO'];
    const ALERTA = ['LOGIN_FALLIDO', 'LOGIN_BLOQUEADO', 'ACCESO_DENEGADO'];
    const MODERACION = ['RESENA_MODERADA', 'OPINION_MODERADA'];
    const ICONOS = {
        LOGIN_EXITOSO: ['bx-log-in', 'icon-success'], LOGIN_FALLIDO: ['bx-error', 'icon-danger'],
        LOGIN_BLOQUEADO: ['bx-lock-alt', 'icon-danger'], ACCESO_DENEGADO: ['bx-block', 'icon-danger'],
        CONTRASENA_CAMBIADA: ['bx-key', 'icon-warning'], CONTRASENA_RESTABLECIDA: ['bx-key', 'icon-warning'],
        CORREO_CAMBIADO: ['bx-envelope', 'icon-info'], RESENA_MODERADA: ['bx-hide', 'icon-brand'],
        OPINION_MODERADA: ['bx-hide', 'icon-brand'], VENTA_MOSTRADOR: ['bx-store', 'icon-success'],
        PAGO_REGISTRADO: ['bx-credit-card', 'icon-success'], PEDIDO_REEMBOLSADO: ['bx-undo', 'icon-warning'],
        PEDIDO_CANCELADO: ['bx-x-circle', 'icon-warning'], PEDIDO_ENTREGADO: ['bx-package', 'icon-info']
    };

    function dispositivo(agente) {
        if (!agente) return 'Desconocido';
        if (/okhttp|CFNetwork|Expo|Dalvik/i.test(agente)) return 'App GymTrack';
        const so = /Android/i.test(agente) ? 'Android' : /iPhone|iPad/i.test(agente) ? 'iOS' : /Windows/i.test(agente) ? 'Windows' : /Mac OS/i.test(agente) ? 'macOS' : /Linux/i.test(agente) ? 'Linux' : '';
        const nav = /Edg\//.test(agente) ? 'Edge' : /OPR\//.test(agente) ? 'Opera' : /Chrome\//.test(agente) ? 'Chrome' : /Firefox\//.test(agente) ? 'Firefox' : /Safari\//.test(agente) ? 'Safari' : /node/i.test(agente) ? 'Script' : 'Otro';
        return nav + (so ? ' en ' + so : '');
    }

    function cargarBitacora(forzar) {
        const tbody = document.getElementById('bitacora-tbody');
        if (!forzar && datos.bitacora) return pintarBitacora();
        cargando(tbody, 'Cargando la bitácora…', 4);
        api('/api/admin/bitacora?limite=1000')
            .then(b => { datos.bitacora = b; pintarBitacora(); })
            .catch(err => error(tbody, err.message, () => cargarBitacora(true), 4));
    }

    function pintarBitacora() {
        const semana = datos.bitacora.filter(e => new Date(e.fecha).getTime() >= Date.now() - 7 * 86400000);
        const cuantos = tipos => numero.format(semana.filter(e => tipos.includes(e.tipo)).length);
        document.getElementById('bitacora-kpis').innerHTML = [
            { valor: cuantos(['LOGIN_EXITOSO']), texto: 'Inicios de sesión', nota: 'Últimos 7 días', icono: 'bx-log-in', color: 'icon-success' },
            { valor: cuantos(['LOGIN_FALLIDO', 'LOGIN_BLOQUEADO']), texto: 'Contraseñas incorrectas', nota: 'Últimos 7 días', icono: 'bx-error', color: 'icon-danger' },
            { valor: cuantos(['ACCESO_DENEGADO']), texto: 'Accesos negados', nota: 'Últimos 7 días', icono: 'bx-block', color: 'icon-warning' },
            { valor: numero.format(new Set(semana.filter(e => e.tipo === 'LOGIN_EXITOSO').map(e => e.userId)).size), texto: 'Cuentas activas', nota: 'Entraron en 7 días', icono: 'bx-user-check', color: 'icon-brand' }
        ].map(tile).join('');

        const tipo = elegido('bitacora-filtro');
        const q = document.getElementById('bitacora-buscar').value;
        const lista = datos.bitacora.filter(e => (!tipo
                || (tipo === 'alertas' && ALERTA.includes(e.tipo))
                || (tipo === 'sesion' && SESION.includes(e.tipo))
                || (tipo === 'admin' && MODERACION.includes(e.tipo)))
            && contiene(q, e.email, e.gimnasio, e.ip, e.descripcion));
        document.getElementById('bitacora-conteo').textContent = lista.length
            ? contar(lista.length, 'evento', 'eventos', 0) + (lista.length > visibles.bitacora ? ' · mostrando los ' + visibles.bitacora + ' más recientes' : '')
            : '';
        document.getElementById('bitacora-mas-caja').classList.toggle('d-none', lista.length <= visibles.bitacora);
        document.getElementById('bitacora-tbody').innerHTML = lista.length ? lista.slice(0, visibles.bitacora).map(e => {
            const [icono, color] = ICONOS[e.tipo] || ['bx-edit', 'icon-info'];
            return `
                <tr${e.ruta ? ' title="' + escapeHtml((e.metodo || '') + ' ' + e.ruta) + '"' : ''}>
                    <td class="ps-4">
                        <div class="d-flex align-items-center gap-3">
                            <div class="icon-circle ${color} rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width:36px;height:36px">
                                <i class='bx ${icono}' aria-hidden="true"></i>
                            </div>
                            <div style="min-width:0">
                                <div class="fw-semibold">${escapeHtml(e.descripcion)}${ALERTA.includes(e.tipo) ? ' <span class="badge rounded-pill bg-danger-subtle text-danger-emphasis ms-1">Alerta</span>' : ''}</div>
                                ${e.email ? '<div class="small text-body-secondary text-truncate" style="max-width:320px">' + escapeHtml(e.email) + '</div>' : ''}
                            </div>
                        </div>
                    </td>
                    <td class="text-nowrap">${fecha(e.fecha, true)}</td>
                    <td>${e.gimnasio ? escapeHtml(e.gimnasio) : '<span class="text-body-secondary">—</span>'}</td>
                    <td class="pe-4 text-nowrap">${escapeHtml(dispositivo(e.agente))}<div class="small text-body-secondary">${escapeHtml(e.ip || '')}</div></td>
                </tr>`;
        }).join('') : vacio('No hay eventos con estos filtros.', 'bx-shield', 4);
    }

    document.querySelectorAll('input[name="bitacora-filtro"]').forEach(el =>
        el.addEventListener('change', () => { visibles.bitacora = POR_PAGINA; datos.bitacora && pintarBitacora(); }));
    document.getElementById('bitacora-buscar').addEventListener('input', () => { visibles.bitacora = POR_PAGINA; datos.bitacora && pintarBitacora(); });
    document.getElementById('bitacora-mas').addEventListener('click', () => { visibles.bitacora += POR_PAGINA; pintarBitacora(); });

    // ═══════════════════════════ ARRANQUE ═══════════════════════════

    const CARGAS = { resumen: cargarResumen, gimnasios: cargarGimnasios, cuentas: cargarCuentas, resenas: cargarResenas, bitacora: cargarBitacora };

    const nombre = localStorage.getItem('userName') || 'equipo';
    document.getElementById('nav-user-name').textContent = nombre;
    document.getElementById('nav-avatar').textContent = iniciales(nombre);
    document.getElementById('dash-user-name').textContent = nombre;
    document.getElementById('cerrar-sesion').addEventListener('click', e => { e.preventDefault(); Sesion.cerrar(); });
    document.getElementById('actualizar-todo').addEventListener('click', () => {
        Object.keys(datos).forEach(k => { datos[k] = null; });
        cargarResumen(true);
    });

    cargarResumen();
})();
