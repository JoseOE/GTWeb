// Prueba del bloque 4 (Paynet, recibos iText y correos) contra Spring y MongoDB locales.
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { API, ESTADO, MONGO, leerLog, CONSENTIMIENTO } from './entorno.mjs';

const require = createRequire(import.meta.url);
const { MongoClient } = require('mongodb');
const n = JSON.parse(fs.readFileSync(ESTADO, 'utf8'));
const TOKENS = n.tokens || {};
const cabeceras = userId => userId ? { 'X-User-Id': userId, ...(TOKENS[userId] ? { Authorization: 'Bearer ' + TOKENS[userId] } : {}) } : {};
let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));
async function api(metodo, ruta, cuerpo, userId) {
  const r = await fetch(API + ruta, { method: metodo, headers: { 'Content-Type': 'application/json', ...cabeceras(userId) }, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
async function binario(ruta, userId) {
  const r = await fetch(API + ruta, { headers: cabeceras(userId) });
  const buf = Buffer.from(await r.arrayBuffer());
  return { status: r.status, tipo: r.headers.get('content-type'), buf };
}
const lineasLog = (texto) => leerLog().split('\n').filter(l => l.includes(texto));
const luhn = (d) => { let s = 0, dob = false; for (let i = d.length - 1; i >= 0; i--) { let x = +d[i]; if (dob) { x *= 2; if (x > 9) x -= 9; } s += x; dob = !dob; } return s % 10 === 0; };
const { gymId, owner, member, planes } = n;
fs.mkdirSync(new URL('./pdfs/', import.meta.url), { recursive: true });
const guardar = (nombre, buf) => fs.writeFileSync(new URL('./pdfs/' + nombre, import.meta.url), buf);

let productos = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body;
if (!productos.some(p => p.nombre.startsWith('Agua'))) {
  await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'Agua natural', categoria: 'bebidas', activo: true, variantes: [{ presentacion: 'Botella 1 L', precio: 18, existencias: 50 }] }, owner);
  productos = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body;
}
const agua = productos.find(p => p.nombre.startsWith('Agua')).variantes[0];

console.log('\n1. Recibo de una compra pagada');
await api('DELETE', '/api/tienda/carrito', null, member);
await api('POST', '/api/tienda/carrito/items', { varianteId: agua.id, cantidad: 2 }, member);
await api('POST', '/api/tienda/carrito/items', { varianteId: planes.Mensual.varianteId }, member);
let c = (await api('GET', '/api/tienda/carrito', null, member)).body;
const tok = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Ana', mes: '12', anio: '30', cvc: '123' }, member)).body.token;
let r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: tok }, totalVisto: c.total, consentimiento: CONSENTIMIENTO }, member);
const pagado = r.body;
ok(r.status === 200 && pagado.estado === 'pagado', 'compra con tarjeta', pagado.folio);
let b = await binario(`/api/recibos/pedidos/${pagado.orderId}.pdf`, member);
ok(b.status === 200 && b.tipo === 'application/pdf' && b.buf.subarray(0, 5).toString() === '%PDF-', 'recibo carta (comprador)', { bytes: b.buf.length });
guardar('recibo-carta.pdf', b.buf);
b = await binario(`/api/recibos/pedidos/${pagado.orderId}.pdf?formato=ticket`, owner);
ok(b.status === 200 && b.buf.subarray(0, 5).toString() === '%PDF-', 'ticket 80 mm (dueño)', { bytes: b.buf.length });
guardar('ticket-80mm.pdf', b.buf);
const ajeno = (await api('POST', '/api/users/register', { nombre: 'Ajeno', email: `ajeno-${Date.now().toString(36)}@prueba.mx`, password: 'Clave#2026', role: 'member' })).body;
b = await binario(`/api/recibos/pedidos/${pagado.orderId}.pdf`, ajeno.id);
ok(b.status === 404, 'otra persona no puede bajar el recibo');
b = await binario(`/api/recibos/pedidos/${pagado.orderId}.pdf`);
ok(b.status === 401, 'sin sesión → 401');
await espera(1500);
ok(lineasLog('compra-confirmada').length === 0 && lineasLog(`Tu compra en`).some(l => l.includes('adjunto')), 'correo "compra confirmada" con el PDF adjunto (consola, sin Brevo)', lineasLog('Tu compra en').slice(-1)[0]?.slice(-90));

