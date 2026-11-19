// "Simular entrada" desde Mis Usuarios (prueba mientras se termina la puerta):
// con la membresía vencida se niega y se avisa para renovar; al pagar desde la
// app la puerta se abre sola; con la membresía al día entra o sale y el lector
// recibe "abrir" para mover el servo.
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { API, ESTADO, MONGO, CONSENTIMIENTO, leerLog } from './entorno.mjs';

const { MongoClient, ObjectId } = createRequire(import.meta.url)('mongodb');
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
const simular = (userId, quien = owner) => api('POST', `/api/gyms/${gymId}/acceso/simular`, { userId }, quien);

console.log('\n1. Preparar: un lector conectado y el socio con la membresía vencida');
let r = await api('POST', `/api/gyms/${gymId}/acceso/lectores`, { nombre: 'Puerta simulada' }, owner);
ok(r.status === 200 && r.body.llave, 'el dueño da de alta un lector');
const llave = { 'X-Lector-Key': r.body.llave };
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 204, 'el lector se conecta (sin comandos)', r.status);
r = await api('GET', `/api/gyms/${gymId}/acceso/simulacion`, null, owner);
ok(r.status === 200 && r.body.activa === true && r.body.lector?.nombre === 'Puerta simulada' && r.body.lector.enLinea === true,
  'la simulación está activa y usará el lector conectado', r.body);
r = await api('GET', `/api/gyms/${gymId}/acceso/simulacion`, null, member);
ok(r.status === 403, 'un socio no la ve', r.status);

const mongo = await MongoClient.connect(MONGO);
const db = mongo.db();
const filtro = ObjectId.isValid(member) ? { _id: new ObjectId(member) } : { _id: member };
const socio = await db.collection('users').findOne(filtro);
// Que empiece fuera del gimnasio y con la membresía vencida.
await db.collection('accesos').deleteMany({ userId: member });
await db.collection('users').updateOne(filtro, { $set: { membershipActive: false, membershipStatus: 'inactive', fechaProximoPago: new Date('2026-01-01T12:00:00Z') } });

console.log('\n2. Membresía vencida: se niega y se avisa para renovar');
r = await simular(member);
ok(r.status === 200 && r.body.simulado === true && r.body.resultado === 'NEGADO' && r.body.motivo === 'Membresía vencida',
  'la entrada simulada se niega', r.body);
ok(r.body.puertaAbierta === false && r.body.alertaRepetida === false, 'no abre y la alerta es nueva');
ok(r.body.avisoRenovar === Boolean(socio.pushToken), 'dice si le llegó el push (solo con la app instalada)', r.body.avisoRenovar);
const solicitudId = r.body.solicitudId;
await espera(1200);
ok(leerLog().split('\n').some(l => l.includes('aviso de renovación a ' + socio.email)), 'se manda el aviso de renovación (como con la tarjeta)');
r = await simular(member);
ok(r.body.resultado === 'NEGADO' && r.body.alertaRepetida === true && r.body.solicitudId === solicitudId, 'otra vez: misma alerta, sin repetir el aviso');
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 204, 'negado: el lector no recibe "abrir"', r.status);
r = await api('GET', `/api/gyms/${gymId}/acceso`, null, owner);
ok(r.body.simulacion === true && r.body.solicitudes.some(s => s.id === solicitudId), 'la alerta aparece en Accesos RFID');

console.log('\n3. Paga desde la app y la puerta se abre sola');
r = await api('GET', '/api/tienda/renovacion', null, member);
ok(r.body?.puerta?.solicitudId === solicitudId && r.body.puerta.estado === 'PENDIENTE', 'la app ve la alerta de la puerta', r.body?.puerta);
r = await api('POST', '/api/tienda/renovacion?canal=app', {}, member);
const carrito = r.body?.carrito;
const tok = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Socio Prueba', mes: '12', anio: '30', cvc: '123' }, member)).body.token;
r = await api('POST', '/api/tienda/carrito/checkout?canal=app', { metodo: 'stripe', datos: { token: tok }, totalVisto: carrito?.total, consentimiento: CONSENTIMIENTO }, member);
ok(r.status === 200 && r.body.estado === 'pagado', 'paga la renovación', r.body?.folio ?? r.body?.error);
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 200 && r.body.comando === 'abrir' && r.body.solicitudId === solicitudId, 'el lector recibe "abrir" de la alerta', r.body);

console.log('\n4. Al día: simular otra vez abre la puerta');
r = await simular(member);
ok(r.body.resultado === 'IGNORADO', 'recién entró: un pase inmediato se ignora', r.body?.resultado);
await espera(10500);
r = await simular(member);
ok(r.body.resultado === 'SALIDA' && r.body.puertaAbierta === true, 'ya estaba dentro: cuenta como salida y abre', r.body);
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 200 && r.body.comando === 'abrir' && r.body.solicitudId === null && r.body.nombre === socio.nombre, 'el lector recibe "abrir" de la simulación', r.body);
r = await api('GET', '/api/lectores/comandos?espera=0', null, null, llave);
ok(r.status === 204, 'y solo una vez', r.status);
await espera(10500);
r = await simular(member);
ok(r.body.resultado === 'ENTRADA' && r.body.puertaAbierta === true && r.body.nombre === socio.nombre, 'entra con la membresía al día', r.body);
r = await api('GET', '/api/lectores/comandos?espera=5', null, null, llave);
ok(r.status === 200 && r.body.comando === 'abrir', 'el lector que esperaba comandos recibe "abrir"', r.body?.comando);
r = await api('GET', `/api/gyms/${gymId}/acceso`, null, owner);
ok(r.body.accesos.some(a => a.tipo === 'ENTRADA' && a.motivo === 'Simulada desde el panel'), 'el historial dice que fue simulada');

console.log('\n5. Permisos');
r = await simular(member, member);
ok(r.status === 403, 'un socio no puede simular', r.status);
r = await simular('000000000000000000000000');
ok(r.status === 404, 'alguien que no es socio del gimnasio → 404', r.body?.error);
r = await simular(member, null);
ok(r.status === 401, 'sin sesión → 401', r.status);

await mongo.close();
console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
