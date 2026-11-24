// Deja vencida la membresía de algunos socios de Gym Maximus para probar el
// lector RFID y la renovación (LED rojo, aviso en la app, pagar y volver a
// pasar la tarjeta). Hace lo mismo que la revisión diaria de las 6:00: la
// fecha de pago queda en el pasado y el estado pasa a "inactive". Para
// reactivarlos basta con que paguen (app, tienda o "Registrar pago").
//
//   node pruebas/vencer-socios.mjs                   → solo muestra cómo están
//   node pruebas/vencer-socios.mjs --vencer          → los deja vencidos
// Usa DEMO_MONGODB_URI del .env, igual que demo-datos.mjs.
import fs from 'node:fs';
import { MongoClient } from 'mongodb';

const SOCIOS = ['edwin.maximus@gymtrack.demo', 'eduardo.maximus@gymtrack.demo', 'yael.maximus@gymtrack.demo', 'valeria.maximus@gymtrack.demo'];
// Venció hace unos días.
const DIAS_VENCIDA = 4;

function leerEnv() {
  try {
    return Object.fromEntries(fs.readFileSync(new URL('../.env', import.meta.url), 'utf8')
      .split(/\r?\n/).filter(l => l.includes('=') && !l.trim().startsWith('#'))
      .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
  } catch {
    return {};
  }
}
const MONGO = { ...leerEnv(), ...process.env }.DEMO_MONGODB_URI;
if (!MONGO) {
  console.error('Falta DEMO_MONGODB_URI en el .env.');
  process.exit(2);
}

const cliente = await MongoClient.connect(MONGO);
const users = cliente.db().collection('users');
const mostrar = async () => {
  for (const email of SOCIOS) {
    const u = await users.findOne({ email });
    if (!u) { console.log(`  ${email}: no existe`); continue; }
    const fecha = u.fechaProximoPago instanceof Date ? u.fechaProximoPago.toISOString() : u.fechaProximoPago;
    console.log(`  ${u.nombre.padEnd(22)} estado=${u.membershipStatus} activa=${u.membershipActive} proximoPago=${fecha}`);
  }
};

console.log('Antes:');
await mostrar();
if (process.argv.includes('--vencer')) {
  const hoy = new Date();
  // Fecha sin hora, como guarda Spring un LocalDate (medianoche UTC).
  const vencio = new Date(Date.UTC(hoy.getUTCFullYear(), hoy.getUTCMonth(), hoy.getUTCDate() - DIAS_VENCIDA));
  const r = await users.updateMany({ email: { $in: SOCIOS }, role: 'member' },
    // membershipActive en true: pagar no lo cambia, y en false el socio
    // seguiría sin poder entrar aunque renueve.
    { $set: { fechaProximoPago: vencio, membershipStatus: 'inactive', membershipActive: true } });
  console.log(`\nVencidos: ${r.modifiedCount} de ${SOCIOS.length}. Después:`);
  await mostrar();
}
await cliente.close();
