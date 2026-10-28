// Controles técnicos de ISO 27001 contra Spring local: cabeceras de seguridad
// (A.13.1, A.14.1) y bitácora de seguridad (A.12.4). Lee los códigos de
// verificación en el log de Spring, como seguridad.mjs.
import { API, leerLog } from './entorno.mjs';

let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra).slice(0, 240) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));

async function api(metodo, ruta, cuerpo, { token, encabezados } = {}) {
  const headers = { 'Content-Type': 'application/json', ...(encabezados || {}) };
  if (token) headers.Authorization = 'Bearer ' + token;
  const r = await fetch(API + ruta, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body, headers: r.headers };
}

const sufijo = Date.now().toString(36);
async function duenoNuevo(nombre) {
  const correo = `iso-${nombre}-${sufijo}@prueba.mx`;
  await api('POST', '/api/users/register', { nombre: 'Dueño ' + nombre, email: correo, password: 'Clave#2026', aceptaPrivacidad: true, privacidadVersion: '1' });
  await espera(400);
  const codigo = [...leerLog().matchAll(new RegExp(`verificación de ${correo.replace(/\./g, '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
  const v = await api('POST', '/api/cuenta/verificar', { email: correo, codigo });
  return { correo, ...v.body };
}

console.log('\n1. Cabeceras de seguridad');
let r = await fetch(API + '/index.html');
const csp = r.headers.get('content-security-policy') || '';
ok(csp.includes("frame-ancestors 'none'") && csp.includes("object-src 'none'") && csp.includes("default-src 'self'"), 'las páginas llevan Content-Security-Policy', csp.slice(0, 80));
ok(r.headers.get('x-content-type-options') === 'nosniff', 'X-Content-Type-Options: nosniff');
ok(r.headers.get('x-frame-options') === 'DENY', 'X-Frame-Options: DENY (contra clickjacking)');
ok(r.headers.get('referrer-policy') === 'strict-origin-when-cross-origin', 'Referrer-Policy');
const permisos = r.headers.get('permissions-policy') || '';
ok(/camera=\(self\)/.test(permisos) && /geolocation=\(self\)/.test(permisos) && /microphone=\(\)/.test(permisos),
  'Permissions-Policy: cámara y ubicación solo en este sitio, micrófono en ninguno', permisos);
ok(!r.headers.get('strict-transport-security'), 'sin HTTPS no se manda HSTS');
r = await fetch(API + '/index.html', { headers: { 'X-Forwarded-Proto': 'https' } });
ok(/max-age=31536000/.test(r.headers.get('strict-transport-security') || ''), 'detrás del proxy HTTPS (Render) sí se manda HSTS', r.headers.get('strict-transport-security'));
r = await api('GET', '/api/pagos/modo');
ok(r.headers.get('cache-control') === 'no-store' && r.headers.get('x-frame-options') === 'DENY', 'la API: no-store y las mismas cabeceras', r.headers.get('cache-control'));
r = await api('POST', '/api/users/login', { email: 'nadie@prueba.mx', password: 'x' });
ok(r.headers.get('content-security-policy') && r.headers.get('x-content-type-options') === 'nosniff', 'también en las respuestas de error');

console.log('\n2. Bitácora: inicios de sesión');
const a = await duenoNuevo('a');
const b = await duenoNuevo('b');
r = await api('POST', '/api/users/login', { email: a.correo, password: 'Incorrecta#1' });
ok(r.status === 401, 'contraseña incorrecta → 401');
r = await api('POST', '/api/users/login', { email: a.correo, password: 'Clave#2026' });
ok(r.status === 200 && r.body.token, 'inicio de sesión correcto');
const tokenA = r.body.token;
r = await api('POST', '/api/gyms', { nombre: 'Gimnasio ISO', cuotaMensual: 400 }, { token: tokenA });
const gymA = r.body.id;
ok(r.status === 200 && gymA, 'el dueño A crea su gimnasio');
r = await api('POST', `/api/gyms/${gymA}/codigo`, null, { token: tokenA });
ok(r.status === 200, 'el dueño A regenera el código de invitación');
r = await api('GET', `/api/gyms/${gymA}/bitacora`, null, { token: b.token });
ok(r.status === 403, 'el dueño B no ve la bitácora de A → 403', r.body?.error);
r = await api('POST', `/api/gyms/${gymA}/codigo`, null, { token: b.token });
ok(r.status === 403, 'el dueño B no cambia el código de A → 403', r.body?.error);
r = await api('GET', `/api/gyms/${gymA}/bitacora`);
ok(r.status === 401, 'sin sesión → 401', r.body?.error);
await espera(800);   // la bitácora se escribe en segundo plano

r = await api('GET', `/api/gyms/${gymA}/bitacora?limite=50`, null, { token: tokenA });
ok(r.status === 200 && Array.isArray(r.body), 'el dueño A ve su bitácora', r.body?.length);
const ev = r.body || [];
const de = tipo => ev.filter(e => e.tipo === tipo);
ok(de('LOGIN_FALLIDO').some(e => e.propio && e.email === a.correo && e.ip), 'anota la contraseña incorrecta, con correo e IP', de('LOGIN_FALLIDO')[0]);
ok(de('LOGIN_EXITOSO').some(e => e.propio && e.agente), 'anota el inicio de sesión, con el dispositivo');
ok(de('GIMNASIO_CREADO').length === 1 && de('CODIGO_GIMNASIO').length === 1, 'anota lo que cambió en el panel', ev.map(e => e.tipo));
const negados = de('ACCESO_DENEGADO');
ok(negados.length >= 2 && negados.every(e => !e.propio && e.email === null && e.ruta.startsWith(`/api/gyms/${gymA}/`)),
  'anota los intentos de B sobre su gimnasio, sin el correo de B', negados.map(e => e.metodo + ' ' + e.ruta));
ok(!ev.some(e => e.tipo.startsWith('LOGIN') && !e.propio), 'no ve los inicios de sesión de otras cuentas');
ok(ev.every(e => !('password' in e) && !JSON.stringify(e).includes('Clave#2026')), 'nunca guarda contraseñas');
ok(ev.length > 1 && new Date(ev[0].fecha) >= new Date(ev[ev.length - 1].fecha), 'lo más reciente primero');

console.log('\n3. Freno por intentos');
for (let i = 0; i < 10; i++) await api('POST', '/api/users/login', { email: b.correo, password: 'Mala#' + i });
r = await api('POST', '/api/users/login', { email: b.correo, password: 'Clave#2026' });
ok(r.status === 429, 'después de 10 intentos fallidos se frena → 429', r.body?.error);
await espera(800);
r = await api('GET', `/api/gyms/${gymA}/bitacora`, null, { token: tokenA });
ok(!r.body.some(e => e.tipo === 'LOGIN_BLOQUEADO'), 'el freno de B no aparece en la bitácora de A');

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
