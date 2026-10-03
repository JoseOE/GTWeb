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

    function montar(contenedor, resumen) {
        cargarEstilos();
        contenedor.innerHTML = `
            <div class="pp-caja">
                <span class="pp-simulacion">Simulación</span>
                ${cancelado ? '<div class="alert alert-warning small py-2 mt-2 mb-0" role="status">Cancelaste el pago en PayPal. Tu carrito sigue igual: vuelve a intentarlo o elige otro método.</div>' : ''}
                <p class="mt-2 mb-2">Al pagar te llevamos al <strong>Simulador PayPal</strong> para aprobar
                    <strong>${dinero(resumen && resumen.total)}</strong> con una cuenta de prueba. Después regresas aquí y tu pedido queda pagado.</p>
                <ul class="small text-body-secondary mb-0 ps-3">
                    <li>No se pide contraseña ni se usa tu cuenta real de PayPal.</li>
                    <li>Si cancelas en el simulador, no se crea ningún pedido.</li>
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
            return Promise.resolve({ token: datos.token, payerId: datos.payerId });
        }
        // Las direcciones de regreso son de esta misma página: Spring no acepta
        // otro sitio web (la app usa su propio enlace, p. ej. gymtrack://...).
        const base = location.origin + location.pathname.replace(/[^/]*$/, '');
        return Tienda.api('/api/simuladores/paypal/ordenes?canal=web', {
            method: 'POST',
            body: JSON.stringify({
                returnUrl: base + 'checkout.html?metodo=paypal&continuar=1',
                cancelUrl: base + 'checkout.html?metodo=paypal&paypal=cancelado'
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
        descripcion: 'Aprueba el pago con una cuenta de prueba',
        icono: 'bx-wallet',
        orden: 3,
        montar,
        obtenerDatos
    });
})();
