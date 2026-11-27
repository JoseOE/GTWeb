/* Panel del dueño (bienvenido.html): Suscripción a GymTrack.

   El gimnasio paga a GymTrack con el plan anual (cubre hasta el 31 de
   diciembre) o el mensual (hasta fin de mes). Si entra a medio periodo paga
   solo los días que faltan. Se paga con tarjeta (Stripe) o PayPal, en su
   modo de prueba:
     1. "Pagar" abre la página de Stripe o de PayPal.
     2. Al volver (?suscripcion=tarjeta&plan=anual&session_id=... o
        ?suscripcion=paypal&plan=anual&token=...&PayerID=...) se cobra solo.
   Si vence y pasan los días de gracia, el panel queda en solo lectura: el
   servidor rechaza los cambios hasta renovar (SuscripcionInterceptor). */

let suscripcionDatos = null;
let suscripcionPlan = 'anual';
let suscripcionModo = null;    // "sandbox" o "simulado" (/api/pagos/modo)
let suscripcionMensaje = null; // {tipo, texto}: lo que pasó con el último pago

const SUSCRIPCION_ESTADOS = {
    AL_DIA: { texto: 'Al día', clase: 'es-al-dia', icono: 'bx-check-circle' },
    POR_VENCER: { texto: 'Por vencer', clase: 'es-por-vencer', icono: 'bx-time-five' },
    GRACIA: { texto: 'Vencida · en días de gracia', clase: 'es-gracia', icono: 'bx-error' },
    VENCIDA: { texto: 'Vencida · solo lectura', clase: 'es-vencida', icono: 'bx-lock-alt' }
};

function initSuscripcion() {
    document.addEventListener('panel:mostrado', (e) => { if (e.detail === 'suscripcion') cargarSuscripcion(); });
    document.getElementById('suscripcion-contenido').addEventListener('click', alClicSuscripcion);

    fetch('/api/pagos/modo').then(r => r.ok ? r.json() : null).then(m => { suscripcionModo = m ? m.modo : null; }).catch(() => {});

    const regreso = new URLSearchParams(location.search);
    if (regreso.has('suscripcion')) {
        const metodo = regreso.get('suscripcion');
        const plan = regreso.get('plan') || 'anual';
        let datos = null;
        if (metodo === 'tarjeta' && regreso.get('session_id')) datos = { sesion: regreso.get('session_id') };
        if (metodo === 'paypal' && regreso.get('token')) datos = { token: regreso.get('token'), payerId: regreso.get('PayerID') };
        history.replaceState(null, '', location.pathname);
        const tab = document.getElementById('tab-suscripcion');
        if (tab) tab.click();
        if (datos) {
            suscripcionPlan = plan;
            pagarSuscripcion(metodo, datos);
            return;
        }
        suscripcionMensaje = { tipo: 'warning', texto: 'Cancelaste el pago. No se cobró nada: puedes intentarlo de nuevo.' };
    }
    cargarSuscripcion();
}

function cargarSuscripcion() {
    const gymId = localStorage.getItem('gymId');
    const contenedor = document.getElementById('suscripcion-contenido');
    if (!gymId) {
        contenedor.innerHTML = avisoSinGimnasio('Configura tu gimnasio primero: la suscripción empieza al crearlo.', 'bx-badge-check');
        pintarAvisoSuscripcion(null);
        return;
    }
    if (!suscripcionDatos) estadoCargando(contenedor, 'Revisando tu suscripción…');
    apiTienda('/api/gyms/' + gymId + '/suscripcion')
        .then(d => {
            suscripcionDatos = d;
            pintarSuscripcion();
            pintarAvisoSuscripcion(d);
        })
        .catch(e => estadoError(contenedor, e.message, cargarSuscripcion));
}

