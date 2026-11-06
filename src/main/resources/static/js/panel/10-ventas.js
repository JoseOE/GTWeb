/* Panel del dueño (bienvenido.html): Ventas. */

/* ═══════════════════════════════════════
   VENTAS — /api/gyms/{id}/ventas
   KPIs, gráficas y la lista de pedidos del periodo elegido, y el detalle con sus
   acciones (entregar, recibos, cancelar). Todo sale de
   la colección de pedidos.
═══════════════════════════════════════ */
const COLORES_VENTAS = ['#f45124', '#20221f', '#8a8c82', '#f2a541', '#5b8def'];
const CANALES_VENTAS = { web: 'Página web', app: 'App', mostrador: 'Mostrador' };
const ESTADOS_VENTAS = {
    pagado: { texto: 'Pagado', clase: 'bg-success-subtle text-success-emphasis' },
    pendiente_pago: { texto: 'Pendiente de pago', clase: 'bg-warning-subtle text-warning-emphasis' },
    cancelado: { texto: 'Cancelado', clase: 'bg-secondary-subtle text-secondary-emphasis' },
    reembolsado: { texto: 'Reembolsado', clase: 'bg-danger-subtle text-danger-emphasis' }
};
let ventasPagina = 0;
let ventasTurno = 0;
let ventaActual = null;
let graficasVentas = [];
let chartJsCargando = null;

function initVentas() {
    const detalleModal = new bootstrap.Modal(document.getElementById('ventaDetalleModal'));

    document.getElementById('tab-ventas').addEventListener('click', cargarVentas);
    document.getElementById('ventas-actualizar').addEventListener('click', cargarVentas);
    // El periodo manda en las gráficas y en la lista de pedidos.
    document.getElementById('ventas-periodo').addEventListener('change', () => { ventasPagina = 0; cargarVentas(); });
    document.getElementById('ventas-anterior').addEventListener('click', () => { ventasPagina--; cargarPedidosVentas(); });
    document.getElementById('ventas-siguiente').addEventListener('click', () => { ventasPagina++; cargarPedidosVentas(); });

    document.getElementById('ventas-tbody').addEventListener('click', (e) => {
        const fila = e.target.closest('[data-venta]');
        if (!fila) return;
        avisoVenta('');
        document.getElementById('venta-detalle-acciones').innerHTML = '';
        estadoCargando(document.getElementById('venta-detalle-cuerpo'), 'Cargando el pedido…');
        detalleModal.show();
        apiTienda(rutaVenta(fila.dataset.venta))
            .then(pintarDetalleVenta)
            .catch(err => estadoError(document.getElementById('venta-detalle-cuerpo'), err.message, () => fila.click()));
    });
    document.getElementById('venta-detalle-acciones').addEventListener('click', (e) => {
        const boton = e.target.closest('[data-accion]');
        if (boton) accionVenta(boton.dataset.accion, boton);
    });
}

function rutaVenta(orderId) {
    return '/api/gyms/' + localStorage.getItem('gymId') + '/ventas/pedidos/' + encodeURIComponent(orderId);
}

function cargarVentas() {
    cargarResumenVentas();
    cargarPedidosVentas();
    if (typeof Entregas !== 'undefined') Entregas.cargarPendientes();
}

// Fechas del periodo elegido (hora de México), de hoy hacia atrás.
function periodoVentas() {
    const elegido = document.querySelector('input[name="ventas-periodo"]:checked');
    const dias = Number(elegido ? elegido.value : 30);
    const hasta = fechaMx(new Date());
    return { dias, desde: sumarDias(hasta, 1 - dias), hasta };
}

// ─── Resumen: KPIs y gráficas ───
function cargarResumenVentas() {
    const gymId = localStorage.getItem('gymId');
    const kpis = document.getElementById('ventas-kpis');
    if (!gymId) {
        kpis.innerHTML = '<div class="col-12">' + avisoSinGimnasio('Configura primero "Mi Gimnasio" para ver tus ventas.', 'bx-line-chart') + '</div>';
        return;
    }
    const { desde, hasta } = periodoVentas();
    kpis.innerHTML = '<div class="col-12" id="ventas-kpis-estado"></div>';
    estadoCargando(document.getElementById('ventas-kpis-estado'), 'Cargando tus ventas…');
    apiTienda('/api/gyms/' + gymId + '/ventas/resumen?desde=' + desde + '&hasta=' + hasta)
        .then(pintarResumenVentas)
        .catch(err => estadoError(document.getElementById('ventas-kpis-estado'), err.message, cargarResumenVentas));
}

