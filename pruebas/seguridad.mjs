// Prueba de la sesión con token y de los permisos (SesionFilter) contra Spring local.
// Necesita el log de Spring para leer los códigos de verificación (sin correo configurado).
import fs from 'node:fs';
import { API, LOG } from './entorno.mjs';

let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));

async function api(metodo, ruta, cuerpo, { token, userId } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (userId) headers['X-User-Id'] = userId;
  const r = await fetch(API + ruta, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}

const sufijo = Date.now().toString(36);
async function duenoNuevo(nombre) {
  const correo = `seg-${nombre}-${sufijo}@prueba.mx`;
  await api('POST', '/api/users/register', { nombre: 'Dueño ' + nombre, email: correo, password: 'Clave#2026' });
  await espera(400);
  const codigo = [...fs.readFileSync(LOG, 'latin1').matchAll(new RegExp(`verificación de ${correo.replace(/\./g, '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
  const v = await api('POST', '/api/cuenta/verificar', { email: correo, codigo });
  return { correo, ...v.body };
}

console.log('\n1. Registro: solo nombre, correo y contraseña');
let r = await api('POST', '/api/users/register', {
  nombre: 'Colado', email: `colado-${sufijo}@prueba.mx`, password: 'Clave#2026', role: 'member',
  fechaProximoPago: '2099-12-31', diaDePago: 31, planActual: 'Anual VIP', membershipActive: true, gymId: 'cualquiera', emailVerificado: true
});
ok(r.status === 200 && r.body.fechaProximoPago === null && r.body.planActual === null && r.body.diaDePago === null && r.body.gymId === null,
  'los campos de cobranza del cliente se ignoran', { fecha: r.body.fechaProximoPago, plan: r.body.planActual, gym: r.body.gymId });
ok(typeof r.body.token === 'string' && r.body.token.length >= 40, 'el miembro que se registra en la app recibe su token');
const colado = r.body;

console.log('\n2. Verificar el correo abre la sesión');
const a = await duenoNuevo('a');
ok(typeof a.token === 'string' && a.role === 'owner' && !!a.id, 'verificar devuelve token, id y rol', { role: a.role });
r = await api('POST', '/api/gyms', { nombre: 'Gimnasio A', cuotaMensual: 400, ownerUserId: a.id }, { token: a.token });
const gymA = r.body.id; ok(r.status === 200 && !!gymA, 'el dueño A registra su gimnasio con su token');
const b = await duenoNuevo('b');
r = await api('POST', '/api/gyms', { nombre: 'Gimnasio B', cuotaMensual: 500 }, { token: b.token });
const gymB = r.body.id; ok(r.status === 200 && !!gymB, 'el dueño B registra el suyo (sin mandar ownerUserId)');

console.log('\n3. El panel exige sesión');
r = await api('GET', `/api/gyms/${gymA}/members`);
ok(r.status === 401 && r.body.sesionVencida === true, 'miembros sin nada → 401', r.body);
r = await api('GET', `/api/gyms/${gymA}/members`, null, { userId: a.id });
ok(r.status === 401, 'miembros con el X-User-Id del dueño pero sin token → 401 (ya no basta el encabezado)', r.body.error);
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: a.token });
ok(r.status === 200 && Array.isArray(r.body), 'el dueño A ve sus miembros con su token');
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: b.token });
ok(r.status === 403, 'el dueño B no ve los miembros de A → 403', r.body.error);
r = await api('POST', `/api/gyms/${gymA}/members`, { nombre: 'Intruso', email: `intruso-${sufijo}@prueba.mx`, password: 'Clave#2026' }, { token: b.token });
ok(r.status === 403, 'B no da de alta miembros en A → 403');
r = await api('POST', `/api/gyms/${gymA}/codigo`, null, { token: b.token });
ok(r.status === 403, 'B no regenera el código de A → 403');
r = await api('POST', `/api/gyms/${gymA}/members`, { nombre: 'Socio A', email: `socio-${sufijo}@prueba.mx`, password: 'Clave#2026' }, { token: a.token });
const socio = r.body; ok(r.status === 200, 'A da de alta a un socio');
r = await api('POST', `/api/gyms/${gymA}/members/${socio.id}/payments`, { monto: 1, metodo: 'Efectivo' }, { token: b.token });
ok(r.status === 403, 'B no registra pagos en A (no se regalan membresías) → 403');
r = await api('POST', `/api/gyms/${gymA}/members/${socio.id}/payments`, { monto: 1, metodo: 'Efectivo' }, { userId: a.id });
ok(r.status === 401, 'registrar un pago solo con el X-User-Id → 401');
r = await api('POST', `/api/gyms/${gymA}/routines`, { nombre: 'Rutina colada' }, { token: b.token });
ok(r.status === 403, 'B no crea rutinas en A → 403');

console.log('\n4. La sesión manda sobre el encabezado');
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: a.token, userId: b.id });
ok(r.status === 403 && /no corresponde/.test(r.body.error), 'token de A con X-User-Id de B → 403', r.body.error);
r = await api('POST', '/api/gyms', { nombre: 'Robado', ownerUserId: a.id }, { token: b.token });
ok(r.status === 403, 'B no puede reescribir el gimnasio de A mandando su id → 403', r.body.error);
r = await api('POST', '/api/gyms', { nombre: 'Gym del colado' }, { token: colado.token });
ok(r.status === 403, 'un miembro no registra gimnasios → 403', r.body.error);

console.log('\n5. Datos públicos del gimnasio');
r = await api('GET', `/api/gyms/${gymA}`);
ok(r.status === 200 && r.body.nombre === 'Gimnasio A' && !('codigo' in r.body) && !('ownerId' in r.body) && !('tienda' in r.body), 'sin sesión: sin código, dueño ni tienda', Object.keys(r.body));
r = await api('GET', `/api/gyms/${gymA}`, null, { token: b.token });
ok(!('codigo' in r.body), 'otro dueño tampoco ve el código');
r = await api('GET', `/api/gyms/${gymA}`, null, { token: a.token });
ok(typeof r.body.codigo === 'string' && r.body.ownerId === a.id, 'el dueño sí ve su código');

console.log('\n6. Cuentas: cada quien la suya');
r = await api('GET', `/api/users/${a.id}/me`, null, { token: b.token });
ok(r.status === 403, 'B no lee la cuenta de A con su token → 403');
r = await api('GET', `/api/users/${a.id}/me`, null, { token: a.token });
ok(r.status === 200 && r.body.id === a.id, 'A lee su cuenta');
r = await api('PUT', `/api/users/${a.id}/push-token`, { pushToken: 'ExponentPushToken[robado]' }, { token: b.token });
ok(r.status === 403, 'B no cambia el token de notificaciones de A → 403');
r = await api('POST', '/api/cuenta/contrasena', { userId: a.id, actual: 'Clave#2026', nueva: 'Otra#2026x' }, { token: b.token });
ok(r.status === 403, 'B no cambia la contraseña de A aunque sepa la actual → 403', r.body.error);
r = await api('GET', `/api/users/${a.id}/me`);
ok(r.status === 200, 'transición: sin token, la app todavía lee /me (se cierra con EXIGIR_TOKEN=true)');

console.log('\n7. Rutas que ya no existen');
const antes = (await api('GET', '/api/servicios')).body;
r = await api('GET', '/api/servicios/seed');
const despues = (await api('GET', '/api/servicios')).body;
ok(Array.isArray(antes) && antes.length > 0 && JSON.stringify(antes.map(s => s.id)) === JSON.stringify(despues.map(s => s.id)), 'GET /api/servicios/seed ya no borra el catálogo', { antes: antes.length, despues: despues.length });
r = await api('POST', '/api/billing/run', null, { token: a.token });
ok(r.status === 404 || r.status === 405, 'POST /api/billing/run ya no existe', r.status);

console.log('\n8. Cerrar y cambiar contraseña');
const l1 = (await api('POST', '/api/users/login', { email: a.correo, password: 'Clave#2026' })).body.token;
const l2 = (await api('POST', '/api/users/login', { email: a.correo, password: 'Clave#2026' })).body.token;
r = await api('POST', '/api/users/logout', null, { token: l2 });
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: l2 });
ok(r.status === 401 && r.body.sesionVencida === true, 'el token cerrado ya no sirve → 401 sesionVencida');
r = await api('POST', '/api/cuenta/contrasena', { userId: a.id, actual: 'Clave#2026', nueva: 'Nueva#2026' }, { token: l1 });
ok(r.status === 200, 'A cambia su contraseña con la sesión l1', r.body.error);
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: a.token });
ok(r.status === 401, 'las demás sesiones de A se cerraron');
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: l1 });
ok(r.status === 200, 'la sesión con la que cambió sigue abierta');
r = await api('POST', '/api/users/login', { email: a.correo, password: 'Nueva#2026' });
ok(r.status === 200 && !!r.body.token, 'entra con la contraseña nueva');
r = await api('POST', '/api/users/login', { email: a.correo, password: 'Clave#2026' }, { token: 'token-falso' });
ok(r.status === 401 && !r.body.sesionVencida, 'un token falso no estorba en el login: solo responde credenciales incorrectas');
r = await api('GET', `/api/gyms/${gymA}/members`, null, { token: 'token-falso' });
ok(r.status === 401 && r.body.sesionVencida === true, 'token falso en el panel → 401 sesionVencida');

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
