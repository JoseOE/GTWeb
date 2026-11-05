// Prueba de la portada de la app: hasta 6 fotos del gimnasio y la tipografía
// de su nombre, que el dueño elige en el panel y la app recibe en /me.
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
// Un PNG de 1×1 basta para subir imágenes de prueba.
const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

// El panel manda el gimnasio completo al guardar: se parte de lo que ya tiene.
const actual = (await api('GET', `/api/gyms/${gymId}`, null, owner)).body;
const guardar = extra => api('POST', '/api/gyms', {
  nombre: actual.nombre, telefono: actual.telefono, direccion: actual.direccion, horario: actual.horario,
  equipamiento: actual.equipamiento, maquinas: actual.maquinas, logo: actual.logo, colorPrimario: actual.colorPrimario,
  enDirectorio: actual.enDirectorio, cuotaMensual: actual.cuotaMensual, ownerUserId: owner, ...extra,
}, owner);

console.log('\n1. Fotos de la portada');
const subidas = [];
for (let i = 0; i < 7; i++) subidas.push((await api('POST', `/api/gyms/${gymId}/imagenes`, { dataUrl: PNG }, owner)).body.url);
ok(subidas.every(Boolean), 'se suben 7 imágenes del gimnasio');
let r = await guardar({ fotos: subidas });
ok(r.status === 400 && r.body.error.includes('hasta 6'), 'más de 6 fotos se rechazan', r.body && r.body.error);
r = await guardar({ fotos: ['https://otro-sitio.test/foto.jpg'] });
ok(r.status === 400 && r.body.error.includes('subirse desde el panel'), 'una foto de otro sitio se rechaza', r.body && r.body.error);
const tres = [subidas[2], subidas[0], subidas[1]];
r = await guardar({ fotos: tres });
ok(r.status === 200, 'guarda 3 fotos en el orden elegido', r.body && r.body.error);
const rutas = tres.map(u => u.replace(/^https?:\/\/[^/]+/, ''));
r = await api('GET', `/api/gyms/${gymId}`, null, member);
ok(JSON.stringify(r.body.fotos) === JSON.stringify(rutas), 'la vista pública trae las fotos en orden', r.body.fotos);
ok(r.body.foto === rutas[0], 'la primera también es "foto" (la de las opiniones)', r.body.foto);

console.log('\n2. Tipografía');
r = await guardar({ tipografia: 'comic-sans' });
ok(r.status === 400 && r.body.error.includes('tipografías de la lista'), 'una tipografía fuera de la lista se rechaza', r.body && r.body.error);
r = await guardar({ tipografia: 'Bebas-Neue' });
ok(r.status === 200, 'guarda Bebas Neue (sin importar mayúsculas)', r.body && r.body.error);

console.log('\n3. La app la recibe en /me');
r = await api('GET', `/api/users/${member}/me`, null, member);
ok(r.status === 200 && r.body.gym && r.body.gym.tipografia === 'bebas-neue', 'el miembro recibe la tipografía', r.body.gym && r.body.gym.tipografia);
ok(r.body.gym && JSON.stringify(r.body.gym.fotos) === JSON.stringify(rutas), 'y las fotos de la portada', r.body.gym && r.body.gym.fotos);
r = await guardar({});
ok(r.status === 200, 'guardar sin mandar fotos ni tipografía las conserva');
r = await api('GET', `/api/users/${member}/me`, null, member);
ok(r.body.gym.fotos.length === 3 && r.body.gym.tipografia === 'bebas-neue', 'siguen ahí', [r.body.gym.fotos.length, r.body.gym.tipografia]);

console.log('\n4. Quitar');
r = await guardar({ fotos: [], tipografia: '' });
r = await api('GET', `/api/users/${member}/me`, null, member);
ok(r.body.gym.fotos.length === 0 && r.body.gym.tipografia === null, 'con [] y "" vuelve a la portada de GymTrack', [r.body.gym.fotos, r.body.gym.tipografia]);

console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
