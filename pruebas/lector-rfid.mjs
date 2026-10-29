// Lector RFID de Gym Maximus, sin hardware.
//
//   node pruebas/lector-rfid.mjs preparar   crea el lector "Puerta principal" (si no
//       hay llave guardada) y liga los tags que ya leímos: la tarjeta a Chema, el
//       sticker 1 a Valeria y el sticker 2 a Eduardo. La llave del lector se
//       guarda en LECTOR_LLAVE del .env y en los secrets.h de lector_wifi y
//       xiao_camara (fuera de git), junto con la IP de esta PC; no se imprime.
//   node pruebas/lector-rfid.mjs probar     se hace pasar por la LILYGO y la XIAO:
//       entrada, pase repetido, salida, tag desconocido con foto, y el dueño
//       abriendo desde el panel mientras el lector espera el comando.
//   node pruebas/lector-rfid.mjs limpiar    borra los accesos, alertas y fotos de
//       Gym Maximus (antes de una demo); los tags y el lector se quedan.
//
// DEMO_API (por defecto http://localhost:8080) y DEMO_PASSWORD salen del .env.
import fs from 'node:fs';
import os from 'node:os';

const RUTA_ENV = new URL('../.env', import.meta.url);
const RUTAS_SECRETS = ['lector_wifi', 'xiao_camara'].map(d => new URL(`../hardware/acceso-rfid/${d}/secrets.h`, import.meta.url));

