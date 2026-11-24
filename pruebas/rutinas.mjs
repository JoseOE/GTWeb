// Prueba de las rutinas con ejercicios de las máquinas: el coach los elige en
// el panel y la app recibe cada uno con su músculo, series, reps y descanso.
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

console.log('\n1. El gimnasio tiene máquinas con ejercicios');
await api('POST', `/api/gyms/${gymId}/machines/import`, null, owner);
let r = await api('GET', `/api/gyms/${gymId}/machines`, null, owner);
const banca = (r.body || []).find(m => m.nombre === 'Press banca');
const poleas = (r.body || []).find(m => m.nombre === 'Poleas cruzadas');
ok(banca && poleas, 'hay Press banca y Poleas cruzadas', (r.body || []).map(m => m.nombre));
const plano = banca.ejercicios.find(e => e.nombre === 'Press banca plano');
const cruce = poleas.ejercicios.find(e => e.nombre === 'Cruce de poleas alto');

console.log('\n2. El coach arma la rutina');
const base = `/api/gyms/${gymId}/routines`;
const rutina = (items) => ({ nombre: 'Pecho estructurado', categoria: 'Pecho', items });
r = await api('POST', base, rutina([{ machineId: banca.id, exerciseId: 'no-existe' }]), owner);
ok(r.status === 400 && r.body.error.includes('ya no existe'), 'un ejercicio que no está en la máquina se rechaza', r.body && r.body.error);
r = await api('POST', base, rutina([{ machineId: 'otra-maquina', exerciseId: plano.id }]), owner);
ok(r.status === 400 && r.body.error.includes('máquina'), 'una máquina de otro gimnasio se rechaza', r.body && r.body.error);
r = await api('POST', base, rutina([{ machineId: banca.id, exerciseId: plano.id, series: 11 }]), owner);
ok(r.status === 400 && r.body.error.includes('1 a 10 series'), 'más de 10 series se rechaza', r.body && r.body.error);
r = await api('POST', base, rutina([{ nombre: '  ', musculo: 'Pecho' }]), owner);
ok(r.status === 400 && r.body.error.includes('nombre'), 'un ejercicio libre sin nombre se rechaza', r.body && r.body.error);
r = await api('POST', base, rutina([{ machineId: banca.id, exerciseId: plano.id, descansoSegundos: 900 }]), owner);
ok(r.status === 400 && r.body.error.includes('descanso'), 'un descanso de más de 10 min se rechaza', r.body && r.body.error);

r = await api('POST', base, rutina([
  { machineId: banca.id, exerciseId: plano.id, nombre: 'Nombre inventado', musculo: 'Pierna', series: 4, repeticiones: '8', descansoSegundos: 120 },
  { machineId: poleas.id, exerciseId: cruce.id },
  { nombre: 'Lagartijas', musculo: 'Pecho', series: 3, repeticiones: 'al fallo', descansoSegundos: 60 },
]), owner);
ok(r.status === 200, 'la rutina se guarda', r.body && r.body.error);
const id = r.body.id;
const [a, b, c] = r.body.items;
ok(a.nombre === 'Press banca plano' && a.musculo === 'Pecho' && a.machineNombre === 'Press banca', 'el nombre y el músculo salen de la máquina, no del navegador', [a.nombre, a.musculo]);
ok(b.series === cruce.series && b.repeticiones === cruce.repeticiones && b.descansoSegundos === 90, 'sin series ni reps se usan las de la máquina y 90 s de descanso', [b.series, b.repeticiones, b.descansoSegundos]);
ok(c.machineId == null && c.nombre === 'Lagartijas' && c.musculo === 'Pecho', 'un ejercicio libre conserva su nombre y músculo');
ok(JSON.stringify(r.body.ejercicios) === JSON.stringify(['Press banca plano — 4 x 8', `Cruce de poleas alto — ${cruce.series} x ${cruce.repeticiones}`, 'Lagartijas — 3 x al fallo']),
  'el texto de ejercicios se arma solo, para las apps anteriores', r.body.ejercicios);

console.log('\n3. Lo que ve la app');
r = await api('GET', `/api/users/${member}/routines`, null, member);
const vista = (r.body || []).find(x => x.id === id);
ok(r.status === 200 && vista && vista.items.length === 3 && vista.items[0].descansoSegundos === 120, 'el miembro recibe los ejercicios con su descanso');

console.log('\n4. Editar');
r = await api('PUT', `/api/routines/${id}`, { ...rutina([{ machineId: poleas.id, exerciseId: cruce.id, series: 2, repeticiones: '15' }]), nombre: 'Pecho corto' }, owner);
ok(r.status === 200 && r.body.items.length === 1 && r.body.ejercicios[0] === 'Cruce de poleas alto — 2 x 15', 'al editar se reemplazan los ejercicios', r.body && (r.body.error || r.body.ejercicios));
r = await api('PUT', `/api/routines/${id}`, rutina([{ machineId: banca.id, exerciseId: 'no-existe' }]), owner);
ok(r.status === 400, 'al editar también se revisan');
r = await api('PUT', `/api/routines/${id}`, { nombre: 'Solo texto', categoria: 'Pecho', ejercicios: ['Sentadilla — 5 x 5'] }, owner);
ok(r.status === 200 && r.body.items.length === 0 && r.body.ejercicios[0] === 'Sentadilla — 5 x 5', 'una rutina de solo texto sigue funcionando');

console.log('\n5. El entrenamiento recuerda de qué rutina salió');
r = await api('POST', `/api/users/${member}/workouts`, {
  nombre: 'Pecho corto', rutinaId: id, duracionMinutos: 42,
  ejercicios: [{ nombre: 'Cruce de poleas alto', musculo: 'Pecho', series: [{ repeticiones: 15, peso: 20, completada: true }] }],
}, member);
ok(r.status === 200 && r.body.rutinaId === id, 'el entrenamiento guarda rutinaId', r.body && (r.body.error || r.body.rutinaId));
if (r.body && r.body.id) await api('DELETE', `/api/users/${member}/workouts/${r.body.id}`, null, member);
await api('DELETE', `/api/routines/${id}`, null, owner);

console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