console.log('\n2. Paynet');
await api('POST', '/api/tienda/carrito/items', { varianteId: agua.id, cantidad: 3 }, member);
await api('POST', '/api/tienda/carrito/items', { varianteId: planes.Visita.varianteId }, member);
c = (await api('GET', '/api/tienda/carrito', null, member)).body;
const dispAntes = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body.find(p => p.nombre.startsWith('Agua')).variantes[0].disponible;
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'paynet', datos: {}, totalVisto: c.total, consentimiento: CONSENTIMIENTO }, member);
const ficha = r.body;
const ref = (ficha.paynet?.referencia || '').replace(/\s/g, '');
ok(r.status === 200 && ficha.estado === 'pendiente_pago', 'pedido Paynet pendiente', ficha.detallePago);
ok(/^93\d{16}$/.test(ref) && luhn(ref), 'referencia de 18 dígitos con dígito verificador válido', ref);
const horas = (new Date(ficha.paynet.vence) - Date.now()) / 3600000;
ok(horas > 71.9 && horas <= 72, 'fecha límite a 72 h', horas.toFixed(2));
b = await binario(`/api/recibos/paynet/${ficha.orderId}/codigo.png`, member);
ok(b.status === 200 && b.tipo === 'image/png' && b.buf.subarray(1, 4).toString() === 'PNG', 'código de barras PNG', { bytes: b.buf.length });
guardar('codigo-paynet.png', b.buf);
b = await binario(`/api/recibos/paynet/${ficha.orderId}.pdf`, member);
ok(b.status === 200 && b.buf.subarray(0, 5).toString() === '%PDF-', 'ficha Paynet en PDF', { bytes: b.buf.length });
guardar('ficha-paynet.pdf', b.buf);
r = await api('GET', `/api/recibos/pedidos/${ficha.orderId}.pdf`, null, member);
ok(r.status === 409, 'el recibo de un pedido pendiente → 409', r.body?.error);
const venceAntes = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
r = await api('POST', `/api/simuladores/paynet/${ficha.orderId}/pagar`, null, member);
ok(r.status === 403, 'un miembro no puede simular el pago', r.body.error);
r = await api('POST', `/api/simuladores/paynet/${ficha.orderId}/pagar`, null, owner);
ok(r.status === 200 && r.body.estado === 'pagado', '"Simular pago en tienda" → pagado', r.body.estadoTexto);
const venceDespues = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
ok(venceDespues !== venceAntes, 'la Visita del pedido Paynet se aplicó al pagarse', { antes: venceAntes, despues: venceDespues });
r = await api('POST', `/api/simuladores/paynet/${ficha.orderId}/pagar`, null, owner);
ok(r.status === 409, 'pagar dos veces → 409', r.body.error);
await espera(2500);
ok(lineasLog('Tu ficha de pago en efectivo').some(l => l.includes('#' + ficha.folio)), 'correo con la ficha', lineasLog('Tu ficha de pago en efectivo').slice(-1)[0]?.slice(-70));
ok(lineasLog('Recibimos tu pago').some(l => l.includes('#' + ficha.folio)), 'correo "Recibimos tu pago" con recibo', lineasLog('Recibimos tu pago').slice(-1)[0]?.slice(-70));
b = await binario(`/api/recibos/pedidos/${ficha.orderId}.pdf`, member);
ok(b.status === 200, 'ahora sí tiene recibo');

