/* Panel del dueño (bienvenido.html): Resumen. */

/* ═══════════════════════════════════════
   RESUMEN — primeros pasos, ingresos, ventas recientes y pendientes
   Miembros, máquinas, ventas y pagos salen de la base. Planes y
   productos se toman de lo que ya cargaron sus pestañas.
═══════════════════════════════════════ */
const PASOS_INICIO = [
    { clave: 'gimnasio', titulo: 'Configura tu gimnasio', texto: 'Nombre, horario y cuota mensual. Con eso tus miembros te encuentran en la app.', boton: 'Configurar', destino: 'gimnasio' },
    { clave: 'planes', titulo: 'Crea tus planes de membresía', texto: 'Mensualidad, visita, semana: lo que cobras en recepción y vendes en la tienda.', boton: 'Crear planes', destino: 'planes' },
    { clave: 'miembros', titulo: 'Suma a tus miembros', texto: 'Comparte tu código de acceso o dales de alta tú mismo en recepción.', boton: 'Agregar usuario', destino: 'usuarios', abrir: 'member-new-btn' },
    { clave: 'productos', titulo: 'Sube tus productos', texto: 'Suplementos, bebidas o accesorios para el mostrador y la tienda.', boton: 'Nuevo producto', destino: 'productos', abrir: 'producto-nuevo-btn' },
    { clave: 'maquinas', titulo: 'Registra tus máquinas', texto: 'La app arma las rutinas con el equipo que de verdad tienes.', boton: 'Agregar máquinas', destino: 'maquinas' }
];
const RESUMEN_DIAS = 14;
// Cuántos hay de cada cosa; null mientras todavía no se sabe.
const resumenConteos = { planes: null, productos: null, miembros: null, maquinas: null };
let resumenTurno = 0;

function initResumen() {
    document.addEventListener('panel:mostrado', (e) => { if (e.detail === 'resumen') cargarResumen(); });
    document.getElementById('resumen-pasos-ocultar').addEventListener('click', () => {
        localStorage.setItem('pasosOcultos', localStorage.getItem('gymId') || '');
        pintarPasos();
    });
    cargarResumen();
}

function cargarResumen() {
    const gymId = localStorage.getItem('gymId');
    const turno = ++resumenTurno;
    const pendientes = document.getElementById('resumen-pendientes');
    const fechaLarga = new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'America/Mexico_City' });
    document.getElementById('resumen-fecha').textContent = fechaLarga.charAt(0).toUpperCase() + fechaLarga.slice(1);
    document.getElementById('resumen-detalle').classList.toggle('d-none', !gymId);
    document.getElementById('resumen-subtitulo').textContent = gymId
        ? 'Así va tu gimnasio hoy.'
        : 'Empieza por configurar tu gimnasio: con eso se activa todo lo demás.';
    if (!gymId) {
        document.getElementById('resumen-kpis').innerHTML = '';
        pendientes.innerHTML = '<p class="text-body-secondary mb-0">Aquí verás las solicitudes nuevas y los cobros pendientes en cuanto configures tu gimnasio.</p>';
        document.getElementById('resumen-ventas-total').textContent = '';
        document.getElementById('resumen-ventas').innerHTML = '<p class="text-body-secondary mb-0">Aquí verás cuánto vendes cada día en cuanto configures tu gimnasio.</p>';
        pintarPasos();
        return;
    }
    pintarKpisResumen(null);
    pendientes.innerHTML = '<p class="text-body-secondary mb-0"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Revisando pendientes…</p>';
    if (!document.querySelector('#resumen-ventas .resumen-barras')) {
        ['resumen-ventas', 'resumen-vencen', 'resumen-top'].forEach(id => {
            document.getElementById(id).innerHTML = '<div class="resumen-espera" aria-hidden="true"></div>';
        });
    }

    const hoy = fechaMx(new Date());
    const leer = (url) => fetch(url).then(r => r.ok ? r.json() : Promise.reject(new Error('No se pudo cargar.')));
    Promise.allSettled([
        leer('/api/gyms/' + gymId + '/members'),
        leer('/api/gyms/' + gymId + '/machines'),
        // Las cifras de hoy y del mes vienen aparte del periodo: se pide el de las barras.
        apiTienda('/api/gyms/' + gymId + '/ventas/resumen?desde=' + sumarDias(hoy, -(RESUMEN_DIAS - 1)) + '&hasta=' + hoy),
        apiTienda('/api/gyms/' + gymId + '/payments?desde=' + hoy.slice(0, 8) + '01&limite=500')
    ]).then(([miembros, maquinas, ventas, pagos]) => {
        if (turno !== resumenTurno) return;
        const valor = (r) => r.status === 'fulfilled' ? r.value : null;
        if (valor(miembros)) resumenConteos.miembros = valor(miembros).length;
        if (valor(maquinas)) resumenConteos.maquinas = valor(maquinas).length;
        pintarPasos();
        pintarKpisResumen({ hoy, miembros: valor(miembros), ventas: valor(ventas), pagos: valor(pagos) });
        pintarPendientes(valor(miembros), valor(ventas));
        pintarVentasResumen(valor(ventas));
        pintarVencimientos(valor(miembros));
    });
}

