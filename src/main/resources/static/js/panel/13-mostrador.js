/* Panel del dueño (bienvenido.html): Mostrador. */

/* ═══════════════════════════════════════
   MOSTRADOR — /api/gyms/{id}/mostrador
   El ticket vive en el navegador (y en localStorage, para que no se
   pierda al recargar): agregar, cambiar y quitar no esperan al
   servidor. Al cobrar se manda completo y Spring lo revisa otra vez.
═══════════════════════════════════════ */
const MAX_PIEZAS_MOSTRADOR = 20;
let mostradorCatalogo = [];
let mostradorTicket = [];      // [{varianteId, cantidad, precio, nombre}] en el orden en que se agregaron
let mostradorCarrito = null;   // el ticket listo para pintar: partidas con nombre y totales
let mostradorMiembros = [];
let mostradorCliente = null;
let mostradorOcupado = false;
// Al volver de la página de pago de Stripe o de PayPal: con qué método y
// qué se le manda al servidor ({sesion} o {token, payerId}), y qué hacer
// en cuanto el ticket esté cargado (cobrarlo).
let mostradorRegreso = null;
let alCargarMostrador = null;
let grupoAbierto = null;

function initMostrador() {
    const buscar = document.getElementById('mostrador-buscar');
    const catalogo = document.getElementById('mostrador-catalogo');
    const partidas = document.getElementById('mostrador-partidas');
    const recibido = document.getElementById('mostrador-recibido');
    const clienteBuscar = document.getElementById('mostrador-cliente-buscar');
    const clienteResultados = document.getElementById('mostrador-cliente-resultados');
    const ventaModal = new bootstrap.Modal(document.getElementById('ventaModal'));
    let ultimaVenta = null;

    buscar.addEventListener('input', pintarCatalogoMostrador);

    // Una tarjeta por producto: si solo tiene una presentación se agrega
    // directo; si tiene varias (o son las membresías) se abre la lista.
    const opcionesModal = new bootstrap.Modal(document.getElementById('opcionesModal'));
    catalogo.addEventListener('click', (e) => {
        const tarjeta = e.target.closest('[data-mostrador-grupo]');
        if (!tarjeta || mostradorOcupado) return;
        const grupo = gruposMostrador().find(g => g.clave === tarjeta.dataset.mostradorGrupo);
        if (!grupo) return;
        if (grupo.opciones.length === 1) {
            agregarAlTicket(grupo.opciones[0].variante.id);
            return;
        }
        grupoAbierto = grupo.clave;
        pintarOpcionesMostrador();
        opcionesModal.show();
    });
    document.getElementById('opciones-cuerpo').addEventListener('click', (e) => {
        const opcion = e.target.closest('[data-mostrador-variante]');
        if (!opcion || mostradorOcupado) return;
        agregarAlTicket(opcion.dataset.mostradorVariante);
        const error = document.getElementById('mostrador-error').textContent;
        const caja = document.getElementById('opciones-error');
        caja.textContent = error;
        caja.classList.toggle('d-none', !error);
    });
    document.getElementById('opcionesModal').addEventListener('hidden.bs.modal', () => {
        grupoAbierto = null;
        document.getElementById('opciones-error').classList.add('d-none');
    });

    partidas.addEventListener('click', (e) => {
        const mas = e.target.closest('[data-m-mas]');
        const menos = e.target.closest('[data-m-menos]');
        const quitar = e.target.closest('[data-m-quitar]');
        if (mostradorOcupado || (!mas && !menos && !quitar)) return;
        if (quitar) return quitarDelTicket(quitar.dataset.mQuitar);
        if (mas) cambiarEnTicket(mas.dataset.mMas, 1);
        else cambiarEnTicket(menos.dataset.mMenos, -1);
    });

    document.getElementById('mostrador-cancelar').addEventListener('click', () => {
        if (mostradorOcupado || !mostradorTicket.length) return;
        if (!confirm('¿Cancelar esta venta? Se vacía el ticket.')) return;
        mostradorTicket = [];
        guardarTicket();
        errorMostrador('');
        pintarTicket();
    });

    // ─── Cliente: un miembro del gimnasio o el público en general ───
    clienteBuscar.addEventListener('input', () => {
        const q = clienteBuscar.value.trim().toLowerCase();
        if (!q) { clienteResultados.innerHTML = ''; return; }
        const encontrados = mostradorMiembros
            .filter(m => (m.nombre || '').toLowerCase().includes(q) || (m.email || '').toLowerCase().includes(q))
            .slice(0, 6);
        clienteResultados.innerHTML = encontrados.length
            ? encontrados.map(m => '<button type="button" class="list-group-item list-group-item-action py-2" data-cliente="' + escapeHtml(m.id) + '">'
                + '<span class="fw-semibold">' + escapeHtml(m.nombre) + '</span><br><small class="text-body-secondary">' + escapeHtml(m.email) + '</small></button>').join('')
            : '<div class="list-group-item small text-body-secondary">Ningún miembro coincide.</div>';
    });
    clienteResultados.addEventListener('click', (e) => {
        const b = e.target.closest('[data-cliente]');
        if (!b) return;
        elegirCliente(mostradorMiembros.find(m => m.id === b.dataset.cliente));
    });
    document.getElementById('mostrador-cliente-quitar').addEventListener('click', () => elegirCliente(null));

    function elegirCliente(m) {
        mostradorCliente = m || null;
        clienteBuscar.value = '';
        clienteResultados.innerHTML = '';
        document.getElementById('mostrador-cliente-nombre').innerHTML = m
            ? '<i class="bx bx-user-check"></i> ' + escapeHtml(m.nombre) + ' <small class="text-body-secondary">· ' + escapeHtml(m.email) + '</small>'
            : '<i class="bx bx-user"></i> Público en general';
        document.getElementById('mostrador-cliente-quitar').classList.toggle('d-none', !m);
    }

    // ─── Forma de cobro ───
    document.querySelectorAll('input[name="mostrador-metodo"]').forEach(r => r.addEventListener('change', () => {
        const metodo = metodoMostrador();
        ['efectivo', 'tarjeta', 'paypal', 'tienda'].forEach(m =>
            document.getElementById('mostrador-' + m).classList.toggle('d-none', m !== metodo));
        actualizarCobro();
    }));
    recibido.addEventListener('input', actualizarCobro);
    document.getElementById('mostrador-billetes').addEventListener('click', (e) => {
        const b = e.target.closest('[data-billete]');
        if (!b) return;
        recibido.value = b.dataset.billete;
        actualizarCobro();
    });

    document.getElementById('mostrador-cobrar').addEventListener('click', cobrarMostrador);

    function cobrarMostrador() {
        if (mostradorOcupado || !mostradorCarrito || !mostradorCarrito.items.length) return;
        const gymId = localStorage.getItem('gymId');
        const metodo = metodoMostrador();
        const boton = document.getElementById('mostrador-cobrar');
        errorMostrador('');
        const cuerpo = {
            partidas: mostradorTicket.map(p => ({ varianteId: p.varianteId, cantidad: p.cantidad })),
            clienteId: mostradorCliente ? mostradorCliente.id : null, metodo, totalVisto: mostradorCarrito.total
        };
        let preparar;
        if (metodo === 'efectivo') {
            const monto = parseFloat(recibido.value);
            if (!(monto + 0.009 >= mostradorCarrito.total)) return errorMostrador('El efectivo recibido no alcanza para ' + dinero(mostradorCarrito.total) + '.');
            cuerpo.recibido = monto;
            preparar = Promise.resolve();
        } else if (metodo === 'tienda') {
            // Openpay genera la ficha de Paynet; el pedido queda pendiente de pago.
            preparar = Promise.resolve();
        } else if (mostradorRegreso && mostradorRegreso.metodo === metodo) {
            // De regreso de Stripe o PayPal: se cobra con lo que aprobó el cliente.
            cuerpo.datos = mostradorRegreso.datos;
            mostradorRegreso = null;
            preparar = Promise.resolve();
        } else {
            // Se abre la página de pago de Stripe o de PayPal; al volver, la venta se cobra sola.
            const base = location.origin + location.pathname.replace(/[^/]*$/, '');
            try { sessionStorage.setItem('mostradorCliente', JSON.stringify(mostradorCliente)); } catch (e) { /* sin almacenamiento: vuelve como público en general */ }
            const paypal = metodo === 'paypal';
            preparar = apiTienda('/api/gyms/' + gymId + '/mostrador/' + (paypal ? 'paypal/orden' : 'stripe/sesion'), {
                method: 'POST',
                body: JSON.stringify({
                    partidas: cuerpo.partidas, totalVisto: cuerpo.totalVisto,
                    returnUrl: base + 'bienvenido.html?cobro=' + metodo,
                    cancelUrl: base + 'bienvenido.html?cobro=cancelado&metodo=' + metodo
                })
            }).then(r => {
                location.href = paypal ? r.aprobarUrl : r.url;
                return new Promise(() => {});
            });
        }
        mostradorOcupado = true;
        boton.disabled = true;
        boton.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> Cobrando…';
        preparar
            .then(() => apiTienda('/api/gyms/' + gymId + '/mostrador/cobrar', { method: 'POST', body: JSON.stringify(cuerpo) }))
            .then(venta => {
                ultimaVenta = venta.pedido;
                const p = venta.pedido;
                mostrarFicha(p);
                document.getElementById('venta-detalle').innerHTML = 'Venta <strong>#' + escapeHtml(p.folio) + '</strong> · ' + dinero(p.total)
                    + '<br>' + escapeHtml(p.cliente || 'Público en general') + ' · ' + escapeHtml(p.detallePago || p.metodo);
                document.getElementById('venta-cambio').innerHTML = venta.cambio != null
                    ? '<div class="small text-body-secondary">Cambio</div><div class="display-5 fw-bold">' + dinero(venta.cambio) + '</div>' : '';
                const conPlan = p.partidas.some(x => x.tipo === 'membresia');
                document.getElementById('venta-membresia').innerHTML = conPlan && venta.cliente && venta.cliente.fechaProximoPago
                    ? '<div class="alert alert-success small py-2 mb-0"><i class="bx bx-check-circle"></i> Membresía de ' + escapeHtml(venta.cliente.nombre)
                        + ' activa hasta el ' + new Date(venta.cliente.fechaProximoPago + 'T12:00:00').toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' }) + '.</div>'
                    : '';
                document.getElementById('venta-ticket-error').classList.add('d-none');
                ventaModal.show();
                recibido.value = '';
                elegirCliente(null);
                mostradorTicket = [];
                guardarTicket();
                pintarTicket();
                loadMostrador(gymId);   // existencias nuevas
                loadMembers(gymId);
                loadBilling(gymId);
            })
            .catch(err => {
                // 409: algo del ticket cambió (existencias, precio). Se recarga
                // el catálogo y el ticket se muestra corregido, con los avisos.
                if (err.status === 409) loadMostrador(gymId, err.datos && err.datos.avisos);
                errorMostrador(err.message);
            })
            .finally(() => {
                mostradorOcupado = false;
                boton.innerHTML = 'Cobrar';
                actualizarCobro();
            });
    }

    // Con pago en tienda el modal muestra la ficha de Paynet en lugar del
    // ticket: la venta todavía no está pagada ni entregada.
    function mostrarFicha(p) {
        const ficha = p.paynet && p.estado === 'pendiente_pago' ? p.paynet : null;
        document.getElementById('venta-icono').className = ficha ? 'bx bx-receipt text-primary' : 'bx bx-check-circle text-success';
        document.getElementById('ventaModalLabel').textContent = ficha ? 'Ficha de pago generada' : 'Venta cobrada';
        document.getElementById('venta-ficha').classList.toggle('d-none', !ficha);
        document.getElementById('venta-ticket').classList.toggle('d-none', !!ficha);
        if (!ficha) return;
        document.getElementById('venta-ficha-referencia').textContent = ficha.referencia;
        const vence = ficha.vence ? new Date(ficha.vence) : null;
        document.getElementById('venta-ficha-vence').textContent = vence && !isNaN(vence)
            ? 'Vence el ' + vence.toLocaleString('es-MX', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
            : '';
        document.getElementById('venta-ficha-nota').innerHTML = '<i class="bx bx-info-circle"></i> El cliente paga '
            + dinero(p.total) + ' en efectivo en una tienda de la red Paynet con esta referencia. Los productos quedan apartados: entrégalos cuando el pedido aparezca pagado en <strong>Ventas</strong>.'
            + (p.cliente && p.cliente !== 'Público en general' ? ' La ficha también le llega por correo.' : ' Dale la ficha impresa o que le tome foto.')
            + (ficha.prueba ? '<br><strong>Modo de prueba:</strong> simula el pago en el panel de Openpay (Paynet → Pagos).' : '');
        const oficial = document.getElementById('venta-ficha-oficial');
        oficial.classList.toggle('d-none', !ficha.fichaUrl);
        if (ficha.fichaUrl) oficial.href = ficha.fichaUrl;
    }

    // Ticket de 80 mm y ficha de Paynet (los genera el módulo de recibos). La
    // ventana se abre antes del fetch para que el navegador no la bloquee como emergente.
    function abrirPdf(url, errorFaltante, errorGeneral) {
        const aviso = document.getElementById('venta-ticket-error');
        const ventana = window.open('', '_blank');
        fetch(url, { headers: { 'X-User-Id': localStorage.getItem('userId') || '' } })
            .then(res => {
                if (res.status === 404) throw new Error(errorFaltante);
                if (!res.ok) throw new Error(errorGeneral);
                return res.blob();
            })
            .then(blob => { if (ventana) ventana.location = URL.createObjectURL(blob); })
            .catch(err => {
                if (ventana) ventana.close();
                aviso.textContent = err.message;
                aviso.classList.remove('d-none');
            });
    }
    document.getElementById('venta-ticket').addEventListener('click', () => {
        if (!ultimaVenta) return;
        abrirPdf('/api/recibos/pedidos/' + encodeURIComponent(ultimaVenta.orderId) + '.pdf?formato=ticket',
            'El ticket todavía no está disponible: llega con el módulo de recibos.', 'No se pudo generar el ticket.');
    });
    document.getElementById('venta-ficha-pdf').addEventListener('click', () => {
        if (!ultimaVenta) return;
        abrirPdf('/api/recibos/paynet/' + encodeURIComponent(ultimaVenta.orderId) + '.pdf',
            'No se encontró la ficha de este pedido.', 'No se pudo generar la ficha.');
    });

    // Regreso de la página de pago de Stripe (?cobro=tarjeta&session_id=...)
    // o de PayPal (?cobro=paypal&token=...&PayerID=...): se abre el
    // Mostrador y, con el ticket cargado, se cobra la venta.
    const regreso = new URLSearchParams(location.search);
    if (regreso.has('cobro')) {
        const cobro = regreso.get('cobro');
        const metodo = cobro === 'paypal' || regreso.get('metodo') === 'paypal' ? 'paypal' : 'tarjeta';
        let pagada = null;
        if (cobro === 'tarjeta' && regreso.get('session_id')) pagada = { sesion: regreso.get('session_id') };
        if (cobro === 'paypal' && regreso.get('token')) pagada = { token: regreso.get('token'), payerId: regreso.get('PayerID'), ...PaypalSimular.tomar() };
        history.replaceState(null, '', location.pathname);
        const tab = document.getElementById('tab-mostrador');
        if (tab) tab.click();
        let cliente = null;
        try {
            cliente = JSON.parse(sessionStorage.getItem('mostradorCliente') || 'null');
            sessionStorage.removeItem('mostradorCliente');
        } catch (e) { /* sin almacenamiento */ }
        const radio = document.getElementById('mostrador-' + metodo + '-radio');
        radio.checked = true;
        radio.dispatchEvent(new Event('change'));
        if (cliente) elegirCliente(cliente);
        if (pagada) {
            mostradorRegreso = { metodo, datos: pagada };
            alCargarMostrador = () => cobrarMostrador();
        } else {
            errorMostrador('Se canceló el pago ' + (metodo === 'paypal' ? 'con PayPal' : 'con tarjeta') + '. El ticket sigue igual: cobra de nuevo o con otro método.');
        }
    }

    const gymId = localStorage.getItem('gymId');
    loadMostrador(gymId);
}

function metodoMostrador() {
    return document.querySelector('input[name="mostrador-metodo"]:checked').value;
}

function errorMostrador(mensaje) {
    const caja = document.getElementById('mostrador-error');
    caja.textContent = mensaje;
    caja.classList.toggle('d-none', !mensaje);
}

// ─── El ticket ───
function claveTicket() {
    return 'mostradorTicket:' + (localStorage.getItem('gymId') || '');
}

function guardarTicket() {
    try { localStorage.setItem(claveTicket(), JSON.stringify(mostradorTicket)); } catch (e) { /* sin almacenamiento: el ticket solo vive en la página */ }
}

function leerTicketGuardado() {
    try {
        const guardado = JSON.parse(localStorage.getItem(claveTicket()) || '[]');
        return Array.isArray(guardado) ? guardado.filter(p => p && p.varianteId && p.cantidad > 0) : [];
    } catch (e) {
        return [];
    }
}

function enCatalogo(varianteId) {
    for (const producto of mostradorCatalogo) {
        const variante = producto.variantes.find(v => v.id === varianteId);
        if (variante) return { producto, variante };
    }
    return null;
}

// Cuántas piezas caben de una opción: los planes de uno en uno; los
// productos hasta sus existencias (si las controla) y nunca más de 20.
function maximoEnTicket(producto, variante) {
    if (producto.tipo === 'membresia') return 1;
    return variante.disponible == null ? MAX_PIEZAS_MOSTRADOR : Math.min(variante.disponible, MAX_PIEZAS_MOSTRADOR);
}

// El plan del ticket que extiende la membresía (la inscripción de pago único no cuenta).
function planEnTicket() {
    for (const p of mostradorTicket) {
        const e = enCatalogo(p.varianteId);
        if (e && e.producto.tipo === 'membresia' && e.producto.plan && e.producto.plan.duracionUnidad) return e.producto;
    }
    return null;
}

function agregarAlTicket(varianteId) {
    errorMostrador('');
    const e = enCatalogo(varianteId);
    if (!e) return errorMostrador('Ese producto ya no está a la venta.');
    const { producto, variante } = e;
    const partida = mostradorTicket.find(p => p.varianteId === varianteId);
    if (producto.tipo === 'membresia') {
        if (partida) return errorMostrador('«' + producto.nombre + '» ya está en el ticket.');
        const otro = planEnTicket();
        if (otro && producto.plan && producto.plan.duracionUnidad) {
            return errorMostrador('Solo puede ir un plan por venta. Quita «' + otro.nombre + '» para elegir otro.');
        }
    }
    const cantidad = (partida ? partida.cantidad : 0) + 1;
    if (cantidad > maximoEnTicket(producto, variante)) return errorMostrador(textoDeTope(producto, variante));
    if (partida) partida.cantidad = cantidad;
    else mostradorTicket.push({ varianteId, cantidad, precio: variante.precio, nombre: producto.nombre });
    guardarTicket();
    pintarTicket();
}

function cambiarEnTicket(varianteId, cambio) {
    errorMostrador('');
    const partida = mostradorTicket.find(p => p.varianteId === varianteId);
    const e = enCatalogo(varianteId);
    if (!partida || !e) return;
    const cantidad = partida.cantidad + cambio;
    if (cantidad < 1) return;
    if (cantidad > maximoEnTicket(e.producto, e.variante)) return errorMostrador(textoDeTope(e.producto, e.variante));
    partida.cantidad = cantidad;
    guardarTicket();
    pintarTicket();
}

function quitarDelTicket(varianteId) {
    errorMostrador('');
    mostradorTicket = mostradorTicket.filter(p => p.varianteId !== varianteId);
    guardarTicket();
    pintarTicket();
}

function textoDeTope(producto, variante) {
    if (variante.disponible != null && variante.disponible <= MAX_PIEZAS_MOSTRADOR) {
        return variante.disponible <= 0 ? '«' + producto.nombre + '» se agotó.' : 'Solo hay ' + variante.disponible + ' de «' + producto.nombre + '».';
    }
    return 'Puedes vender hasta ' + MAX_PIEZAS_MOSTRADOR + ' piezas de «' + producto.nombre + '» por venta.';
}

// Repasa el ticket contra el catálogo recién cargado: quita lo que ya no
// se vende o se agotó, ajusta lo que ya no alcanza y toma los precios
// vigentes. Devuelve un aviso por cada cambio.
function reconciliarTicket() {
    const avisos = [];
    mostradorTicket = mostradorTicket.filter(p => {
        const e = enCatalogo(p.varianteId);
        if (!e) {
            avisos.push('Quitamos «' + (p.nombre || 'un producto') + '» porque ya no está a la venta.');
            return false;
        }
        const maximo = maximoEnTicket(e.producto, e.variante);
        if (maximo <= 0) {
            avisos.push('«' + e.producto.nombre + '» se agotó y lo quitamos del ticket.');
            return false;
        }
        if (p.cantidad > maximo) {
            p.cantidad = maximo;
            avisos.push('Solo quedan ' + maximo + ' de «' + e.producto.nombre + '»: ajustamos la cantidad.');
        }
        if (Math.abs(e.variante.precio - p.precio) > 0.009) {
            avisos.push('El precio de «' + e.producto.nombre + '» cambió de ' + dinero(p.precio) + ' a ' + dinero(e.variante.precio) + '.');
            p.precio = e.variante.precio;
        }
        p.nombre = e.producto.nombre;
        return true;
    });
    guardarTicket();
    return avisos;
}

// El ticket listo para pintar. Los precios ya incluyen IVA: el total es
// la suma de las partidas y el IVA se desglosa de ahí.
function armarCarrito() {
    const centavos = n => Math.round(n * 100) / 100;
    const items = [];
    mostradorTicket.forEach(p => {
        const e = enCatalogo(p.varianteId);
        if (!e) return;
        const esPlan = e.producto.tipo === 'membresia';
        items.push({
            id: p.varianteId,
            varianteId: p.varianteId,
            titulo: e.producto.nombre,
            variante: esPlan ? (e.producto.plan ? e.producto.plan.duracionTexto : '') : (e.variante.nombre === 'Única' ? '' : e.variante.nombre),
            esPlan,
            cantidad: p.cantidad,
            maximo: maximoEnTicket(e.producto, e.variante),
            precioUnitario: p.precio,
            total: centavos(p.precio * p.cantidad)
        });
    });
    const total = centavos(items.reduce((suma, i) => suma + i.total, 0));
    const iva = centavos(total - total / 1.16);
    mostradorCarrito = { items, total, iva, subtotal: centavos(total - iva) };
}

// avisosDelServidor: los del 409 al cobrar, si el catálogo recargado no
// explica por sí solo qué cambió.
function loadMostrador(gymId, avisosDelServidor) {
    const catalogo = document.getElementById('mostrador-catalogo');
    if (!gymId) {
        catalogo.innerHTML = avisoSinGimnasio('Configura primero "Mi Gimnasio" para vender en mostrador.', 'bx-store');
        return;
    }
    mostradorTicket = leerTicketGuardado();
    estadoCargando(catalogo, 'Cargando productos…');
    apiTienda('/api/tienda/' + gymId + '/productos')
        .then(lista => {
            mostradorCatalogo = lista;
            const avisos = reconciliarTicket();
            pintarTicket(avisos.length ? avisos : avisosDelServidor);
            if (alCargarMostrador) {
                const accion = alCargarMostrador;
                alCargarMostrador = null;
                accion();
            }
        })
        .catch(err => estadoError(catalogo, err.message, () => loadMostrador(gymId)));
    fetch('/api/gyms/' + gymId + '/members').then(r => r.json()).then(m => { mostradorMiembros = m; }).catch(() => {});
}

function sinAcentos(texto) {
    return (texto || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

// Grupos del Mostrador: una sola tarjeta "Membresías" con todos los planes
// (visita, semana, mensual, trimestral, anual...) y una por producto con
// todas sus presentaciones y sabores.
function gruposMostrador() {
    const grupos = [];
    // Del más corto al más largo (visita, semana, mensual... anual); la
    // inscripción, que no tiene duración, al final.
    const DIAS = { dia: 1, semana: 7, mes: 30 };
    const duracion = p => p.plan && p.plan.duracionUnidad ? DIAS[p.plan.duracionUnidad] * p.plan.duracionCantidad : Infinity;
    const planes = mostradorCatalogo.filter(p => p.tipo === 'membresia').sort((a, b) => duracion(a) - duracion(b));
    if (planes.length) {
        grupos.push({
            clave: 'membresias', titulo: 'Membresías', esMembresia: true,
            opciones: planes.map(p => ({
                variante: p.variantes[0], producto: p,
                seccion: '', texto: p.nombre, detalle: p.plan ? p.plan.duracionTexto : ''
            }))
        });
    }
    mostradorCatalogo.filter(p => p.tipo !== 'membresia').forEach(p => {
        const conSabor = p.variantes.some(v => v.sabor);
        grupos.push({
            clave: p.id, titulo: p.nombre, esMembresia: false,
            // Con sabores, las opciones se ordenan por presentación y cada
            // botón es un sabor; sin sabores, cada botón es una presentación.
            opciones: p.variantes.map(v => ({
                variante: v, producto: p,
                seccion: conSabor ? v.presentacion : '',
                texto: conSabor ? (v.sabor || 'Natural') : (v.presentacion === 'Única' ? p.nombre : v.presentacion),
                detalle: ''
            }))
        });
    });
    return grupos;
}

function piezasEnTicket(varianteIds) {
    if (!mostradorCarrito) return 0;
    return mostradorCarrito.items.filter(i => varianteIds.includes(i.varianteId)).reduce((n, i) => n + i.cantidad, 0);
}

function pintarCatalogoMostrador() {
    const catalogo = document.getElementById('mostrador-catalogo');
    const q = sinAcentos(document.getElementById('mostrador-buscar').value.trim());
    const tarjetas = gruposMostrador()
        .filter(g => !q || sinAcentos(g.titulo + ' ' + g.opciones.map(o => o.texto + ' ' + o.seccion + ' ' + o.producto.nombre).join(' ')).includes(q))
        .map(g => {
            const desde = Math.min(...g.opciones.map(o => o.variante.precio));
            const unica = g.opciones.length === 1 ? g.opciones[0].variante : null;
            const agotado = g.opciones.every(o => o.variante.agotado);
            const enTicket = piezasEnTicket(g.opciones.map(o => o.variante.id));
            let detalle;
            if (g.esMembresia) detalle = g.opciones.length + (g.opciones.length === 1 ? ' plan' : ' planes');
            else if (unica) detalle = unica.presentacion === 'Única' ? '' : unica.presentacion;
            else detalle = g.opciones.length + ' opciones';
            const existencias = unica && unica.disponible != null ? (unica.agotado ? 'Agotado' : unica.disponible + ' disp.') : '';
            return `
            <div class="col-6 col-md-4">
                <button type="button" class="mostrador-tile w-100 h-100 position-relative${g.esMembresia ? ' mostrador-tile-membresias' : ''}" data-mostrador-grupo="${escapeHtml(g.clave)}" ${agotado ? 'disabled' : ''}
                        aria-label="${escapeHtml(g.titulo)}${unica ? ', agregar al ticket' : ', ver opciones'}">
                    ${enTicket ? '<span class="badge rounded-pill bg-dark position-absolute top-0 end-0 m-2">' + enTicket + '</span>' : ''}
                    <span class="d-flex align-items-center gap-1 fw-semibold pe-4">
                        ${g.esMembresia ? '<i class="bx bx-purchase-tag-alt text-primary"></i>' : ''}${escapeHtml(g.titulo)}
                    </span>
                    ${detalle ? '<small class="d-block text-body-secondary">' + escapeHtml(detalle) + '</small>' : ''}
                    <span class="d-flex justify-content-between align-items-end mt-2">
                        <strong>${unica ? '' : '<small class="fw-normal text-body-secondary">desde </small>'}${dinero(desde)}</strong>
                        <small class="${agotado ? 'text-danger' : 'text-body-secondary'}">${unica ? existencias : '<i class="bx bx-chevron-right"></i>'}</small>
                    </span>
                </button>
            </div>`;
        });
    catalogo.innerHTML = tarjetas.length
        ? '<div class="row g-2">' + tarjetas.join('') + '</div>'
        : '<div class="dash-empty text-center text-body-secondary py-5">' + (mostradorCatalogo.length ? 'Nada coincide con la búsqueda.' : 'Aún no tienes productos publicados. Agrégalos en la pestaña Productos.') + '</div>';
    pintarOpcionesMostrador();
}

// Opciones del grupo abierto, agrupadas por presentación. Cada botón
// dice cuántas piezas de esa opción ya van en el ticket.
function pintarOpcionesMostrador() {
    if (!grupoAbierto) return;
    const grupo = gruposMostrador().find(g => g.clave === grupoAbierto);
    const cuerpo = document.getElementById('opciones-cuerpo');
    if (!grupo) { cuerpo.innerHTML = '<p class="text-body-secondary mb-0">Este producto ya no está a la venta.</p>'; return; }
    document.getElementById('opcionesModalLabel').textContent = grupo.titulo;
    const secciones = [];
    grupo.opciones.forEach(o => {
        let seccion = secciones.find(x => x.nombre === o.seccion);
        if (!seccion) secciones.push(seccion = { nombre: o.seccion, opciones: [] });
        seccion.opciones.push(o);
    });
    cuerpo.innerHTML = secciones.map(sec => `
        ${sec.nombre ? '<h6 class="text-uppercase fw-bold text-body-secondary small mt-2 mb-2" style="letter-spacing:0.06em">' + escapeHtml(sec.nombre) + '</h6>' : ''}
        <div class="row g-2 mb-2">
            ${sec.opciones.map(o => {
                const v = o.variante;
                const enTicket = piezasEnTicket([v.id]);
                const existencias = v.disponible == null ? '' : v.agotado ? 'Agotado' : v.disponible + ' disp.';
                return `
                <div class="col-6">
                    <button type="button" class="mostrador-tile w-100 h-100 position-relative" data-mostrador-variante="${escapeHtml(v.id)}" ${v.agotado ? 'disabled' : ''}>
                        ${enTicket ? '<span class="badge rounded-pill bg-dark position-absolute top-0 end-0 m-2" aria-label="' + enTicket + ' en el ticket">' + enTicket + '</span>' : ''}
                        <span class="d-block fw-semibold pe-4">${escapeHtml(o.texto)}</span>
                        ${o.detalle ? '<small class="d-block text-body-secondary">' + escapeHtml(o.detalle) + '</small>' : ''}
                        <span class="d-flex justify-content-between align-items-end mt-2">
                            <strong>${dinero(v.precio)}</strong>
                            <small class="${v.agotado ? 'text-danger' : 'text-body-secondary'}">${existencias}</small>
                        </span>
                    </button>
                </div>`;
            }).join('')}
        </div>`).join('');
}

function pintarTicket(avisos) {
    armarCarrito();
    const c = mostradorCarrito;
    const lista = avisos || [];
    document.getElementById('mostrador-avisos').innerHTML = lista.length
        ? '<div class="alert alert-warning small py-2 mb-0">' + lista.map(escapeHtml).join('<br>') + '</div>' : '';
    const partidas = document.getElementById('mostrador-partidas');
    partidas.innerHTML = !c.items.length
        ? '<p class="text-body-secondary small mb-0">Toca un producto para agregarlo al ticket.</p>'
        : c.items.map(i => `
            <div class="d-flex align-items-center gap-2 py-2 border-bottom">
                <div class="flex-grow-1" style="min-width:0">
                    <div class="fw-semibold text-truncate">${escapeHtml(i.titulo)}</div>
                    <small class="text-body-secondary">${escapeHtml(i.variante || '')} · ${dinero(i.precioUnitario)}</small>
                </div>
                ${i.esPlan ? '' : `
                    <div class="btn-group btn-group-sm" role="group" aria-label="Cantidad de ${escapeHtml(i.titulo)}">
                        <button type="button" class="btn btn-outline-secondary" data-m-menos="${escapeHtml(i.id)}" aria-label="Una pieza menos" ${i.cantidad <= 1 ? 'disabled' : ''}><i class="bx bx-minus"></i></button>
                        <span class="btn btn-outline-secondary disabled fw-semibold">${i.cantidad}</span>
                        <button type="button" class="btn btn-outline-secondary" data-m-mas="${escapeHtml(i.id)}" aria-label="Una pieza más" ${i.cantidad >= i.maximo ? 'disabled' : ''}><i class="bx bx-plus"></i></button>
                    </div>`}
                <strong class="text-nowrap">${dinero(i.total)}</strong>
                <button type="button" class="btn btn-sm btn-link text-danger px-1" data-m-quitar="${escapeHtml(i.id)}" aria-label="Quitar ${escapeHtml(i.titulo)}"><i class="bx bx-trash fs-5"></i></button>
            </div>`).join('') + `
            <div class="pt-2 small text-body-secondary d-flex justify-content-between"><span>Subtotal (sin IVA)</span><span>${dinero(c.subtotal)}</span></div>
            <div class="small text-body-secondary d-flex justify-content-between"><span>IVA 16 %</span><span>${dinero(c.iva)}</span></div>
            <div class="d-flex justify-content-between align-items-baseline mt-1"><span class="fw-bold">Total</span><span class="fs-4 fw-bold">${dinero(c.total)}</span></div>`;
    pintarBilletes();
    actualizarCobro();
    pintarCatalogoMostrador();
}

// Atajos de efectivo: el total exacto y los billetes que lo cubren.
function pintarBilletes() {
    const total = mostradorCarrito ? mostradorCarrito.total : 0;
    const opciones = total > 0 ? [total, ...[50, 100, 200, 500, 1000].map(b => Math.ceil(total / b) * b)] : [];
    const unicos = [...new Set(opciones.map(v => Math.round(v * 100) / 100))].slice(0, 5);
    document.getElementById('mostrador-billetes').innerHTML = unicos.map((v, i) =>
        '<button type="button" class="btn btn-sm btn-outline-secondary rounded-pill" data-billete="' + v + '">' + (i === 0 ? 'Exacto' : dinero(v)) + '</button>').join('');
}

function actualizarCobro() {
    const total = mostradorCarrito ? mostradorCarrito.total : 0;
    const hayItems = !!(mostradorCarrito && mostradorCarrito.items.length);
    const boton = document.getElementById('mostrador-cobrar');
    const cambio = document.getElementById('mostrador-cambio');
    const recibido = parseFloat(document.getElementById('mostrador-recibido').value);
    if (metodoMostrador() === 'efectivo') {
        const falta = !(recibido + 0.009 >= total);
        cambio.textContent = isNaN(recibido) ? '$0.00' : falta ? 'Faltan ' + dinero(total - recibido) : dinero(recibido - total);
        cambio.classList.toggle('text-danger', !isNaN(recibido) && falta);
    }
    if (!mostradorOcupado) {
        boton.disabled = !hayItems;
        boton.textContent = hayItems ? 'Cobrar ' + dinero(total) : 'Cobrar';
    }
}