function pintarResumenVentas(r) {
    const k = r.kpis;
    const tiles = [
        { valor: dinero(k.hoy.total), texto: 'Ventas de hoy', detalle: textoPedidos(k.hoy.pedidos), icono: 'bx-calendar-check', color: 'icon-brand' },
        { valor: dinero(k.mes.total), texto: 'Ventas del mes', detalle: textoPedidos(k.mes.pedidos), icono: 'bx-calendar', color: 'icon-success' },
        { valor: dinero(k.ticketPromedio), texto: 'Ticket promedio', detalle: 'Este mes', icono: 'bx-receipt', color: 'icon-info' },
        { valor: String(k.paynetPendientes.pedidos), texto: 'Fichas de pago por cobrar', detalle: dinero(k.paynetPendientes.total), icono: 'bx-store', color: 'icon-warning' },
        { valor: String(k.porEntregar || 0), texto: 'Pedidos por entregar', detalle: 'Pagados, sin recoger', icono: 'bx-package', color: 'icon-info' },
        { valor: String(k.membresiasDelMes), texto: 'Membresías vendidas', detalle: 'Este mes, en la tienda', icono: 'bx-purchase-tag-alt', color: 'icon-brand' }
    ];
    document.getElementById('ventas-kpis').innerHTML = tiles.map((t, i) => `
        <div class="${i === tiles.length - 1 && tiles.length % 2 ? 'col-12' : 'col-6'} col-md-4 col-xl">
            <div class="kpi-tile h-100">
                <div class="icon-circle ${t.color} rounded-circle d-flex align-items-center justify-content-center mb-3" style="width:42px;height:42px">
                    <i class='bx ${t.icono}' aria-hidden="true"></i>
                </div>
                <div class="ventas-dinero fw-bold">${escapeHtml(t.valor)}</div>
                <div class="small fw-semibold">${t.texto}</div>
                <small class="text-body-secondary">${escapeHtml(t.detalle)}</small>
            </div>
        </div>`).join('');

    // Con 12 meses el periodo cruza de año: "Del 15 oct 2025 al 14 oct 2026".
    const fecha = r.desde.slice(0, 4) === r.hasta.slice(0, 4) ? diaCorto : diaConAnio;
    document.getElementById('ventas-periodo-texto').textContent = (r.desde === r.hasta ? diaCorto(r.hasta) : 'Del ' + fecha(r.desde) + ' al ' + fecha(r.hasta))
        + ' · ' + dinero(r.periodo.total) + ' en ' + textoPedidos(r.periodo.pedidos);
    pintarBarrasVentas(document.getElementById('ventas-top'), r.topProductos.map(p => ({
        nombre: p.titulo, total: p.total,
        extra: (p.tipo === 'membresia' ? 'Plan · ' : '') + p.cantidad + (p.cantidad === 1 ? ' vendido' : ' vendidos')
    })), 'Todavía no hay ventas en el periodo.', true);
    pintarBarrasVentas(document.getElementById('ventas-canales'), r.porCanal.map(c => ({
        nombre: CANALES_VENTAS[c.nombre] || c.nombre, total: c.total, extra: textoPedidos(c.pedidos)
    })), 'Todavía no hay ventas en el periodo.', false);
    pintarGraficasVentas(r);
}

// Lista con barras proporcionales (lo más vendido y los canales).
function pintarBarrasVentas(contenedor, filas, vacio, numerar) {
    const maximo = Math.max(0, ...filas.map(f => f.total));
    contenedor.innerHTML = filas.length ? filas.map((f, i) => `
        <div class="mb-3">
            <div class="d-flex justify-content-between gap-2 small">
                <span class="fw-semibold text-truncate">${numerar ? (i + 1) + '. ' : ''}${escapeHtml(f.nombre)}</span>
                <strong class="text-nowrap">${dinero(f.total)}</strong>
            </div>
            <div class="ventas-barra my-1" aria-hidden="true"><span style="width:${maximo ? Math.max(3, f.total / maximo * 100) : 0}%"></span></div>
            <small class="text-body-secondary">${escapeHtml(f.extra)}</small>
        </div>`).join('')
        : '<p class="text-body-secondary small mb-0">' + vacio + '</p>';
}

