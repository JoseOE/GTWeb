// Entregar un pedido con la tarjeta RFID del socio, en lugar del QR: el panel
// pone el lector a esperar una tarjeta, el socio la pasa y salen sus pedidos
// pagados por recoger.
import fs from 'node:fs';
import { API, ESTADO, CONSENTIMIENTO } from './entorno.mjs';

const n = JSON.parse(fs.readFileSync(ESTADO, 'utf8'));
const TOKENS = n.tokens || {};
const cabeceras = userId => userId ? { 'X-User-Id': userId, ...(TOKENS[userId] ? { Authorization: 'Bearer ' + TOKENS[userId] } : {}) } : {};
let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
async function api(metodo, ruta, cuerpo, userId, extra = {}) {
  const r = await fetch(API + ruta, { method: metodo, headers: { 'Content-Type': 'application/json', ...cabeceras(userId), ...extra }, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
const epcAlAzar = () => 'E2' + Array.from({ length: 22 }, () => '0123456789ABCDEF'[Math.floor(Math.random() * 16)]).join('');
const { gymId, owner, member } = n;
const base = `/api/gyms/${gymId}/ventas/entregas/tarjeta`;

console.log('\n1. Preparar: lector, tarjeta del socio y un pedido pagado');
let r = await api('POST', `/api/gyms/${gymId}/acceso/lectores`, { nombre: 'Recepción' }, owner);
ok(r.status === 200 && r.body.llave, 'el dueño da de alta un lector');
const llave = { 'X-Lector-Key': r.body.llave };
// El lector "se conecta" para quedar como el más reciente del gimnasio.
await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
const epc = epcAlAzar();
r = await api('POST', `/api/gyms/${gymId}/acceso/tarjetas`, { epc, userId: member }, owner);
ok(r.status === 200, 'liga una tarjeta al socio', r.body?.nombre);
const producto = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body.find(p => p.tipo !== 'membresia' && p.variantes?.length);
await api('POST', '/api/tienda/carrito/items?canal=app', { varianteId: producto.variantes[0].id, cantidad: 1 }, member);
const carrito = (await api('GET', '/api/tienda/carrito?canal=app', null, member)).body;
const tok = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Socio Prueba', mes: '12', anio: '30', cvc: '123' }, member)).body.token;
r = await api('POST', '/api/tienda/carrito/checkout?canal=app', { metodo: 'stripe', datos: { token: tok }, totalVisto: carrito.total, consentimiento: CONSENTIMIENTO }, member);
ok(r.status === 200 && r.body.estado === 'pagado', 'el socio compra un producto en la app', r.body?.folio ?? r.body?.error);
const orderId = r.body.orderId;

console.log('\n2. Pasa su tarjeta y salen sus pedidos');
r = await api('POST', base, {}, member);
ok(r.status === 403, 'un socio no puede poner el lector a esperar', r.status);
r = await api('POST', base, {}, owner);
ok(r.status === 200 && r.body.lectorId && r.body.segundos === 60 && r.body.enLinea === true, 'el lector espera una tarjeta 60 s', r.body);
const lectorId = r.body.lectorId;
r = await api('GET', `${base}?lectorId=${lectorId}`, null, owner);
ok(r.status === 200 && r.body.estado === 'esperando', 'mientras no pase, sigue esperando', r.body?.estado);
r = await api('POST', '/api/lectores/accesos', { epc }, null, llave);
ok(r.status === 200 && r.body.resultado === 'REGISTRO', 'el lector solo la captura (LED azul), no abre ni niega', r.body?.resultado);
r = await api('GET', `${base}?lectorId=${lectorId}`, null, owner);
ok(r.status === 200 && r.body.estado === 'leida' && r.body.nombre, 'el panel ve de quién es', r.body?.nombre);
ok(Array.isArray(r.body.pedidos) && r.body.pedidos.some(p => p.orderId === orderId && p.porEntregar), 'y su pedido pagado por recoger', r.body.pedidos?.map(p => p.folio));
ok(!('userId' in r.body), 'no expone el id del socio');
r = await api('GET', `${base}?lectorId=${lectorId}`, null, owner);
ok(r.body.estado === 'sin_tarjeta', 'la lectura se usa una sola vez', r.body?.estado);

console.log('\n3. Tarjeta que no es de un socio');
await api('POST', base, {}, owner);
r = await api('POST', '/api/lectores/accesos', { epc: epcAlAzar() }, null, llave);
ok(r.body?.resultado === 'REGISTRO', 'se captura igual');
r = await api('GET', `${base}?lectorId=${lectorId}`, null, owner);
ok(r.body.estado === 'desconocida', 'el panel dice que no es de ningún socio', r.body?.estado);

console.log('\n4. Cancelar deja el lector como estaba');
await api('POST', base, {}, owner);
r = await api('DELETE', `${base}?lectorId=${lectorId}`, null, owner);
ok(r.status === 204, 'cancelar la espera', r.status);
r = await api('POST', '/api/lectores/accesos', { epc }, null, llave);
ok(r.status === 200 && r.body.resultado !== 'REGISTRO', 'la tarjeta vuelve a decidir la entrada', r.body?.resultado);
r = await api('GET', `${base}?lectorId=lector_que_no_existe`, null, owner);
ok(r.status === 404, 'un lector que no es del gimnasio → 404', r.status);

console.log('\n5. Ya entregado, no vuelve a salir');
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${orderId}/entregar`, {}, owner);
ok(r.status === 200, 'el dueño lo entrega', r.body?.entrega?.estado);
await api('POST', base, {}, owner);
await api('POST', '/api/lectores/accesos', { epc }, null, llave);
r = await api('GET', `${base}?lectorId=${lectorId}`, null, owner);
ok(r.body.estado === 'leida' && !r.body.pedidos.some(p => p.orderId === orderId), 'el pedido entregado ya no aparece', r.body.pedidos?.length);

console.log(fallos ? `\n${fallos} comprobación(es) fallaron.` : '\nTodo bien.');
process.exit(fallos ? 1 : 0);
