// Panel de administración del equipo de GymTrack (admin.html, /api/admin):
// quién entra, qué ve (gimnasios, cuentas, reseñas buenas y malas, bitácora) y
// ocultar o volver a mostrar una reseña u opinión. Contra Spring local, que debe
// arrancar con ADMIN_EMAIL y ADMIN_PASSWORD (en la CI, los de prueba de abajo).
import { createRequire } from 'node:module';
import { API, MONGO, leerLog } from './entorno.mjs';

const require = createRequire(import.meta.url);
const { MongoClient } = require('mongodb');

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@prueba.mx';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Admin#Prueba2026';

let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra).slice(0, 240) : '')); if (!cond) fallos++; };
const espera = ms => new Promise(r => setTimeout(r, ms));

async function api(metodo, ruta, cuerpo, token, extra = {}) {
  const headers = { 'Content-Type': 'application/json', ...extra };
  if (token) headers.Authorization = 'Bearer ' + token;
  const r = await fetch(API + ruta, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}

const sufijo = Date.now().toString(36);
async function duenoNuevo(nombre, extra = {}) {
  const correo = `adm-${nombre.split(' ')[0].toLowerCase()}-${sufijo}@prueba.mx`;
  await api('POST', '/api/users/register', { nombre, email: correo, password: 'Clave#2026', aceptaPrivacidad: true, privacidadVersion: '1', ...extra });
  await espera(400);
  const codigo = [...leerLog().matchAll(new RegExp(`verificación de ${correo.replace(/\./g, '\\.')} es (\\d{6})`, 'g'))].pop()?.[1];
  const v = await api('POST', '/api/cuenta/verificar', { email: correo, codigo });
  return { correo, ...v.body };
}

console.log('\n1. La cuenta de administrador');
let r = await api('POST', '/api/users/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
ok(r.status === 200 && r.body.role === 'admin' && r.body.token, 'entra con ADMIN_EMAIL y ADMIN_PASSWORD y su rol es admin', r.body?.error || r.body?.role);
const admin = r.body || {};
r = await api('POST', '/api/users/login', { email: ADMIN_EMAIL, password: 'Otra#Clave2026' });
ok(r.status === 401, 'con otra contraseña no entra → 401', r.status);
const colado = await duenoNuevo('Colado Admin', { role: 'admin' });
ok(colado.role === 'owner', 'registrarse pidiendo role "admin" da una cuenta de dueño normal', colado.role);

// Su contraseña es ADMIN_PASSWORD: ni se recupera por correo ni se cambia desde la página.
const correosAlAdmin = () => leerLog().split(/\r?\n/).filter(l => l.includes('CorreoService') && l.includes(ADMIN_EMAIL)).length;
const antes = correosAlAdmin();
r = await api('POST', '/api/cuenta/recuperar', { email: ADMIN_EMAIL });
await espera(500);
ok(r.status === 200 && correosAlAdmin() === antes, '"¿Olvidaste tu contraseña?" responde igual pero no le manda código al administrador', r.status);
r = await api('POST', '/api/cuenta/restablecer/codigo', { email: ADMIN_EMAIL, codigo: '000000', password: 'Nueva#Clave2026' });
ok(r.status === 400, 'tampoco se restablece con un código', r.status);
r = await api('POST', '/api/cuenta/contrasena', { userId: admin.id, actual: ADMIN_PASSWORD, nueva: 'Nueva#Clave2026' }, admin.token);
ok(r.status === 400 && /ADMIN_PASSWORD/.test(r.body?.error), 'ni se cambia desde Mi cuenta → 400', r.body?.error);
r = await api('POST', '/api/users/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
ok(r.status === 200, 'y la contraseña sigue siendo la de ADMIN_PASSWORD');

console.log('\n2. Solo el administrador ve /api/admin');
const a = await duenoNuevo('Andrea Torres Admin');
r = await api('POST', '/api/gyms', { nombre: 'Gym Admin ' + sufijo, cuotaMensual: 500 }, a.token);
const gymId = r.body?.id;
ok(r.status === 200 && gymId, 'un dueño crea su gimnasio');
const correoM = `adm-luis-${sufijo}@prueba.mx`;
r = await api('POST', `/api/gyms/${gymId}/members`, { nombre: 'Luis Méndez', email: correoM }, a.token);
const m = (await api('POST', '/api/users/login', { email: correoM, password: 'Luis Méndez' })).body;
ok(m?.token && m.role === 'member', 'y da de alta a un miembro');

for (const ruta of ['/api/admin/resumen', '/api/admin/gimnasios', '/api/admin/usuarios', '/api/admin/resenas', '/api/admin/bitacora']) {
  const sin = await api('GET', ruta);
  const dueno = await api('GET', ruta, null, a.token);
  const miembro = await api('GET', ruta, null, m.token);
  ok(sin.status === 401 && dueno.status === 403 && miembro.status === 403,
    `${ruta}: sin sesión 401, dueño y miembro 403`, [sin.status, dueno.status, miembro.status]);
}
r = await api('GET', '/api/admin/resumen', null, null, { 'X-User-Id': admin.id });
ok(r.status === 401, 'el X-User-Id del administrador sin su token no sirve → 401', r.status);
r = await api('GET', '/api/admin/resumen', null, a.token, { 'X-User-Id': admin.id });
ok(r.status === 403, 'un dueño que manda el X-User-Id del administrador → 403', r.status);
r = await api('POST', '/api/gyms', { nombre: 'Gym del admin', cuotaMensual: 1 }, admin.token);
ok(r.status >= 400 && r.status < 500, 'el administrador no puede crear gimnasios (no es dueño)', r.status);

console.log('\n3. Lo que ve');
r = await api('GET', '/api/admin/resumen', null, admin.token);
ok(r.status === 200 && r.body.gimnasios >= 1 && r.body.duenos >= 1 && r.body.miembros >= 1 && typeof r.body.ventasTotal === 'number'
  && r.body.resenas && 'malas' in r.body.resenas && r.body.opiniones && 'buenas' in r.body.opiniones,
  'resumen: gimnasios, dueños, miembros, ventas y reseñas', r.body);
r = await api('GET', '/api/admin/gimnasios', null, admin.token);
const g = (r.body || []).find(x => x.id === gymId);
ok(r.status === 200 && g && g.dueno?.email === a.correo && g.miembros === 1 && g.creado, 'gimnasios: con su dueño, sus miembros y su fecha de alta', g);
r = await api('GET', '/api/admin/usuarios', null, admin.token);
const cuentas = r.body || [];
ok(r.status === 200 && cuentas.some(u => u.email === a.correo && u.rol === 'owner') && cuentas.some(u => u.email === correoM && u.rol === 'member' && u.gimnasio === 'Gym Admin ' + sufijo),
  'cuentas: dueños y miembros, con su gimnasio');
ok(cuentas.length && cuentas.every(u => !('password' in u) && !('pushToken' in u) && !('token' in u)), 'ninguna cuenta trae su contraseña ni tokens');
ok(cuentas.find(u => u.email === ADMIN_EMAIL)?.ultimoAcceso, 'el último acceso sale de la bitácora');

console.log('\n4. Reseñas buenas y malas, y ocultarlas');
const textoBuena = 'Excelente control de membresías y de la tienda ' + sufijo;
const textoMala = 'La app se traba al pagar y nadie me responde ' + sufijo;
r = await api('PUT', '/api/opiniones/mia', { estrellas: 5, texto: textoBuena }, a.token);
ok(r.status === 200, 'el dueño opina con 5★');
r = await api('PUT', '/api/opiniones/mia', { estrellas: 1, texto: textoMala }, m.token);
ok(r.status === 200, 'el miembro opina con 1★');

// Una reseña de producto pide haberlo comprado: el pedido pagado se pone directo en la base.
r = await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'Proteína ' + sufijo, categoria: 'suplementos', activo: true, requiereAdvertencia: false,
  variantes: [{ presentacion: 'Única', precio: 500, controlarInventario: true, existencias: 20 }] }, a.token);
const productoId = r.body?.id;
const mongo = await MongoClient.connect(MONGO);
await mongo.db().collection('pedidos').insertOne({ orderId: 'adm-' + sufijo, userId: m.id, gymId, canal: 'web', estado: 'pagado', total: 500, subtotal: 431.03, iva: 68.97,
  partidas: [{ tipo: 'producto', productoId, cantidad: 1, precioUnitario: 500 }], creadoEn: new Date(), pagadoEn: new Date() });
await mongo.close();
const textoProducto = 'Llegó abierta y con sabor raro ' + sufijo;
r = await api('PUT', `/api/tienda/${gymId}/productos/${productoId}/resenas/mia`, { estrellas: 2, comentario: textoProducto }, m.token);
ok(r.status === 200, 'el miembro reseña el producto con 2★', r.body?.error);

r = await api('GET', '/api/admin/resenas', null, admin.token);
const todas = r.body || [];
const buena = todas.find(x => x.texto === textoBuena);
const mala = todas.find(x => x.texto === textoMala);
const resena = todas.find(x => x.texto === textoProducto);
ok(r.status === 200 && buena?.tipo === 'gimnasio' && buena.estrellas === 5 && buena.enCarrusel === true && buena.rolAutor === 'Dueño',
  've la opinión buena, marcada como "en la portada"', buena);
ok(mala?.tipo === 'gimnasio' && mala.estrellas === 1 && mala.enCarrusel === false && mala.email === correoM && mala.autor === 'Luis Méndez',
  've la opinión mala, con el nombre completo y el correo de quien la escribió', mala);
ok(resena?.tipo === 'producto' && resena.estrellas === 2 && resena.producto === 'Proteína ' + sufijo && resena.gimnasio === 'Gym Admin ' + sufijo,
  've la reseña de producto, con el producto y el gimnasio', resena);

r = await api('POST', `/api/admin/opiniones/${buena.id}/visibilidad`, { oculta: true }, a.token);
ok(r.status === 403, 'un dueño no puede usar la moderación → 403', r.status);
r = await api('POST', `/api/admin/opiniones/${buena.id}/visibilidad`, { oculta: 'si' }, admin.token);
ok(r.status === 400, 'oculta tiene que ser true o false → 400', r.status);
r = await api('POST', `/api/admin/opiniones/${buena.id}/visibilidad`, { oculta: true }, admin.token);
ok(r.status === 200 && r.body.oculta === true && r.body.enCarrusel === false, 'el administrador oculta la opinión buena', r.body);
r = await api('GET', '/api/opiniones/destacadas?limite=24');
ok(!(r.body || []).some(o => o.texto === textoBuena), 'oculta, ya no sale en el carrusel de la portada');
r = await api('GET', '/api/opiniones/mia', null, a.token);
ok(r.status === 200 && r.body.texto === textoBuena, 'su autor la sigue viendo en "mi opinión"');
r = await api('POST', `/api/admin/opiniones/${buena.id}/visibilidad`, { oculta: false }, admin.token);
ok(r.status === 200 && r.body.enCarrusel === true, 'al volver a mostrarla regresa a la portada');
r = await api('GET', '/api/opiniones/destacadas?limite=24');
ok((r.body || []).some(o => o.texto === textoBuena), 'y sale otra vez en el carrusel');

r = await api('POST', `/api/admin/resenas/${resena.id}/visibilidad`, { oculta: true }, admin.token);
ok(r.status === 200 && r.body.oculta === true, 'el administrador oculta la reseña de producto');
r = await api('GET', `/api/tienda/${gymId}/productos/${productoId}/resenas`, null, m.token);
ok(r.status === 200 && !r.body.resenas.some(x => x.comentario === textoProducto) && r.body.calificacion.total === 0,
  'oculta, ya no se ve en el producto ni cuenta en su promedio', r.body?.calificacion);
r = await api('GET', '/api/admin/resenas', null, admin.token);
ok((r.body || []).find(x => x.id === resena.id)?.oculta === true, 'en el panel sigue apareciendo, marcada como oculta');
r = await api('POST', '/api/admin/resenas/no-existe/visibilidad', { oculta: true }, admin.token);
ok(r.status === 404, 'una reseña que no existe → 404', r.status);

console.log('\n5. Bitácora');
await espera(600);
r = await api('GET', '/api/admin/bitacora?limite=1000', null, admin.token);
const eventos = r.body || [];
ok(r.status === 200 && eventos.some(e => e.tipo === 'LOGIN_EXITOSO' && e.email === ADMIN_EMAIL), 'anota el inicio de sesión del administrador');
ok(eventos.some(e => e.tipo === 'OPINION_MODERADA') && eventos.some(e => e.tipo === 'RESENA_MODERADA'), 'anota cada reseña u opinión que oculta o muestra');
ok(eventos.some(e => e.tipo === 'ACCESO_DENEGADO' && e.ruta?.startsWith('/api/admin/')), 'anota los intentos de entrar a /api/admin sin permiso');
ok(eventos.some(e => e.gimnasio === 'Gym Admin ' + sufijo), 'trae el nombre del gimnasio de cada evento');

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