// Chart.js se carga la primera vez que se abre la pestaña. Si no carga
// (sin internet, CDN bloqueado), las gráficas se muestran como lista.
function cargarChartJs() {
    if (window.Chart) return Promise.resolve(window.Chart);
    if (!chartJsCargando) {
        chartJsCargando = new Promise(resolve => {
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js';
            script.onload = () => resolve(window.Chart || null);
            script.onerror = () => { chartJsCargando = null; resolve(null); };
            document.head.appendChild(script);
        });
    }
    return chartJsCargando;
}

function pintarGraficasVentas(r) {
    const dias = document.getElementById('ventas-dias');
    const metodos = document.getElementById('ventas-metodos');
    cargarChartJs().then(Chart => {
        graficasVentas.forEach(g => g.destroy());
        graficasVentas = [];
        if (!Chart) {
            pintarBarrasVentas(dias, r.porDia.filter(d => d.total > 0).map(d => ({ nombre: diaCorto(d.fecha), total: d.total, extra: textoPedidos(d.pedidos) })),
                'Sin ventas en el periodo.', false);
            pintarBarrasVentas(metodos, r.porMetodo.map(m => ({ nombre: m.nombre, total: m.total, extra: textoPedidos(m.pedidos) })),
                'Sin ventas en el periodo.', false);
            dias.style.height = metodos.style.height = 'auto';
            return;
        }
        const sinAnimacion = matchMedia('(prefers-reduced-motion: reduce)').matches;
        Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
        Chart.defaults.color = '#62645d';

        dias.innerHTML = '<canvas role="img" aria-label="Ventas por día del periodo"></canvas>';
        graficasVentas.push(new Chart(dias.querySelector('canvas'), {
            type: 'bar',
            data: {
                labels: r.porDia.map(d => diaCorto(d.fecha)),
                datasets: [{ label: 'Ventas', data: r.porDia.map(d => d.total), backgroundColor: COLORES_VENTAS[0], borderRadius: 4, maxBarThickness: 28 }]
            },
            options: {
                responsive: true, maintainAspectRatio: false, animation: sinAnimacion ? false : undefined,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { label: c => dinero(c.parsed.y) + ' · ' + textoPedidos(r.porDia[c.dataIndex].pedidos) } }
                },
                scales: {
                    y: { beginAtZero: true, ticks: { callback: v => '$' + Number(v).toLocaleString('es-MX'), maxTicksLimit: 5 } },
                    x: { grid: { display: false }, ticks: { maxRotation: 0, autoSkip: true, maxTicksLimit: 7 } }
                }
            }
        }));

        if (!r.porMetodo.length) {
            metodos.innerHTML = '<p class="text-body-secondary small mb-0 pt-2">Sin ventas en el periodo.</p>';
            metodos.style.height = 'auto';
            return;
        }
        metodos.style.height = '';
        metodos.innerHTML = '<canvas role="img" aria-label="Ventas por forma de pago: '
            + escapeHtml(r.porMetodo.map(m => m.nombre + ' ' + dinero(m.total)).join(', ')) + '"></canvas>';
        graficasVentas.push(new Chart(metodos.querySelector('canvas'), {
            type: 'doughnut',
            data: {
                labels: r.porMetodo.map(m => m.nombre),
                datasets: [{ data: r.porMetodo.map(m => m.total), backgroundColor: COLORES_VENTAS, borderWidth: 0 }]
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '62%', animation: sinAnimacion ? false : undefined,
                plugins: {
                    legend: { position: 'bottom', labels: { boxWidth: 10, usePointStyle: true } },
                    tooltip: { callbacks: { label: c => ' ' + c.label + ': ' + dinero(c.parsed) + ' (' + textoPedidos(r.porMetodo[c.dataIndex].pedidos) + ')' } }
                }
            }
        }));
    });
}

