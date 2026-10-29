// Socios de Gym Maximus para probar el lector de acceso RFID: Chema, Valeria,
// Yael, Eduardo y Edwin. Se crean como en demo-datos.mjs: el dueño los da de
// alta por la API, se les pone la contraseña de demostración y un pago del
// plan mensual con fecha de hoy, así que entran con la membresía al corriente.
// Si un socio ya existe, se deja como está.
//
//   DEMO_API=https://gtweb.onrender.com node pruebas/socios-rfid.mjs --produccion
// Usa DEMO_MONGODB_URI y DEMO_PASSWORD del .env, igual que demo-datos.mjs.
import fs from 'node:fs';
import { MongoClient } from 'mongodb';

function leerEnv() {
  try {
    return Object.fromEntries(fs.readFileSync(new URL('../.env', import.meta.url), 'utf8')
      .split(/\r?\n/).filter(l => l.includes('=') && !l.trim().startsWith('#'))
      .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
  } catch {
    return {};
  }
}
const env = { ...leerEnv(), ...process.env };
const API = (env.DEMO_API || 'http://localhost:8080').replace(/\/$/, '');
const MONGO = env.DEMO_MONGODB_URI;
const CLAVE = env.DEMO_PASSWORD;
if (!MONGO || !CLAVE) {
  console.error('Faltan DEMO_MONGODB_URI y DEMO_PASSWORD en el .env.');
  process.exit(2);
}
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(API) && !process.argv.includes('--produccion')) {
  console.error(`${API} no es local: agrega --produccion si de verdad quieres crear los socios ahí.`);
  process.exit(2);
}

const DUENO = 'dueno.maximus@gymtrack.demo';
const SOCIOS = [
  { nombre: 'Chema',   email: 'chema.maximus@gymtrack.demo' },
  { nombre: 'Valeria', email: 'valeria.maximus@gymtrack.demo' },
  { nombre: 'Yael',    email: 'yael.maximus@gymtrack.demo' },
  { nombre: 'Eduardo', email: 'eduardo.maximus@gymtrack.demo' },
  { nombre: 'Edwin',   email: 'edwin.maximus@gymtrack.demo' },
];

async function api(metodo, ruta, cuerpo, token) {
  const r = await fetch(API + ruta, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined
  });
  let body = null; try { body = await r.json(); } catch {}
  if (r.status < 200 || r.status >= 300) throw new Error(`${metodo} ${ruta}: ${r.status} ${JSON.stringify(body)}`);
  return body;
}

const cliente = new MongoClient(MONGO);
try {
  await cliente.connect();
  const db = cliente.db();
  const users = db.collection('users');

  const dueno = await api('POST', '/api/users/login', { email: DUENO, password: CLAVE });
  const t = dueno.token;
  const duenoDb = await users.findOne({ email: DUENO });
  const gym = await db.collection('gyms').findOne({ ownerId: duenoDb._id.toString() });
  if (!gym) throw new Error('Gym Maximus no existe: corre primero demo-datos.mjs sembrar.');
  const gymId = gym._id.toString();
  const mensual = (await api('GET', `/api/gyms/${gymId}/planes`, null, t)).find(p => p.nombre === 'Mensual');
  console.log(`${gym.nombre} (${gymId})`);

  const hoy = new Date().toISOString().slice(0, 10);
  for (const s of SOCIOS) {
    if (await users.findOne({ email: s.email })) {
      console.log(`  ${s.nombre}: ya existe`);
      continue;
    }
    const m = await api('POST', `/api/gyms/${gymId}/members`, { nombre: s.nombre, email: s.email }, t);
    await users.updateOne({ email: s.email }, { $set: { password: duenoDb.password, contrasenaTemporal: false,
      emailVerificado: true, privacidadVersion: '1', privacidadAceptadaEn: new Date() } });
    if (mensual) {
      await api('POST', `/api/gyms/${gymId}/members/${m.id}/payments`, { monto: mensual.precioDesde ?? mensual.variantes?.[0]?.precio ?? gym.cuotaMensual,
        metodo: 'Efectivo', fechaPago: hoy, planId: mensual.id, nota: 'Socio para probar el lector RFID' }, t);
    }
    console.log(`  ${s.nombre}: creado (${s.email})`);
  }
  console.log('\nContraseña: la de DEMO_PASSWORD en el .env.');
} finally {
  await cliente.close();
}
