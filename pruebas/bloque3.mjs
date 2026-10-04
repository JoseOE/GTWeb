// Prueba del bloque 3 (simulador Stripe y mostrador) contra Spring y Medusa locales.
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
const tarjeta = (numero, extra = {}) => ({ numero, titular: 'Ana Prueba', mes: '12', anio: '30', cvc: '123', ...extra });
const token = async (numero, userId = member, extra) => (await api('POST', '/api/simuladores/stripe/tokens', tarjeta(numero, extra), userId)).body;

console.log('\n1. Tokens');
let r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('4242 4242 4242 4242'), member);
ok(r.status === 200 && r.body.token.startsWith('tok_sim_') && r.body.marca === 'visa' && r.body.ultimos4 === '4242', '4242 → token de Visa', r.body);
ok(!JSON.stringify(r.body).includes('4242424242424242') && !('cvc' in r.body), 'la respuesta no trae el número completo ni el CVC');
r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('4111 1111 1111 1111'), member);
ok(r.status === 400 && r.body.error.includes('tarjeta de prueba'), 'número real (Luhn válido) → "Usa una tarjeta de prueba"', r.body.error);
r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('4242 4242 4242 4241'), member);
ok(r.status === 400 && r.body.error.includes('no es válido'), 'Luhn inválido', r.body.error);
r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('4242424242424242', { mes: '01', anio: '24' }), member);
ok(r.status === 400 && r.body.error.includes('ya pasó'), 'vencimiento en el pasado', r.body.error);
r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('378282246310005', { cvc: '123' }), member);
ok(r.status === 400 && r.body.error.includes('4 dígitos'), 'Amex exige CVC de 4 dígitos', r.body.error);
r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('378282246310005', { cvc: '1234' }), member);
ok(r.status === 200 && r.body.marca === 'amex', 'Amex con CVC de 4', r.body.ultimos4);
r = await api('POST', '/api/simuladores/stripe/tokens', tarjeta('4242424242424242'));
ok(r.status === 401, 'sin sesión → 401');

console.log('\n2. Cobro con tarjeta en la tienda');
let productos = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body;
if (!productos.some(p => p.nombre.startsWith('Agua'))) {
  await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'Agua natural 1 L', categoria: 'bebidas', activo: true, variantes: [{ presentacion: 'Botella 1 L', precio: 18, existencias: 50 }] }, owner);
  productos = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body;
}
const agua = productos.find(p => p.nombre.startsWith('Agua'));
async function carritoCon(variante, cantidad = 1) {
  await api('DELETE', '/api/tienda/carrito', null, member);
  return (await api('POST', '/api/tienda/carrito/items', { varianteId: variante, cantidad }, member)).body;
}
let c = await carritoCon(agua.variantes[0].id);
for (const [num, texto] of [['4000000000000002', 'rechazada'], ['4000000000009995', 'fondos'], ['4000000000000069', 'vencida'], ['4000000000000127', 'CVC']]) {
  const t = await token(num);
  r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: t.token }, totalVisto: c.total }, member);
  ok(r.status === 402 && r.body.error.includes(texto), `${num.slice(-4)} → 402 "${texto}"`, r.body.error);
}
r = await api('GET', '/api/tienda/carrito', null, member);
ok(r.body.articulos === 1, 'tras los rechazos el carrito sigue intacto', r.body.articulos);
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: 'tok_sim_inventado', resultado: 'aprobada', marca: 'visa' }, totalVisto: c.total }, member);
ok(r.status === 400, 'token inventado → 400 (no se puede forzar "aprobada")', r.body.error);
const ajeno = await token('4242424242424242', owner);
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: ajeno.token }, totalVisto: c.total }, member);
ok(r.status === 400, 'token de otra persona → 400', r.body.error);
const bueno = await token('4242424242424242');
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: bueno.token }, totalVisto: c.total }, member);
ok(r.status === 200 && r.body.estado === 'pagado' && r.body.detallePago === 'Visa •••• 4242', '4242 aprueba', { folio: r.body.folio, detalle: r.body.detallePago });
c = await carritoCon(agua.variantes[0].id);
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: bueno.token }, totalVisto: c.total }, member);
ok(r.status === 400, 'el mismo token no se puede usar dos veces', r.body.error);
await api('DELETE', '/api/tienda/carrito', null, member);