// Una barra por día. Al pasar el cursor (o el foco) se lee el día debajo;
// sin tocar nada se lee el mejor día del periodo.
function pintarVentasResumen(ventas) {
    const contenedor = document.getElementById('resumen-ventas');
    const total = document.getElementById('resumen-ventas-total');
    const top = document.getElementById('resumen-top');
    if (!ventas || !ventas.porDia) {
        total.textContent = '';
        contenedor.innerHTML = '<p class="text-body-secondary mb-0">No se pudieron cargar las ventas.</p>';
        top.innerHTML = '<p class="text-body-secondary mb-0">No se pudo cargar.</p>';
        return;
    }
    pintarBarrasVentas(top, (ventas.topProductos || []).slice(0, 5).map(p => ({
        nombre: p.titulo, total: p.total,
        extra: (p.tipo === 'membresia' ? 'Plan · ' : '') + p.cantidad + (p.cantidad === 1 ? ' vendido' : ' vendidos')
    })), 'Cuando vendas algo, aquí verás qué se lleva más la gente.', true);

    const dias = ventas.porDia;
    const mayor = Math.max(0, ...dias.map(d => d.total));
    total.textContent = dinero(ventas.periodo.total) + ' en ' + textoPedidos(ventas.periodo.pedidos);
    if (!mayor) {
        contenedor.innerHTML = '<p class="text-body-secondary mb-0">Todavía no hay ventas en estos días. Cobra la primera desde el Mostrador.</p>';
        return;
    }
    const texto = d => diaCorto(d.fecha) + ' · ' + dinero(d.total) + ' · ' + textoPedidos(d.pedidos);
    const mejor = dias.reduce((a, b) => (b.total > a.total ? b : a));
    const reposo = 'Mejor día: ' + texto(mejor);
    contenedor.innerHTML = '<ol class="resumen-barras">' + dias.map((d, i) => `
        <li class="${i === dias.length - 1 ? 'es-hoy' : ''}${d.total ? '' : ' sin-venta'}" tabindex="0" data-texto="${escapeHtml(texto(d))}" style="--alto:${(d.total / mayor * 100).toFixed(1)}%;--orden:${i}">
            <span class="resumen-barra" aria-hidden="true"></span>
            <span class="resumen-barra-dia" aria-hidden="true">${Number(d.fecha.slice(8))}</span>
            <span class="visually-hidden">${escapeHtml(texto(d))}</span>
        </li>`).join('') + '</ol><p class="resumen-barras-lectura small mb-0" id="resumen-ventas-lectura">' + escapeHtml(reposo) + '</p>';
    const lectura = document.getElementById('resumen-ventas-lectura');
    const lista = contenedor.querySelector('.resumen-barras');
    const leer = e => { const li = e.target.closest('li'); if (li) lectura.textContent = li.dataset.texto; };
    lista.addEventListener('mouseover', leer);
    lista.addEventListener('focusin', leer);
    lista.addEventListener('mouseleave', () => { lectura.textContent = reposo; });
    lista.addEventListener('focusout', () => { lectura.textContent = reposo; });
}

// A quién le toca renovar: los cinco que vencen antes (o ya vencieron).
function pintarVencimientos(miembros) {
    const contenedor = document.getElementById('resumen-vencen');
    if (!miembros) {
        contenedor.innerHTML = '<p class="text-body-secondary mb-0">No se pudo cargar.</p>';
        return;
    }
    const lista = miembros
        .filter(m => m.membershipStatus !== 'pending' && m.diasParaVencer != null && m.diasParaVencer >= -30)
        .sort((a, b) => a.diasParaVencer - b.diasParaVencer)
        .slice(0, 5);
    if (!lista.length) {
        contenedor.innerHTML = '<p class="text-body-secondary mb-0">Cuando registres pagos, aquí verás a quién le toca renovar.</p>';
        return;
    }
    const cuando = n => n < 0 ? { texto: n === -1 ? 'Venció ayer' : 'Venció hace ' + (-n) + ' días', clase: 'es-vencida' }
        : n === 0 ? { texto: 'Vence hoy', clase: 'es-pronto' }
        : n === 1 ? { texto: 'Vence mañana', clase: 'es-pronto' }
        : { texto: 'En ' + n + ' días', clase: n <= 5 ? 'es-pronto' : '' };
    contenedor.innerHTML = '<ul class="list-unstyled mb-0 resumen-vencen">' + lista.map(m => {
        const c = cuando(m.diasParaVencer);
        return `
            <li>
                <div class="persona">${avatar(m.nombre)}<div class="persona-texto"><div class="fw-semibold">${escapeHtml(m.nombre)}</div><div class="text-body-secondary small">${escapeHtml(m.planActual || 'Membresía')}${m.fechaProximoPago ? ' · ' + escapeHtml(fechaCorta(m.fechaProximoPago)) : ''}</div></div></div>
                <span class="resumen-plazo ${c.clase}">${c.texto}</span>
            </li>`;
    }).join('') + '</ul>';
}

