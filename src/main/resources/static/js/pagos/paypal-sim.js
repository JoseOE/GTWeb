/* ═══════════════════════════════════════
   Simulador de PayPal.
   ───────────────────────────────────────
   Como en PayPal, el pago se aprueba fuera de la tienda:
     1. Al pulsar "Pagar", Spring crea la orden (PAYID-SIM-...) por el total
        del carrito y la página va a paypal-sim.html.
     2. Ahí la persona elige una cuenta de prueba y aprueba o cancela.
     3. Si aprueba, vuelve a checkout.html?metodo=paypal&continuar=1&token=...
        &PayerID=... y el checkout pulsa "Pagar" solo: obtenerDatos() resuelve
        con el token y Spring paga con la orden aprobada.
   Si cancela, vuelve a checkout.html?metodo=paypal&paypal=cancelado y no se
   crea ningún pedido.

   Cuenta vinculada: con "Recordar mi cuenta PayPal" la orden se crea con
   recordar: true y, al pagar, la cuenta queda vinculada. Las siguientes
   compras muestran esa cuenta y pagan con {vinculo: true}, sin salir de la
   tienda. "Usar otra cuenta" vuelve al flujo normal.
═══════════════════════════════════════ */
(function () {
    // Se lee al cargar, antes de que el checkout quite "continuar" de la URL:
    // solo ese regreso automático paga con el token. Si después se recarga la
    // página, el token viejo se ignora y "Pagar" pide una orden nueva.
    const inicio = new URLSearchParams(location.search);
    let aprobado = inicio.get('continuar') === '1' && inicio.get('token') && inicio.get('PayerID')
        ? { token: inicio.get('token'), payerId: inicio.get('PayerID') }
        : null;
    let cancelado = inicio.get('paypal') === 'cancelado';
    // La cuenta vinculada (GET /api/simuladores/paypal/vinculo) y si la persona prefirió otra.
    let vinculo = null;
    let usarOtra = false;
    let contenedorActual = null;
    let resumenActual = null;

    function cargarEstilos() {
        if (document.getElementById('estilos-paypal-sim')) return;
        const link = document.createElement('link');
        link.id = 'estilos-paypal-sim';
        link.rel = 'stylesheet';
        link.href = 'css/pagos/paypal-sim.css';
        document.head.appendChild(link);
    }

    function dinero(n) {
        return '$' + Number(n || 0).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // Quita de la URL lo que dejó el simulador, para que recargar no lo reuse.
    function limpiarUrl() {
        const params = new URLSearchParams(location.search);
        ['token', 'PayerID', 'paypal'].forEach(k => params.delete(k));
        history.replaceState(null, '', location.pathname + (params.toString() ? '?' + params : ''));
    }

    function conVinculo() {
        return !!(vinculo && vinculo.vinculada && !usarOtra);
    }

    function montar(contenedor, resumen) {
        cargarEstilos();
        contenedorActual = contenedor;
        resumenActual = resumen;
        pintar();
        if (vinculo === null) {
            Tienda.api('/api/simuladores/paypal/vinculo')
                .then(v => { vinculo = v; if (contenedorActual === contenedor) pintar(); })
                .catch(() => { vinculo = { vinculada: false }; });
        }
    }

    function pintar() {
        const contenedor = contenedorActual;
        const resumen = resumenActual;
        if (!contenedor) return;
        if (conVinculo()) {
            const quien = vinculo.nombre || vinculo.cuenta;
            contenedor.innerHTML = `
                <div class="pp-caja">
                    <div class="pp-caja-cabeza">
                        <img src="img/pagos/paypal.svg" alt="PayPal" width="78" height="26">
                        <span class="pp-simulacion">Simulación</span>
                    </div>
                    <div class="pp-vinculada mt-3">
                        <span class="pp-avatar-mini" aria-hidden="true">${Tienda.escapar(String(quien).trim().charAt(0).toUpperCase())}</span>
                        <span style="min-width:0"><span class="fw-semibold d-block">Cuenta vinculada</span><small class="text-body-secondary">${Tienda.escapar(vinculo.cuenta)}</small></span>
                    </div>
                    <p class="small mt-3 mb-2">Pagas <strong>${dinero(resumen && resumen.total)}</strong> con esta cuenta al confirmar tu compra, sin salir de la tienda.</p>
                    <button type="button" class="btn btn-link btn-sm p-0" id="pp-usar-otra">Usar otra cuenta</button>
                </div>`;
            contenedor.querySelector('#pp-usar-otra').addEventListener('click', () => { usarOtra = true; pintar(); });
            return;
        }
        contenedor.innerHTML = `
            <div class="pp-caja">
                <div class="pp-caja-cabeza">
                    <img src="img/pagos/paypal.svg" alt="PayPal" width="78" height="26">
                    <span class="pp-simulacion">Simulación</span>
                </div>
                ${cancelado ? '<div class="alert alert-warning small py-2 mt-2 mb-0" role="status">Cancelaste el pago en PayPal. Tu carrito sigue igual: vuelve a intentarlo o elige otro método.</div>' : ''}
                <p class="mt-3 mb-2">Al pagar te llevamos al <strong>Simulador PayPal</strong> para aprobar
                    <strong>${dinero(resumen && resumen.total)}</strong> con una cuenta de prueba. Después regresas aquí y tu pedido queda pagado.</p>
                <ul class="small text-body-secondary mb-3 ps-3">
                    <li>No se pide contraseña ni se usa tu cuenta real de PayPal.</li>
                    <li>Si cancelas en el simulador, no se crea ningún pedido.</li>
                </ul>
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="pp-recordar">
                    <label class="form-check-label small" for="pp-recordar"><strong>Recordar mi cuenta PayPal.</strong> Tus siguientes compras se pagan con solo confirmar, sin volver a PayPal. Puedes quitarla en Mi cuenta.</label>
                </div>
            </div>`;
        if (cancelado) {
            cancelado = false;
            limpiarUrl();
        }
    }

    function obtenerDatos() {
        if (conVinculo() && !aprobado) return Promise.resolve({ vinculo: true });
        if (aprobado) {
            const datos = aprobado;
            aprobado = null;
            limpiarUrl();
            return Promise.resolve({ token: datos.token, payerId: datos.payerId });
        }
        // Las direcciones de regreso son de esta misma página: Spring no acepta
        // otro sitio web (la app usa su propio enlace, p. ej. gymtrack://...).
        const base = location.origin + location.pathname.replace(/[^/]*$/, '');
        return Tienda.api('/api/simuladores/paypal/ordenes?canal=web', {
            method: 'POST',
            body: JSON.stringify({
                returnUrl: base + 'checkout.html?metodo=paypal&continuar=1',
                cancelUrl: base + 'checkout.html?metodo=paypal&paypal=cancelado',
                recordar: !!(document.getElementById('pp-recordar') || {}).checked
            })
        }).then(orden => {
            location.href = 'paypal-sim.html?token=' + encodeURIComponent(orden.id);
            // La página se va al simulador: esta promesa no se resuelve.
            return new Promise(() => {});
        });
    }

    MetodosPago.registrar({
        id: 'paypal',
        nombre: 'PayPal',
        descripcion: 'Paga con tu cuenta PayPal y regresa a la tienda',
        icono: 'bx-wallet',
        logos: ['paypal'],
        orden: 3,
        // Sale a paypal-sim.html: el checkout pide la confirmación antes de salir.
        saleDeLaPagina: true,
        montar,
        obtenerDatos,
        // Para "¿Confirmas tu compra?": con qué cuenta se cobra.
        detalle: () => conVinculo() ? 'Cuenta vinculada · ' + vinculo.cuenta : ''
    });
})();