function leerEnv() {
  try {
    return Object.fromEntries(fs.readFileSync(RUTA_ENV, 'utf8')
      .split(/\r?\n/).filter(l => l.includes('=') && !l.trim().startsWith('#'))
      .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
  } catch {
    return {};
  }
}
const env = { ...leerEnv(), ...process.env };
const API = (env.DEMO_API || 'http://localhost:8080').replace(/\/$/, '');
const accion = process.argv[2];
if (!['preparar', 'probar', 'limpiar'].includes(accion) || !env.DEMO_PASSWORD) {
  console.error('Uso: node pruebas/lector-rfid.mjs preparar|probar|limpiar   (necesita DEMO_PASSWORD en el .env)');
  process.exit(2);
}

const DUENO = 'dueno.maximus@gymtrack.demo';
const TAGS = [
  { epc: 'E2004712C1A060265132010B', socio: 'chema.maximus@gymtrack.demo',   que: 'Tarjeta' },
  { epc: 'E28069150000502D9DF80D65', socio: 'valeria.maximus@gymtrack.demo', que: 'Sticker 1' },
  { epc: 'E2827802000000000DFFC5F9', socio: 'eduardo.maximus@gymtrack.demo', que: 'Sticker 2' },
];
// Un JPEG mínimo (1x1) para probar la subida de fotos.
const JPEG = Buffer.from('/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=', 'base64');

async function pedir(metodo, ruta, { cuerpo, token, llave, binario } = {}) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (llave) headers['X-Lector-Key'] = llave;
  if (cuerpo !== undefined) headers['Content-Type'] = binario ? 'image/jpeg' : 'application/json';
  const r = await fetch(API + ruta, { method: metodo, headers, body: cuerpo === undefined ? undefined : (binario ? cuerpo : JSON.stringify(cuerpo)) });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
function exigir(r, que) {
  if (r.status < 200 || r.status >= 300) throw new Error(`${que}: ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
}

async function entrarComoDueno() {
  const sesion = exigir(await pedir('POST', '/api/users/login', { cuerpo: { email: DUENO, password: env.DEMO_PASSWORD } }), 'entrar como dueño');
  const gymId = sesion.user?.gymId ?? sesion.gymId;
  if (!gymId) throw new Error('La sesión del dueño no trae gymId: ' + JSON.stringify(Object.keys(sesion)));
  return { token: sesion.token, gymId };
}

function guardarLlave(llave) {
  let texto = fs.existsSync(RUTA_ENV) ? fs.readFileSync(RUTA_ENV, 'utf8') : '';
  texto = /^LECTOR_LLAVE=.*$/m.test(texto) ? texto.replace(/^LECTOR_LLAVE=.*$/m, 'LECTOR_LLAVE=' + llave)
    : texto.replace(/\s*$/, '\n') + '\n# Llave del lector RFID de Gym Maximus (pruebas/lector-rfid.mjs)\nLECTOR_LLAVE=' + llave + '\n';
  fs.writeFileSync(RUTA_ENV, texto);

  // La LILYGO y la XIAO usan la misma llave; el servidor local es la IP de esta PC.
  // Primero la del WiFi: un puerto Ethernet desconectado puede seguir mostrando una IP vieja.
  const interfaces = Object.entries(os.networkInterfaces())
    .sort(([a], [b]) => /wi-?fi|wlan/i.test(b) - /wi-?fi|wlan/i.test(a))
    .flatMap(([, lista]) => lista);
  const ip = interfaces.find(i => i.family === 'IPv4' && !i.internal && !i.address.startsWith('169.254.'))?.address;
  let escritos = 0;
  for (const ruta of RUTAS_SECRETS) {
    if (!fs.existsSync(ruta)) continue;
    let h = fs.readFileSync(ruta, 'utf8').replace(/#define LECTOR_LLAVE\s+".*"/, `#define LECTOR_LLAVE    "${llave}"`);
    if (ip && /#define SERVIDOR\s+"http:\/\/192\.168\.1\.100:8080"/.test(h)) h = h.replace(/#define SERVIDOR\s+".*"/, `#define SERVIDOR        "http://${ip}:8080"`);
    fs.writeFileSync(ruta, h);
    escritos++;
  }
  return escritos > 0;
}

// Borra la bitácora, las alertas y las fotos del gimnasio (no los tags ni el lector).
async function limpiar() {
  const { MongoClient } = await import('mongodb');
  if (!env.DEMO_MONGODB_URI) throw new Error('Falta DEMO_MONGODB_URI en el .env.');
  const { gymId } = await entrarComoDueno();
  const cliente = new MongoClient(env.DEMO_MONGODB_URI);
  try {
    await cliente.connect();
    const db = cliente.db();
    for (const c of ['accesos', 'solicitudes_acceso', 'fotos_acceso']) {
      const r = await db.collection(c).deleteMany({ gymId });
      console.log(`  ${c}: ${r.deletedCount} borrados`);
    }
  } finally {
    await cliente.close();
  }
}

async function preparar() {
  const { token, gymId } = await entrarComoDueno();
  if (env.LECTOR_LLAVE) {
    const enSecrets = guardarLlave(env.LECTOR_LLAVE);
    console.log(`El lector ya tiene llave (LECTOR_LLAVE en el .env): no se crea otro${enSecrets ? '; se copió a los secrets.h' : ''}.`);
  } else {
    const lector = exigir(await pedir('POST', `/api/gyms/${gymId}/acceso/lectores`, { cuerpo: { nombre: 'Puerta principal' }, token }), 'crear lector');
    const enSecrets = guardarLlave(lector.llave);
    console.log(`Lector "${lector.nombre}" creado; su llave quedó en .env (LECTOR_LLAVE)${enSecrets ? ' y en los secrets.h de la LILYGO y la XIAO' : ''}.`);
  }
  const { socios } = exigir(await pedir('GET', `/api/gyms/${gymId}/acceso`, { token }), 'resumen');
  const miembros = exigir(await pedir('GET', `/api/gyms/${gymId}/members`, { token }), 'miembros');
  for (const t of TAGS) {
    const m = miembros.find(x => x.email === t.socio);
    if (!m) { console.log(`  ${t.que}: no encontré a ${t.socio} (corre socios-rfid.mjs)`); continue; }
    const r = exigir(await pedir('POST', `/api/gyms/${gymId}/acceso/tarjetas`, { cuerpo: { epc: t.epc, userId: m.id }, token }), 'ligar ' + t.que);
    console.log(`  ${t.que} ${t.epc} → ${r.nombre}`);
  }
  console.log(`\n${socios.length} socios en el gimnasio. Los demás tags se ligan desde el panel cuando aparezcan como "tag desconocido".`);
}

async function probar() {
  const llave = env.LECTOR_LLAVE;
  if (!llave) throw new Error('Primero corre "preparar".');
  const { token, gymId } = await entrarComoDueno();
  const pase = async (epc, que) => {
    const r = exigir(await pedir('POST', '/api/lectores/accesos', { cuerpo: { epc }, llave }), 'pase ' + que);
    console.log(`  ${que.padEnd(28)} → ${r.resultado}${r.nombre ? ' ' + r.nombre : ''}${r.motivo ? ' (' + r.motivo + ')' : ''}`);
    return r;
  };

  console.log('Llave equivocada:', (await pedir('POST', '/api/lectores/accesos', { cuerpo: { epc: TAGS[0].epc }, llave: 'no-es' })).status, '(debe ser 401)');
  console.log('Pases:');
  const primero = await pase(TAGS[0].epc, 'Tarjeta');
  await pase(TAGS[0].epc, 'Tarjeta otra vez');
  const desconocido = 'E2000000000000000000BEEF';
  const negado = await pase(desconocido, 'Tag desconocido');
  if (negado.resultado !== 'NEGADO' || !negado.solicitudId) throw new Error('El tag desconocido debía negarse con solicitud.');
  await pase(desconocido, 'Mismo tag (no repite alerta)');

  const foto = exigir(await pedir('POST', '/api/lectores/fotos', { cuerpo: JPEG, llave, binario: true }), 'subir foto');
  console.log(`Foto de la XIAO guardada en la solicitud: ${foto.solicitudId === negado.solicitudId ? 'sí' : 'NO'}`);

  // El lector espera el comando mientras el dueño abre desde el panel.
  const esperando = pedir('GET', '/api/lectores/comandos', { llave });
  await new Promise(r => setTimeout(r, 1500));
  exigir(await pedir('POST', `/api/gyms/${gymId}/acceso/solicitudes/${negado.solicitudId}/abrir`, { token }), 'abrir desde el panel');
  const comando = await esperando;
  console.log(`Comando que recibió el lector: ${comando.status} ${JSON.stringify(comando.body)}`);

  const resumen = exigir(await pedir('GET', `/api/gyms/${gymId}/acceso`, { token }), 'resumen');
  console.log(`Panel: ${resumen.tarjetas.length} tags, dentro: ${resumen.dentro.join(', ') || 'nadie'}, último acceso: ${resumen.accesos[0]?.tipo} ${resumen.accesos[0]?.nombre}`);
  if (primero.resultado === 'ENTRADA') {
    console.log('\nChema quedó "dentro": su siguiente pase (después de 10 s) será su SALIDA.');
  }
}

({ preparar, probar, limpiar })[accion]().catch(e => { console.error(e.message); process.exit(1); });