// Una guía de cinco pasos que se muestra mientras falte alguno (o hasta
// que el dueño la oculte). El siguiente paso lleva el botón principal.
function pintarPasos() {
    const gymId = localStorage.getItem('gymId');
    const seccion = document.getElementById('resumen-pasos');
    // true: listo · false: falta · null: todavía no se sabe
    const estados = PASOS_INICIO.map(p => {
        if (p.clave === 'gimnasio') return !!gymId;
        if (!gymId) return false;
        return resumenConteos[p.clave] == null ? null : resumenConteos[p.clave] > 0;
    });
    const oculta = !!gymId && localStorage.getItem('pasosOcultos') === gymId;
    seccion.classList.toggle('d-none', oculta || !estados.includes(false));
    if (seccion.classList.contains('d-none')) return;

    const listos = estados.filter(e => e === true).length;
    const total = PASOS_INICIO.length;
    const siguiente = estados.indexOf(false);
    document.getElementById('resumen-pasos-avance').textContent = listos + ' de ' + total + ' listos';
    const progreso = document.getElementById('resumen-pasos-progreso');
    progreso.setAttribute('aria-valuemin', '0');
    progreso.setAttribute('aria-valuemax', String(total));
    progreso.setAttribute('aria-valuenow', String(listos));
    progreso.setAttribute('aria-valuetext', listos + ' de ' + total + ' pasos listos');
    document.getElementById('resumen-pasos-barra').style.width = (listos / total * 100) + '%';
    document.getElementById('resumen-pasos-ocultar').classList.toggle('d-none', !gymId);
    document.getElementById('resumen-pasos-lista').innerHTML = PASOS_INICIO.map((p, i) => {
        const e = estados[i];
        let accion = '';
        if (e === null) {
            accion = '<span class="spinner-border spinner-border-sm text-body-secondary flex-shrink-0" role="status"><span class="visually-hidden">Revisando…</span></span>';
        } else if (e === false && (gymId || p.clave === 'gimnasio')) {
            accion = '<button type="button" class="btn ' + (i === siguiente ? 'btn-primary' : 'btn-outline-primary') + ' btn-sm rounded-pill px-3 flex-shrink-0" data-goto="'
                + p.destino + '"' + (p.abrir ? ' data-abrir="' + p.abrir + '"' : '') + '>' + p.boton + '</button>';
        }
        return `
            <li class="resumen-paso${e ? ' hecho' : i === siguiente ? ' siguiente' : ''}">
                <span class="resumen-paso-marca" aria-hidden="true">${e ? "<i class='bx bx-check'></i>" : i + 1}</span>
                <div class="resumen-paso-texto">
                    <div class="fw-semibold">${e ? '<span class="visually-hidden">Listo: </span>' : ''}${p.titulo}</div>
                    ${e ? '' : '<div class="small text-body-secondary">' + p.texto + '</div>'}
                </div>
                ${accion}
            </li>`;
    }).join('');
}