console.log('\n3. Fichas vencidas');
c = (await api('POST', '/api/tienda/carrito/items', { varianteId: agua.id, cantidad: 4 }, member)).body;
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'paynet', datos: {}, totalVisto: c.total, consentimiento: CONSENTIMIENTO }, member);
const vieja = r.body;
const conApartado = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body.find(p => p.nombre.startsWith('Agua')).variantes[0].disponible;
const mongo = await MongoClient.connect(MONGO);
await mongo.db().collection('pedidos').updateOne({ orderId: vieja.orderId }, { $set: { 'datosPago.vence': new Date(Date.now() - 3600000).toISOString() } });
await mongo.close();
r = await api('POST', '/api/simuladores/paynet/vencidas', null, owner);
ok(r.status === 200 && r.body.canceladas >= 1, 'el revisor cancela la ficha vencida', r.body);
r = await api('GET', `/api/tienda/pedidos/${vieja.orderId}`, null, member);
ok(r.body.estado === 'cancelado', 'el pedido quedó cancelado', r.body.estadoTexto);
const liberado = (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body.find(p => p.nombre.startsWith('Agua')).variantes[0].disponible;
ok(liberado === conApartado + 4, 'se liberó el inventario apartado', { conApartado, liberado });

console.log('\n4. Recibo de mensualidad');
r = await api('POST', `/api/gyms/${gymId}/members/${member}/payments`, { monto: 450, metodo: 'Efectivo', nota: 'Pago en recepción' }, owner);
const pago = r.body.pago;
b = await binario(`/api/recibos/pagos/${pago.id}.pdf`, owner);
ok(b.status === 200 && b.buf.subarray(0, 5).toString() === '%PDF-', 'recibo de mensualidad (dueño)', { bytes: b.buf.length });
guardar('recibo-mensualidad.pdf', b.buf);
b = await binario(`/api/recibos/pagos/${pago.id}.pdf`, member);
ok(b.status === 200, 'el miembro también lo puede bajar');
b = await binario(`/api/recibos/pagos/${pago.id}.pdf`, ajeno.id);
ok(b.status === 404, 'otra persona no');
r = await api('POST', `/api/recibos/pagos/${pago.id}/enviar`, null, owner);
ok(r.status === 200 && 'enviado' in r.body, 'reenviar por correo', r.body);
r = await api('POST', `/api/recibos/pedidos/${pagado.orderId}/enviar`, null, member);
ok(r.status === 200, 'reenviar recibo de compra', r.body);
await espera(1500);
ok(lineasLog('Tu recibo de').length >= 1, 'correo del recibo de mensualidad', lineasLog('Tu recibo de').slice(-1)[0]?.slice(-70));

console.log('\n5. Sin duplicados');
for (let i = 0; i < 3; i++) await api('GET', `/api/tienda/pedidos/${ficha.orderId}`, null, member);
await espera(1500);
const veces = lineasLog('Recibimos tu pago').filter(l => l.includes('#' + ficha.folio + ' ') || l.endsWith('#' + ficha.folio + '"')).length;
const compras = lineasLog('Tu compra en').filter(l => l.includes('#' + pagado.folio)).length;
ok(lineasLog('Recibimos tu pago').filter(l => l.includes('pedido #' + ficha.folio + '"')).length === 1, 'el aviso de pago Paynet salió una sola vez', veces);
ok(compras === 2, 'compra: un correo automático + el reenvío pedido', compras);
ok(lineasLog('La plantilla de correo').length === 0, 'las plantillas se arman sin errores', lineasLog('La plantilla de correo').slice(-1)[0]);

console.log('\n6. Compartir el comprobante (WhatsApp)');
r = await api('POST', `/api/recibos/pedidos/${pagado.orderId}/enlace`, null, member);
const enlace = r.body || {};
const token = (enlace.url || '').split('/compartidos/')[1]?.replace(/\.pdf$/, '') || '';
ok(r.status === 200 && /\/api\/recibos\/compartidos\/[A-Za-z0-9_-]{43}\.pdf$/.test(enlace.url || ''), 'el comprador genera el enlace', enlace.url?.slice(-20));
ok(enlace.texto?.startsWith('Comprobante del pedido #' + pagado.folio) && enlace.texto.includes(enlace.url) && enlace.texto.includes('vence'), 'mensaje listo para WhatsApp', enlace.texto?.slice(0, 60));
const dias = (new Date(enlace.venceEn) - Date.now()) / 86400000;
ok(dias > 6.99 && dias <= 7, 'vence a los 7 días', dias.toFixed(3));
b = await binario(`/api/recibos/compartidos/${token}.pdf`);
ok(b.status === 200 && b.tipo === 'application/pdf' && b.buf.subarray(0, 5).toString() === '%PDF-', 'el enlace baja el PDF sin sesión', { bytes: b.buf.length });
b = await binario('/api/recibos/compartidos/' + 'x'.repeat(43) + '.pdf');
ok(b.status === 404, 'un token inventado → 404');
r = await api('POST', `/api/recibos/pedidos/${pagado.orderId}/enlace`, null, ajeno.id);
ok(r.status === 404, 'otra persona no puede generar el enlace', r.status);
r = await api('POST', `/api/recibos/pedidos/${pagado.orderId}/enlace`, null, owner);
ok(r.status === 200, 'el dueño también lo genera (Ventas)');
r = await api('POST', `/api/recibos/pedidos/${vieja.orderId}/enlace`, null, member);
ok(r.status === 409, 'un pedido cancelado no tiene enlace', r.body?.error);
let m2 = await MongoClient.connect(MONGO);
const guardado = await m2.db().collection('enlaces_recibo').findOne({ orderId: pagado.orderId });
const enClaro = await m2.db().collection('enlaces_recibo').countDocuments({ _id: token });
ok(guardado && enClaro === 0 && /^[0-9a-f]{64}$/.test(guardado._id), 'en la base solo queda la huella del token', guardado?._id?.slice(0, 12));
await m2.db().collection('enlaces_recibo').updateOne({ _id: guardado._id }, { $set: { venceEn: new Date(Date.now() - 1000) } });
b = await binario(`/api/recibos/compartidos/${token}.pdf`);
ok(b.status === 404, 'un enlace vencido → 404');
// Ficha pendiente: el enlace baja la ficha; si se cancela, ya no hay nada que bajar.
c = (await api('POST', '/api/tienda/carrito/items', { varianteId: agua.id, cantidad: 1 }, member)).body;
const pendiente = (await api('POST', '/api/tienda/carrito/checkout', { metodo: 'paynet', datos: {}, totalVisto: c.total, consentimiento: CONSENTIMIENTO }, member)).body;
r = await api('POST', `/api/recibos/pedidos/${pendiente.orderId}/enlace`, null, member);
ok(r.status === 200 && r.body.tipo === 'ficha' && r.body.texto.startsWith('Ficha de pago del pedido #'), 'un pedido pendiente comparte su ficha', r.body?.tipo);
const tokenFicha = r.body.url.split('/compartidos/')[1].replace(/\.pdf$/, '');
b = await binario(`/api/recibos/compartidos/${tokenFicha}.pdf`);
ok(b.status === 200 && b.buf.subarray(0, 5).toString() === '%PDF-', 'el enlace de la ficha baja el PDF');
r = await api('POST', `/api/gyms/${gymId}/ventas/pedidos/${pendiente.orderId}/cancelar`, null, owner);
ok(r.status === 200, 'el dueño cancela el pedido pendiente', r.body?.estado);
b = await binario(`/api/recibos/compartidos/${tokenFicha}.pdf`);
ok(b.status === 410, 'el enlace de un pedido cancelado → 410');
await m2.close();

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
// Código de salida 1 si algo falló: así correr-todas.mjs y la CI lo marcan en rojo.
if (fallos) process.exitCode = 1;