console.log('\n3. Mostrador (ticket en el navegador, se cobra completo)');
const cat = (await api('GET', `/api/tienda/${gymId}/productos`, null, owner)).body;
const varDe = id => cat.flatMap(p => p.variantes).find(v => v.id === id);
const aguaV = varDe(agua.variantes[0].id);
const mensualV = varDe(planes.Mensual.varianteId);
const otroPlan = Object.entries(planes).find(([nombre, p]) => nombre !== 'Mensual' && p.varianteId && varDe(p.varianteId)
  && cat.find(x => x.variantes.some(v => v.id === p.varianteId)).plan.duracionUnidad);
const otroPlanV = otroPlan ? varDe(otroPlan[1].varianteId) : null;
const total = partidas => Math.round(partidas.reduce((s, p) => s + varDe(p.varianteId).precio * p.cantidad, 0) * 100) / 100;
const cobrar = (cuerpo, quien = owner) => api('POST', `/api/gyms/${gymId}/mostrador/cobrar`, cuerpo, quien);

r = await api('GET', `/api/gyms/${gymId}/mostrador/carrito`, null, owner);
ok(r.status === 404 || r.status === 405, 'el carrito del mostrador en el servidor ya no existe', r.status);
const ticket = [{ varianteId: aguaV.id, cantidad: 1 }, { varianteId: mensualV.id, cantidad: 1 }, { varianteId: aguaV.id, cantidad: 2 }];
const totalMostrador = total([{ varianteId: aguaV.id, cantidad: 3 }, { varianteId: mensualV.id, cantidad: 1 }]);
r = await cobrar({ partidas: ticket, clienteId: member, metodo: 'efectivo', recibido: 1000, totalVisto: totalMostrador }, member);
ok(r.status === 403, 'un miembro no usa el mostrador', r.body.error);
r = await cobrar({ partidas: [], metodo: 'efectivo', recibido: 100, totalVisto: 0 });
ok(r.status === 400 && r.body.error.includes('vacío'), 'ticket vacío → 400', r.body.error);
r = await cobrar({ partidas: ticket, metodo: 'transferencia', totalVisto: totalMostrador });
ok(r.status === 400, 'método desconocido → 400', r.body.error);
r = await cobrar({ partidas: ticket, clienteId: member, metodo: 'efectivo', recibido: totalMostrador - 10, totalVisto: totalMostrador });
ok(r.status === 400 && r.body.error.includes('no alcanza'), 'efectivo insuficiente → 400', r.body.error);
r = await cobrar({ partidas: ticket, clienteId: member, metodo: 'efectivo', recibido: 1000, totalVisto: totalMostrador + 5 });
ok(r.status === 409 && r.body.error.includes('total cambió'), 'total distinto al del catálogo → 409', r.body.error);
r = await cobrar({ partidas: [{ varianteId: mensualV.id, cantidad: 2 }], metodo: 'efectivo', recibido: 99999, totalVisto: mensualV.precio * 2 });
ok(r.status === 400 && r.body.error.includes('uno en uno'), 'plan con cantidad 2 → 400', r.body.error);
if (otroPlanV) {
  r = await cobrar({ partidas: [{ varianteId: mensualV.id, cantidad: 1 }, { varianteId: otroPlanV.id, cantidad: 1 }], metodo: 'efectivo', recibido: 99999, totalVisto: mensualV.precio + otroPlanV.precio });
  ok(r.status === 400 && r.body.error.includes('un plan por venta'), 'dos planes en la misma venta → 400', r.body.error);
} else {
  ok(false, 'no encontré un segundo plan con duración para probar "un plan por venta"');
}
r = await cobrar({ partidas: [{ varianteId: 'variant_no_existe', cantidad: 1 }], metodo: 'efectivo', recibido: 100, totalVisto: 10 });
ok(r.status === 409 && (r.body.avisos || []).some(a => a.includes('ya no está a la venta')), 'presentación que ya no se vende → 409 con aviso', r.body.avisos);
if (aguaV.disponible != null && aguaV.disponible < 20) {
  r = await cobrar({ partidas: [{ varianteId: aguaV.id, cantidad: aguaV.disponible + 1 }], metodo: 'efectivo', recibido: 99999, totalVisto: aguaV.precio * (aguaV.disponible + 1) });
  ok(r.status === 409 && (r.body.avisos || []).some(a => a.includes('Solo quedan')), 'más piezas de las disponibles → 409 con aviso', r.body.avisos);
}
r = await cobrar({ partidas: [{ varianteId: aguaV.id, cantidad: 21 }], metodo: 'efectivo', recibido: 99999, totalVisto: aguaV.precio * 21 });
ok(r.status === 400 || r.status === 409, 'más de 20 piezas → rechazado', r.body.error || r.body.avisos);