// ─── Lista de pedidos ───
function cargarPedidosVentas() {
    const gymId = localStorage.getItem('gymId');
    const tbody = document.getElementById('ventas-tbody');
    if (!gymId) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-body-secondary py-5 px-4">Configura primero "Mi Gimnasio".</td></tr>';
        return;
    }
    const { desde, hasta } = periodoVentas();
    const params = new URLSearchParams({ desde, hasta });
    params.set('pagina', ventasPagina);
    params.set('tamano', 15);
    const turno = ++ventasTurno;
    tbody.innerHTML = '<tr><td colspan="6" class="text-center text-body-secondary py-4"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Cargando pedidos…</td></tr>';
    apiTienda('/api/gyms/' + gymId + '/ventas/pedidos?' + params)
        .then(r => { if (turno === ventasTurno) pintarPedidosVentas(r); })
        .catch(err => {
            if (turno !== ventasTurno) return;
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-danger py-4 px-4">' + escapeHtml(err.message) + '</td></tr>';
        });
}

function pintarPedidosVentas(r) {
    document.getElementById('ventas-conteo').textContent = r.total
        ? (r.total === 1 ? '1 pedido' : r.total + ' pedidos') + ' · cobrado ' + dinero(r.cobrado)
        : '';
    document.getElementById('ventas-tbody').innerHTML = r.pedidos.length
        ? r.pedidos.map(v => `
            <tr class="ventas-fila" data-venta="${escapeHtml(v.orderId)}">
                <td class="ps-4">
                    <button type="button" class="btn btn-link p-0 fw-semibold text-decoration-none" data-venta="${escapeHtml(v.orderId)}">#${escapeHtml(v.folio)}</button>
                    <div class="small text-body-secondary text-nowrap">${fechaHoraVenta(v.creadoEn)}</div>
                </td>
                <td>${escapeHtml(v.cliente || '—')}<div class="small text-body-secondary">${v.articulos} ${v.articulos === 1 ? 'artículo' : 'artículos'}</div></td>
                <td class="text-nowrap">${escapeHtml(CANALES_VENTAS[v.canal] || v.canal || '')}</td>
                <td>${escapeHtml(v.metodo)}<div class="small text-body-secondary text-truncate" style="max-width:220px">${escapeHtml(v.detallePago)}</div></td>
                <td>${insigniaVenta(v.estado)}${v.porEntregar ? '<div><span class="badge rounded-pill bg-info-subtle text-info-emphasis mt-1">Por entregar</span></div>' : ''}</td>
                <td class="pe-4 text-end fw-semibold text-nowrap">${dinero(v.total)}</td>
            </tr>`).join('')
        : '<tr><td colspan="6" class="text-center text-body-secondary py-5 px-4"><i class="bx bx-receipt fs-1 d-block mb-2 opacity-50"></i>'
            + 'No hay pedidos en este periodo. Aparecen aquí al vender en la tienda, la app o el mostrador.</td></tr>';
    const paginacion = document.getElementById('ventas-paginacion');
    paginacion.classList.toggle('d-none', r.paginas <= 1);
    document.getElementById('ventas-pagina').textContent = 'Página ' + (r.pagina + 1) + ' de ' + r.paginas;
    document.getElementById('ventas-anterior').disabled = r.pagina <= 0;
    document.getElementById('ventas-siguiente').disabled = r.pagina + 1 >= r.paginas;
}