// El aviso arriba de todas las pestañas: solo cuando hay algo que hacer.
function pintarAvisoSuscripcion(d) {
    const aviso = document.getElementById('suscripcion-aviso');
    const marca = document.getElementById('suscripcion-nav-aviso');
    document.body.classList.toggle('panel-solo-lectura', !!(d && d.soloLectura));
    if (!d || d.estado === 'AL_DIA') {
        aviso.classList.add('d-none');
        marca.classList.add('d-none');
        return;
    }
    const hasta = fechaCorta(d.hasta);
    const limite = fechaCorta(sumarDias(d.soloLecturaDesde, -1));
    let texto;
    if (d.estado === 'POR_VENCER') {
        texto = '<strong>Tu suscripción a GymTrack vence el ' + hasta + '</strong> (' + (d.diasRestantes === 0 ? 'hoy' : d.diasRestantes === 1 ? 'mañana' : 'en ' + d.diasRestantes + ' días') + '). Renuévala para seguir sin interrupciones.';
    } else if (d.estado === 'GRACIA') {
        texto = d.nuncaPagada
            ? '<strong>Activa tu suscripción a GymTrack.</strong> Tienes hasta el ' + limite + ' para pagarla; después el panel queda en solo lectura.'
            : '<strong>Tu suscripción venció el ' + hasta + '.</strong> Tienes hasta el ' + limite + ' para renovarla; después el panel queda en solo lectura.';
    } else {
        texto = '<strong>Tu panel está en solo lectura.</strong> Puedes ver todo, pero no vender ni hacer cambios hasta renovar. La puerta, la tienda y la app de tus socios siguen funcionando.';
    }
    aviso.className = 'suscripcion-aviso ' + SUSCRIPCION_ESTADOS[d.estado].clase;
    aviso.innerHTML = '<i class="bx ' + SUSCRIPCION_ESTADOS[d.estado].icono + '" aria-hidden="true"></i><p class="mb-0">' + texto + '</p>'
        + '<button type="button" class="btn btn-sm ' + (d.estado === 'POR_VENCER' ? 'btn-outline-dark' : 'btn-dark') + ' tocable px-3" data-goto="suscripcion">Renovar</button>';
    marca.classList.toggle('d-none', d.estado === 'POR_VENCER');
}

