// Configuración común de las pruebas de punta a punta.
//
// Todo se lee de variables de entorno (o del .env de la raíz del proyecto),
// con los valores del entorno local por defecto. Las pruebas crean cuentas,
// gimnasios y pedidos, y una cambia un pedido directo en MongoDB: por eso se
// niegan a correr contra algo que no sea local, salvo que se pida a propósito.
import fs from 'node:fs';

function leerEnv() {
  try {
    return Object.fromEntries(fs.readFileSync(new URL('../.env', import.meta.url), 'utf8')
      .split(/\r?\n/).filter(l => l.includes('=') && !l.trim().startsWith('#'))
      .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
  } catch {
    return {};
  }
}
const archivo = leerEnv();
const valor = (nombre, porDefecto) => process.env[nombre] || archivo[nombre] || porDefecto;

export const API = valor('GYMTRACK_API', 'http://localhost:8080').replace(/\/$/, '');
export const MEDUSA = valor('MEDUSA_URL', 'http://localhost:9000').replace(/\/$/, '');
export const MONGO = valor('MONGODB_URI', 'mongodb://127.0.0.1:27017/gymtrackdb');
// Secreto compartido de los avisos de Medusa a Spring (el bloque 1 manda avisos firmados).
export const SECRETO_WEBHOOK = valor('MEDUSA_WEBHOOK_SECRET');
// Log de Spring (su salida estándar): las pruebas leen ahí los códigos de
// verificación, porque en local no hay correo configurado.
export const LOG = process.env.SPRING_LOG || new URL('./spring.log', import.meta.url);
// Lo que deja el bloque 1 (gimnasio, dueño, miembro, planes) para los demás.
export const ESTADO = new URL('./ultima-prueba.json', import.meta.url);

export function adminMedusa() {
  const llave = valor('MEDUSA_ADMIN_TOKEN');
  if (!llave) throw new Error('Falta MEDUSA_ADMIN_TOKEN (en el .env o como variable de entorno).');
  return 'Basic ' + Buffer.from(llave + ':').toString('base64');
}

const esLocal = (url) => /^(https?|mongodb):\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?(\/|$)/.test(url);
if (process.env.PRUEBAS_PERMITIR_REMOTO !== '1') {
  for (const [nombre, url] of [['GYMTRACK_API', API], ['MEDUSA_URL', MEDUSA], ['MONGODB_URI', MONGO]]) {
    if (!esLocal(url)) {
      console.error(`${nombre} apunta a ${url.replace(/\/\/[^@/]*@/, '//***@')}, que no es local.`
        + ' Las pruebas crean datos de prueba: córrelas solo contra tu entorno local'
        + ' (o pon PRUEBAS_PERMITIR_REMOTO=1 si de verdad es un entorno de pruebas).');
      process.exit(2);
    }
  }
}
