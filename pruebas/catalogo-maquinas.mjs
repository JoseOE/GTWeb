// Catálogo de máquinas: el dueño ve máquinas por categoría (Pecho, Bíceps,
// Multifuncional…), elige los ejercicios que sí se hacen en su gimnasio y la
// máquina se agrega con ellos; si ya la tenía, solo se le suman los que faltan.
import fs from 'node:fs';
import { API, ESTADO } from './entorno.mjs';

const n = JSON.parse(fs.readFileSync(ESTADO, 'utf8'));
const TOKENS = n.tokens || {};
const cabeceras = userId => userId ? { 'X-User-Id': userId, ...(TOKENS[userId] ? { Authorization: 'Bearer ' + TOKENS[userId] } : {}) } : {};
let fallos = 0;
const ok = (cond, msg, extra) => { console.log((cond ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')); if (!cond) fallos++; };
async function api(metodo, ruta, cuerpo, userId) {
  const r = await fetch(API + ruta, { method: metodo, headers: { 'Content-Type': 'application/json', ...cabeceras(userId) }, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  let body = null; try { body = await r.json(); } catch {}
  return { status: r.status, body };
}
const { gymId, owner, member } = n;
const ruta = `/api/gyms/${gymId}/machines/catalogo`;

console.log('\n1. El catálogo por categorías');
let r = await api('GET', ruta, null, owner);
ok(r.status === 200 && r.body.maquinas.length >= 40, 'trae muchas máquinas', r.body?.maquinas?.length);
ok(['Pecho', 'Hombro', 'Bíceps', 'Tríceps', 'Multifuncional'].every(c => r.body.categorias.includes(c)), 'clasificadas por músculo y multifuncionales', r.body?.categorias);
ok(r.body.categorias.every(c => r.body.maquinas.some(m => m.categoria === c)), 'cada categoría tiene máquinas');
ok(r.body.maquinas.every(m => m.ejercicios.length && m.ejercicios.every(e => e.musculo && e.instrucciones)),
  'cada máquina trae ejercicios con músculo y cómo se hacen');
const conFoto = r.body.maquinas.flatMap(m => m.ejercicios).filter(e => e.imagenes?.length).length;
ok(conFoto === r.body.maquinas.flatMap(m => m.ejercicios).length, 'todos los ejercicios traen ilustración', conFoto);
const mancuernas = r.body.maquinas.find(m => m.nombre === 'Rack de mancuernas');
ok(mancuernas && new Set(mancuernas.ejercicios.map(e => e.musculo)).size >= 5, 'una máquina sirve para varios músculos', mancuernas?.ejercicios.map(e => e.musculo));

console.log('\n2. El dueño agrega una máquina con los ejercicios que elige');
r = await api('POST', ruta, { nombre: 'Rack de mancuernas', ejercicios: ['Curl martillo', 'Elevaciones laterales'] }, owner);
ok(r.status === 200 && r.body.agregados === 2, 'se agrega con 2 ejercicios', r.body);
const id = r.body?.maquina?.id;
let m = (await api('GET', `/api/gyms/${gymId}/machines`, null, owner)).body.find(x => x.id === id);
ok(m && m.ejercicios.length === 2 && m.ejercicios.every(e => e.id && e.instrucciones), 'la máquina del gimnasio los tiene, con id y cómo se hacen', m?.ejercicios);
ok(m.ejercicios.find(e => e.nombre === 'Curl martillo')?.musculo === 'Bíceps', 'el curl martillo queda como Bíceps');
const ILUSTRACIONES = 'https://cdn.jsdelivr.net/gh/hitmanrepo/open-exercise-illustrations@';
ok(m.ejercicios.every(e => e.imagenes?.length === 2 && e.imagenes.every(u => u.startsWith(ILUSTRACIONES) && u.endsWith('.webp'))),
  'cada ejercicio trae sus ilustraciones de inicio y final', m.ejercicios.map(e => e.imagenes?.length));

// Las máquinas que guardaron las fotos anteriores (personas reales) ya no las muestran.
const VIEJA = 'https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Hammer_Curls/0.jpg';
r = await api('POST', `/api/gyms/${gymId}/machines`, { nombre: 'Máquina con fotos viejas', zona: 'Pesas', ejercicios: [
  { nombre: 'curl  MARTILLO', musculo: 'Bíceps', imagenes: [VIEJA] },
  { nombre: 'Ejercicio inventado', musculo: 'Otro', imagenes: [VIEJA] },
  { nombre: 'Prensa a 45°', musculo: 'Pierna' },
  { nombre: 'Curl femoral', musculo: 'Pierna' },
  { nombre: 'Peso muerto convencional', musculo: 'Espalda' },
  { nombre: 'Ondas alternas', musculo: 'Cardio' },
] }, owner);
const vieja = (await api('GET', `/api/gyms/${gymId}/machines`, null, owner)).body.find(x => x.nombre === 'Máquina con fotos viejas');
ok(vieja?.ejercicios[0].imagenes?.every(u => u.startsWith(ILUSTRACIONES)), 'el nombre del catálogo (sin importar mayúsculas ni espacios) toma la ilustración', vieja?.ejercicios[0].imagenes?.[0]);
ok(vieja && !vieja.ejercicios[1].imagenes?.length, 'un ejercicio fuera del catálogo ya no muestra la foto anterior', vieja?.ejercicios[1].imagenes);
ok(vieja?.ejercicios.slice(2).every(e => e.imagenes?.[0]?.startsWith(ILUSTRACIONES)), 'los nombres que escriben los gimnasios (alias o parecidos) también la toman', vieja?.ejercicios.slice(2).map(e => e.imagenes?.[0]?.split('/images/')[1]));
if (vieja) await api('DELETE', `/api/machines/${vieja.id}`, null, owner);

r = await api('POST', ruta, { nombre: 'rack de mancuernas', ejercicios: ['Curl martillo', 'Patada de tríceps'] }, owner);
ok(r.status === 200 && r.body.agregados === 1 && r.body.maquina.id === id, 'la misma máquina: solo suma el que faltaba', r.body?.agregados);
r = await api('GET', ruta, null, owner);
const vista = r.body.maquinas.find(x => x.nombre === 'Rack de mancuernas');
ok(vista.machineId === id && vista.ejerciciosQueTienes.length === 3, 'el catálogo dice que ya la tiene y cuáles ejercicios', vista.ejerciciosQueTienes);

console.log('\n3. Errores y permisos');
r = await api('POST', ruta, { nombre: 'Máquina inventada', ejercicios: ['Algo'] }, owner);
ok(r.status === 404, 'una máquina que no está en el catálogo → 404', r.body?.error);
r = await api('POST', ruta, { nombre: 'Caminadora', ejercicios: [] }, owner);
ok(r.status === 400, 'sin ejercicios → 400', r.body?.error);
r = await api('GET', ruta, null, member);
ok(r.status === 403, 'un socio no administra el catálogo', r.status);
r = await api('GET', ruta, null, null);
ok(r.status === 401, 'sin sesión → 401', r.status);

await api('DELETE', `/api/machines/${id}`, null, owner);
console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
if (fallos) process.exitCode = 1;