// ─── Detalle y acciones ───
function pintarDetalleVenta(d) {
    ventaActual = d;
    document.getElementById('ventaDetalleLabel').textContent = 'Pedido #' + d.folio;
    const fila = (titulo, valor) => valor ? '<dt class="col-4 text-body-secondary fw-normal">' + titulo + '</dt><dd class="col-8 mb-2">' + valor + '</dd>' : '';
    const vence = d.paynet && d.paynet.vence ? new Date(d.paynet.vence).toLocaleString('es-MX', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) : '';
    document.getElementById('venta-detalle-cuerpo').innerHTML = `
        <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
            ${insigniaVenta(d.estado)}
            <span class="small text-body-secondary">${fechaHoraVenta(d.creadoEn)} · ${escapeHtml(CANALES_VENTAS[d.canal] || d.canal || '')}</span>
        </div>
        <dl class="row small mb-2">
            ${fila('Cliente', escapeHtml(d.clienteNombre || 'Público en general') + (d.email ? '<br><span class="text-body-secondary">' + escapeHtml(d.email) + '</span>' : ''))}
            ${fila('Pago', escapeHtml(d.detallePago || d.metodo))}
            ${d.paynet ? fila('Referencia', '<span class="font-monospace">' + escapeHtml(d.paynet.referencia) + '</span>' + (d.paynet.tiendaNombre ? '<br><span class="text-body-secondary">Pagada en ' + escapeHtml(d.paynet.tiendaNombre) + ' · folio de caja ' + escapeHtml(d.paynet.folioCaja || '') + '</span>' : (vence ? '<br><span class="text-body-secondary">Vence el ' + escapeHtml(vence) + '</span>' : ''))) : ''}
            ${fila('Pagado', d.pagadoEn ? fechaHoraVenta(d.pagadoEn) : '')}
            ${fila('Cancelado', d.canceladoEn ? fechaHoraVenta(d.canceladoEn) : '')}
            ${fila('Cobró', escapeHtml(d.vendedor || ''))}
            ${d.entrega && d.entrega.estado === 'pendiente' ? fila('Entrega', '<span class="badge rounded-pill bg-info-subtle text-info-emphasis">Por entregar</span>') : ''}
            ${d.entrega && d.entrega.estado === 'entregado' && d.canal !== 'mostrador' ? fila('Entregado', fechaHoraVenta(d.entrega.entregadoEn) + (d.entregadoPor ? '<br><span class="text-body-secondary">por ' + escapeHtml(d.entregadoPor) + '</span>' : '')) : ''}
        </dl>
        <div class="border-top pt-2">
            ${d.partidas.map(p => `
                <div class="d-flex justify-content-between gap-3 py-1 small">
                    <span style="min-width:0"><span class="fw-semibold">${p.cantidad} × ${escapeHtml(p.titulo)}</span>${p.variante ? '<br><span class="text-body-secondary">' + escapeHtml(p.variante) + '</span>' : ''}</span>
                    <span class="text-nowrap">${dinero(p.total)}</span>
                </div>`).join('')}
            <div class="d-flex justify-content-between small text-body-secondary pt-2 border-top mt-1"><span>Subtotal (sin IVA)</span><span>${dinero(d.subtotal)}</span></div>
            <div class="d-flex justify-content-between small text-body-secondary"><span>IVA 16 %</span><span>${dinero(d.iva)}</span></div>
            <div class="d-flex justify-content-between fw-bold"><span>Total</span><span>${dinero(d.total)}</span></div>
        </div>
        ${d.fichaVencida ? '<div class="alert alert-warning small py-2 mt-3 mb-0">La ficha ya venció: el pedido se cancelará solo en unos minutos.</div>' : ''}`;

    const a = d.acciones;
    const botones = [
        a.entregar && ['entregar', 'btn-success', 'bx-package', 'Entregar productos'],
        a.recibo && ['recibo', 'btn-outline-primary', 'bx-receipt', 'Recibo (PDF)'],
        a.ficha && ['ficha', 'btn-outline-primary', 'bx-barcode', 'Ficha de pago (PDF)'],
        (a.recibo || a.ficha) && ['whatsapp', 'btn-outline-success', 'bxl-whatsapp', 'WhatsApp'],
        a.reenviar && ['reenviar', 'btn-outline-secondary', 'bx-envelope', 'Reenviar correo'],
        a.cancelar && ['cancelar', 'btn-outline-danger', 'bx-x-circle', 'Cancelar pedido']
    ].filter(Boolean);
    // Ficha de Paynet pendiente: en el sandbox el pago en tienda se simula en el
    // panel de Openpay y el pedido se actualiza solo al confirmarse.
    document.getElementById('venta-detalle-acciones').innerHTML = (a.ficha && d.estado === 'pendiente_pago'
            ? '<p class="w-100 small text-body-secondary mb-1"><i class="bx bx-info-circle" aria-hidden="true"></i> Para probar el pago en tienda, busca la ficha en el <a href="https://sandbox-dashboard.openpay.mx" target="_blank" rel="noopener">panel de Openpay (sandbox)</a> y simula su pago: este pedido pasa a pagado solo.</p>'
            : '')
        + botones.map(([accion, clase, icono, texto]) =>
        `<button type="button" class="btn ${clase} btn-sm rounded-pill px-3" data-accion="${accion}"><i class='bx ${icono}' aria-hidden="true"></i> ${texto}</button>`).join('');
}

