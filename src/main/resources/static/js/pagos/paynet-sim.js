/* ═══════════════════════════════════════
   Simulador de Paynet: pago en efectivo en tiendas.
   ───────────────────────────────────────
   Al pagar, el pedido queda apartado como "Pendiente de pago" con una
   referencia de 18 dígitos y 72 h para pagar. La ficha (código de barras,
   referencia copiable, tiendas e instrucciones) se muestra en
   confirmacion.html y llega por correo en PDF. Todo es simulado: en la
   simulación, el dueño marca el pago desde el dashboard de ventas.
═══════════════════════════════════════ */
(function () {
    const TIENDAS = ['Tiendas de conveniencia', 'Farmacias', 'Supermercados', 'Tiendas departamentales'];

    function cargarEstilos() {
        if (document.getElementById('estilos-paynet-sim')) return;
        const link = document.createElement('link');
        link.id = 'estilos-paynet-sim';
        link.rel = 'stylesheet';
        link.href = 'css/pagos/paynet-sim.css';
        document.head.appendChild(link);
    }

    function escapar(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    function dinero(n) {
        return '$' + Number(n || 0).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function conSesion(url) {
        return fetch(url, { headers: { 'X-User-Id': localStorage.getItem('userId') || '' } });
    }

    function montar(contenedor, resumen) {
        cargarEstilos();
        contenedor.innerHTML = `
            <div class="pn-ficha">
                <span class="pn-simulacion">Simulación</span>
                <p class="mt-2 mb-2">Al confirmar te damos una <strong>referencia</strong> y un <strong>código de barras</strong> para pagar
                    <strong>${dinero(resumen && resumen.total)}</strong> en efectivo en cualquier tienda de la red.</p>
                <ul class="small text-body-secondary mb-0 ps-3">
                    <li>Tienes 72 horas para pagar; después el pedido se cancela solo.</li>
                    <li>Apartamos tu pedido y te avisamos por correo cuando recibamos el pago.</li>
                    <li>Si incluye un plan, tu membresía se activa al pagarse.</li>
                </ul>
            </div>`;
    }

    // Paynet no pide datos al comprador: la referencia la genera el simulador.
    function obtenerDatos() {
        return Promise.resolve({});
    }

    // Ficha en la confirmación: código de barras, referencia, fecha límite y
    // dónde pagar. Solo mientras el pedido esté pendiente.
    function confirmacion(contenedor, pedido) {
        if (pedido.estado !== 'pendiente_pago' || !pedido.paynet) return;
        cargarEstilos();
        const vence = pedido.paynet.vence
            ? new Date(pedido.paynet.vence).toLocaleString('es-MX', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
            : '';
        contenedor.innerHTML = `
            <section class="pn-ficha" aria-labelledby="pn-titulo">
                <div class="d-flex justify-content-between align-items-start gap-2">
                    <h2 class="seccion-titulo fs-5 mb-0" id="pn-titulo">Paga en efectivo</h2>
                    <span class="pn-simulacion">Simulación</span>
                </div>
                <div class="pn-monto mt-2">${dinero(pedido.total)}</div>
                <div class="small text-body-secondary">Monto exacto${vence ? ' · paga antes del <strong>' + escapar(vence) + '</strong>' : ''}</div>
                <img class="pn-codigo" id="pn-codigo" alt="Código de barras de la referencia ${escapar(pedido.paynet.referencia)}">
                <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <div>
                        <div class="small text-body-secondary">Referencia</div>
                        <div class="pn-referencia" id="pn-referencia">${escapar(pedido.paynet.referencia)}</div>
                    </div>
                    <button type="button" class="btn btn-outline-primary tocable px-3" id="pn-copiar"><i class="bx bx-copy" aria-hidden="true"></i> Copiar</button>
                </div>
                <h3 class="fs-6 fw-bold mt-4 mb-2">Cómo pagar</h3>
                <ol class="pn-pasos">
                    <li>Acude a cualquier tienda de la red Paynet.</li>
                    <li>Indica en caja que vas a pagar un servicio Paynet.</li>
                    <li>Muestra el código de barras o dicta la referencia.</li>
                    <li>Paga el monto exacto en efectivo y conserva tu comprobante.</li>
                </ol>
                <h3 class="fs-6 fw-bold mt-3 mb-2">Dónde pagar <small class="text-body-secondary fw-normal">(red simulada)</small></h3>
                <div class="pn-tiendas">${TIENDAS.map(t => '<span>' + escapar(t) + '</span>').join('')}</div>
                <p class="small mt-3 mb-3"><strong>Esta ficha es una simulación:</strong> no la presentes en una tienda real.</p>
                <button type="button" class="btn btn-outline-primary w-100 tocable" id="pn-pdf"><i class="bx bx-download" aria-hidden="true"></i> Descargar ficha (PDF)</button>
                <div class="small text-danger mt-2" id="pn-error" role="alert"></div>
            </section>`;

        const error = contenedor.querySelector('#pn-error');
        // La imagen se pide con fetch: un <img src> no puede mandar el X-User-Id.
        conSesion('/api/recibos/paynet/' + encodeURIComponent(pedido.orderId) + '/codigo.png')
            .then(res => res.ok ? res.blob() : Promise.reject())
            .then(blob => { contenedor.querySelector('#pn-codigo').src = URL.createObjectURL(blob); })
            .catch(() => { contenedor.querySelector('#pn-codigo').remove(); });

        contenedor.querySelector('#pn-copiar').addEventListener('click', (e) => {
            const boton = e.currentTarget;
            navigator.clipboard.writeText(pedido.paynet.referencia.replace(/\s/g, ''))
                .then(() => {
                    boton.innerHTML = '<i class="bx bx-check" aria-hidden="true"></i> Copiada';
                    setTimeout(() => { boton.innerHTML = '<i class="bx bx-copy" aria-hidden="true"></i> Copiar'; }, 1800);
                })
                .catch(() => { error.textContent = 'No se pudo copiar: selecciona la referencia y cópiala a mano.'; });
        });

        contenedor.querySelector('#pn-pdf').addEventListener('click', (e) => {
            const boton = e.currentTarget;
            boton.disabled = true;
            conSesion('/api/recibos/paynet/' + encodeURIComponent(pedido.orderId) + '.pdf')
                .then(res => res.ok ? res.blob() : Promise.reject(new Error('No se pudo descargar la ficha.')))
                .then(blob => {
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'ficha-paynet-' + pedido.folio + '.pdf';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    setTimeout(() => URL.revokeObjectURL(url), 10000);
                })
                .catch(err => { error.textContent = err.message; })
                .finally(() => { boton.disabled = false; });
        });
    }

    MetodosPago.registrar({
        id: 'paynet',
        nombre: 'Efectivo en tiendas (Paynet)',
        descripcion: 'Paga en tiendas de conveniencia en 72 h',
        icono: 'bx-store',
        orden: 2,
        montar,
        obtenerDatos,
        confirmacion
    });
})();
