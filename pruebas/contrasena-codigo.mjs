// Prueba de Valeria: cambiar la contraseña con el código que llega al correo
// (lo que usa la app en "¿Olvidaste tu contraseña?" y en Mi cuenta).
import { API, leerLog } from './entorno.mjs';

let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));
async function api(metodo, ruta, cuerpo, token) {
  const r = await fetch(API + ruta, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
async function codigoDe(correo) {
  await espera(500);
  return [...leerLog().matchAll(new RegExp(`restablecer la contraseña de ${correo.replace(/\./g, '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
}

const correo = `codigo-${Date.now().toString(36)}@prueba.test`;
console.log('\n1. Una cuenta de la app con dos sesiones abiertas');
let r = await api('POST', '/api/users/register', { nombre: 'Codigo Prueba', email: correo, password: 'Clave#2026', role: 'member' });
ok(r.status === 200 && !!r.body.token, 'se registra desde la app', r.status);
const id = r.body.id;
const telefono = r.body.token;
const otra = (await api('POST', '/api/users/login', { email: correo, password: 'Clave#2026' })).body.token;

console.log('\n2. Pide el código');
r = await api('POST', '/api/cuenta/recuperar', { email: correo });
ok(r.status === 200, 'el servidor responde lo mismo de siempre', r.body && r.body.message);
const codigo = await codigoDe(correo);
ok(/^\d{6}$/.test(codigo || ''), 'el correo trae un código de 6 dígitos', codigo);
r = await api('POST', '/api/cuenta/recuperar', { email: 'nadie-' + correo });
ok(r.status === 200, 'con un correo que no existe responde igual');

console.log('\n3. Lo usa');
r = await api('POST', '/api/cuenta/restablecer/codigo', { email: correo, codigo, password: 'corta' });
ok(r.status === 400 && r.body.error.includes('8 caracteres'), 'una contraseña débil se rechaza sin gastar intentos', r.body && r.body.error);
const malo = codigo === '000000' ? '111111' : '000000';
r = await api('POST', '/api/cuenta/restablecer/codigo', { email: correo, codigo: malo, password: 'Nueva#2026' });
ok(r.status === 400 && r.body.error.includes('Te quedan 4 intentos'), 'un código equivocado cuenta un intento', r.body && r.body.error);
r = await api('POST', '/api/cuenta/restablecer/codigo', { email: 'nadie-' + correo, codigo, password: 'Nueva#2026' });
ok(r.status === 400 && r.body.error.includes('incorrecto o ya venció'), 'con otro correo no sirve', r.body && r.body.error);
r = await api('POST', '/api/cuenta/restablecer/codigo', { email: correo, codigo, password: 'Nueva#2026' }, telefono);
ok(r.status === 200, 'con el código correcto cambia la contraseña', r.body && (r.body.message || r.body.error));

console.log('\n4. Las sesiones');
r = await api('GET', `/api/users/${id}/me`, null, telefono);
ok(r.status === 200, 'la sesión del teléfono que la cambió sigue abierta', r.status);
r = await api('GET', `/api/users/${id}/me`, null, otra);
ok(r.status === 401 && r.body.sesionVencida === true, 'las demás sesiones se cierran', r.body);
r = await api('POST', '/api/users/login', { email: correo, password: 'Clave#2026' });
ok(r.status === 401, 'la contraseña anterior ya no sirve');
r = await api('POST', '/api/users/login', { email: correo, password: 'Nueva#2026' });
ok(r.status === 200 && r.body.contrasenaTemporal === false, 'entra con la nueva');
r = await api('POST', '/api/cuenta/restablecer/codigo', { email: correo, codigo, password: 'Otra#2026x' });
ok(r.status === 400, 'el mismo código no se puede usar dos veces');

console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