function pintarSuscripcion() {
    const d = suscripcionDatos;
    const contenedor = document.getElementById('suscripcion-contenido');
    const est = SUSCRIPCION_ESTADOS[d.estado];
    const planActual = d.plan === 'anual' ? 'Plan anual' : d.plan === 'mensual' ? 'Plan mensual' : d.plan === 'cortesia' ? 'Cortesía de GymTrack' : 'Sin plan todavía';
    const dias = d.diasRestantes;
    const cuando = d.nuncaPagada ? 'Todavía no has pagado tu suscripción.'
        : dias > 1 ? 'Faltan ' + dias + ' días.'
        : dias === 1 ? 'Vence mañana.'
        : dias === 0 ? 'Vence hoy.'
        : 'Venció hace ' + (-dias) + (dias === -1 ? ' día.' : ' días.');

    const plan = (clave, titulo, nota) => {
        const c = d.cotizaciones[clave];
        const elegido = suscripcionPlan === clave;
        const precioLista = clave === 'anual' ? d.precios.anual : d.precios.mensual;
        return `
            <label class="suscripcion-plan${elegido ? ' es-elegido' : ''}">
                <input type="radio" name="suscripcion-plan" value="${clave}" class="visually-hidden"${elegido ? ' checked' : ''}${d.puedeRenovar ? '' : ' disabled'}>
                <span class="suscripcion-plan-cabeza">
                    <span class="fw-bold">${titulo}</span>
                    ${clave === 'anual' && d.precios.ahorroAnual > 0 ? '<span class="suscripcion-ahorro">Ahorras ' + dinero(d.precios.ahorroAnual) + '</span>' : ''}
                </span>
                <span class="suscripcion-precio">${dinero(precioLista)}<small>/${clave === 'anual' ? 'año' : 'mes'}</small></span>
                <span class="small text-body-secondary">${nota}</span>
                <span class="suscripcion-cotizacion">
                    Pagarías <strong>${dinero(c.monto)}</strong> del ${fechaCorta(c.desde)} al ${fechaCorta(c.hasta)}${c.proporcional ? ' <span class="text-body-secondary">(proporcional a los días)</span>' : ''}.
                </span>
            </label>`;
    };

    const elegida = d.cotizaciones[suscripcionPlan];
    const simulado = suscripcionModo === 'simulado';
    const pagos = d.pagos || [];
    contenedor.innerHTML = `
        ${suscripcionMensaje ? `<div class="alert alert-${suscripcionMensaje.tipo} rounded-3" role="status">${escapeHtml(suscripcionMensaje.texto)}</div>` : ''}
        <section class="dash-empty text-start p-4 mb-3 suscripcion-estado ${est.clase}" aria-labelledby="suscripcion-estado-titulo">
            <div class="d-flex flex-wrap align-items-start justify-content-between gap-3">
                <div>
                    <span class="suscripcion-chip ${est.clase}"><i class="bx ${est.icono}" aria-hidden="true"></i> ${est.texto}</span>
                    <h3 class="fw-bold fs-4 mt-2 mb-1" id="suscripcion-estado-titulo">${d.nuncaPagada ? 'Activa tu suscripción' : 'Pagada hasta el ' + fechaCorta(d.hasta)}</h3>
                    <p class="text-body-secondary mb-0">${planActual} · ${cuando}</p>
                </div>
                ${d.soloLectura ? '<p class="suscripcion-candado mb-0"><i class="bx bx-lock-alt" aria-hidden="true"></i> Panel en solo lectura</p>' : ''}
            </div>
        </section>

        <section class="dash-empty text-start p-4 mb-3" aria-labelledby="suscripcion-renovar-titulo">
            <h3 class="fw-bold fs-6 mb-1" id="suscripcion-renovar-titulo">${d.nuncaPagada ? 'Elige tu plan' : 'Renovar'}</h3>
            ${d.puedeRenovar
                ? `<p class="small text-body-secondary mb-3">Precios con IVA incluido. Todos los planes incluyen el panel, la tienda, la app para tus socios y el control de acceso.</p>
                   <div class="suscripcion-planes" role="radiogroup" aria-label="Plan de la suscripción">
                       ${plan('anual', 'Anual', 'Cubre hasta el 31 de diciembre.')}
                       ${plan('mensual', 'Mensual', 'Cubre hasta el último día del mes.')}
                   </div>
                   <div class="suscripcion-pagar">
                       <p class="mb-0"><span class="text-body-secondary">Total</span> <strong class="fs-4">${dinero(elegida.monto)}</strong></p>
                       <div class="d-flex flex-wrap gap-2">
                           <button type="button" class="btn btn-primary tocable px-4" data-pagar="tarjeta"><i class="bx bx-credit-card" aria-hidden="true"></i> Pagar con tarjeta</button>
                           ${simulado ? '' : `<button type="button" class="btn btn-outline-primary tocable px-4" data-pagar="paypal"><i class="bx bxl-paypal" aria-hidden="true"></i> Pagar con PayPal</button>`}
                       </div>
                   </div>
                   <p class="small text-body-secondary mt-2 mb-0"><i class="bx bx-lock-alt" aria-hidden="true"></i> Pagas en la página de Stripe o de PayPal. Pagos en modo de prueba: no se cobra dinero real.${simulado ? ' En modo simulado (pruebas locales) solo hay tarjeta, con la de prueba 4242 4242 4242 4242.' : ''}</p>`
                : `<p class="text-body-secondary mb-0">Ya estás al día. Podrás renovar cuando falten 45 días o menos para que venza.</p>`}
        </section>

        <section class="dash-empty text-start p-0 overflow-hidden" aria-labelledby="suscripcion-pagos-titulo">
            <div class="p-4 pb-3"><h3 class="fw-bold fs-6 mb-0" id="suscripcion-pagos-titulo">Tus pagos a GymTrack</h3></div>
            ${pagos.length ? `
            <div class="table-responsive">
                <table class="table dash-table mb-0">
                    <thead><tr><th class="ps-4">Fecha</th><th>Plan</th><th>Cubre</th><th>Método</th><th class="pe-4 text-end">Monto</th></tr></thead>
                    <tbody>${pagos.map(p => `
                        <tr>
                            <td class="ps-4">${fechaCorta(p.pagadoEn)}</td>
                            <td>${p.plan === 'anual' ? 'Anual' : 'Mensual'}</td>
                            <td class="text-body-secondary">${fechaCorta(p.desde)} – ${fechaCorta(p.hasta)}</td>
                            <td>${metodoSuscripcion(p)}</td>
                            <td class="pe-4 text-end fw-semibold">${dinero(p.monto)}</td>
                        </tr>`).join('')}
                    </tbody>
                </table>
            </div>` : '<p class="text-body-secondary px-4 pb-4 mb-0">Todavía no hay pagos.</p>'}
        </section>`;
}

