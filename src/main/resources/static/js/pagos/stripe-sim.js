/* ═══════════════════════════════════════
   Simulador de Stripe: tarjeta de crédito o débito de prueba.
   ───────────────────────────────────────
   Sirve en el checkout de la tienda y como terminal en el Mostrador del panel.
   La tarjeta se dibuja mientras se escribe (número, nombre, vencimiento y
   marca) y gira al escribir el CVC. Al pagar, los datos van a
   POST /api/simuladores/stripe/tokens y al pedido solo llega el token: el número
   completo y el CVC no salen de esa petición.
   Solo se aceptan las tarjetas de prueba de abajo.
═══════════════════════════════════════ */
(function () {
    const PRUEBAS = [
        { numero: '4242424242424242', texto: 'Visa · aprobada' },
        { numero: '5555555555554444', texto: 'Mastercard · aprobada' },
        { numero: '378282246310005', texto: 'American Express · aprobada' },
        { numero: '4000000000000002', texto: 'Tarjeta rechazada' },
        { numero: '4000000000009995', texto: 'Fondos insuficientes' },
        { numero: '4000000000000069', texto: 'Tarjeta vencida' },
        { numero: '4000000000000127', texto: 'CVC incorrecto' }
    ];
    const NOMBRES_MARCA = { visa: 'Visa', mastercard: 'Mastercard', amex: 'Amex' };
    let instancia = 0;
    let actual = null;

    function cargarEstilos() {
        if (document.getElementById('estilos-stripe-sim')) return;
        const link = document.createElement('link');
        link.id = 'estilos-stripe-sim';
        link.rel = 'stylesheet';
        link.href = 'css/pagos/stripe-sim.css';
        document.head.appendChild(link);
    }

    function marcaDe(digitos) {
        if (/^4/.test(digitos)) return 'visa';
        if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(digitos)) return 'mastercard';
        if (/^3[47]/.test(digitos)) return 'amex';
        return '';
    }

    // Amex se agrupa 4-6-5; las demás, de 4 en 4.
    function agrupar(digitos, marca) {
        if (marca === 'amex') {
            return [digitos.slice(0, 4), digitos.slice(4, 10), digitos.slice(10, 15)].filter(Boolean).join(' ');
        }
        return digitos.slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
    }

    function largoDe(marca) { return marca === 'amex' ? 15 : 16; }

    function pasaLuhn(digitos) {
        let suma = 0;
        let doblar = false;
        for (let i = digitos.length - 1; i >= 0; i--) {
            let d = Number(digitos[i]);
            if (doblar) { d *= 2; if (d > 9) d -= 9; }
            suma += d;
            doblar = !doblar;
        }
        return suma % 10 === 0;
    }

    function escapar(s) {
        const div = document.createElement('div');
        div.textContent = s == null ? '' : String(s);
        return div.innerHTML;
    }

    function montar(contenedor, resumen) {
        cargarEstilos();
        const id = 'tv' + (++instancia);
        contenedor.innerHTML = `
            <div class="tv" id="${id}" data-marca="" aria-hidden="true">
                <div class="tv-giro">
                    <div class="tv-cara frente">
                        <span class="tv-simulacion">Simulación</span>
                        <div class="tv-chip"></div>
                        <div class="tv-numero" data-tv="numero">•••• •••• •••• ••••</div>
                        <div class="tv-pie">
                            <div style="min-width:0">
                                <div class="tv-etiqueta">Titular</div>
                                <div class="tv-titular" data-tv="titular">Nombre en la tarjeta</div>
                            </div>
                            <div>
                                <div class="tv-etiqueta">Vence</div>
                                <div data-tv="vence">MM/AA</div>
                            </div>
                            <div class="tv-marca" data-tv="marca"></div>
                        </div>
                    </div>
                    <div class="tv-cara atras">
                        <div class="tv-banda"></div>
                        <div class="tv-firma" data-tv="cvc">•••</div>
                        <div class="tv-nota-atras">Tarjeta de prueba de GymTrack · sin validez</div>
                    </div>
                </div>
            </div>
            <div class="tv-form row g-2">
                <div class="col-12">
                    <label class="form-label small fw-semibold mb-1" for="${id}-numero">Número de tarjeta</label>
                    <input type="text" class="form-control" id="${id}-numero" inputmode="numeric" autocomplete="off" placeholder="4242 4242 4242 4242" maxlength="19" aria-describedby="${id}-error">
                </div>
                <div class="col-12">
                    <label class="form-label small fw-semibold mb-1" for="${id}-titular">Nombre en la tarjeta</label>
                    <input type="text" class="form-control" id="${id}-titular" autocomplete="off" maxlength="60" placeholder="Como aparece en la tarjeta">
                </div>
                <div class="col-6">
                    <label class="form-label small fw-semibold mb-1" for="${id}-vence">Vencimiento</label>
                    <input type="text" class="form-control" id="${id}-vence" inputmode="numeric" autocomplete="off" placeholder="MM/AA" maxlength="5">
                </div>
                <div class="col-6">
                    <label class="form-label small fw-semibold mb-1" for="${id}-cvc">CVC</label>
                    <input type="text" class="form-control" id="${id}-cvc" inputmode="numeric" autocomplete="off" placeholder="123" maxlength="4">
                </div>
                <div class="col-12">
                    <div id="${id}-error" class="small text-danger" role="alert"></div>
                </div>
                <div class="col-12">
                    <button type="button" class="btn btn-outline-primary w-100 tv-generar" id="${id}-generar">
                        <i class="bx bx-shuffle" aria-hidden="true"></i> Generar tarjeta virtual de prueba
                    </button>
                </div>
                <div class="col-12">
                    <details class="tv-pruebas">
                        <summary>Ver tarjetas de prueba</summary>
                        <p class="small text-body-secondary mb-2">Es un simulador: solo acepta estas tarjetas. Usa cualquier nombre, un vencimiento futuro y cualquier CVC.</p>
                        <div class="d-grid gap-1">
                            ${PRUEBAS.map(p => `<button type="button" class="btn btn-light border d-flex justify-content-between gap-2" data-prueba="${p.numero}">
                                <span class="font-monospace">${agrupar(p.numero, marcaDe(p.numero))}</span><small class="text-body-secondary">${escapar(p.texto)}</small></button>`).join('')}
                        </div>
                    </details>
                </div>
            </div>`;

        const $ = (sufijo) => document.getElementById(id + sufijo);
        const tarjeta = document.getElementById(id);
        const campos = { numero: $('-numero'), titular: $('-titular'), vence: $('-vence'), cvc: $('-cvc') };
        const vista = (k) => tarjeta.querySelector('[data-tv="' + k + '"]');
        actual = { campos, error: $('-error'), resumen };

        function pintar() {
            const digitos = campos.numero.value.replace(/\D/g, '');
            const marca = marcaDe(digitos);
            tarjeta.dataset.marca = marca;
            const largo = largoDe(marca);
            const relleno = (digitos + '•'.repeat(Math.max(0, largo - digitos.length))).slice(0, largo);
            vista('numero').textContent = agrupar(relleno, marca);
            vista('marca').textContent = NOMBRES_MARCA[marca] || '';
            vista('titular').textContent = campos.titular.value.trim() || 'Nombre en la tarjeta';
            vista('vence').textContent = campos.vence.value || 'MM/AA';
            vista('cvc').textContent = campos.cvc.value || (marca === 'amex' ? '••••' : '•••');
            campos.cvc.maxLength = marca === 'amex' ? 4 : 3;
            campos.cvc.placeholder = marca === 'amex' ? '1234' : '123';
        }

        campos.numero.addEventListener('input', () => {
            const digitos = campos.numero.value.replace(/\D/g, '');
            const marca = marcaDe(digitos);
            campos.numero.value = agrupar(digitos.slice(0, largoDe(marca)), marca);
            actual.error.textContent = '';
            pintar();
        });
        campos.titular.addEventListener('input', pintar);
        campos.vence.addEventListener('input', (e) => {
            let d = campos.vence.value.replace(/\D/g, '').slice(0, 4);
            // Al borrar no se vuelve a poner la diagonal.
            if (d.length > 2 || (d.length === 2 && e.inputType !== 'deleteContentBackward')) d = d.slice(0, 2) + '/' + d.slice(2);
            campos.vence.value = d;
            pintar();
        });
        campos.cvc.addEventListener('input', () => { campos.cvc.value = campos.cvc.value.replace(/\D/g, ''); pintar(); });
        campos.cvc.addEventListener('focus', () => tarjeta.classList.add('girada'));
        campos.cvc.addEventListener('blur', () => tarjeta.classList.remove('girada'));

        function llenar(numero) {
            const marca = marcaDe(numero);
            const hoy = new Date();
            const mes = String(1 + Math.floor(Math.random() * 12)).padStart(2, '0');
            const anio = String((hoy.getFullYear() + 2 + Math.floor(Math.random() * 4)) % 100).padStart(2, '0');
            const cvc = String(Math.floor(Math.random() * (marca === 'amex' ? 9000 : 900)) + (marca === 'amex' ? 1000 : 100));
            const nombre = (resumen && resumen.titular) || localStorage.getItem('userName') || 'Cliente GymTrack';
            campos.numero.value = agrupar(numero, marca);
            if (!campos.titular.value.trim()) campos.titular.value = nombre.toUpperCase();
            campos.vence.value = mes + '/' + anio;
            campos.cvc.value = cvc;
            actual.error.textContent = '';
            pintar();
        }

        $('-generar').addEventListener('click', () => {
            const aprobadas = PRUEBAS.slice(0, 3);
            llenar(aprobadas[Math.floor(Math.random() * aprobadas.length)].numero);
        });
        contenedor.querySelectorAll('[data-prueba]').forEach(b => b.addEventListener('click', () => llenar(b.dataset.prueba)));
        pintar();
    }

    // Valida lo básico aquí para responder al instante; el servidor vuelve a
    // validar todo y es quien decide.
    function obtenerDatos() {
        if (!actual) return Promise.reject(new Error('Captura los datos de la tarjeta.'));
        const { campos, error } = actual;
        const digitos = campos.numero.value.replace(/\D/g, '');
        const [mes, anio] = campos.vence.value.split('/');
        let problema = '';
        if (digitos.length < 13) problema = 'Escribe el número completo de la tarjeta.';
        else if (!pasaLuhn(digitos)) problema = 'El número de tarjeta no es válido. Revisa que esté bien escrito.';
        else if (!campos.titular.value.trim()) problema = 'Escribe el nombre como aparece en la tarjeta.';
        else if (!mes || !anio || anio.length !== 2) problema = 'Escribe el vencimiento como MM/AA.';
        else if (campos.cvc.value.length !== (marcaDe(digitos) === 'amex' ? 4 : 3)) problema = 'Revisa el CVC.';
        if (problema) {
            error.textContent = problema;
            return Promise.reject(new Error(problema));
        }
        error.textContent = '';
        return fetch('/api/simuladores/stripe/tokens', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-User-Id': localStorage.getItem('userId') || '' },
            body: JSON.stringify({ numero: digitos, titular: campos.titular.value.trim(), mes, anio, cvc: campos.cvc.value })
        })
            .catch(() => { throw new Error('Sin conexión. Revisa tu internet e intenta de nuevo.'); })
            .then(res => res.json().catch(() => ({})).then(body => {
                if (!res.ok) {
                    error.textContent = body.error || 'No se pudo validar la tarjeta.';
                    throw new Error(body.error || 'No se pudo validar la tarjeta.');
                }
                return { token: body.token };
            }));
    }

    MetodosPago.registrar({
        id: 'stripe',
        nombre: 'Tarjeta de crédito o débito',
        descripcion: 'Visa, Mastercard o American Express de prueba',
        icono: 'bx-credit-card',
        orden: 1,
        montar,
        obtenerDatos,
        desmontar() { actual = null; }
    });
})();
