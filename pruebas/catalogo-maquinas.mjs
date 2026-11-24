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
ok(conFoto >= 120, 'casi todos los ejercicios traen fotos', conFoto);
const mancuernas = r.body.maquinas.find(m => m.nombre === 'Rack de mancuernas');
ok(mancuernas && new Set(mancuernas.ejercicios.map(e => e.musculo)).size >= 5, 'una máquina sirve para varios músculos', mancuernas?.ejercicios.map(e => e.musculo));

console.log('\n2. El dueño agrega una máquina con los ejercicios que elige');
r = await api('POST', ruta, { nombre: 'Rack de mancuernas', ejercicios: ['Curl martillo', 'Elevaciones laterales'] }, owner);
ok(r.status === 200 && r.body.agregados === 2, 'se agrega con 2 ejercicios', r.body);
const id = r.body?.maquina?.id;
let m = (await api('GET', `/api/gyms/${gymId}/machines`, null, owner)).body.find(x => x.id === id);
ok(m && m.ejercicios.length === 2 && m.ejercicios.every(e => e.id && e.instrucciones), 'la máquina del gimnasio los tiene, con id y cómo se hacen', m?.ejercicios);
ok(m.ejercicios.find(e => e.nombre === 'Curl martillo')?.musculo === 'Bíceps', 'el curl martillo queda como Bíceps');
ok(m.ejercicios.every(e => e.imagenes?.length === 2 && e.imagenes.every(u => u.startsWith('https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@'))),
  'cada ejercicio trae sus fotos de inicio y final', m.ejercicios.map(e => e.imagenes?.length));

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
