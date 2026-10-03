// Corre las pruebas en orden y al final dice cuáles fallaron.
//   node correr-todas.mjs            → todas
//   node correr-todas.mjs 3 4        → solo bloque3 y bloque4 (el bloque 1 ya corrió antes)
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ORDEN = ['bloque1', 'bloque2', 'bloque3', 'bloque4', 'bloque5', 'seguridad', 'visita-mes', 'alta-recepcion'];
const pedidas = process.argv.slice(2).map(a => /^\d$/.test(a) ? 'bloque' + a : a);
const lista = pedidas.length ? ORDEN.filter(p => pedidas.includes(p)) : ORDEN;
const carpeta = fileURLToPath(new URL('.', import.meta.url));

const resultados = [];
for (const prueba of lista) {
  console.log(`\n━━━━━━━━ ${prueba} ━━━━━━━━`);
  const inicio = Date.now();
  const r = spawnSync(process.execPath, [prueba + '.mjs'], { cwd: carpeta, stdio: 'inherit' });
  resultados.push({ prueba, ok: r.status === 0, segundos: ((Date.now() - inicio) / 1000).toFixed(1) });
  // Sin el bloque 1 no hay gimnasio ni miembro para los demás.
  if (prueba === 'bloque1' && r.status !== 0) {
    console.error('\nEl bloque 1 falló: las demás pruebas dependen de lo que crea. Se detiene aquí.');
    break;
  }
}

console.log('\n━━━━━━━━ Resumen ━━━━━━━━');
for (const r of resultados) console.log(`${r.ok ? '✔' : '✘'} ${r.prueba.padEnd(15)} ${r.segundos} s`);
const fallaron = resultados.filter(r => !r.ok).length;
console.log(fallaron ? `\n${fallaron} prueba(s) con fallos.` : '\nTodo bien.');
process.exit(fallaron ? 1 : 0);
