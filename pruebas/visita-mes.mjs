// Prueba del panel del dueño (Valeria): historial de pagos del gimnasio y la
// regla "Visita / Semana + mes": el mes empieza al terminar lo pagado.
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
const { gymId, owner, member } = n;

console.log('\n1. Historial de pagos del gimnasio');
let r = await api('GET', `/api/gyms/${gymId}/payments?limite=5`, null, owner);
ok(r.status === 200 && Array.isArray(r.body) && r.body.length <= 5, 'el dueño recibe los últimos 5', r.body && r.body.length);
ok(r.body.every((p, i, a) => i === 0 || String(a[i - 1].fechaPago) >= String(p.fechaPago)), 'vienen del más reciente al más viejo');
ok(r.body.every(p => p.nombre && 'cubreHasta' in p && 'orderId' in p), 'cada pago trae el nombre del miembro, el corte y si vino de un pedido');
r = await api('GET', `/api/gyms/${gymId}/payments`, null, member);
ok(r.status === 403, 'un miembro no puede verlo → 403', r.status);
r = await fetch(`${API}/api/gyms/${gymId}/payments`);
ok(r.status === 401, 'sin sesión → 401', r.status);
r = await api('GET', `/api/gyms/${gymId}/payments?desde=2026-10-01&limite=500`, null, owner);
ok(r.status === 200 && r.body.every(p => p.fechaPago >= '2026-10-01'), 'desde=2026-10-01 → solo pagos de octubre', r.body.length);
r = await api('GET', `/api/gyms/${gymId}/payments?desde=1-oct`, null, owner);
ok(r.status === 400 && r.body.error.includes('AAAA-MM-DD'), 'fecha mal escrita → 400', r.body && r.body.error);

console.log('\n2. Planes para la prueba');
let planes = (await api('GET', `/api/gyms/${gymId}/planes`, null, owner)).body;
let semana = planes.find(p => p.duracionUnidad === 'semana' && p.duracionCantidad === 1 && !p.pagoUnico);
if (!semana) {
  r = await api('POST', `/api/gyms/${gymId}/planes`, { nombre: 'Semana', precio: 160, pagoUnico: false, duracionUnidad: 'semana', duracionCantidad: 1, beneficios: ['7 días de acceso'], destacado: false, activo: true }, owner);
  ok(r.status === 200, 'se crea el plan Semana', r.body && r.body.error);
  planes = (await api('GET', `/api/gyms/${gymId}/planes`, null, owner)).body;
  semana = planes.find(p => p.duracionUnidad === 'semana' && p.duracionCantidad === 1 && !p.pagoUnico);
}
const mensual = planes.find(p => p.duracionUnidad === 'mes' && p.duracionCantidad === 1 && !p.pagoUnico);
ok(!!semana && !!mensual, 'hay plan Semana y plan Mensual', [semana && semana.nombre, mensual && mensual.nombre]);

const sufijo = Date.now().toString(36);
const creados = [];
async function nuevoMiembro(nombre) {
  const res = await api('POST', `/api/gyms/${gymId}/members`, { nombre, email: `${nombre.toLowerCase().replace(/\s+/g, '.')}.${sufijo}@prueba.test`, password: 'Prueba#2026x' }, owner);
  const id = res.body && (res.body.id || (res.body.user && res.body.user.id));
  creados.push(id);
  return id;
}
const pagar = (userId, plan, fecha) => api('POST', `/api/gyms/${gymId}/members/${userId}/payments`, { monto: plan.precio, metodo: 'Efectivo', fechaPago: fecha, planId: plan.id }, owner);
async function estado(userId) {
  const lista = (await api('GET', `/api/gyms/${gymId}/members`, null, owner)).body;
  const m = lista.find(x => x.id === userId);
  return { dia: m.diaDePago, corte: m.fechaProximoPago };
}

console.log('\n3. Semana y luego mes: el mes empieza al terminar la semana');
let a = await nuevoMiembro('Semana Luego Mes');
ok(!!a, 'miembro A creado');
r = await pagar(a, semana, '2026-10-01');
ok(r.status === 200 && r.body.fechaProximoPago === '2026-10-08', 'semana el 1 de octubre → cubre hasta el 8', r.body.fechaProximoPago);
let e = await estado(a);
ok(e.dia == null, 'la semana no fija el día de pago', e.dia);
r = await pagar(a, mensual, '2026-10-03');
e = await estado(a);
ok(e.corte === '2026-11-08' && e.dia === 8, 'el mes pagado el 3 corre del 8 de octubre al 8 de noviembre y el día de pago queda en 8', e);

console.log('\n4. Ya pagaba mensual, compra una semana y vuelve a pagar el mes');
let b = await nuevoMiembro('Mensual Con Semana');
await pagar(b, mensual, '2026-09-03');
e = await estado(b);
ok(e.corte === '2026-10-03' && e.dia === 3, 'mensual el 3 de septiembre → corte el 3, día 3', e);
await pagar(b, semana, '2026-10-01');
e = await estado(b);
ok(e.corte === '2026-10-10' && e.dia === 3, 'la semana se suma al corte vigente (3 → 10 de octubre) y el día no cambia', e);
await pagar(b, mensual, '2026-10-05');
e = await estado(b);
ok(e.corte === '2026-11-10' && e.dia === 10, 'el mes empieza el 10 (al terminar la semana) y el día de pago pasa a 10', e);

console.log('\n5. Lo que no cambia');
let c = await nuevoMiembro('Paga Tarde');
await pagar(c, mensual, '2026-08-20');
await pagar(c, mensual, '2026-09-25');
e = await estado(c);
ok(e.corte === '2026-10-20' && e.dia === 20, 'pagar tarde no mueve el día de pago (sigue en 20)', e);
let d = await nuevoMiembro('Paga Los 31');
await pagar(d, mensual, '2026-01-31');
e = await estado(d);
ok(e.corte === '2026-02-28' && e.dia === 31, 'paga los 31: en febrero el corte cae el 28', e);
await pagar(d, mensual, '2026-02-20');
e = await estado(d);
ok(e.corte === '2026-03-31' && e.dia === 31, 'pagar antes con corte el 28 de febrero no lo cambia a 28: sigue en 31', e);

console.log('\n6. Limpieza');
for (const id of creados.filter(Boolean)) await api('DELETE', `/api/gyms/${gymId}/members/${id}`, null, owner);
const restantes = (await api('GET', `/api/gyms/${gymId}/members`, null, owner)).body.filter(m => creados.includes(m.id));
ok(restantes.length === 0, 'los miembros de prueba se quitaron del gimnasio', restantes.length);

console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
