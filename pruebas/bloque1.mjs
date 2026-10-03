// Prueba de punta a punta del bloque 1 contra Spring (8080) y Medusa (9000) locales.
import fs from 'node:fs';
import crypto from 'node:crypto';
import { API, ESTADO, LOG, MEDUSA, SECRETO_WEBHOOK, adminMedusa } from './entorno.mjs';

const ADMIN = adminMedusa();

let fallos = 0;
const TOKENS = {};
const cabeceras = userId => userId ? { 'X-User-Id': userId, ...(TOKENS[userId] ? { Authorization: 'Bearer ' + TOKENS[userId] } : {}) } : {};
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));

async function api(metodo, ruta, cuerpo, userId) {
  const r = await fetch(API + ruta, { method: metodo, headers: { 'Content-Type': 'application/json', ...cabeceras(userId) }, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
async function medusa(metodo, ruta, cuerpo, llave) {
  const headers = { 'Content-Type': 'application/json', ...(llave ? { 'x-publishable-api-key': llave } : { Authorization: ADMIN }) };
  const r = await fetch(MEDUSA + ruta, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  const body = await r.json().catch(() => null);
  if (!r.ok) throw new Error(`${metodo} ${ruta} → ${r.status} ${JSON.stringify(body)}`);
  return body;
}

const sufijo = Date.now().toString(36);
console.log('\n1. Dueño, gimnasio y miembro');
const correoDueno = `dueno-${sufijo}@prueba.mx`;
let r = await api('POST', '/api/users/register', { nombre: 'Dueña Prueba', email: correoDueno, password: 'Clave#2026' });
ok(r.status === 200, 'registro del dueño', r.status);
await espera(500);
const codigo = [...fs.readFileSync(LOG, 'latin1').matchAll(new RegExp(`verificación de ${correoDueno.replace('.', '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
r = await api('POST', '/api/cuenta/verificar', { email: correoDueno, codigo });
ok(r.status === 200, 'verificación con el código de la consola', codigo);
r = await api('POST', '/api/users/login', { email: correoDueno, password: 'Clave#2026' });
const owner = r.body; ok(owner.role === 'owner' && !!owner.token, 'login del dueño (con token)');
TOKENS[owner.id] = owner.token;
r = await api('POST', '/api/gyms', { nombre: 'PowerFit Prueba', direccion: 'Av. Juárez 100, Pachuca', cuotaMensual: 450, ownerUserId: owner.id }, owner.id);
const gymId = r.body.id; ok(!!gymId, 'gimnasio creado', gymId);
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: 'Miembro Prueba', email: `miembro-${sufijo}@prueba.mx`, password: 'Clave#2026' }, owner.id);
const member = r.body; ok(member.membershipStatus === 'active', 'miembro dado de alta en recepción');
TOKENS[member.id] = (await api('POST', '/api/users/login', { email: `miembro-${sufijo}@prueba.mx`, password: 'Clave#2026' })).body.token;

console.log('\n2. Acceso');
r = await api('GET', `/api/gyms/${gymId}/productos`);
ok(r.status === 401, 'sin X-User-Id → 401', r.body);
r = await api('GET', `/api/gyms/${gymId}/productos`, null, member.id);
ok(r.status === 403, 'un miembro no administra productos → 403', r.body);

console.log('\n3. Planes');
r = await api('GET', `/api/gyms/${gymId}/planes/presets`, null, owner.id);
ok(r.status === 200 && r.body.length === 7, 'presets', r.body.map(p => `${p.nombre}:${p.precio}`));
const preset = n => r.body.find(p => p.nombre === n);
const planes = {};
for (const n of ['Visita', 'Mensual', 'Trimestral', 'Inscripción']) {
  const p = preset(n);
  const c = await api('POST', `/api/gyms/${gymId}/planes`, { ...p, descripcion: 'Plan ' + n, activo: true }, owner.id);
  ok(c.status === 200, `plan ${n} creado`, c.status === 200 ? `${c.body.duracionTexto} $${c.body.precio}` : c.body);
  planes[n] = c.body;
}
r = await api('GET', `/api/gyms/${gymId}/gyms`, null, owner.id);
const gymDoc = (await api('GET', `/api/gyms/${gymId}`, null, owner.id)).body;
ok(gymDoc.tienda && gymDoc.tienda.lista, 'tienda del gimnasio inicializada en Medusa', Object.keys(gymDoc.tienda || {}));
const edit = await api('PUT', `/api/gyms/${gymId}/planes/${planes.Mensual.id}`, { ...planes.Mensual, precio: 480, beneficios: ['Acceso total', 'App'] }, owner.id);
ok(edit.status === 200 && edit.body.precio === 480, 'editar precio del plan Mensual', edit.body.precio);

console.log('\n4. Imagen y productos');
const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
r = await api('POST', `/api/gyms/${gymId}/imagenes`, { dataUrl: png }, owner.id);
const imagen = r.body.url; ok(r.status === 200 && imagen.includes('/api/imagenes/'), 'imagen subida', imagen);
const img = await fetch(imagen); ok(img.ok && img.headers.get('content-type') === 'image/png', 'imagen servida', img.headers.get('cache-control'));
r = await api('POST', `/api/gyms/${gymId}/productos`, {
  nombre: 'Proteína Whey Gold', descripcion: 'Proteína de suero, 24 g por porción', categoria: 'suplementos', imagen, activo: true,
  variantes: [
    { presentacion: 'Scoop', sabor: 'Vainilla', precio: 35, controlarInventario: false },
    { presentacion: 'Bote 2 lb', sabor: 'Vainilla', precio: 899.5, controlarInventario: true, existencias: 5 },
  ],
}, owner.id);
const prod = r.body;
ok(r.status === 200 && prod.variantes.length === 2, 'producto con 2 variantes', r.status === 200 ? prod.variantes.map(v => `${v.nombre} $${v.precio} stock:${v.existencias}`) : r.body);
const bote = prod.variantes.find(v => v.presentacion === 'Bote 2 lb');
r = await api('PUT', `/api/gyms/${gymId}/productos/${prod.id}`, {
  nombre: 'Proteína Whey Gold', descripcion: 'Proteína de suero', categoria: 'suplementos', imagen, activo: true,
  variantes: [
    { id: prod.variantes[0].id, presentacion: 'Scoop', sabor: 'Vainilla', precio: 40, controlarInventario: false },
    { id: bote.id, presentacion: 'Bote 2 lb', sabor: 'Vainilla', precio: 899.5, controlarInventario: true, existencias: 8 },
    { presentacion: 'Bote 2 lb', sabor: 'Fresa', precio: 899.5, controlarInventario: true, existencias: 3 },
  ],
}, owner.id);
ok(r.status === 200 && r.body.variantes.length === 3, 'editar: nuevo sabor, precio y stock', r.status === 200 ? r.body.variantes.map(v => `${v.nombre} $${v.precio} stock:${v.existencias}`) : r.body);
r = await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'Agua 1 L', categoria: 'bebidas', variantes: [{ precio: 15, existencias: 24 }] }, owner.id);
ok(r.status === 200 && r.body.variantes[0].nombre === 'Única', 'producto sin presentación → "Única"', r.body.variantes?.[0]);
const agua = r.body;
r = await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'X', categoria: 'bebidas', variantes: [{ presentacion: 'A', precio: 0 }] }, owner.id);
ok(r.status === 400, 'precio 0 → 400', r.body);
r = await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'X', categoria: 'bebidas', variantes: [{ presentacion: 'A', precio: 1 }, { presentacion: 'a', precio: 2 }] }, owner.id);
ok(r.status === 400, 'variantes repetidas → 400', r.body);
r = await api('GET', `/api/gyms/${gymId}/productos`, null, owner.id);
ok(r.status === 200 && r.body.length === 2, 'listado de productos del gimnasio', r.body.map(p => p.nombre));

console.log('\n5. Compra con tarjeta (sim-stripe): plan Mensual + 2 botes');
const llave = gymDoc.tienda.publishableKey;
const region = (await medusa('GET', '/admin/regions?fields=id&currency_code=mxn')).regions[0].id;
async function comprar(items, proveedor, data) {
  const cart = (await medusa('POST', '/store/carts', { region_id: region, email: member.email, metadata: { userId: member.id, gymId, canal: 'web' } }, llave)).cart;
  for (const it of items) await medusa('POST', `/store/carts/${cart.id}/line-items`, it, llave);
  await medusa('POST', `/store/carts/${cart.id}/shipping-methods`, { option_id: gymDoc.tienda.shippingOptionId }, llave);
  const pc = (await medusa('POST', '/store/payment-collections', { cart_id: cart.id }, llave)).payment_collection;
  await medusa('POST', `/store/payment-collections/${pc.id}/payment-sessions`, { provider_id: proveedor, data }, llave);
  const res = await medusa('POST', `/store/carts/${cart.id}/complete`, {}, llave);
  return res.order;
}
const antes = (await api('GET', `/api/users/${member.id}/me`)).body;
const varPlan = planes.Mensual.varianteId;
const orden1 = await comprar([
  { variant_id: varPlan, quantity: 1, metadata: { duracionUnidad: 'mes', duracionCantidad: 1 } },
  { variant_id: bote.id, quantity: 2 },
], 'pp_sim-stripe_default', { token: 'tok_sim_prueba', resultado: 'aprobada', marca: 'visa', ultimos4: '4242' });
ok(!!orden1, 'pedido creado en Medusa', orden1.display_id);
await espera(2500);
let yo = (await api('GET', `/api/users/${member.id}/me`)).body;
ok(yo.planActual === 'Mensual' && yo.fechaProximoPago, 'membresía extendida por el aviso order.placed', { antes: antes.fechaProximoPago, despues: yo.fechaProximoPago, dia: yo.diaDePago });
let pagos = (await api('GET', `/api/gyms/${gymId}/members/${member.id}/payments`, null, owner.id)).body;
ok(pagos.length === 1 && pagos[0].orderId === orden1.id, 'un Payment con orderId', pagos.map(p => [p.plan, p.metodo, p.monto, p.cubreHasta]));
r = await api('GET', `/api/gyms/${gymId}/productos/${prod.id}`, null, owner.id);
ok(r.body.variantes.find(v => v.id === bote.id).disponible === 6, 'stock disponible bajó de 8 a 6', r.body.variantes.find(v => v.id === bote.id));

console.log('\n6. Aviso repetido');
async function avisar(evento, orderId, paymentId, secreto = SECRETO_WEBHOOK, desfase = 0) {
  const cuerpo = JSON.stringify({ id: crypto.randomUUID(), evento, orderId, paymentId, enviadoEn: new Date().toISOString() });
  const t = Math.floor(Date.now() / 1000) - desfase;
  const firma = crypto.createHmac('sha256', secreto).update(`${t}.${cuerpo}`).digest('hex');
  const res = await fetch(API + '/api/tienda/webhooks/medusa', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-GymTrack-Firma': `t=${t},v1=${firma}` }, body: cuerpo });
  return { status: res.status, body: await res.json() };
}
r = await avisar('order.placed', orden1.id);
ok(r.status === 200 && r.body.message === 'Aviso ya procesado.', 'el mismo aviso se reconoce como procesado', r.body);
r = await avisar('order.canceled', orden1.id, undefined, 'otra-clave');
ok(r.status === 401, 'firma con otra clave → 401', r.body);
r = await avisar('order.placed', orden1.id, undefined, SECRETO_WEBHOOK, 600);
ok(r.status === 401, 'firma de hace 10 minutos → 401', r.body);
await fetch(MEDUSA + '/admin/orders/' + orden1.id, { headers: { Authorization: ADMIN } });
// Aunque llegue con otro id de evento (payment.captured), no extiende dos veces.
r = await avisar('payment.captured', orden1.id, 'pay_inventado');
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
pagos = (await api('GET', `/api/gyms/${gymId}/members/${member.id}/payments`, null, owner.id)).body;
ok(pagos.length === 1, 'otro evento del mismo pedido no crea otro pago ni extiende', { pagos: pagos.length, vence: yo.fechaProximoPago });

console.log('\n7. Paynet: pendiente no activa; al capturar sí');
const venceAntes = yo.fechaProximoPago;
const orden2 = await comprar([{ variant_id: planes.Visita.varianteId, quantity: 1, metadata: { duracionUnidad: 'dia', duracionCantidad: 1 } }], 'pp_sim-paynet_default', {});
await espera(2500);
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
ok(yo.fechaProximoPago === venceAntes, 'pedido Paynet pendiente no mueve la fecha', yo.fechaProximoPago);
const pagoMedusa = (await medusa('GET', `/admin/orders/${orden2.id}?fields=payment_collections.payments.id,payment_status`)).order;
ok(pagoMedusa.payment_status === 'authorized', 'Medusa: pago autorizado sin capturar', pagoMedusa.payment_status);
await medusa('POST', `/admin/payments/${pagoMedusa.payment_collections[0].payments[0].id}/capture`, {});
await espera(3000);
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
const esperado = new Date(venceAntes + 'T12:00:00'); esperado.setDate(esperado.getDate() + 1);
ok(yo.fechaProximoPago === esperado.toISOString().slice(0, 10) && yo.planActual === 'Visita', 'al capturar (payment.captured) la Visita suma 1 día', { antes: venceAntes, despues: yo.fechaProximoPago });

console.log('\n8. Pago manual con plan Trimestral');
r = await api('POST', `/api/gyms/${gymId}/members/${member.id}/payments`, { monto: 1215, metodo: 'Efectivo', planId: planes.Trimestral.id }, owner.id);
ok(r.status === 200, 'pago manual con plan', r.body.fechaProximoPago);
const tri = new Date(yo.fechaProximoPago + 'T12:00:00'); tri.setMonth(tri.getMonth() + 3);
console.log('     (antes ' + yo.fechaProximoPago + ', día de pago ' + yo.diaDePago + ')');
r = await api('POST', `/api/gyms/${gymId}/members/${member.id}/payments`, { monto: 100, metodo: 'Efectivo', planId: planes['Inscripción'].id }, owner.id);
ok(r.status === 400, 'la inscripción no se puede registrar como plan → 400', r.body);

console.log('\n9. Borrar');
r = await api('DELETE', `/api/gyms/${gymId}/productos/${agua.id}`, null, owner.id);
ok(r.status === 200, 'producto eliminado');
r = await api('GET', `/api/gyms/${gymId}/productos/${agua.id}`, null, owner.id);
ok(r.status === 404, 'ya no aparece', r.body);

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
fs.writeFileSync(ESTADO, JSON.stringify({ gymId, owner: owner.id, member: member.id, tokens: TOKENS, planes, prod: prod.id }, null, 2));
