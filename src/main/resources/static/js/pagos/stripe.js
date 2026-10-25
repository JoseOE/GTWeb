/* ═══════════════════════════════════════
   Tarjeta de crédito o débito con Stripe Checkout (modo de prueba).
   ───────────────────────────────────────
   La tarjeta se captura en la página de pago de Stripe, no en la tienda:
     1. Al pulsar "Pagar", Spring abre una sesión de Stripe por el total del
        carrito (POST /api/pagos/stripe/sesiones) y la página va a
        checkout.stripe.com.
     2. Ahí la persona escribe su tarjeta. Stripe la autoriza y regresa a
        checkout.html?metodo=stripe&continuar=1&session_id=cs_...
     3. El checkout pulsa "Pagar" solo: obtenerDatos() resuelve con la sesión y
        Spring captura el cargo al crear el pedido (si algo cambió, lo libera).
   Si la persona cancela en Stripe, vuelve con ?stripe=cancelado y no se cobra nada.
═══════════════════════════════════════ */
(function () {
    // Se lee al cargar, antes de que el checkout quite "continuar" de la URL:
    // solo ese regreso automático cobra con la sesión.
    const inicio = new URLSearchParams(location.search);
    let pagada = inicio.get('continuar') === '1' && inicio.get('session_id') ? inicio.get('session_id') : null;
    let cancelado = inicio.get('stripe') === 'cancelado';

    function dinero(n) {
        return '$' + Number(n || 0).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // Quita de la URL lo que dejó Stripe, para que recargar no lo reuse.
    function limpiarUrl() {
        const params = new URLSearchParams(location.search);
        ['session_id', 'stripe'].forEach(k => params.delete(k));
        history.replaceState(null, '', location.pathname + (params.toString() ? '?' + params : ''));
    }

    function montar(contenedor, resumen) {
        contenedor.innerHTML = `
            <div class="pago-externo">
                <div class="d-flex align-items-center justify-content-between gap-2">
                    <span class="fw-semibold"><i class="bx bx-lock-alt" aria-hidden="true"></i> Pago seguro con Stripe</span>
                    <span class="pago-prueba">Modo de prueba</span>
                </div>
                ${cancelado ? '<div class="alert alert-warning small py-2 mt-2 mb-0" role="status">Cancelaste el pago con tarjeta. Tu carrito sigue igual: vuelve a intentarlo o elige otro método.</div>' : ''}
                <p class="mt-2 mb-2">Al pagar te llevamos a la página de <strong>Stripe</strong> para pagar
                    <strong>${dinero(resumen && resumen.total)}</strong> con tu tarjeta. Tu tarjeta no pasa por GymTrack.</p>
                <ul class="small text-body-secondary mb-0 ps-3">
                    <li>Tarjeta de prueba: <strong>4242 4242 4242 4242</strong>, cualquier fecha futura y cualquier CVC.</li>
                    <li>El cargo se hace al confirmarse tu pedido; si algo de tu carrito cambia, se libera.</li>
                </ul>
            </div>`;
        if (cancelado) {
            cancelado = false;
            limpiarUrl();
        }
    }

    function obtenerDatos() {
        if (pagada) {
            const sesion = pagada;
            pagada = null;
            limpiarUrl();
            return Promise.resolve({ sesion });
        }
        // Las direcciones de regreso son de esta misma página: Spring no acepta otro sitio web.
        const base = location.origin + location.pathname.replace(/[^/]*$/, '');
        return Tienda.api('/api/pagos/stripe/sesiones?canal=web', {
            method: 'POST',
            body: JSON.stringify({
                returnUrl: base + 'checkout.html?metodo=stripe&continuar=1',
                cancelUrl: base + 'checkout.html?metodo=stripe&stripe=cancelado'
            })
        }).then(sesion => {
            location.href = sesion.url;
            // La página se va a Stripe: esta promesa no se resuelve.
            return new Promise(() => {});
        });
    }

    MetodosPago.registrar({
        id: 'stripe',
        nombre: 'Tarjeta de crédito o débito',
        descripcion: 'Visa, Mastercard o American Express en la página segura de Stripe',
        icono: 'bx-credit-card',
        logos: ['visa', 'mastercard', 'amex'],
        orden: 1,
        // Sale a checkout.stripe.com: el checkout pide la confirmación antes de salir.
        saleDeLaPagina: true,
        montar,
        obtenerDatos,
        detalle: () => 'Con tarjeta, en la página de pago de Stripe'
    });
})();