// Ingresos = ventas (tienda, app y mostrador) + pagos registrados a mano en
// "Membresías y Pagos". Los pagos que vienen de un pedido ya están en ventas.
function pintarKpisResumen(d) {
    const tiles = [
        { titulo: 'Ingresos de hoy', icono: 'bx-calendar-check' },
        { titulo: 'Ingresos del mes', icono: 'bx-calendar' },
        { titulo: 'Miembros activos', icono: 'bx-group' }
    ];
    if (d) {
        const aMano = (d.pagos || []).filter(p => !p.orderId && !p.reembolsadoEn);
        const sumar = (lista) => lista.reduce((s, p) => s + (Number(p.monto) || 0), 0);
        const pagosHoy = sumar(aMano.filter(p => p.fechaPago === d.hoy));
        const pagosMes = sumar(aMano);
        const k = d.ventas && d.ventas.kpis;
        if (k && d.pagos) {
            tiles[0].valor = dinero(k.hoy.total + pagosHoy);
            tiles[0].detalle = 'Ventas ' + dinero(k.hoy.total) + ' · Pagos ' + dinero(pagosHoy);
            tiles[1].valor = dinero(k.mes.total + pagosMes);
            tiles[1].detalle = 'Ventas ' + dinero(k.mes.total) + ' · Pagos ' + dinero(pagosMes);
        }
        if (d.miembros) {
            const activos = d.miembros.filter(m => m.membershipStatus === 'active');
            const alDia = activos.filter(m => m.diasParaVencer != null && m.diasParaVencer >= 0).length;
            tiles[2].valor = String(activos.length);
            tiles[2].detalle = alDia + ' con su pago al día';
        }
    }
    document.getElementById('resumen-kpis').innerHTML = tiles.map((t, i) => `
        <div class="${i === 2 ? 'col-12' : 'col-6'} col-md-4">
            <div class="kpi-tile h-100">
                <div class="kpi-cabeza"><span class="kpi-titulo">${t.titulo}</span><i class="bx ${t.icono}" aria-hidden="true"></i></div>
                <div class="kpi-value">${t.valor ? escapeHtml(t.valor) : '—'}</div>
                <small class="kpi-detalle">${t.detalle ? escapeHtml(t.detalle) : (d ? 'No se pudo cargar.' : 'Cargando…')}</small>
            </div>
        </div>`).join('');
}

// Solo lo que pide una acción del dueño, cada cosa con el botón que la resuelve.
function pintarPendientes(miembros, ventas) {
    const contenedor = document.getElementById('resumen-pendientes');
    if (!miembros) {
        contenedor.innerHTML = '<p class="text-body-secondary mb-2">No pudimos revisar tus pendientes.</p>'
            + '<button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" id="resumen-reintentar">Reintentar</button>';
        document.getElementById('resumen-reintentar').addEventListener('click', cargarResumen);
        return;
    }
    const plural = (n, uno, varios) => n + ' ' + (n === 1 ? uno : varios);
    const cobranza = miembros.filter(m => m.membershipStatus !== 'pending');
    const solicitudes = miembros.length - cobranza.length;
    // Las vencidas de hace más de un mes ya no son un cobro del día: siguen en Membresías y Pagos.
    const vencidas = cobranza.filter(m => m.diasParaVencer != null && m.diasParaVencer < 0 && m.diasParaVencer >= -30).length;
    const porVencer = cobranza.filter(m => m.diasParaVencer != null && m.diasParaVencer >= 0 && m.diasParaVencer <= 5).length;
    const sinPagos = cobranza.filter(m => m.membershipStatus === 'active' && m.diasParaVencer == null).length;
    const paynet = ventas && ventas.kpis ? ventas.kpis.paynetPendientes : null;
    const filas = [
        solicitudes && { icono: 'bx-user-plus', texto: plural(solicitudes, 'solicitud para unirse', 'solicitudes para unirse'), boton: 'Revisar', destino: 'usuarios' },
        vencidas && { icono: 'bx-error-circle', texto: plural(vencidas, 'membresía vencida', 'membresías vencidas') + ' en los últimos 30 días', boton: 'Cobrar', destino: 'membresias' },
        porVencer && { icono: 'bx-time-five', texto: plural(porVencer, 'membresía vence', 'membresías vencen') + ' en los próximos 5 días', boton: 'Ver', destino: 'membresias' },
        sinPagos && { icono: 'bx-wallet', texto: plural(sinPagos, 'miembro activo sin pagos registrados', 'miembros activos sin pagos registrados'), boton: 'Registrar', destino: 'membresias' },
        paynet && paynet.pedidos && { icono: 'bx-store', texto: plural(paynet.pedidos, 'ficha de pago por cobrar', 'fichas de pago por cobrar') + ' (' + dinero(paynet.total) + ')', boton: 'Ver', destino: 'ventas' }
    ].filter(Boolean);
    contenedor.innerHTML = filas.length
        ? '<ul class="list-unstyled mb-0 resumen-pendientes">' + filas.map(f => `
            <li>
                <i class="bx ${f.icono}" aria-hidden="true"></i>
                <span class="flex-grow-1">${escapeHtml(f.texto)}</span>
                <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3 flex-shrink-0" data-goto="${f.destino}">${f.boton}<span class="visually-hidden">: ${escapeHtml(f.texto)}</span></button>
            </li>`).join('') + '</ul>'
        : '<div class="resumen-al-dia"><i class="bx bx-check-circle" aria-hidden="true"></i><p class="mb-0"><strong>Todo al día</strong>No hay solicitudes ni cobros pendientes.</p></div>';
}