function metodoSuscripcion(p) {
    const d = p.detalle || {};
    if (p.metodo === 'paypal') return 'PayPal' + (d.cuenta ? ' · ' + escapeHtml(d.cuenta) : '');
    const marca = d.marca ? String(d.marca).charAt(0).toUpperCase() + String(d.marca).slice(1) : 'Tarjeta';
    return escapeHtml(marca) + (d.ultimos4 ? ' •••• ' + escapeHtml(d.ultimos4) : '');
}

function alClicSuscripcion(e) {
    const radio = e.target.closest('input[name="suscripcion-plan"]');
    if (radio) {
        suscripcionPlan = radio.value;
        suscripcionMensaje = null;
        pintarSuscripcion();
        return;
    }
    const boton = e.target.closest('[data-pagar]');
    if (boton) abrirPagoSuscripcion(boton.dataset.pagar, boton);
}

// Abre la página de pago. En modo simulado (pruebas locales) la tarjeta usa
// la de prueba del simulador y se cobra sin salir del panel.
function abrirPagoSuscripcion(metodo, boton) {
    const gymId = localStorage.getItem('gymId');
    const base = location.origin + location.pathname.replace(/[^/]*$/, '');
    document.querySelectorAll('[data-pagar]').forEach(b => { b.disabled = true; });
    boton.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Abriendo…';
    let paso;
    if (metodo === 'tarjeta' && suscripcionModo === 'simulado') {
        paso = apiTienda('/api/simuladores/stripe/tokens', {
            method: 'POST',
            body: JSON.stringify({ numero: '4242424242424242', titular: localStorage.getItem('userName') || 'Dueño', mes: '12', anio: '30', cvc: '123' })
        }).then(t => pagarSuscripcion('tarjeta', { token: t.token }));
    } else {
        const paypal = metodo === 'paypal';
        paso = apiTienda('/api/gyms/' + gymId + '/suscripcion/' + (paypal ? 'paypal/orden' : 'stripe/sesion'), {
            method: 'POST',
            body: JSON.stringify({
                plan: suscripcionPlan,
                returnUrl: base + 'bienvenido.html?suscripcion=' + metodo + '&plan=' + suscripcionPlan,
                cancelUrl: base + 'bienvenido.html?suscripcion=cancelado'
            })
        }).then(r => { location.href = paypal ? r.aprobarUrl : r.url; });
    }
    paso.catch(err => {
        suscripcionMensaje = { tipo: 'danger', texto: err.message };
        pintarSuscripcion();
    });
}

function pagarSuscripcion(metodo, datos) {
    const gymId = localStorage.getItem('gymId');
    const contenedor = document.getElementById('suscripcion-contenido');
    estadoCargando(contenedor, 'Confirmando tu pago…');
    return apiTienda('/api/gyms/' + gymId + '/suscripcion/pagar', {
        method: 'POST',
        body: JSON.stringify({ plan: suscripcionPlan, metodo, datos })
    }).then(d => {
        suscripcionDatos = d;
        suscripcionMensaje = { tipo: 'success', texto: '¡Listo! Pagaste ' + dinero(d.pago.monto) + '. Tu suscripción queda pagada hasta el ' + fechaCorta(d.hasta) + '. Te mandamos el comprobante a tu correo.' };
        pintarSuscripcion();
        pintarAvisoSuscripcion(d);
    }).catch(err => {
        suscripcionMensaje = { tipo: 'danger', texto: err.message };
        cargarSuscripcion();
    });
}
