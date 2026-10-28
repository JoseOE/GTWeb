// Opiniones sobre GymTrack (carrusel de la página de inicio), foto del
// gimnasio, lo más vendido de la tienda y las cuentas de demostración (que no
// reciben correo). Contra Spring local; lee los códigos en su log.
import { API, leerLog } from './entorno.mjs';

let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra).slice(0, 240) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));

async function api(metodo, ruta, cuerpo, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const r = await fetch(API + ruta, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}

const sufijo = Date.now().toString(36);
async function duenoNuevo(nombre) {
  const correo = `op-${nombre}-${sufijo}@prueba.mx`;
  await api('POST', '/api/users/register', { nombre, email: correo, password: 'Clave#2026', aceptaPrivacidad: true, privacidadVersion: '1' });
  await espera(400);
  const codigo = [...leerLog().matchAll(new RegExp(`verificación de ${correo.replace(/\./g, '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
  const v = await api('POST', '/api/cuenta/verificar', { email: correo, codigo });
  return { correo, ...v.body };
}

console.log('\n1. Opiniones');
const a = await duenoNuevo('Mariana López Pérez');
const sinGym = await duenoNuevo('Pedro Sin Gym');
let r = await api('POST', '/api/gyms', { nombre: 'Gym Opiniones ' + sufijo, cuotaMensual: 450 }, a.token);
const gymId = r.body.id;
ok(r.status === 200 && gymId, 'el dueño crea su gimnasio');

r = await api('PUT', '/api/opiniones/mia', { estrellas: 5, texto: 'Me encanta' });
ok(r.status === 401, 'sin sesión no se opina → 401', r.status);
r = await api('PUT', '/api/opiniones/mia', { estrellas: 5, texto: 'Excelente plataforma para mi gimnasio.' }, sinGym.token);
ok(r.status === 400 && /gimnasio/.test(r.body.error), 'sin gimnasio no se opina → 400', r.body?.error);
r = await api('PUT', '/api/opiniones/mia', { estrellas: 6, texto: 'Excelente plataforma para mi gimnasio.' }, a.token);
ok(r.status === 400 && /1 a 5/.test(r.body.error), 'estrellas fuera de 1 a 5 → 400', r.body?.error);
r = await api('PUT', '/api/opiniones/mia', { estrellas: 4.5, texto: 'Excelente plataforma para mi gimnasio.' }, a.token);
ok(r.status === 400, 'estrellas no enteras → 400', r.body?.error);
r = await api('PUT', '/api/opiniones/mia', { estrellas: 5, texto: 'Corto' }, a.token);
ok(r.status === 400 && /caracteres/.test(r.body.error), 'texto muy corto → 400', r.body?.error);
const textoA = 'Llevo las membresías y la tienda en un solo lugar ' + sufijo;
r = await api('PUT', '/api/opiniones/mia', { estrellas: 5, texto: '  ' + textoA + '  ' }, a.token);
ok(r.status === 200 && r.body.estrellas === 5 && r.body.texto === textoA && r.body.autor === 'Mariana L.' && r.body.rol === 'Dueño',
  'el dueño publica su opinión (nombre corto, sin espacios de más)', r.body);
r = await api('GET', '/api/opiniones/mia', null, a.token);
ok(r.status === 200 && r.body.texto === textoA, 'la ve en "mi opinión"');

// Un miembro del gimnasio, aceptado, con 3 estrellas.
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: 'Luis Ramírez', email: `op-luis-${sufijo}@prueba.mx` }, a.token);
ok(r.status === 200 && r.body.id, 'el dueño da de alta a un miembro', r.body?.error);
const m = (await api('POST', '/api/users/login', { email: `op-luis-${sufijo}@prueba.mx`, password: 'Luis Ramírez' })).body;
const textoM = 'Buena app, aunque le faltan cosas todavía ' + sufijo;
r = await api('PUT', '/api/opiniones/mia', { estrellas: 3, texto: textoM }, m.token);
ok(r.status === 200 && r.body.rol === 'Miembro', 'el miembro también opina', r.body?.error || r.body?.rol);

r = await api('GET', '/api/opiniones/destacadas?limite=24');
const mias = (r.body || []).filter(o => o.gimnasio?.nombre === 'Gym Opiniones ' + sufijo);
ok(r.status === 200 && mias.some(o => o.texto === textoA), 'la de 5★ sale en el carrusel (sin sesión)', mias.length);
ok(!mias.some(o => o.texto === textoM), 'la de 3★ no sale');
ok((r.body || []).every(o => o.estrellas >= 4 && !('email' in o) && !('userId' in o) && !('id' in o)), 'solo 4★ o más y sin datos de la cuenta');

r = await api('PUT', '/api/opiniones/mia', { estrellas: 4, texto: textoM + ' (actualizada)' }, m.token);
r = await api('GET', '/api/opiniones/destacadas?limite=24');
ok((r.body || []).some(o => o.texto === textoM + ' (actualizada)' && o.autor === 'Luis R.'), 'al subirla a 4★ ya sale, y sigue siendo una sola');
r = await api('DELETE', '/api/opiniones/mia', null, m.token);
ok(r.status === 200, 'el miembro borra su opinión');
r = await api('GET', '/api/opiniones/destacadas?limite=24');
ok(!(r.body || []).some(o => (o.texto || '').startsWith(textoM)), 'borrada, ya no sale');

console.log('\n2. Foto del gimnasio');
r = await api('POST', '/api/gyms', { nombre: 'Gym Opiniones ' + sufijo, cuotaMensual: 450, foto: 'https://otro-sitio.example/foto.jpg' }, a.token);
ok(r.status === 400, 'una foto de otro sitio → 400', r.body?.error);
const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
const subida = (await api('POST', `/api/gyms/${gymId}/imagenes`, { dataUrl: png }, a.token)).body;
r = await api('POST', '/api/gyms', { nombre: 'Gym Opiniones ' + sufijo, cuotaMensual: 450, foto: subida.url }, a.token);
ok(r.status === 200 && r.body.foto === '/api/imagenes/' + subida.id, 'la foto subida se guarda como ruta de GymTrack', r.body?.foto);
r = await api('POST', '/api/gyms', { nombre: 'Gym Opiniones ' + sufijo, cuotaMensual: 450 }, a.token);
ok(r.status === 200 && r.body.foto === '/api/imagenes/' + subida.id, 'guardar sin foto no la borra');
r = await api('GET', '/api/opiniones/destacadas?limite=24');
ok((r.body || []).some(o => o.texto === textoA && o.gimnasio.foto === '/api/imagenes/' + subida.id), 'la opinión sale con la foto del gimnasio');

console.log('\n3. Lo más vendido');
const crear = (nombre, precio) => api('POST', `/api/gyms/${gymId}/productos`, { nombre, categoria: 'suplementos', activo: true, requiereAdvertencia: false,
  variantes: [{ presentacion: 'Única', precio, controlarInventario: true, existencias: 50 }] }, a.token);
await crear('Creatina', 120);
await crear('Barra de proteína', 35);
await crear('Shaker', 90);
const cat = (await api('GET', `/api/tienda/${gymId}/productos`, null, m.token)).body;
const v = n => cat.find(p => p.nombre === n).variantes[0];
r = await api('GET', `/api/tienda/${gymId}/productos/mas-vendidos`, null, m.token);
ok(r.status === 200 && Array.isArray(r.body) && r.body.length === 0, 'sin ventas, la lista está vacía');
const vender = (n, cantidad) => api('POST', `/api/gyms/${gymId}/mostrador/cobrar`, { partidas: [{ varianteId: v(n).id, cantidad }], metodo: 'efectivo', recibido: 9999, totalVisto: v(n).precio * cantidad }, a.token);
await vender('Barra de proteína', 5);
await vender('Creatina', 2);
await vender('Barra de proteína', 1);
r = await api('GET', `/api/tienda/${gymId}/productos/mas-vendidos?limite=2`, null, m.token);
ok(r.status === 200 && r.body.length === 2 && r.body[0].nombre === 'Barra de proteína' && r.body[0].vendidos === 6 && r.body[1].nombre === 'Creatina' && r.body[1].vendidos === 2,
  'ordenados por piezas vendidas, con "vendidos" y el límite', r.body?.map(p => p.nombre + ':' + p.vendidos));
ok(r.body[0].variantes?.length && r.body[0].id, 'cada uno con la forma de la lista (id y variantes)');
r = await api('GET', `/api/tienda/${gymId}/productos/mas-vendidos`, null, sinGym.token);
ok(r.status === 403 || r.status === 404, 'alguien de fuera del gimnasio no lo ve', r.status);

console.log('\n4. Cuentas de demostración');
const demo = `demo-${sufijo}@gymtrack.demo`;
await api('POST', '/api/users/register', { nombre: 'Cuenta Demo', email: demo, password: 'Clave#2026', aceptaPrivacidad: true, privacidadVersion: '1' });
await espera(500);
ok(leerLog().includes('Cuenta de demostración: no se envió') && leerLog().includes(demo), 'a una cuenta @gymtrack.demo no se le manda correo (solo se anota)');

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
