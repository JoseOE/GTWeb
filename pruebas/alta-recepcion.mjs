// Prueba de Edwin: alta en recepción con correo y nombre de usuario. El nombre
// de usuario es la contraseña temporal hasta que el miembro la cambia.
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
const usuario = 'JuanPerez' + Date.now().toString(36).slice(-3);

console.log('\n1. Alta en recepción');
let r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: 'Jo', email: correo }, owner);
ok(r.status === 400 && r.body.error.includes('de 3 a 40'), 'un nombre de usuario muy corto se rechaza', r.body && r.body.error);
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: usuario, email: 'no-es-correo' }, owner);
ok(r.status === 400 && r.body.error.includes('correo válido'), 'un correo mal escrito se rechaza', r.body && r.body.error);
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: '  ' + usuario + '  ', email: correo }, owner);
ok(r.status === 200 && r.body.contrasenaTemporal === true, 'con correo y nombre de usuario se crea, marcada como temporal', r.body && (r.body.error || r.body.contrasenaTemporal));
ok(r.body.nombre === usuario, 'el nombre de usuario se guarda sin espacios a los lados', r.body.nombre);
const id = r.body.id;
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: usuario, email: correo }, owner);
ok(r.status === 400 && r.body.error.includes('ya está registrado'), 'el mismo correo no se da de alta dos veces');
const lista = (await api('GET', `/api/gyms/${gymId}/members`, null, owner)).body;
ok(lista.find(m => m.id === id).contrasenaTemporal === true, 'el dueño la ve como temporal en Mis Usuarios');

console.log('\n2. El miembro entra con su nombre de usuario y lo cambia');
r = await api('POST', '/api/users/login', { email: correo, password: usuario.toLowerCase() });
ok(r.status === 401, 'el nombre de usuario respeta mayúsculas');
r = await api('POST', '/api/users/login', { email: correo, password: usuario });
ok(r.status === 200 && r.body.contrasenaTemporal === true, 'entra con su correo y su nombre de usuario; el login avisa que es temporal', r.body && r.body.contrasenaTemporal);
if (r.body.token) TOKENS[id] = r.body.token;
r = await api('GET', `/api/users/${id}/me`, null, id);
ok(r.status === 200 && r.body.contrasenaTemporal === true, '/me también lo dice (la tienda y la app piden cambiarla)');
r = await api('POST', '/api/cuenta/contrasena', { userId: id, actual: usuario, nueva: 'MiClave#2026' }, id);
ok(r.status === 200, 'la cambia desde Mi cuenta de la página', r.body && (r.body.message || r.body.error));
r = await api('GET', `/api/users/${id}/me`, null, id);
ok(r.body.contrasenaTemporal === false, 'ya no es temporal', r.body.contrasenaTemporal);
r = await api('POST', '/api/users/login', { email: correo, password: usuario });
ok(r.status === 401, 'el nombre de usuario ya no sirve como contraseña');
r = await api('POST', '/api/users/login', { email: correo, password: 'MiClave#2026' });
ok(r.status === 200 && r.body.contrasenaTemporal === false, 'entra con la nueva y sin aviso');

console.log('\n3. Las cuentas que no vienen de recepción');
r = await api('GET', `/api/users/${owner}/me`, null, owner);
ok(r.body.contrasenaTemporal === false, 'el dueño no tiene contraseña temporal');

await api('DELETE', `/api/gyms/${gymId}/members/${id}`, null, owner);
console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
