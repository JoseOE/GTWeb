// Prueba de punta a punta del bloque 1 contra Spring (8080) y MongoDB locales.
import fs from 'node:fs';
import { API, ESTADO, leerLog } from './entorno.mjs';

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

const sufijo = Date.now().toString(36);
console.log('\n1. Dueño, gimnasio y miembro');
const correoDueno = `dueno-${sufijo}@prueba.mx`;
let r = await api('POST', '/api/users/register', { nombre: 'Dueña Prueba', email: correoDueno, password: 'Clave#2026' });
ok(r.status === 200, 'registro del dueño', r.status);
await espera(500);
const codigo = [...leerLog().matchAll(new RegExp(`verificación de ${correoDueno.replace('.', '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
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
r = await api('GET', '/api/tienda/estado');
ok(r.status === 200 && r.body.lista === true, 'la tienda está lista sin servicio aparte', r.body);
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
// Lo mismo que hace la tienda: carrito en el servidor, token de la tarjeta y cobro.
async function comprar(items, metodo, numero) {
  await api('DELETE', '/api/tienda/carrito', null, member.id);
  let carrito;
  for (const it of items) carrito = (await api('POST', '/api/tienda/carrito/items', it, member.id)).body;
  let datos = {};
  if (numero) {
    datos = { token: (await api('POST', '/api/simuladores/stripe/tokens', { numero, titular: 'Miembro Prueba', mes: '12', anio: '30', cvc: '123' }, member.id)).body.token };
  }
  return api('POST', '/api/tienda/carrito/checkout', { metodo, datos, totalVisto: carrito.total }, member.id);
}
const antes = (await api('GET', `/api/users/${member.id}/me`)).body;
r = await comprar([{ varianteId: planes.Mensual.varianteId }, { varianteId: bote.id, cantidad: 2 }], 'stripe', '4242424242424242');
const orden1 = r.body;
ok(r.status === 200 && orden1.estado === 'pagado' && /^order_/.test(orden1.orderId), 'pedido pagado', { folio: orden1.folio, estado: orden1.estado });
let yo = (await api('GET', `/api/users/${member.id}/me`)).body;
ok(yo.planActual === 'Mensual' && yo.fechaProximoPago, 'membresía extendida al pagar', { antes: antes.fechaProximoPago, despues: yo.fechaProximoPago, dia: yo.diaDePago });
let pagos = (await api('GET', `/api/gyms/${gymId}/members/${member.id}/payments`, null, owner.id)).body;
ok(pagos.length === 1 && pagos[0].orderId === orden1.orderId, 'un Payment con orderId', pagos.map(p => [p.plan, p.metodo, p.monto, p.cubreHasta]));
r = await api('GET', `/api/gyms/${gymId}/productos/${prod.id}`, null, owner.id);
ok(r.body.variantes.find(v => v.id === bote.id).disponible === 6, 'stock disponible bajó de 8 a 6', r.body.variantes.find(v => v.id === bote.id));

console.log('\n6. Consultas repetidas del pedido');
// Ya no hay webhook: leer el pedido o su recibo las veces que sea no vuelve a
// aplicar el plan.
r = await fetch(API + '/api/tienda/webhooks/medusa', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
ok(r.status === 404, 'el aviso de Medusa ya no existe', r.status);
for (let k = 0; k < 3; k++) await api('GET', `/api/tienda/pedidos/${orden1.orderId}`, null, member.id);
r = await fetch(API + `/api/recibos/pedidos/${orden1.orderId}.pdf`, { headers: cabeceras(member.id) });
ok(r.status === 200 && r.headers.get('content-type') === 'application/pdf', 'el recibo abre', r.status);
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
pagos = (await api('GET', `/api/gyms/${gymId}/members/${member.id}/payments`, null, owner.id)).body;
ok(pagos.length === 1, 'consultar el pedido no crea otro pago ni extiende', { pagos: pagos.length, vence: yo.fechaProximoPago });

console.log('\n7. Paynet: pendiente no activa; al pagar en tienda sí');
const venceAntes = yo.fechaProximoPago;
r = await comprar([{ varianteId: planes.Visita.varianteId }], 'paynet');
const orden2 = r.body;
ok(r.status === 200 && orden2.estado === 'pendiente_pago', 'ficha Paynet pendiente', orden2.paynet && orden2.paynet.referencia);
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
ok(yo.fechaProximoPago === venceAntes, 'pedido Paynet pendiente no mueve la fecha', yo.fechaProximoPago);
r = await api('POST', `/api/simuladores/paynet/${orden2.orderId}/pagar`, null, owner.id);
ok(r.status === 200 && r.body.estado === 'pagado', 'simular pago en tienda', r.body.estado);
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
const esperado = new Date(venceAntes + 'T12:00:00'); esperado.setDate(esperado.getDate() + 1);
ok(yo.fechaProximoPago === esperado.toISOString().slice(0, 10) && yo.planActual === 'Visita', 'al pagarse la Visita suma 1 día', { antes: venceAntes, despues: yo.fechaProximoPago });
r = await api('POST', `/api/simuladores/paynet/${orden2.orderId}/pagar`, null, owner.id);
const venceTrasFicha = yo.fechaProximoPago;
yo = (await api('GET', `/api/users/${member.id}/me`)).body;
ok(r.status === 409 && yo.fechaProximoPago === venceTrasFicha, 'pagar la misma ficha otra vez → 409 y no extiende', r.body.error);

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
