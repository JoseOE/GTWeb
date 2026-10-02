/* ═══════════════════════════════════════
   Tienda del gimnasio: lo que comparten tienda, producto, checkout,
   confirmación y mis pedidos.
   ───────────────────────────────────────
   - Sesión: la que guarda login.html (userId, userName, role, gymId).
   - Llamadas a la API con X-User-Id y reintento solo mientras Medusa despierta.
   - Barra superior, mini-carrito (offcanvas), barra inferior con el total en
     celular y la notificación de "Agregado".
   El carrito vive en el servidor (es el mismo en cualquier dispositivo);
   localStorage solo guarda el último contador para pintarlo al instante.
═══════════════════════════════════════ */
const Tienda = (() => {
    const sesion = {
        userId: localStorage.getItem('userId') || '',
        userName: localStorage.getItem('userName') || '',
        role: localStorage.getItem('role') || 'member',
        gymId: localStorage.getItem('gymId') || ''
    };
    const CACHE = 'carrito:' + sesion.userId;
    let carrito = null;
    let cuentaActual = null;
    let opciones = { barraInferior: true };
    let ocupado = false;

    /* ─── Utilidades ─── */
    function escapar(str) {
        const div = document.createElement('div');
        div.textContent = str == null ? '' : String(str);
        return div.innerHTML;
    }

    function dinero(n) {
        return '$' + Number(n || 0).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function fecha(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' });
    }

    function exigirSesion() {
        if (!sesion.userId) {
            window.location.href = 'login.html';
            throw new Error('Sin sesión');
        }
        return sesion;
    }

    // Llamada a la API. Si Medusa está despertando (503 + despertando), muestra
    // el aviso y reintenta cada 5 s hasta un minuto. Los errores llevan
    // .status y .datos (el cuerpo completo: avisos, carrito...).
    function api(url, opts, intentos) {
        opts = opts || {};
        intentos = intentos == null ? 12 : intentos;
        const headers = { 'X-User-Id': sesion.userId };
        if (opts.body) headers['Content-Type'] = 'application/json';
        return fetch(url, { ...opts, headers })
            .catch(() => { const e = new Error('Sin conexión. Revisa tu internet e intenta de nuevo.'); e.status = 0; throw e; })
            .then(res => res.json().catch(() => ({})).then(body => {
                if (res.status === 503 && body.despertando && intentos > 0) {
                    despertando(true);
                    return new Promise(r => setTimeout(r, 5000)).then(() => api(url, opts, intentos - 1));
                }
                despertando(false);
                if (!res.ok) {
                    const e = new Error(body.error || 'No se pudo completar. Intenta de nuevo.');
                    e.status = res.status;
                    e.datos = body;
                    throw e;
                }
                return body;
            }));
    }

    function despertando(visible) {
        const el = document.getElementById('aviso-despertando');
        if (el) el.classList.toggle('d-none', !visible);
    }

    // Estado vigente de la cuenta: gimnasio actual, membresía y si puede comprar.
    function cuenta() {
        if (cuentaActual) return Promise.resolve(cuentaActual);
        return fetch('/api/users/' + encodeURIComponent(sesion.userId) + '/me')
            .then(res => {
                if (res.status === 404) {
                    localStorage.removeItem('userId');
                    window.location.href = 'login.html';
                    throw new Error('Sesión vencida');
                }
                return res.json();
            })
            .then(c => {
                cuentaActual = c;
                // Si cambió de gimnasio desde otro lado, la tienda sigue al gimnasio actual.
                if (c.gymId) localStorage.setItem('gymId', c.gymId); else localStorage.removeItem('gymId');
                sesion.gymId = c.gymId || '';
                return c;
            });
    }

    function puedeComprar(c) {
        return c && c.role === 'member' && !!c.gymId && c.membershipStatus !== 'pending';
    }

    /* ─── Barra superior, mini-carrito y barra inferior ─── */
    function montar(opts) {
        opciones = { ...opciones, ...(opts || {}) };
        const esDueno = sesion.role === 'owner';
        const barra = document.getElementById('tienda-barra');
        barra.innerHTML = `
            <nav class="tienda-nav" aria-label="Tienda">
                <div class="container-tienda">
                    <a class="marca" href="tienda.html" aria-label="Ir a la tienda">
                        <i class='bx bx-dumbbell' aria-hidden="true"></i><span>Gym<span>Track</span></span>
                    </a>
                    ${esDueno
                        ? '<a class="icono-nav" href="bienvenido.html" title="Volver al panel" aria-label="Volver al panel"><i class="bx bx-grid-alt"></i></a>'
                        : '<a class="icono-nav" href="pedidos.html" title="Mis pedidos" aria-label="Mis pedidos"><i class="bx bx-receipt"></i></a>'}
                    <button type="button" class="icono-nav" id="boton-carrito" aria-label="Abrir carrito" title="Carrito" ${esDueno ? 'hidden' : ''}>
                        <i class='bx bx-shopping-bag'></i><span class="badge-carrito" id="badge-carrito" aria-hidden="true"></span>
                    </button>
                    <div class="dropdown">
                        <button type="button" class="icono-nav" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Menú de la cuenta" title="Cuenta">
                            <i class='bx bx-user-circle'></i>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end">
                            <li><span class="dropdown-item-text small text-body-secondary">${escapar(sesion.userName)}</span></li>
                            ${esDueno ? '' : '<li><a class="dropdown-item py-2" href="pedidos.html"><i class="bx bx-receipt"></i> Mis pedidos</a></li>'}
                            <li><a class="dropdown-item py-2" href="cuenta.html"><i class="bx bx-cog"></i> Mi cuenta</a></li>
                            <li><hr class="dropdown-divider"></li>
                            <li><a class="dropdown-item py-2 text-danger" href="login.html" id="cerrar-sesion"><i class="bx bx-log-out"></i> Cerrar sesión</a></li>
                        </ul>
                    </div>
                </div>
            </nav>
            <div class="container-tienda"><div id="aviso-despertando" class="aviso-despertando mt-3 d-none" role="status">
                <span class="spinner-border spinner-border-sm text-secondary" aria-hidden="true"></span>
                <span>Despertando la tienda… La primera vez puede tardar hasta un minuto.</span>
            </div></div>`;

        document.body.insertAdjacentHTML('beforeend', `
            <div class="offcanvas offcanvas-end" tabindex="-1" id="minicarrito" aria-labelledby="minicarrito-titulo">
                <div class="offcanvas-header">
                    <h2 class="seccion-titulo mb-0" id="minicarrito-titulo">Tu carrito</h2>
                    <button type="button" class="btn-close tocable" data-bs-dismiss="offcanvas" aria-label="Cerrar carrito"></button>
                </div>
                <div class="offcanvas-body d-flex flex-column pt-0">
                    <div id="minicarrito-avisos"></div>
                    <div id="minicarrito-lista" class="flex-grow-1"></div>
                    <div id="minicarrito-pie" aria-live="polite"></div>
                </div>
            </div>
            <div class="barra-carrito" id="barra-carrito" aria-hidden="true">
                <div class="resumen"><small id="barra-articulos">0 artículos</small><br><strong id="barra-total">$0.00</strong></div>
                <button type="button" class="btn btn-primary px-4" id="barra-ver" tabindex="-1">Ver carrito</button>
            </div>
            <div class="toast-tienda" aria-live="polite">
                <div class="toast" id="toast-tienda" role="status" data-bs-delay="3500">
                    <div class="d-flex align-items-center gap-2 p-2 ps-3">
                        <span class="flex-grow-1" id="toast-texto"></span>
                        <button type="button" class="btn btn-sm btn-primary d-none tocable" id="toast-accion"></button>
                        <button type="button" class="btn-close btn-close-white tocable" data-bs-dismiss="toast" aria-label="Cerrar"></button>
                    </div>
                </div>
            </div>`);

        document.getElementById('cerrar-sesion').addEventListener('click', () => {
            ['userName', 'userId', 'gymId', 'role', CACHE].forEach(k => localStorage.removeItem(k));
        });
        document.getElementById('boton-carrito').addEventListener('click', abrirCarrito);
        document.getElementById('barra-ver').addEventListener('click', abrirCarrito);
        document.getElementById('minicarrito').addEventListener('click', alTocarCarrito);

        // El contador sale primero de la caché y luego se confirma con el servidor.
        try {
            const cache = JSON.parse(localStorage.getItem(CACHE) || 'null');
            if (cache) pintarContador(cache.articulos, cache.total);
        } catch (e) { /* caché dañada: se ignora */ }
    }

    function abrirCarrito() {
        bootstrap.Offcanvas.getOrCreateInstance(document.getElementById('minicarrito')).show();
        cargarCarrito().catch(err => pintarErrorCarrito(err.message));
    }

    function pintarContador(articulos, total) {
        const badge = document.getElementById('badge-carrito');
        if (badge) badge.textContent = articulos > 0 ? (articulos > 99 ? '99+' : articulos) : '';
        const boton = document.getElementById('boton-carrito');
        if (boton) boton.setAttribute('aria-label', articulos > 0 ? 'Abrir carrito, ' + articulos + ' artículos' : 'Abrir carrito');
        const barra = document.getElementById('barra-carrito');
        if (!barra) return;
        const mostrar = opciones.barraInferior && articulos > 0;
        barra.classList.toggle('visible', mostrar);
        barra.setAttribute('aria-hidden', String(!mostrar));
        document.getElementById('barra-ver').tabIndex = mostrar ? 0 : -1;
        document.body.classList.toggle('con-barra-carrito', mostrar);
        document.getElementById('barra-articulos').textContent = articulos + (articulos === 1 ? ' artículo' : ' artículos');
        document.getElementById('barra-total').textContent = dinero(total);
    }

    function guardar(c) {
        carrito = c;
        try { localStorage.setItem(CACHE, JSON.stringify({ articulos: c.articulos, total: c.total })); } catch (e) { /* sin espacio */ }
        pintarContador(c.articulos, c.total);
        pintarCarrito();
        document.dispatchEvent(new CustomEvent('carrito', { detail: c }));
        return c;
    }

    function cargarCarrito() {
        if (sesion.role === 'owner') return Promise.resolve(null);
        return api('/api/tienda/carrito').then(guardar);
    }

    function pintarErrorCarrito(mensaje) {
        const lista = document.getElementById('minicarrito-lista');
        if (lista) lista.innerHTML = '<div class="estado-vacio mt-3"><i class="bx bx-error-circle"></i>' + escapar(mensaje) + '</div>';
    }

    function pintarCarrito() {
        const lista = document.getElementById('minicarrito-lista');
        const pie = document.getElementById('minicarrito-pie');
        const avisos = document.getElementById('minicarrito-avisos');
        if (!lista || !carrito) return;
        avisos.innerHTML = (carrito.avisos || []).length
            ? '<div class="alert alert-warning small py-2 mt-3 mb-0">' + carrito.avisos.map(escapar).join('<br>') + '</div>'
            : '';
        if (!carrito.items.length) {
            lista.innerHTML = '<div class="estado-vacio mt-3"><i class="bx bx-shopping-bag"></i>Tu carrito está vacío.<div class="mt-3"><a class="btn btn-outline-primary tocable px-4" href="tienda.html">Ver la tienda</a></div></div>';
            pie.innerHTML = '';
            return;
        }
        lista.innerHTML = carrito.items.map(i => `
            <div class="partida">
                ${i.imagen ? '<img class="miniatura" src="' + escapar(i.imagen) + '" alt="">' : '<div class="miniatura"><i class="bx ' + (i.esPlan ? 'bx-purchase-tag-alt' : 'bx-package') + '"></i></div>'}
                <div>
                    <div class="titulo">${escapar(i.titulo)}</div>
                    <div class="detalle">${escapar(i.variante || '')}${i.variante ? ' · ' : ''}${dinero(i.precioUnitario)}</div>
                    <div class="acciones">
                        ${i.esPlan
                            ? '<span class="detalle">Cantidad: 1</span>'
                            : `<div class="cantidad" role="group" aria-label="Cantidad de ${escapar(i.titulo)}">
                                   <button type="button" data-menos="${escapar(i.id)}" aria-label="Quitar una pieza" ${i.cantidad <= 1 ? 'disabled' : ''}><i class="bx bx-minus"></i></button>
                                   <output aria-live="polite">${i.cantidad}</output>
                                   <button type="button" data-mas="${escapar(i.id)}" aria-label="Agregar una pieza" ${i.cantidad >= i.maximo ? 'disabled' : ''}><i class="bx bx-plus"></i></button>
                               </div>`}
                        <span class="importe">${dinero(i.total)}</span>
                        <button type="button" class="boton-quitar" data-quitar="${escapar(i.id)}" aria-label="Quitar ${escapar(i.titulo)} del carrito"><i class="bx bx-trash"></i></button>
                    </div>
                </div>
            </div>`).join('');
        pie.innerHTML = `
            <div class="totales pt-3">
                <div class="fila text-body-secondary"><span>Subtotal (sin IVA)</span><span>${dinero(carrito.subtotal)}</span></div>
                <div class="fila text-body-secondary"><span>IVA 16 %</span><span>${dinero(carrito.iva)}</span></div>
                <div class="fila total"><span>Total</span><span>${dinero(carrito.total)}</span></div>
            </div>
            <p class="small text-body-secondary my-2"><i class="bx bx-store-alt"></i> Recoges tu compra en la recepción de tu gimnasio.</p>
            <a class="btn btn-primary btn-grande w-100" href="checkout.html">Ir a pagar</a>`;
    }

    function alTocarCarrito(e) {
        const menos = e.target.closest('[data-menos]');
        const mas = e.target.closest('[data-mas]');
        const quitarBtn = e.target.closest('[data-quitar]');
        if (!menos && !mas && !quitarBtn) return;
        if (ocupado) return;
        const boton = menos || mas || quitarBtn;
        const id = boton.dataset.menos || boton.dataset.mas || boton.dataset.quitar;
        const item = carrito.items.find(i => i.id === id);
        let accion;
        if (quitarBtn) accion = quitar(id);
        else accion = cambiar(id, item.cantidad + (mas ? 1 : -1));
        accion.catch(err => avisar(err.message));
    }

    function conCarrito(promesa) {
        ocupado = true;
        document.getElementById('minicarrito')?.setAttribute('aria-busy', 'true');
        return promesa
            .then(guardar)
            .catch(err => {
                // 409 con carrito: algo cambió mientras tanto; se pinta la versión corregida.
                if (err.datos && err.datos.carrito) guardar(err.datos.carrito);
                throw err;
            })
            .finally(() => {
                ocupado = false;
                document.getElementById('minicarrito')?.removeAttribute('aria-busy');
            });
    }

    function agregar(varianteId, cantidad, nombre) {
        return conCarrito(api('/api/tienda/carrito/items', {
            method: 'POST',
            body: JSON.stringify({ varianteId, cantidad: cantidad || 1 })
        })).then(c => {
            avisar('Agregado: ' + (nombre || 'producto'), 'Ver carrito', abrirCarrito);
            return c;
        });
    }

    function cambiar(partidaId, cantidad) {
        return conCarrito(api('/api/tienda/carrito/items/' + encodeURIComponent(partidaId), {
            method: 'PATCH',
            body: JSON.stringify({ cantidad })
        }));
    }

    function quitar(partidaId) {
        return conCarrito(api('/api/tienda/carrito/items/' + encodeURIComponent(partidaId), { method: 'DELETE' }));
    }

    function avisar(texto, accionTexto, accion) {
        const el = document.getElementById('toast-tienda');
        if (!el) { alert(texto); return; }
        document.getElementById('toast-texto').textContent = texto;
        const boton = document.getElementById('toast-accion');
        boton.classList.toggle('d-none', !accionTexto);
        boton.textContent = accionTexto || '';
        boton.onclick = accion ? () => { bootstrap.Toast.getOrCreateInstance(el).hide(); accion(); } : null;
        bootstrap.Toast.getOrCreateInstance(el).show();
    }

    // El recibo lo genera GET /api/recibos/pedidos/{orderId}.pdf. Se descarga con
    // fetch porque un enlace normal no puede mandar el X-User-Id.
    function descargarRecibo(orderId, folio, boton) {
        if (boton) boton.disabled = true;
        return fetch('/api/recibos/pedidos/' + encodeURIComponent(orderId) + '.pdf', { headers: { 'X-User-Id': sesion.userId } })
            .then(res => {
                if (res.status === 404) throw new Error('El recibo de este pedido todavía no está disponible.');
                if (!res.ok) throw new Error('No se pudo descargar el recibo. Intenta de nuevo.');
                return res.blob();
            })
            .then(blob => {
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = 'recibo-' + (folio || orderId) + '.pdf';
                document.body.appendChild(a);
                a.click();
                a.remove();
                setTimeout(() => URL.revokeObjectURL(url), 10000);
            })
            .catch(err => avisar(err.message))
            .finally(() => { if (boton) boton.disabled = false; });
    }

    function pintarEstado(contenedor, tipo, mensaje, reintentar) {
        const iconos = { vacio: 'bx-package', error: 'bx-error-circle', info: 'bx-info-circle' };
        contenedor.innerHTML = '<div class="estado-vacio"><i class="bx ' + (iconos[tipo] || 'bx-info-circle') + '"></i>'
            + escapar(mensaje) + (reintentar ? '<div class="mt-3"><button type="button" class="btn btn-outline-primary tocable px-4">Reintentar</button></div>' : '') + '</div>';
        if (reintentar) contenedor.querySelector('button').addEventListener('click', reintentar);
    }

    return {
        sesion, escapar, dinero, fecha, exigirSesion, api, cuenta, puedeComprar, montar,
        cargarCarrito, agregar, cambiar, quitar, abrirCarrito, avisar, descargarRecibo, pintarEstado,
        get carrito() { return carrito; }
    };
})();
