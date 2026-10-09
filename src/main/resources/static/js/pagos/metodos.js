/* ═══════════════════════════════════════
   Métodos de pago del checkout
   ───────────────────────────────────────
   Cada simulador vive en su propio archivo y lo mantiene su dueño:
     js/pagos/stripe-sim.js  → tarjeta (bloque 3)
     js/pagos/paynet-sim.js  → efectivo en tiendas (bloque 4)
     js/pagos/paypal-sim.js  → PayPal (bloque 5)
   checkout.html los carga solos; si un archivo todavía no existe, ese método
   simplemente no aparece.

   Cada archivo se registra con:

   MetodosPago.registrar({
       id: 'stripe',                 // lo que Spring recibe en "metodo"
       nombre: 'Tarjeta de crédito o débito',
       descripcion: 'Visa, Mastercard o American Express de prueba',
       icono: 'bx-credit-card',      // ícono de Boxicons
       orden: 1,                     // posición en el selector

       // Dibuja el formulario del método dentro de `contenedor`.
       // resumen = { total, subtotal, iva, articulos, items, gym: { nombre, direccion } }
       montar(contenedor, resumen) {},

       // Valida lo que capturó la persona y resuelve con los datos que viajan
       // al servidor con el cobro (POST /api/tienda/carrito/checkout →
       // "datos"). Si falta algo, rechaza con new Error('mensaje para la persona').
       // Nunca mandes el número completo de una tarjeta ni el CVC: solo el token.
       obtenerDatos() { return Promise.resolve({}); },

       // Opcional. Se llama ya creado el pedido. Puede devolver (o resolver)
       // una URL a la que ir en lugar de confirmacion.html?pedido=<orderId>.
       despues(pedido) {},

       // Opcional. En confirmacion.html, para mostrar lo propio del método
       // (la ficha de Paynet, por ejemplo) bajo el resumen del pedido.
       confirmacion(contenedor, pedido) {},

       // Opcional. Se llama al cambiar a otro método, para limpiar timers o listeners.
       desmontar() {}
   });

   Métodos que salen de la página (PayPal): obtenerDatos() puede navegar a
   otra página. Al volver a checkout.html?metodo=<id>&continuar=1, el checkout
   preselecciona ese método, lo monta y vuelve a pulsar "Pagar" solo; en esa
   segunda llamada obtenerDatos() lee lo que trae la URL y resuelve.
═══════════════════════════════════════ */
(function () {
    const CONOCIDOS = ['stripe', 'paynet', 'paypal'];
    const registrados = {};

    window.MetodosPago = {
        registrar(metodo) {
            const faltan = ['id', 'nombre', 'montar', 'obtenerDatos'].filter(k => !metodo || !metodo[k]);
            if (faltan.length) {
                console.error('Método de pago incompleto, falta: ' + faltan.join(', '), metodo);
                return;
            }
            registrados[metodo.id] = metodo;
        },

        obtener(id) {
            return registrados[id] || null;
        },

        lista() {
            return Object.values(registrados).sort((a, b) => (a.orden || 99) - (b.orden || 99));
        },

        // "pp_sim-paynet_default" (proveedorPago del pedido) → "paynet"
        idDeProveedor(proveedor) {
            const m = /^pp_sim-([a-z]+)_/.exec(proveedor || '');
            return m ? m[1] : null;
        },

        // Carga js/pagos/<id>-sim.js de cada método conocido. Nunca falla: si un
        // archivo no existe todavía, ese método no se ofrece.
        cargar() {
            return Promise.all(CONOCIDOS.map(id => new Promise(resolve => {
                if (registrados[id]) return resolve();
                const script = document.createElement('script');
                script.src = 'js/pagos/' + id + '-sim.js';
                script.onload = resolve;
                script.onerror = resolve;
                document.head.appendChild(script);
            })));
        }
    };
})();
