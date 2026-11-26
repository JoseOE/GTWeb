// Suscripción de cada gimnasio a GymTrack: estado, cobro proporcional con
// tarjeta y PayPal (simuladores), panel en solo lectura al vencer, precios y
// cortesía desde el panel del equipo.
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { API, ESTADO, MONGO } from './entorno.mjs';

const { MongoClient, ObjectId } = createRequire(import.meta.url)('mongodb');
const n = JSON.parse(fs.readFileSync(ESTADO, 'utf8'));
const TOKENS = n.tokens || {};
let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
// quien: el id de una cuenta de bloque1, o {id, token} (el administrador).
async function api(metodo, ruta, cuerpo, quien) {
  const id = quien && typeof quien === 'object' ? quien.id : quien;
  const token = quien && typeof quien === 'object' ? quien.token : TOKENS[quien];
  const headers = { 'Content-Type': 'application/json', ...(id ? { 'X-User-Id': id } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) };
  const r = await fetch(API + ruta, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
const { gymId, owner, member } = n;
const ruta = '/api/gyms/' + gymId + '/suscripcion';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@prueba.mx';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Admin#Prueba2026';

// Hoy en México y las cuentas que hace el servidor.
const hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Mexico_City' });
const fecha = iso => new Date(iso + 'T12:00:00Z');
const masDias = (iso, d) => { const f = fecha(iso); f.setUTCDate(f.getUTCDate() + d); return f.toISOString().slice(0, 10); };
const diasEntre = (a, b) => Math.round((fecha(b) - fecha(a)) / 86400000);
const diasDelAnio = y => (y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0)) ? 366 : 365;
function anualEsperado(desde, precio) {
  let y = Number(desde.slice(0, 4));
  let fin = y + '-12-31';
  if (diasEntre(desde, fin) + 1 < 31) fin = (y + 1) + '-12-31';
  let monto = 0;
  for (let inicio = desde; inicio <= fin; inicio = (Number(inicio.slice(0, 4)) + 1) + '-01-01') {
    const finTramo = inicio.slice(0, 4) + '-12-31';
    monto += precio * (diasEntre(inicio, finTramo) + 1) / diasDelAnio(Number(inicio.slice(0, 4)));
  }
  return { hasta: fin, monto: Math.round(monto * 100) / 100 };
}

const mongo = await MongoClient.connect(MONGO);
const db = mongo.db();
const filtroGym = ObjectId.isValid(gymId) ? { _id: new ObjectId(gymId) } : { _id: gymId };
const ponerHasta = iso => db.collection('gyms').updateOne(filtroGym, { $set: { suscripcionHasta: fecha(iso), suscripcionPlan: 'anual' } });

// Empieza como un gimnasio recién creado (así se puede correr más de una vez).
await db.collection('gyms').updateOne(filtroGym, { $unset: { suscripcionHasta: '', suscripcionPlan: '', suscripcionAvisos: '' } });
await db.collection('pagos_suscripcion').deleteMany({ gymId });
await db.collection('configuracion').deleteOne({ _id: 'suscripcion' });

console.log('\n1. Un gimnasio nuevo empieza sin pagar, con días de gracia');
let r = await api('GET', ruta, null, owner);
ok(r.status === 200 && r.body.estado === 'GRACIA' && r.body.nuncaPagada === true && r.body.soloLectura === false,
  'el gimnasio de las pruebas está en gracia y sin pagar', r.body && { estado: r.body.estado, nuncaPagada: r.body.nuncaPagada });
ok(r.body.precios?.anual === 4990 && r.body.precios?.mensual === 499 && r.body.precios?.diasGracia === 7,
  'precios de arranque: $4,990 al año, $499 al mes y 7 días de gracia', r.body.precios);
const esperado = anualEsperado(hoy, 4990);
ok(r.body.cotizaciones?.anual?.desde === hoy && r.body.cotizaciones.anual.hasta === esperado.hasta && r.body.cotizaciones.anual.monto === esperado.monto,
  'el anual se cobra proporcional desde hoy hasta el 31 de diciembre', r.body.cotizaciones?.anual);
ok(r.body.cotizaciones?.mensual?.hasta?.slice(8) >= '28', 'el mensual cubre hasta fin de mes', r.body.cotizaciones?.mensual);
r = await api('GET', ruta, null, member);
ok(r.status === 403, 'un socio no ve la suscripción del gimnasio', r.status);

console.log('\n2. Vencida y sin gracia: el panel queda en solo lectura');
await ponerHasta(masDias(hoy, -30));
r = await api('GET', ruta, null, owner);
ok(r.body.estado === 'VENCIDA' && r.body.soloLectura === true, 'vencida hace 30 días → solo lectura', r.body.estado);
r = await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'Producto de prueba suscripción' }, owner);
ok(r.status === 402 && r.body?.suscripcionVencida === true, 'crear un producto se rechaza con 402', r.body);
r = await api('GET', `/api/gyms/${gymId}/productos`, null, owner);
ok(r.status === 200, 'ver los productos sí se puede', r.status);
r = await api('GET', `/api/tienda/${gymId}/productos`, null, member);
ok(r.status === 200, 'la tienda de los socios sigue funcionando', r.status);
await ponerHasta(masDias(hoy, -3));
r = await api('GET', ruta, null, owner);
ok(r.body.estado === 'GRACIA' && r.body.soloLectura === false, 'vencida hace 3 días → en gracia, todavía sin bloqueo', r.body.estado);
await ponerHasta(masDias(hoy, -30));