const stockAntes = (await api('GET', `/api/gyms/${gymId}/productos/${agua.id}`, null, owner)).body.variantes[0];
const venceAntes = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
const t0 = performance.now();
r = await cobrar({ partidas: ticket, clienteId: member, metodo: 'efectivo', recibido: 1000, totalVisto: totalMostrador });
const msCobro = Math.round(performance.now() - t0);
ok(r.status === 200 && r.body.pedido.estado === 'pagado' && r.body.pedido.canal === 'mostrador', 'cobro en efectivo a un miembro (' + msCobro + ' ms)',
  r.status === 200 ? { folio: r.body.pedido.folio, cambio: r.body.cambio, detalle: r.body.pedido.detallePago } : r.body);
ok(r.status === 200 && Math.abs(r.body.cambio - (1000 - totalMostrador)) < 0.01 && Math.abs(r.body.pedido.total - totalMostrador) < 0.01, 'total y cambio correctos',
  r.status === 200 ? { total: r.body.pedido.total, cambio: r.body.cambio } : null);
ok(r.status === 200 && r.body.pedido.partidas.length === 2, 'las partidas repetidas se juntan en una',
  r.status === 200 ? r.body.pedido.partidas.map(p => (p.titulo || p.nombre) + ' x' + p.cantidad) : null);
const venceDespues = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
ok(venceDespues !== venceAntes && r.body.cliente && r.body.cliente.fechaProximoPago === venceDespues, 'el plan del mostrador extendió la membresía', { antes: venceAntes, despues: venceDespues });
const stockDespues = (await api('GET', `/api/gyms/${gymId}/productos/${agua.id}`, null, owner)).body.variantes[0];
ok(stockDespues.existencias === stockAntes.existencias - 3 && stockDespues.disponible === stockAntes.disponible - 3, 'se entregó en el acto: bajan existencias y disponible',
  { antes: [stockAntes.existencias, stockAntes.disponible], despues: [stockDespues.existencias, stockDespues.disponible] });

const tTerminal = await token('5555555555554444', owner);
r = await cobrar({ partidas: [{ varianteId: aguaV.id, cantidad: 1 }], clienteId: null, metodo: 'tarjeta', datos: { token: tTerminal.token }, totalVisto: aguaV.precio });
ok(r.status === 200 && r.body.pedido.cliente === 'Público en general' && r.body.pedido.detallePago === 'Mastercard •••• 4444', 'tarjeta en terminal al público en general',
  r.status === 200 ? { cliente: r.body.pedido.cliente, detalle: r.body.pedido.detallePago } : r.body);
if (r.status === 200) {
  r = await api('GET', `/api/tienda/pedidos/${r.body.pedido.orderId}`, null, member);
  ok(r.status === 404, 'el miembro no ve una venta al público');
}
const rechazo = await token('4000000000000002', owner);
r = await cobrar({ partidas: [{ varianteId: aguaV.id, cantidad: 1 }], metodo: 'tarjeta', datos: { token: rechazo.token }, totalVisto: aguaV.precio });
ok(r.status === 402 && r.body.error.includes('rechazada'), 'tarjeta rechazada en terminal → 402', r.body.error);
r = await cobrar({ partidas: [{ varianteId: aguaV.id, cantidad: 1 }], clienteId: 'no-existe', metodo: 'efectivo', recibido: 100, totalVisto: aguaV.precio });
ok(r.status === 400, 'cliente que no es miembro → 400', r.body.error);

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
