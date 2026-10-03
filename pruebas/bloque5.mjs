// Prueba del bloque 5 (simulador de PayPal y dashboard de ventas) contra Spring y Medusa locales.
import fs from 'node:fs';
import { API, ESTADO } from './entorno.mjs';

const n = JSON.parse(fs.readFileSync(ESTADO, 'utf8'));
const TOKENS = n.tokens || {};
const cabeceras = userId => userId ? { 'X-User-Id': userId, ...(TOKENS[userId] ? { Authorization: 'Bearer ' + TOKENS[userId] } : {}) } : {};
let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
async function api(metodo, ruta, cuerpo, userId) {
  const r = await fetch(API + ruta, { method: metodo, headers: { 'Content-Type': 'application/json', ...cabeceras(userId) }, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
const { gymId, owner, member, planes } = n;
const RETORNO = API + '/checkout.html?metodo=paypal&continuar=1';
const CANCELA = API + '/checkout.html?metodo=paypal&paypal=cancelado';
const productos = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body;
const agua = productos.find(p => p.nombre.startsWith('Agua'));
const aguaV = agua.variantes[0];
async function carritoCon(varianteId, cantidad = 1, canal = 'web') {
  await api('DELETE', `/api/tienda/carrito?canal=${canal}`, null, member);
  return (await api('POST', `/api/tienda/carrito/items?canal=${canal}`, { varianteId, cantidad }, member)).body;
}
const crearOrden = (cuerpo = { returnUrl: RETORNO, cancelUrl: CANCELA }, canal = 'web', quien = member) =>
  api('POST', `/api/simuladores/paypal/ordenes?canal=${canal}`, cuerpo, quien);
const aprobar = (id, cuenta = 'ana.compradora@sim-paypal.test') => api('POST', `/api/simuladores/paypal/ordenes/${id}/aprobar`, { cuenta });
const payerDe = redirect => new URL(redirect.replace(/^gymtrack:\/\//, 'http://app/')).searchParams.get('PayerID');
const pagar = (datos, total, canal = 'web', quien = member) =>
  api('POST', `/api/tienda/carrito/checkout?canal=${canal}`, { metodo: 'paypal', datos, totalVisto: total }, quien);

console.log('\n1. Orden de PayPal');
let r = await crearOrden({ returnUrl: RETORNO, cancelUrl: CANCELA }, 'web', null);
ok(r.status === 401, 'sin sesión → 401', r.status);
await api('DELETE', '/api/tienda/carrito', null, member);
r = await crearOrden();
ok(r.status === 400 && r.body.error.includes('vacío'), 'carrito vacío → 400', r.body.error);
let c = await carritoCon(aguaV.id, 2);
r = await crearOrden({ returnUrl: 'https://otro-sitio.example/robar', cancelUrl: CANCELA });
ok(r.status === 400 && r.body.error.includes('returnUrl'), 'returnUrl de otro sitio → 400', r.body.error);
r = await crearOrden({ returnUrl: 'javascript:alert(1)', cancelUrl: CANCELA });
ok(r.status === 400, 'returnUrl javascript: → 400', r.body.error);
r = await crearOrden({ returnUrl: RETORNO });
ok(r.status === 400 && r.body.error.includes('cancelUrl'), 'sin cancelUrl → 400', r.body.error);
r = await crearOrden({ returnUrl: '/checkout.html?metodo=paypal&continuar=1', cancelUrl: '/checkout.html' });
ok(r.status === 200, 'rutas relativas de la misma web → 200');
r = await crearOrden();
ok(r.status === 200 && /^PAYID-SIM-[A-Z0-9]{16}$/.test(r.body.id) && Math.abs(r.body.monto - c.total) < 0.01 && r.body.aprobarUrl.endsWith('/paypal-sim.html?token=' + r.body.id),
  'orden creada por el total del carrito', r.body);
const orden1 = r.body.id;
r = await api('GET', `/api/simuladores/paypal/ordenes/${orden1}`);
ok(r.status === 200 && r.body.estado === 'creada' && r.body.cuentas.length === 3 && r.body.comercio && r.body.regreso === null && !('returnUrl' in r.body),
  'el simulador la ve sin sesión (comercio, monto, cuentas de prueba)', { comercio: r.body.comercio, monto: r.body.monto, articulos: r.body.articulos });
r = await api('GET', '/api/simuladores/paypal/ordenes/PAYID-SIM-NOEXISTE00000000');
ok(r.status === 404, 'orden inexistente → 404', r.body.error);
r = await pagar({ token: orden1, payerId: 'X' }, c.total);
ok(r.status === 400 && r.body.error.includes('Todavía no apruebas'), 'pagar sin aprobar → 400', r.body.error);
r = await aprobar(orden1, 'no-es-correo');
ok(r.status === 400, 'aprobar con un correo inválido → 400', r.body.error);
r = await aprobar(orden1);
const redirect1 = r.body.redirect || '';
ok(r.status === 200 && redirect1.startsWith(RETORNO + '&token=' + orden1 + '&PayerID='), 'aprobar → regresa a returnUrl con token y PayerID', redirect1);
const payer1 = payerDe(redirect1);
r = await aprobar(orden1);
ok(r.status === 200 && r.body.redirect === redirect1, 'aprobar otra vez con la misma cuenta → mismo regreso');
r = await api('POST', `/api/simuladores/paypal/ordenes/${orden1}/cancelar`);
ok(r.status === 409, 'ya aprobada no se puede cancelar → 409', r.body.error);
r = await api('GET', `/api/simuladores/paypal/ordenes/${orden1}`);
ok(r.body.estado === 'aprobada' && r.body.regreso === redirect1, 'el simulador ofrece volver si cerraron la pestaña');
r = await pagar({ token: orden1, payerId: 'OTROPAYER' }, c.total);
ok(r.status === 400, 'PayerID distinto → 400', r.body.error);
r = await pagar({ token: orden1, payerId: payer1 }, c.total, 'web', owner);
ok(r.status === 403, 'el dueño no puede pagar con la orden del miembro', r.status);

console.log('\n2. Pago con PayPal');
const conTres = (await api('POST', '/api/tienda/carrito/items', { varianteId: aguaV.id, cantidad: 1 }, member)).body;
r = await pagar({ token: orden1, payerId: payer1 }, conTres.total);
ok(r.status === 409 && r.body.error.includes('cambió'), 'el carrito cambió tras aprobar → 409', r.body.error);
const partida = conTres.items.find(i => i.varianteId === aguaV.id);
c = (await api('PATCH', `/api/tienda/carrito/items/${partida.id}`, { cantidad: 2 }, member)).body;
r = await pagar({ token: orden1, payerId: payer1 }, c.total);
ok(r.status === 200 && r.body.estado === 'pagado' && r.body.detallePago === 'PayPal · ana.compradora@sim-paypal.test' && r.body.canal === 'web',
  'pago aprobado → pedido pagado', { folio: r.body.folio, detalle: r.body.detallePago, error: r.body.error });
const pedidoPaypal = r.body;
r = await api('GET', `/api/tienda/carrito`, null, member);
ok(r.body.items.length === 0, 'el carrito quedó vacío');
c = await carritoCon(aguaV.id, 1);
r = await pagar({ token: orden1, payerId: payer1 }, c.total);
ok(r.status === 400 && r.body.error.includes('ya se usó'), 'la misma orden no se usa dos veces → 400', r.body.error);
r = await api('GET', `/api/simuladores/paypal/ordenes/${orden1}`);
ok(r.body.estado === 'usada', 'la orden queda usada');

r = await crearOrden();
const orden2 = r.body.id;
r = await api('POST', `/api/simuladores/paypal/ordenes/${orden2}/cancelar`);
ok(r.status === 200 && r.body.redirect === CANCELA + '&token=' + orden2, 'cancelar → regresa a cancelUrl', r.body.redirect);
r = await aprobar(orden2);
ok(r.status === 409, 'cancelada ya no se aprueba → 409', r.body.error);
r = await pagar({ token: orden2, payerId: 'x' }, c.total);
ok(r.status === 400 && r.body.error.includes('Cancelaste'), 'pagar con una orden cancelada → 400', r.body.error);
r = await api('GET', '/api/tienda/carrito', null, member);
ok(r.body.items.length === 1, 'cancelar no crea pedido y el carrito sigue igual');

r = await crearOrden();
const orden3 = r.body.id;
r = await aprobar(orden3, 'sin-saldo@sim-paypal.test');
r = await pagar({ token: orden3, payerId: payerDe(r.body.redirect) }, c.total);
ok(r.status === 402 && r.body.error.includes('rechazó'), 'cuenta sin saldo → 402 con mensaje', r.body.error);
r = await api('GET', '/api/tienda/carrito', null, member);
ok(r.body.items.length === 1, 'tras el rechazo el carrito sigue intacto');
await api('DELETE', '/api/tienda/carrito', null, member);

// La app: vuelve por un enlace propio y paga en su canal. El carrito web tiene
// el mismo total, para que lo único distinto sea el canal.
await carritoCon(aguaV.id, 1, 'web');
const cApp = await carritoCon(aguaV.id, 1, 'app');
r = await crearOrden({ returnUrl: 'gymtrack://pago-paypal', cancelUrl: 'gymtrack://pago-paypal?cancelado=1' }, 'app');
ok(r.status === 200, 'la app crea la orden con su enlace de regreso', r.body.error);
const ordenApp = r.body.id;
r = await aprobar(ordenApp, 'cualquiera@correo.test');
ok(r.status === 200 && r.body.redirect.startsWith('gymtrack://pago-paypal?token=' + ordenApp + '&PayerID='), 'regresa al enlace de la app', r.body.redirect);
const payerApp = payerDe(r.body.redirect);
r = await pagar({ token: ordenApp, payerId: payerApp }, cApp.total, 'web');
ok(r.status === 400 && r.body.error.includes('ya no es válido'), 'la orden de la app no sirve para el carrito web', r.body && r.body.error);
await api('DELETE', '/api/tienda/carrito', null, member);
r = await pagar({ token: ordenApp, payerId: payerApp }, cApp.total, 'app');
ok(r.status === 200 && r.body.canal === 'app' && r.body.detallePago === 'PayPal · cualquiera@correo.test', 'pago con PayPal desde la app', { folio: r.body.folio, canal: r.body.canal, error: r.body.error });
const pedidoApp = r.body;

console.log('\n3. Ventas: acceso y resumen');
r = await api('GET', `/api/gyms/${gymId}/ventas/resumen`, null, member);
ok(r.status === 403, 'un miembro no ve las ventas → 403', r.status);
r = await api('GET', `/api/gyms/${gymId}/ventas/resumen`);
ok(r.status === 401, 'sin sesión → 401', r.status);
r = await api('GET', `/api/gyms/${gymId}/ventas/resumen?desde=2026-10-05&hasta=2026-10-01`, null, owner);
ok(r.status === 400, 'desde después de hasta → 400', r.body.error);
r = await api('GET', `/api/gyms/${gymId}/ventas/resumen?desde=ayer`, null, owner);
ok(r.status === 400 && r.body.error.includes('AAAA-MM-DD'), 'fecha mal escrita → 400 en español', r.body.error);
r = await api('GET', `/api/gyms/${gymId}/ventas/resumen?desde=2024-01-01&hasta=2026-10-02`, null, owner);
ok(r.status === 400, 'más de un año → 400', r.body.error);
const res = (await api('GET', `/api/gyms/${gymId}/ventas/resumen`, null, owner)).body;
const hoyMx = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
ok(res.porDia.length === 30 && res.porDia[29].fecha === hoyMx && res.hasta === hoyMx, 'por defecto, 30 días que terminan hoy', { desde: res.desde, hasta: res.hasta });
const sumaDias = Math.round(res.porDia.reduce((s, d) => s + d.total, 0) * 100) / 100;
ok(Math.abs(sumaDias - res.periodo.total) < 0.01, 'la suma por día es el total del periodo', { sumaDias, periodo: res.periodo.total });
const sumaMetodos = Math.round(res.porMetodo.reduce((s, d) => s + d.total, 0) * 100) / 100;
ok(Math.abs(sumaMetodos - res.periodo.total) < 0.01 && res.porMetodo.some(m => m.nombre === 'PayPal'), 'por método suma lo mismo e incluye PayPal', res.porMetodo.map(m => m.nombre + ' ' + m.total));
ok(res.topProductos.length <= 5 && res.topProductos.every((p, i, a) => i === 0 || a[i - 1].total >= p.total), 'top productos ordenados (máx. 5)', res.topProductos.map(p => p.titulo + ' ' + p.total));

// KPIs contra la lista: lo pagado hoy y en el mes, con la fecha de pago en México.
let todos = [];
for (let pag = 0; ; pag++) {
  const p = (await api('GET', `/api/gyms/${gymId}/ventas/pedidos?estado=pagado&tamano=100&pagina=${pag}`, null, owner)).body;
  todos = todos.concat(p.pedidos);
  if (pag + 1 >= p.paginas) break;
}
const fechaMx = f => new Date(f).toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
const deHoy = todos.filter(p => fechaMx(p.pagadoEn || p.creadoEn) === hoyMx);
const delMes = todos.filter(p => fechaMx(p.pagadoEn || p.creadoEn).slice(0, 7) === hoyMx.slice(0, 7));
const suma = l => Math.round(l.reduce((s, p) => s + p.total, 0) * 100) / 100;
ok(res.kpis.hoy.pedidos === deHoy.length && Math.abs(res.kpis.hoy.total - suma(deHoy)) < 0.01, 'KPI de hoy cuadra con la lista', { kpi: res.kpis.hoy, lista: { pedidos: deHoy.length, total: suma(deHoy) } });
ok(res.kpis.mes.pedidos === delMes.length && Math.abs(res.kpis.mes.total - suma(delMes)) < 0.01, 'KPI del mes cuadra con la lista', { kpi: res.kpis.mes, lista: { pedidos: delMes.length, total: suma(delMes) } });
ok(Math.abs(res.kpis.ticketPromedio - Math.round(suma(delMes) / delMes.length * 100) / 100) < 0.01, 'ticket promedio = total del mes / pedidos', res.kpis.ticketPromedio);
ok(typeof res.kpis.paynetPendientes.pedidos === 'number' && typeof res.kpis.membresiasDelMes === 'number', 'Paynet pendientes y membresías del mes', { paynet: res.kpis.paynetPendientes, membresias: res.kpis.membresiasDelMes });

console.log('\n4. Ventas: lista y filtros');
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos?metodo=paypal`, null, owner);
ok(r.status === 200 && r.body.pedidos.length > 0 && r.body.pedidos.every(p => p.metodo === 'PayPal'), 'filtro por método', r.body.total);
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos?canal=app`, null, owner);
ok(r.body.pedidos.length > 0 && r.body.pedidos.every(p => p.canal === 'app'), 'filtro por canal', r.body.total);
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos?canal=mostrador`, null, owner);
ok(r.body.pedidos.length > 0 && r.body.pedidos.some(p => p.cliente === 'Público en general'), 'mostrador con su cliente', r.body.pedidos.slice(0, 2).map(p => p.cliente));
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos?buscar=%23${pedidoPaypal.folio}`, null, owner);
ok(r.body.total === 1 && r.body.pedidos[0].orderId === pedidoPaypal.orderId, 'buscar por folio (#)', r.body.total);
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos?desde=${hoyMx}&hasta=${hoyMx}`, null, owner);
ok(r.body.pedidos.every(p => fechaMx(p.creadoEn) === hoyMx), 'filtro por fechas', r.body.total);
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos?metodo=cheque`, null, owner);
ok(r.status === 400, 'método desconocido → 400', r.body.error);
const p0 = (await api('GET', `/api/gyms/${gymId}/ventas/pedidos?tamano=2&pagina=0`, null, owner)).body;
const p1 = (await api('GET', `/api/gyms/${gymId}/ventas/pedidos?tamano=2&pagina=1`, null, owner)).body;
ok(p0.pedidos.length === 2 && p1.pedidos.length > 0 && p0.pedidos[0].orderId !== p1.pedidos[0].orderId && p0.paginas === Math.ceil(p0.total / 2), 'paginación', { total: p0.total, paginas: p0.paginas });
ok(new Date(p0.pedidos[0].creadoEn) >= new Date(p0.pedidos[1].creadoEn), 'del más nuevo al más viejo');

console.log('\n5. Ventas: detalle y acciones');
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos/${pedidoPaypal.orderId}`, null, owner);
ok(r.status === 200 && r.body.acciones.reembolsar && !r.body.acciones.cancelar && r.body.acciones.recibo && r.body.acciones.reenviar, 'detalle de un pagado: recibo, reenviar y reembolsar', r.body.acciones);
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos/order_no_existe`, null, owner);
ok(r.status === 404, 'pedido de otro lado → 404');
r = await api('POST', `/api/recibos/pedidos/${pedidoPaypal.orderId}/enviar`, null, owner);
ok(r.status === 200, 'reenviar el correo del pedido', r.body);
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${pedidoPaypal.orderId}/cancelar`, null, owner);
ok(r.status === 409 && r.body.error.includes('reembólsalo'), 'un pagado no se cancela → 409', r.body.error);
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${pedidoPaypal.orderId}/reembolsar`, null, member);
ok(r.status === 403, 'un miembro no reembolsa → 403');
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${pedidoPaypal.orderId}/reembolsar`, null, owner);
ok(r.status === 200 && r.body.estado === 'reembolsado' && !r.body.acciones.reembolsar && r.body.acciones.recibo && !r.body.membresia, 'reembolso simulado → reembolsado', { estado: r.body.estado, error: r.body.error });
ok(r.body.total === pedidoPaypal.total, 'el reembolsado conserva el total que se vendió', { antes: pedidoPaypal.total, despues: r.body.total });
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${pedidoPaypal.orderId}/reembolsar`, null, owner);
ok(r.status === 409, 'no se reembolsa dos veces → 409', r.body.error);
const reciboReemb = await fetch(`${API}/api/recibos/pedidos/${pedidoPaypal.orderId}.pdf`, { headers: cabeceras(owner) });
ok(reciboReemb.status === 200, 'el reembolsado conserva su recibo');
const resDespues = (await api('GET', `/api/gyms/${gymId}/ventas/resumen`, null, owner)).body;
ok(Math.abs((res.kpis.hoy.total - resDespues.kpis.hoy.total) - pedidoPaypal.total) < 0.01, 'el reembolso sale de las ventas de hoy', { antes: res.kpis.hoy.total, despues: resDespues.kpis.hoy.total });