console.log('\n3. Paga el plan anual con tarjeta');
const tok = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Dueño Prueba', mes: '12', anio: '30', cvc: '123' }, owner)).body.token;
r = await api('POST', ruta + '/pagar', { plan: 'anual', metodo: 'tarjeta', datos: { token: tok } }, owner);
ok(r.status === 200 && r.body.pago?.monto === esperado.monto && r.body.hasta === esperado.hasta && r.body.plan === 'anual',
  'cobra lo proporcional y queda pagada hasta el 31 de diciembre', r.body?.error || r.body?.pago);
ok(['AL_DIA', 'POR_VENCER'].includes(r.body.estado) && r.body.soloLectura === false, 'el panel vuelve a funcionar', r.body.estado);
ok(r.body.pago?.detalle?.ultimos4 === '4242' && !JSON.stringify(r.body.pago).includes('4242424242424242'),
  'el pago guarda solo los últimos 4 de la tarjeta', r.body.pago?.detalle);
r = await api('POST', `/api/gyms/${gymId}/productos`, { nombre: '' }, owner);
ok(r.status !== 402, 'ya no responde "solo lectura" al hacer cambios', r.status);
r = await api('POST', ruta + '/pagar', { plan: 'anual', metodo: 'tarjeta', datos: { token: tok } }, owner);
ok(r.status === 400 || r.status === 409, 'la misma tarjeta (token) no se cobra dos veces', r.body?.error);

console.log('\n4. Paga un mes más con PayPal (empieza al día siguiente de lo pagado)');
const regreso = API + '/bienvenido.html?suscripcion=paypal&plan=mensual';
r = await api('POST', ruta + '/paypal/orden', { plan: 'mensual', returnUrl: regreso, cancelUrl: API + '/bienvenido.html?suscripcion=cancelado' }, owner);
ok(r.status === 200 && r.body.id && r.body.monto === 499, 'la orden de PayPal es por un mes completo ($499)', r.body);
const aprobada = (await api('POST', `/api/simuladores/paypal/ordenes/${r.body.id}/aprobar`, { cuenta: 'ana.compradora@sim-paypal.test' })).body;
const payerId = new URL(aprobada.redirect).searchParams.get('PayerID');
const desdeMes = masDias(esperado.hasta, 1);
r = await api('POST', ruta + '/pagar', { plan: 'mensual', metodo: 'paypal', datos: { token: r.body.id, payerId } }, owner);
ok(r.status === 200 && r.body.pago?.desde === desdeMes && r.body.pago.monto === 499 && r.body.plan === 'mensual',
  'cubre el mes siguiente completo', r.body?.error || r.body?.pago);
r = await api('POST', ruta + '/stripe/sesion', { plan: 'mensual', returnUrl: regreso, cancelUrl: regreso }, owner);
ok(r.status === 409, 'con más de 45 días pagados todavía no se puede renovar', r.body?.error);
r = await api('GET', ruta, null, owner);
ok(r.body.pagos?.length === 2 && r.body.puedeRenovar === false, 'el dueño ve sus 2 pagos', r.body.pagos?.length);

console.log('\n5. Panel del equipo: precios, cortesía y permisos');
const login = await api('POST', '/api/users/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
const admin = { id: login.body?.id, token: login.body?.token };
r = await api('GET', '/api/admin/suscripciones', null, admin);
const fila = r.body?.gimnasios?.find(g => g.id === gymId);
ok(r.status === 200 && fila && fila.plan === 'mensual' && r.body.pagos.some(p => p.gymId === gymId) && r.body.cobradoAnio >= esperado.monto,
  'el equipo ve el gimnasio, su plan, sus pagos y lo cobrado', fila && { plan: fila.plan, estado: fila.estado });
r = await api('GET', '/api/admin/suscripciones', null, owner);
ok(r.status === 403, 'un dueño no ve las suscripciones de todos', r.status);
r = await api('PUT', '/api/admin/suscripciones/precios', { anual: 0, mensual: 499, diasGracia: 7 }, admin);
ok(r.status === 400, 'un precio en $0 se rechaza', r.body?.error);
r = await api('PUT', '/api/admin/suscripciones/precios', { anual: 5990, mensual: 599, diasGracia: 10 }, admin);
ok(r.status === 200 && r.body.anual === 5990, 'el equipo cambia los precios', r.body);
r = await api('GET', ruta, null, owner);
ok(r.body.precios.anual === 5990 && r.body.precios.diasGracia === 10, 'el dueño ve los precios nuevos', r.body.precios);
r = await api('PUT', '/api/admin/suscripciones/precios', { anual: 5990, mensual: 599, diasGracia: 10 }, owner);
ok(r.status === 403, 'un dueño no cambia los precios', r.status);
await api('PUT', '/api/admin/suscripciones/precios', { anual: 4990, mensual: 499, diasGracia: 7 }, admin);
const cortesia = masDias(hoy, 200);
r = await api('POST', `/api/admin/suscripciones/${gymId}/cortesia`, { hasta: cortesia }, admin);
ok(r.status === 200 && r.body.hasta === cortesia && r.body.plan === 'cortesia', 'el equipo da cortesía hasta una fecha', r.body);
r = await api('POST', `/api/admin/suscripciones/${gymId}/cortesia`, { hasta: masDias(hoy, 10) }, admin);
ok(r.status === 409, 'una cortesía más corta que lo pagado se rechaza', r.body?.error);
r = await api('POST', `/api/admin/suscripciones/${gymId}/cortesia`, { hasta: 'mañana' }, admin);
ok(r.status === 400, 'una fecha que no es fecha se rechaza', r.body?.error);

await mongo.close();
console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
