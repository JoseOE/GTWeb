/* ═══════════════════════════════════════
   Simular el resultado de PayPal (solo en pruebas)
   ───────────────────────────────────────
   En el sandbox de PayPal se puede pedir que el cobro falle como fallaría de
   verdad: "Sin fondos" (INSTRUMENT_DECLINED) o "Cuenta inválida o vencida"
   (PAYER_CANNOT_PAY). Se aprueba igual en PayPal y, al capturar, PayPal
   responde con ese rechazo: no se cobra ni se crea el pedido.

   El selector solo aparece si /api/pagos/modo dice simularPaypal: true (el
   CI y PayPal en sandbox); con PayPal en producción no existe. La elección
   se guarda en sessionStorage porque la página se va a PayPal y regresa.
═══════════════════════════════════════ */
window.PaypalSimular = (function () {
    const CLAVE = 'paypalSimular';
    const OPCIONES = [
        ['aprobado', 'Aprobado'],
        ['sin_fondos', 'Sin fondos suficientes'],
        ['cuenta_invalida', 'Cuenta inválida o vencida']
    ];
    let disponible = null;

    function permitido() {
        if (!disponible) {
            disponible = fetch('/api/pagos/modo').then(r => r.ok ? r.json() : {}).then(m => !!m.simularPaypal).catch(() => false);
        }
        return disponible;
    }

    function leer() {
        try { return sessionStorage.getItem(CLAVE) || 'aprobado'; } catch (e) { return 'aprobado'; }
    }

    function guardar(valor) {
        try {
            if (!valor || valor === 'aprobado') sessionStorage.removeItem(CLAVE);
            else sessionStorage.setItem(CLAVE, valor);
        } catch (e) { /* sin almacenamiento: se paga aprobado */ }
    }

    // Lo que se manda al cobrar ({simular} o nada) y se borra: cada intento elige de nuevo.
    function tomar() {
        const v = leer();
        guardar(null);
        return v === 'aprobado' ? {} : { simular: v };
    }

    // Pinta el selector dentro de contenedor si se puede simular.
    function montar(contenedor) {
        if (!contenedor) return;
        permitido().then(ok => {
            if (!ok) { contenedor.innerHTML = ''; return; }
            const id = 'pps' + Math.random().toString(36).slice(2, 7);
            const actual = leer();
            contenedor.innerHTML = '<div class="mt-3 p-2 rounded-3 border border-warning-subtle bg-warning-subtle bg-opacity-25">'
                + '<label for="' + id + '" class="form-label small fw-semibold mb-1"><i class="bx bx-test-tube" aria-hidden="true"></i> Simular el resultado de PayPal (modo de prueba)</label>'
                + '<select id="' + id + '" class="form-select form-select-sm">'
                + OPCIONES.map(([v, t]) => '<option value="' + v + '"' + (v === actual ? ' selected' : '') + '>' + t + '</option>').join('')
                + '</select>'
                + '<small class="text-body-secondary d-block mt-1">Apruebas en PayPal como siempre; con un rechazo, PayPal no cobra y no se crea el pedido.</small></div>';
            contenedor.querySelector('select').addEventListener('change', e => guardar(e.target.value));
        });
    }

    return { montar, tomar, guardar };
})();