function accionVenta(accion, boton) {
    const d = ventaActual;
    if (!d) return;
    if (accion === 'recibo' || accion === 'ficha') {
        // La ventana se abre antes del fetch para que el navegador no la bloquee.
        const ruta = accion === 'recibo'
            ? '/api/recibos/pedidos/' + encodeURIComponent(d.orderId) + '.pdf'
            : '/api/recibos/paynet/' + encodeURIComponent(d.orderId) + '.pdf';
        const ventana = window.open('', '_blank');
        fetch(ruta, { headers: { 'X-User-Id': localStorage.getItem('userId') || '' } })
            .then(res => res.ok ? res.blob() : res.json().catch(() => ({})).then(b => { throw new Error(b.error || 'No se pudo generar el PDF.'); }))
            .then(blob => { if (ventana) ventana.location = URL.createObjectURL(blob); })
            .catch(err => { if (ventana) ventana.close(); avisoVenta(err.message, 'danger'); });
        return;
    }
    if (accion === 'whatsapp') {
        // Enlace temporal (7 días) al comprobante o a la ficha, con el
        // mensaje listo; la persona elige a quién mandarlo en WhatsApp.
        const pestana = window.open('', '_blank');
        apiTienda('/api/recibos/pedidos/' + encodeURIComponent(d.orderId) + '/enlace', { method: 'POST' })
            .then(r => {
                const url = 'https://wa.me/?text=' + encodeURIComponent(r.texto);
                if (pestana) { pestana.opener = null; pestana.location.href = url; } else { location.href = url; }
            })
            .catch(err => { if (pestana) pestana.close(); avisoVenta(err.message, 'danger'); });
        return;
    }
    if (accion === 'cancelar' && !confirm('¿Cancelar el pedido #' + d.folio + '? La ficha deja de servir y se libera lo apartado.')) return;

    const rutas = {
        reenviar: '/api/recibos/pedidos/' + encodeURIComponent(d.orderId) + '/enviar',
        entregar: rutaVenta(d.orderId) + '/entregar',
        cancelar: rutaVenta(d.orderId) + '/cancelar'
    };
    const original = boton.innerHTML;
    boton.disabled = true;
    boton.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> ' + boton.textContent.trim();
    avisoVenta('');
    apiTienda(rutas[accion], { method: 'POST' })
        .then(r => {
            if (accion === 'reenviar') {
                avisoVenta(r.message || 'Listo.', r.enviado === false ? 'warning' : 'success');
                boton.disabled = false;
                boton.innerHTML = original;
                return;
            }
            // Las acciones responden con el detalle ya actualizado.
            pintarDetalleVenta(r);
            avisoVenta(mensajeDeAccion(accion, r), 'success');
            cargarVentas();
        })
        .catch(err => {
            avisoVenta(err.message, 'danger');
            boton.disabled = false;
            boton.innerHTML = original;
        });
}

function mensajeDeAccion(accion, r) {
    if (accion === 'entregar') return 'Listo: el pedido quedó entregado y salió del inventario.';
    return 'Pedido cancelado.';
}

function avisoVenta(mensaje, tipo) {
    const caja = document.getElementById('venta-detalle-aviso');
    caja.textContent = mensaje;
    caja.className = 'alert small py-2 mb-2 alert-' + (tipo || 'info') + (mensaje ? '' : ' d-none');
}

// ─── Formatos ───
function insigniaVenta(estado) {
    const e = ESTADOS_VENTAS[estado] || { texto: estado, clase: 'bg-secondary-subtle text-secondary-emphasis' };
    return '<span class="badge rounded-pill ' + e.clase + '">' + escapeHtml(e.texto) + '</span>';
}

function textoPedidos(n) {
    return n === 1 ? '1 pedido' : n + ' pedidos';
}

// Fechas en la hora de México, que es la del gimnasio.
function fechaMx(fecha) {
    return fecha.toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
}

function sumarDias(iso, dias) {
    const d = new Date(iso + 'T12:00:00Z');
    d.setUTCDate(d.getUTCDate() + dias);
    return d.toISOString().slice(0, 10);
}

function diaCorto(iso) {
    return new Date(iso + 'T12:00:00Z').toLocaleDateString('es-MX', { day: 'numeric', month: 'short', timeZone: 'UTC' });
}

function diaConAnio(iso) {
    return new Date(iso + 'T12:00:00Z').toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}

function fechaHoraVenta(instante) {
    return instante ? new Date(instante).toLocaleString('es-MX', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'America/Mexico_City' }) : '';
}