// Reembolso de un plan: la membresía vuelve a como estaba.
const venceAntes = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
const cPlan = await carritoCon(planes.Mensual.varianteId, 1);
r = await crearOrden();
const ordenPlan = r.body.id;
r = await aprobar(ordenPlan);
r = await pagar({ token: ordenPlan, payerId: payerDe(r.body.redirect) }, cPlan.total);
ok(r.status === 200, 'compra del plan Mensual con PayPal', r.body.error);
const pedidoPlan = r.body;
const venceConPlan = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
ok(venceConPlan !== venceAntes, 'el plan extendió la membresía', { antes: venceAntes, despues: venceConPlan });
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${pedidoPlan.orderId}/reembolsar`, null, owner);
const venceTras = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
ok(r.status === 200 && r.body.membresia && r.body.membresia.ajustada === true && venceTras === venceAntes && r.body.membresia.vence === venceAntes,
  'reembolsar el plan regresa la membresía a su fecha anterior', { membresia: r.body.membresia, ahora: venceTras });
const pagos = (await api('GET', `/api/gyms/${gymId}/members/${member}/payments`, null, owner)).body;
const pagoPlan = Array.isArray(pagos) ? pagos.find(p => p.orderId === pedidoPlan.orderId) : null;
ok(pagoPlan && pagoPlan.reembolsadoEn, 'el pago de la membresía queda marcado como reembolsado', pagoPlan && { plan: pagoPlan.plan, reembolsadoEn: pagoPlan.reembolsadoEn });

// Dos planes seguidos: reembolsar el primero no mueve la fecha (hay uno después);
// reembolsar el segundo regresa hasta antes del primero.
const compraPlan = async () => {
  const cp = await carritoCon(planes.Mensual.varianteId, 1);
  const o = (await crearOrden()).body.id;
  const a = (await aprobar(o)).body;
  return (await pagar({ token: o, payerId: payerDe(a.redirect) }, cp.total)).body;
};
const antesDeDos = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
const planA = await compraPlan();
const planB = await compraPlan();
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${planA.orderId}/reembolsar`, null, owner);
ok(r.status === 200 && r.body.membresia && r.body.membresia.ajustada === false, 'con un pago posterior, la fecha no se toca', r.body.membresia);
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${planB.orderId}/reembolsar`, null, owner);
const trasDos = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
ok(r.body.membresia && r.body.membresia.ajustada === true && trasDos === antesDeDos, 'reembolsar el segundo regresa hasta antes del primero', { antes: antesDeDos, ahora: trasDos });

// Paynet pendiente: simular el pago desde Ventas y cancelar otra ficha.
c = await carritoCon(aguaV.id, 1);
const ficha = (await api('POST', '/api/tienda/carrito/checkout', { metodo: 'paynet', datos: {}, totalVisto: c.total }, member)).body;
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos/${ficha.orderId}`, null, owner);
ok(r.body.estado === 'pendiente_pago' && r.body.acciones.simularPaynet && r.body.acciones.cancelar && r.body.acciones.ficha && !r.body.acciones.reembolsar, 'ficha Paynet: simular pago, ficha y cancelar', r.body.acciones);
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${ficha.orderId}/reembolsar`, null, owner);
ok(r.status === 409, 'una ficha pendiente no se reembolsa → 409', r.body.error);
r = await api('POST', `/api/simuladores/paynet/${ficha.orderId}/pagar`, null, owner);
ok(r.status === 200, 'simular pago en tienda desde Ventas', r.body && r.body.estado);
r = await api('GET', `/api/gyms/${gymId}/ventas/pedidos/${ficha.orderId}`, null, owner);
ok(r.body.estado === 'pagado' && r.body.acciones.reembolsar, 'la ficha quedó pagada');
c = await carritoCon(aguaV.id, 1);
const ficha2 = (await api('POST', '/api/tienda/carrito/checkout', { metodo: 'paynet', datos: {}, totalVisto: c.total }, member)).body;
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${ficha2.orderId}/cancelar`, null, owner);
ok(r.status === 200 && r.body.estado === 'cancelado' && !r.body.acciones.cancelar, 'cancelar una ficha pendiente', r.body.estado || r.body.error);
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${ficha2.orderId}/cancelar`, null, owner);
ok(r.status === 409, 'no se cancela dos veces → 409', r.body.error);

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
