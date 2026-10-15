/* ═══════════════════════════════════════
   Entregas en recepción (pestaña Ventas del panel)
   ───────────────────────────────────────
   Lo que se compra en la app (o en la tienda web) se paga desde casa y se
   recoge en el gimnasio. El miembro muestra el QR de su comprobante:
     1. El dueño pulsa "Entregar pedido" y lo escanea con la cámara, con un
        lector de códigos USB (escribe el código y Enter en el campo) o
        escribe el folio.
     2. Ve si está pagado y qué productos le toca entregar.
     3. "Entregar productos" lo marca como entregado y saca lo apartado del
        inventario. Un pedido se entrega una sola vez.
   "Por entregar" lista los pagados que nadie ha recogido.

   Usa apiTienda, escapeHtml y dinero del panel (bienvenido.html). La cámara
   usa html5-qrcode, que se descarga solo la primera vez que se abre.
═══════════════════════════════════════ */
const Entregas = (() => {
    const LIBRERIA_QR = 'https://cdnjs.cloudflare.com/ajax/libs/html5-qrcode/2.3.8/html5-qrcode.min.js';
    let modal = null;
    let lector = null;
    let cargandoLibreria = null;
    let actual = null;
    let ultimoLeido = '';

    const $ = id => document.getElementById(id);
    const ruta = r => '/api/gyms/' + encodeURIComponent(localStorage.getItem('gymId') || '') + '/ventas' + r;

    function fechaHora(iso) {
        return iso ? new Date(iso).toLocaleString('es-MX', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';
    }

    function aviso(mensaje, tipo) {
        const caja = $('entrega-aviso');
        caja.textContent = mensaje || '';
        caja.className = 'alert small py-2 mb-3 alert-' + (tipo || 'info') + (mensaje ? '' : ' d-none');
    }

    /* ─── Abrir y buscar ─── */
    function abrir(codigo) {
        actual = null;
        ultimoLeido = '';
        aviso('');
        $('entrega-resultado').innerHTML = '';
        $('entrega-codigo').value = '';
        modal.show();
        if (codigo) buscar(codigo);
    }

    function buscar(codigo) {
        const texto = String(codigo || '').trim();
        if (!texto) {
            aviso('Escanea el código QR del comprobante o escribe el folio del pedido.', 'warning');
            return;
        }
        detenerCamara();
        aviso('');
        $('entrega-resultado').innerHTML = '<div class="text-center text-body-secondary py-4"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Buscando el pedido…</div>';
        apiTienda(ruta('/entregas/buscar?codigo=' + encodeURIComponent(texto)))
            .then(pintar)
            .catch(err => {
                $('entrega-resultado').innerHTML = '';
                aviso(err.message, 'danger');
            });
    }

    // Qué hacer con el pedido, en una línea que se lee de un vistazo.
    function veredicto(d) {
        const e = d.entrega || {};
        if (d.estado === 'pendiente_pago') return ['warning', 'bx-time-five', 'Todavía no está pagado', 'No entregues nada hasta que se pague.'];
        if (d.estado === 'cancelado') return ['danger', 'bx-x-circle', 'Pedido cancelado', 'No hay nada que entregar.'];
        if (d.estado === 'reembolsado') return ['danger', 'bx-undo', 'Pedido reembolsado', 'No hay nada que entregar.'];
        if (e.estado === 'entregado') {
            return ['secondary', 'bx-check-double', 'Ya se entregó',
                (e.entregadoEn ? 'El ' + fechaHora(e.entregadoEn) : '') + (d.entregadoPor ? ' · entregó ' + d.entregadoPor : '') + '. No lo entregues otra vez.'];
        }
        if (e.estado === 'pendiente') return ['success', 'bx-check-circle', 'Pagado: entrégale estos productos', ''];
        return ['secondary', 'bx-info-circle', 'Solo lleva membresía', 'Ya está activa; no hay productos que entregar.'];
    }

    function pintar(d) {
        actual = d;
        const [tono, icono, titulo, texto] = veredicto(d);
        const productos = d.partidas.filter(p => p.tipo !== 'membresia');
        const membresias = d.partidas.filter(p => p.tipo === 'membresia');
        $('entrega-resultado').innerHTML = `
            <div class="entrega-veredicto entrega-${tono}" role="status">
                <i class="bx ${icono}" aria-hidden="true"></i>
                <div><div class="fw-bold">${escapeHtml(titulo)}</div>${texto ? '<div class="small">' + escapeHtml(texto) + '</div>' : ''}</div>
            </div>
            <div class="d-flex justify-content-between align-items-start gap-3 mt-3">
                <div class="min-w-0">
                    <div class="fw-bold fs-5">Pedido #${escapeHtml(d.folio)}</div>
                    <div class="small text-body-secondary">${escapeHtml(d.clienteNombre || 'Público en general')} · ${escapeHtml(d.detallePago || d.metodo || '')}</div>
                </div>
                <div class="fw-bold text-nowrap">${dinero(d.total)}</div>
            </div>
            ${productos.length ? `
                <ul class="list-unstyled entrega-productos mt-3 mb-0">
                    ${productos.map(p => `
                        <li>
                            <span class="entrega-cantidad">${escapeHtml(p.cantidad)}</span>
                            <span class="min-w-0"><span class="fw-semibold d-block">${escapeHtml(p.titulo)}</span>${p.variante ? '<small class="text-body-secondary">' + escapeHtml(p.variante) + '</small>' : ''}</span>
                        </li>`).join('')}
                </ul>` : ''}
            ${membresias.length ? '<p class="small text-body-secondary mt-2 mb-0"><i class="bx bx-id-card" aria-hidden="true"></i> También lleva ' + membresias.map(m => escapeHtml(m.titulo)).join(', ') + ' (membresía: no se entrega en mano).</p>' : ''}
            ${d.acciones && d.acciones.entregar
                ? '<button type="button" class="btn btn-success btn-lg rounded-pill w-100 mt-4" id="entrega-confirmar"><i class="bx bx-package" aria-hidden="true"></i> Entregar productos</button>'
                : ''}
            <button type="button" class="btn btn-link w-100 mt-2" id="entrega-otro"><i class="bx bx-qr-scan" aria-hidden="true"></i> Buscar otro pedido</button>`;
        const confirmar = $('entrega-confirmar');
        if (confirmar) confirmar.focus();
    }

    function entregar(boton) {
        if (!actual) return;
        boton.disabled = true;
        boton.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Entregando…';
        apiTienda(ruta('/pedidos/' + encodeURIComponent(actual.orderId) + '/entregar'), { method: 'POST' })
            .then(d => {
                pintar(d);
                aviso('Listo: el pedido #' + d.folio + ' quedó entregado y salió del inventario.', 'success');
                cargarPendientes();
                if (typeof cargarResumenVentas === 'function') cargarResumenVentas();
            })
            .catch(err => {
                aviso(err.message, 'danger');
                boton.disabled = false;
                boton.innerHTML = '<i class="bx bx-package" aria-hidden="true"></i> Entregar productos';
            });
    }

    /* ─── Cámara ─── */
    function libreria() {
        if (window.Html5Qrcode) return Promise.resolve();
        if (!cargandoLibreria) {
            cargandoLibreria = new Promise((ok, falla) => {
                const s = document.createElement('script');
                s.src = LIBRERIA_QR;
                s.onload = ok;
                s.onerror = () => { cargandoLibreria = null; falla(new Error('No se pudo cargar el lector de QR. Revisa tu conexión o escribe el folio.')); };
                document.head.appendChild(s);
            });
        }
        return cargandoLibreria;
    }

    function encenderCamara() {
        aviso('');
        $('entrega-resultado').innerHTML = '';
        const boton = $('entrega-camara-btn');
        boton.disabled = true;
        $('entrega-camara').hidden = false;
        libreria()
            .then(() => {
                lector = new Html5Qrcode('entrega-camara', { verbose: false });
                return lector.start({ facingMode: 'environment' }, { fps: 10, qrbox: { width: 220, height: 220 } },
                    texto => {
                        if (texto === ultimoLeido) return;
                        ultimoLeido = texto;
                        buscar(texto);
                    },
                    () => { /* todavía no lee nada */ });
            })
            .then(() => {
                boton.hidden = true;
                $('entrega-camara-apagar').hidden = false;
            })
            .catch(err => {
                detenerCamara();
                const sinPermiso = err && /permission|notallowed/i.test(String(err.name || err.message || err));
                aviso(sinPermiso
                    ? 'El navegador no dio permiso de usar la cámara. Permítelo en el candado de la barra de direcciones, o escribe el folio.'
                    : (err && err.message) || 'No encontramos una cámara. Usa un lector de códigos o escribe el folio.', 'warning');
            });
    }

    function detenerCamara() {
        const caja = $('entrega-camara');
        const boton = $('entrega-camara-btn');
        if (lector) {
            const l = lector;
            lector = null;
            l.stop().catch(() => {}).finally(() => { try { l.clear(); } catch (e) { /* ya estaba limpio */ } });
        }
        if (caja) caja.hidden = true;
        if (boton) { boton.hidden = false; boton.disabled = false; }
        const apagar = $('entrega-camara-apagar');
        if (apagar) apagar.hidden = true;
    }

    /* ─── Por entregar ─── */
    function cargarPendientes() {
        const caja = $('por-entregar');
        if (!caja || !localStorage.getItem('gymId')) return;
        apiTienda(ruta('/pedidos?estado=por_entregar&tamano=8'))
            .then(r => {
                caja.hidden = r.total === 0;
                $('por-entregar-conteo').textContent = r.total === 1 ? '1 pedido' : r.total + ' pedidos';
                $('por-entregar-lista').innerHTML = r.pedidos.map(p => `
                    <li class="por-entregar-fila">
                        <div class="min-w-0">
                            <span class="fw-semibold">#${escapeHtml(p.folio)}</span> · ${escapeHtml(p.cliente || '—')}
                            <div class="small text-body-secondary">${p.articulos} ${p.articulos === 1 ? 'artículo' : 'artículos'} · pagado ${escapeHtml(fechaHora(p.pagadoEn || p.creadoEn))}</div>
                        </div>
                        <button type="button" class="btn btn-outline-success btn-sm rounded-pill px-3" data-entregar="${escapeHtml(p.orderId)}">Entregar</button>
                    </li>`).join('')
                    + (r.total > r.pedidos.length ? '<li class="small text-body-secondary px-4 py-2">Y ' + (r.total - r.pedidos.length) + ' más: búscalos por folio.</li>' : '');
            })
            .catch(() => { caja.hidden = true; });
    }

    function iniciar() {
        if (!$('entregaModal')) return;
        modal = new bootstrap.Modal($('entregaModal'));
        $('entrega-abrir').addEventListener('click', () => abrir());
        $('entregaModal').addEventListener('shown.bs.modal', () => { if (!actual) $('entrega-codigo').focus(); });
        $('entregaModal').addEventListener('hidden.bs.modal', detenerCamara);
        $('entrega-form').addEventListener('submit', (e) => {
            e.preventDefault();
            buscar($('entrega-codigo').value);
        });
        $('entrega-camara-btn').addEventListener('click', encenderCamara);
        $('entrega-camara-apagar').addEventListener('click', detenerCamara);
        $('entrega-resultado').addEventListener('click', (e) => {
            if (e.target.closest('#entrega-confirmar')) entregar(e.target.closest('#entrega-confirmar'));
            if (e.target.closest('#entrega-otro')) {
                actual = null;
                ultimoLeido = '';
                aviso('');
                $('entrega-resultado').innerHTML = '';
                $('entrega-codigo').value = '';
                $('entrega-codigo').focus();
            }
        });
        $('por-entregar-lista').addEventListener('click', (e) => {
            const boton = e.target.closest('[data-entregar]');
            if (boton) abrir(boton.dataset.entregar);
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();

    return { abrir, cargarPendientes };
})();
