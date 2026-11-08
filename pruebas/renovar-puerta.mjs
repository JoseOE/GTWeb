// Renovar la membresía desde la puerta: el lector niega por membresía vencida,
// se avisa al socio, la app prepara la renovación, paga y la puerta se abre sola.
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { API, ESTADO, MONGO, CONSENTIMIENTO, leerLog } from './entorno.mjs';

const { MongoClient } = createRequire(import.meta.url)('mongodb');
const n = JSON.parse(fs.readFileSync(ESTADO, 'utf8'));
const TOKENS = n.tokens || {};
const cabeceras = userId => userId ? { 'X-User-Id': userId, ...(TOKENS[userId] ? { Authorization: 'Bearer ' + TOKENS[userId] } : {}) } : {};
let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));
async function api(metodo, ruta, cuerpo, userId, extra = {}) {
  const r = await fetch(API + ruta, { method: metodo, headers: { 'Content-Type': 'application/json', ...cabeceras(userId), ...extra }, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
const { gymId, owner, member } = n;

console.log('\n1. La puerta niega por membresía vencida');
let r = await api('POST', `/api/gyms/${gymId}/acceso/lectores`, { nombre: 'Puerta de prueba' }, owner);
ok(r.status === 200 && r.body.llave, 'el dueño da de alta un lector');
const llave = { 'X-Lector-Key': r.body.llave };
const epc = 'E2' + Array.from({ length: 22 }, () => '0123456789ABCDEF'[Math.floor(Math.random() * 16)]).join('');
r = await api('POST', `/api/gyms/${gymId}/acceso/tarjetas`, { epc, userId: member }, owner);
ok(r.status === 200, 'liga una tarjeta al socio', r.body?.nombre);
const mongo = await MongoClient.connect(MONGO);
const users = mongo.db().collection('users');
const { ObjectId } = createRequire(import.meta.url)('mongodb');
const filtro = ObjectId.isValid(member) ? { _id: new ObjectId(member) } : { _id: member };
const antes = await users.findOne(filtro);
await users.updateOne(filtro, { $set: { membershipStatus: 'inactive', membershipActive: false } });
r = await api('POST', '/api/lectores/accesos', { epc }, null, llave);
ok(r.status === 200 && r.body.resultado === 'NEGADO' && r.body.motivo === 'Membresía vencida', 'la tarjeta se niega', r.body);
const solicitudId = r.body.solicitudId;
await espera(1500);
ok(leerLog().split('\n').some(l => l.includes('aviso de renovación a ' + antes.email)), 'se avisa al socio para renovar (push)');
r = await api('POST', '/api/lectores/accesos', { epc }, null, llave);
await espera(800);
ok(r.body.solicitudId === solicitudId && leerLog().split('\n').filter(l => l.includes('aviso de renovación a ' + antes.email)).length === 1,
  'si vuelve a pasar la tarjeta, no le llega otro aviso');

console.log('\n2. La app abre la renovación');
r = await api('GET', '/api/tienda/renovacion', null, member);
ok(r.status === 200 && r.body.vencida === true, 'la membresía aparece vencida', r.body?.vencida);
ok(r.body.puerta?.solicitudId === solicitudId && r.body.puerta.estado === 'PENDIENTE', 'la alerta de la puerta sigue esperando', r.body.puerta?.estado);
ok(r.body.plan?.varianteId && r.body.plan.precio > 0 && r.body.planes.length >= 1, 'trae un plan sugerido y los planes para elegir', r.body.plan?.nombre);
r = await api('GET', '/api/tienda/renovacion', null, owner);
ok(r.status === 403, 'el dueño no renueva desde aquí', r.status);
r = await api('POST', '/api/tienda/renovacion?canal=app', { planId: 'prod_no_existe' }, member);
ok(r.status === 404, 'un plan que no existe → 404', r.body?.error);
await api('POST', '/api/tienda/carrito/items?canal=app', { varianteId: (await api('GET', `/api/tienda/${gymId}/productos`, null, member)).body.find(p => p.tipo !== 'membresia')?.variantes[0].id, cantidad: 1 }, member);
r = await api('POST', '/api/tienda/renovacion?canal=app', {}, member);
const carrito = r.body?.carrito;
ok(r.status === 200 && carrito?.items.length === 1 && carrito.items[0].esPlan && carrito.total === r.body.plan.precio,
  'deja el carrito de la app con el plan solo', carrito?.items.map(i => i.titulo));

console.log('\n3. Paga y la puerta se abre sola');
const tok = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Socio Prueba', mes: '12', anio: '30', cvc: '123' }, member)).body.token;
r = await api('POST', '/api/tienda/carrito/checkout?canal=app', { metodo: 'stripe', datos: { token: tok }, totalVisto: carrito.total, consentimiento: CONSENTIMIENTO }, member);
ok(r.status === 200 && r.body.estado === 'pagado', 'pago de la renovación', r.body?.folio ?? r.body?.error);
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 200 && r.body.comando === 'abrir' && r.body.solicitudId === solicitudId, 'el lector recibe "abrir"', r.body);
r = await api('GET', '/api/tienda/renovacion', null, member);
ok(r.body.vencida === false && r.body.puerta?.estado === 'APROBADA' && r.body.puerta.abiertaPorRenovacion === true, 'la app ve la membresía al día y la puerta abierta', r.body.puerta);
r = await api('GET', `/api/gyms/${gymId}/acceso`, null, owner);
ok(JSON.stringify(r.body).includes('Abierta al renovar desde la app'), 'el historial del panel dice que se abrió al renovar');

console.log('\n4. Sin alerta pendiente no se abre nada');
r = await api('POST', '/api/tienda/renovacion?canal=app', {}, member);
const tok2 = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Socio Prueba', mes: '12', anio: '30', cvc: '123' }, member)).body.token;
r = await api('POST', '/api/tienda/carrito/checkout?canal=app', { metodo: 'stripe', datos: { token: tok2 }, totalVisto: r.body.carrito.total, consentimiento: CONSENTIMIENTO }, member);
ok(r.status === 200, 'otra renovación, ya sin estar en la puerta');
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 204, 'el lector no recibe otro "abrir"', r.status);

await mongo.close();
console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
