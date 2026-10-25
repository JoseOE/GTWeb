/* ═══════════════════════════════════════
   PayPal (sandbox).
   ───────────────────────────────────────
   El pago se aprueba en PayPal, fuera de la tienda:
     1. Al pulsar "Pagar", Spring crea la orden en PayPal por el total del
        carrito (POST /api/pagos/paypal/ordenes) y la página va a PayPal.
     2. Ahí la persona inicia sesión y aprueba (en el sandbox, con una cuenta
        compradora de prueba de developer.paypal.com).
     3. PayPal regresa a checkout.html?metodo=paypal&continuar=1&token=...
        &PayerID=... y el checkout pulsa "Pagar" solo: obtenerDatos() resuelve
        con el token y Spring cobra la orden (capture) al crear el pedido.
   Si la persona cancela, vuelve con ?paypal=cancelado y no se crea ningún pedido.
═══════════════════════════════════════ */
(function () {
    // Se lee al cargar, antes de que el checkout quite "continuar" de la URL:
    // solo ese regreso automático paga con el token.
    const inicio = new URLSearchParams(location.search);
    let aprobado = inicio.get('continuar') === '1' && inicio.get('token')
        ? { token: inicio.get('token'), payerId: inicio.get('PayerID') }
        : null;
    let cancelado = inicio.get('paypal') === 'cancelado';

    function dinero(n) {
        return '$' + Number(n || 0).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // Quita de la URL lo que dejó PayPal, para que recargar no lo reuse.
    function limpiarUrl() {
        const params = new URLSearchParams(location.search);
        ['token', 'PayerID', 'paypal'].forEach(k => params.delete(k));
        history.replaceState(null, '', location.pathname + (params.toString() ? '?' + params : ''));
    }

    function montar(contenedor, resumen) {
        contenedor.innerHTML = `
            <div class="pago-externo">
                <div class="d-flex align-items-center justify-content-between gap-2">
                    <img src="img/pagos/paypal.svg" alt="PayPal" width="78" height="26">
                    <span class="pago-prueba">Modo de prueba</span>
                </div>
                ${cancelado ? '<div class="alert alert-warning small py-2 mt-2 mb-0" role="status">Cancelaste el pago en PayPal. Tu carrito sigue igual: vuelve a intentarlo o elige otro método.</div>' : ''}
                <p class="mt-3 mb-2">Al pagar te llevamos a <strong>PayPal</strong> para aprobar
                    <strong>${dinero(resumen && resumen.total)}</strong>. Después regresas aquí y tu pedido queda pagado.</p>
                <ul class="small text-body-secondary mb-0 ps-3">
                    <li>En el modo de prueba entra con una cuenta compradora de prueba de PayPal (sandbox), no con tu cuenta real.</li>
                    <li>Si cancelas en PayPal, no se crea ningún pedido.</li>
                </ul>
            </div>`;
        if (cancelado) {
            cancelado = false;
            limpiarUrl();
        }
    }

    function obtenerDatos() {
        if (aprobado) {
            const datos = aprobado;
            aprobado = null;
            limpiarUrl();
            return Promise.resolve(datos);
        }
        // Las direcciones de regreso son de esta misma página: Spring no acepta
        // otro sitio web (la app usa su propio enlace, p. ej. gymtrack://...).
        const base = location.origin + location.pathname.replace(/[^/]*$/, '');
        return Tienda.api('/api/pagos/paypal/ordenes?canal=web', {
            method: 'POST',
            body: JSON.stringify({
                returnUrl: base + 'checkout.html?metodo=paypal&continuar=1',
                cancelUrl: base + 'checkout.html?metodo=paypal&paypal=cancelado'
            })
        }).then(orden => {
            location.href = orden.aprobarUrl;
            // La página se va a PayPal: esta promesa no se resuelve.
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
        // Sale a PayPal: el checkout pide la confirmación antes de salir.
        saleDeLaPagina: true,
        montar,
        obtenerDatos,
        detalle: () => 'Con tu cuenta PayPal, en la página de PayPal'
    });
})();
