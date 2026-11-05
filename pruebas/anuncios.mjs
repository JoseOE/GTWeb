// Prueba de los anuncios del gimnasio: el dueño los publica en el panel y los
// miembros activos ven en la app solo los vigentes.
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
// Fechas de México en AAAA-MM-DD.
const dia = (desplazamiento) => {
  const d = new Date(Date.now() + desplazamiento * 86400000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Mexico_City' }).format(d);
};
const base = `/api/gyms/${gymId}/anuncios`;

console.log('\n1. El dueño publica');
let r = await api('POST', base, { titulo: 'Ab', texto: 'x' }, owner);
ok(r.status === 400 && r.body.error.includes('título'), 'un título muy corto se rechaza', r.body && r.body.error);
r = await api('POST', base, { titulo: 'Fechas al revés', texto: 'x', desde: dia(3), hasta: dia(1) }, owner);
ok(r.status === 400 && r.body.error.includes('antes de la de inicio'), 'el fin antes del inicio se rechaza', r.body && r.body.error);
r = await api('POST', base, { titulo: 'Raro', texto: 'x', tipo: 'fiesta' }, owner);
ok(r.status === 400 && r.body.error.includes('tipo'), 'un tipo fuera de la lista se rechaza', r.body && r.body.error);
r = await api('POST', base, { titulo: 'Cerramos el lunes', texto: 'Por el día festivo abrimos el martes a las 6:00.', tipo: 'cierre', hasta: dia(2), avisar: true }, owner);
ok(r.status === 200 && r.body.estado === 'vigente' && r.body.desde === dia(0), 'un anuncio sin fecha de inicio empieza hoy y queda vigente', r.body && (r.body.error || r.body.estado));
ok(r.body.avisadoEn && typeof r.body.avisados === 'number', 'con "avisar" se manda la notificación una vez', [r.body.avisadoEn, r.body.avisados]);
const vigente = r.body.id;
r = await api('POST', base, { titulo: 'Promo de diciembre', texto: '2×1 en proteína.', tipo: 'promocion', desde: dia(10), avisar: true }, owner);
ok(r.status === 400 && r.body.error.includes('ya está vigente'), 'no se avisa de uno que todavía no empieza', r.body && r.body.error);
r = await api('POST', base, { titulo: 'Promo de diciembre', texto: '2×1 en proteína.', tipo: 'promocion', desde: dia(10) }, owner);
ok(r.status === 200 && r.body.estado === 'programado', 'uno que empieza después queda programado', r.body.estado);
const programado = r.body.id;
r = await api('POST', base, { titulo: 'Clase especial', texto: 'Ya pasó.', desde: dia(-5), hasta: dia(-1) }, owner);
ok(r.body.estado === 'vencido', 'uno que ya terminó queda vencido', r.body.estado);
const vencido = r.body.id;

console.log('\n2. Lo que ve la app');
r = await api('GET', '/api/anuncios', null, member);
const ids = (r.body || []).map(a => a.id);
ok(r.status === 200 && ids.includes(vigente) && !ids.includes(programado) && !ids.includes(vencido), 'el miembro ve solo el vigente', ids);
r = await api('GET', '/api/anuncios', null, owner);
ok(r.status === 200 && r.body.length === 0, 'el dueño no recibe anuncios en la app');
r = await api('GET', '/api/anuncios');
ok(r.status === 401, 'sin sesión no se ven');

console.log('\n3. Pausar, editar y borrar');
r = await api('POST', `${base}/${vigente}/pausa`, { pausado: true }, owner);
ok(r.status === 200 && r.body.estado === 'pausado', 'el dueño lo pausa');
r = await api('GET', '/api/anuncios', null, member);
ok(!r.body.some(a => a.id === vigente), 'pausado, ya no sale en la app');
r = await api('POST', `${base}/${vigente}/pausa`, { pausado: false }, owner);
r = await api('PUT', `${base}/${vigente}`, { titulo: 'Cerramos el lunes 17', texto: 'Abrimos el martes a las 6:00.', tipo: 'cierre', hasta: dia(2), avisar: true }, owner);
ok(r.status === 200 && r.body.titulo === 'Cerramos el lunes 17' && r.body.avisados === 0, 'al editarlo no se vuelve a avisar', [r.body.titulo, r.body.avisados]);
r = await api('GET', base, null, member);
ok(r.status === 403, 'un miembro no administra los anuncios', r.status);
r = await api('GET', base, null, owner);
ok(r.status === 200 && r.body.length >= 3, 'el panel lista todos con su estado', r.body.map(a => a.estado));
for (const id of [vigente, programado, vencido]) await api('DELETE', `${base}/${id}`, null, owner);
r = await api('GET', base, null, owner);
ok(![vigente, programado, vencido].some(id => r.body.some(a => a.id === id)), 'se borran');

console.log(fallos ? `\n${fallos} fallo(s)` : '\nTodo bien');
process.exit(fallos ? 1 : 0);
