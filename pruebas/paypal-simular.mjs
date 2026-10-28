// Simular el resultado de PayPal (aprobado, sin fondos, cuenta inválida) en la
// tienda y el mostrador. Corre en el CI (PAGOS_MODO=simulado), donde el rechazo
// lo produce Spring con el mismo mensaje que daría el sandbox de PayPal.
import fs from 'node:fs';
import { API, ESTADO, CONSENTIMIENTO } from './entorno.mjs';

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
const { gymId, owner, member } = n;
const RETORNO = API + '/checkout.html?metodo=paypal&continuar=1';
const CANCELA = API + '/checkout.html?metodo=paypal&paypal=cancelado';
const agua = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body.find(p => p.nombre.startsWith('Agua'));
const aguaV = agua.variantes[0];
const disponible = async () => (await api('GET', `/api/tienda/${gymId}/productos/${agua.id}`, null, member)).body.variantes[0].disponible;
async function carrito() {
  await api('DELETE', '/api/tienda/carrito?canal=web', null, member);
  return (await api('POST', '/api/tienda/carrito/items?canal=web', { varianteId: aguaV.id, cantidad: 1 }, member)).body;
}
// Orden aprobada en el simulador de PayPal: {token, payerId}.
async function aprobada() {
  const orden = (await api('POST', '/api/pagos/paypal/ordenes?canal=web', { returnUrl: RETORNO, cancelUrl: CANCELA }, member)).body;
  const r = (await api('POST', `/api/simuladores/paypal/ordenes/${orden.id}/aprobar`, { cuenta: 'ana.compradora@sim-paypal.test' })).body;
  return { token: orden.id, payerId: new URL(r.redirect).searchParams.get('PayerID') };
}
const pagar = (datos, total) => api('POST', '/api/tienda/carrito/checkout?canal=web', { metodo: 'paypal', datos, totalVisto: total, consentimiento: CONSENTIMIENTO }, member);

console.log('\n1. Se puede simular (modo de prueba)');
let r = await api('GET', '/api/pagos/modo');
ok(r.status === 200 && r.body.simularPaypal === true, 'GET /api/pagos/modo → simularPaypal: true', r.body);

console.log('\n2. Tienda');
let c = await carrito();
const antes = await disponible();
r = await pagar({ ...(await aprobada()), simular: 'sin_fondos' }, c.total);
ok(r.status === 402 && /fondos suficientes/.test(r.body.error) && /No se te cobró/.test(r.body.error), 'sin fondos → 402 con el motivo', r.body?.error);
ok(await disponible() === antes, 'no se apartó nada del inventario');
r = await api('GET', '/api/tienda/carrito?canal=web', null, member);
ok(r.body.articulos === 1, 'el carrito sigue igual para intentarlo otra vez', r.body?.articulos);
r = await pagar({ ...(await aprobada()), simular: 'cuenta_invalida' }, c.total);
ok(r.status === 402 && /no puede pagar/.test(r.body.error), 'cuenta inválida o vencida → 402 con el motivo', r.body?.error);
r = await pagar({ ...(await aprobada()), simular: 'robar' }, c.total);
ok(r.status === 400, 'un resultado que no existe → 400', r.body?.error);
r = await pagar({ ...(await aprobada()), simular: 'aprobado' }, c.total);
ok(r.status === 200 && r.body.estado === 'pagado', '"aprobado" cobra normal', r.body?.estado || r.body?.error);

console.log('\n3. Mostrador');
const precio = aguaV.precio;
const ordenMostrador = async () => {
  const o = (await api('POST', `/api/gyms/${gymId}/mostrador/paypal/orden`, { partidas: [{ varianteId: aguaV.id, cantidad: 1 }], totalVisto: precio,
    returnUrl: API + '/bienvenido.html?cobro=paypal', cancelUrl: API + '/bienvenido.html?cobro=cancelado&metodo=paypal' }, owner)).body;
  const a = (await api('POST', `/api/simuladores/paypal/ordenes/${o.id}/aprobar`, { cuenta: 'ana.compradora@sim-paypal.test' })).body;
  return { token: o.id, payerId: new URL(a.redirect).searchParams.get('PayerID') };
};
r = await api('POST', `/api/gyms/${gymId}/mostrador/cobrar`, { partidas: [{ varianteId: aguaV.id, cantidad: 1 }], metodo: 'paypal',
  datos: { ...(await ordenMostrador()), simular: 'sin_fondos' }, totalVisto: precio }, owner);
ok(r.status === 402 && /fondos suficientes/.test(r.body.error), 'mostrador: sin fondos → 402', r.body?.error);
r = await api('POST', `/api/gyms/${gymId}/mostrador/cobrar`, { partidas: [{ varianteId: aguaV.id, cantidad: 1 }], metodo: 'paypal',
  datos: await ordenMostrador(), totalVisto: precio }, owner);
ok(r.status === 200 && r.body.pedido?.estado === 'pagado', 'mostrador: sin simular cobra normal', r.body?.pedido?.estado || r.body?.error);

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
