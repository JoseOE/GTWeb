// Prueba de Edwin: alta en recepción con contraseña temporal.
import fs from 'node:fs';
import { API, ESTADO } from './entorno.mjs';

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
const { gymId, owner } = n;
const correo = `recepcion-${Date.now().toString(36)}@prueba.test`;
const temporal = 'Kp7m-Rx4t-Zq9w';

console.log('\n1. Alta en recepción');
let r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: 'Alta Recepcion', email: correo, password: '123456' }, owner);
ok(r.status === 400 && r.body.error.includes('8+ caracteres'), 'una contraseña débil se rechaza', r.body && r.body.error);
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: 'Alta Recepcion', email: correo, password: temporal }, owner);
ok(r.status === 200 && r.body.contrasenaTemporal === true, 'con la contraseña generada se crea, marcada como temporal', r.body && r.body.contrasenaTemporal);
const id = r.body.id;
const lista = (await api('GET', `/api/gyms/${gymId}/members`, null, owner)).body;
ok(lista.find(m => m.id === id).contrasenaTemporal === true, 'el dueño la ve como temporal en Mis Usuarios');

console.log('\n2. El miembro entra y la cambia');
r = await api('POST', '/api/users/login', { email: correo, password: temporal });
ok(r.status === 200 && r.body.contrasenaTemporal === true, 'el login avisa que es temporal', r.body && r.body.contrasenaTemporal);
if (r.body.token) TOKENS[id] = r.body.token;
r = await api('GET', `/api/users/${id}/me`, null, id);
ok(r.status === 200 && r.body.contrasenaTemporal === true, '/me también lo dice (la tienda y la app muestran el aviso)');
r = await api('POST', '/api/cuenta/contrasena', { userId: id, actual: temporal, nueva: 'MiClave#2026' }, id);
ok(r.status === 200, 'cambia su contraseña', r.body && (r.body.message || r.body.error));
r = await api('GET', `/api/users/${id}/me`, null, id);
ok(r.body.contrasenaTemporal === false, 'ya no es temporal', r.body.contrasenaTemporal);
r = await api('POST', '/api/users/login', { email: correo, password: 'MiClave#2026' });
ok(r.status === 200 && r.body.contrasenaTemporal === false, 'entra con la nueva y sin aviso');

console.log('\n3. Las cuentas que no vienen de recepción');
r = await api('GET', `/api/users/${owner}/me`, null, owner);
ok(r.body.contrasenaTemporal === false, 'el dueño no tiene contraseña temporal');

await api('DELETE', `/api/gyms/${gymId}/members/${id}`, null, owner);
console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
