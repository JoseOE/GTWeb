// Gimnasios de demostración: 6 gimnasios con su información y foto, equipo,
// rutinas, planes, productos con foto, 6 a 8 miembros cada uno con su historial
// de pagos, ventas de mostrador de los últimos 90 días, reseñas de productos y
// opiniones sobre GymTrack (las de 4★ o más salen en la página de inicio).
//
// Todo se crea con la API, como lo haría el dueño desde el panel. La base solo
// se toca para lo que la API no permite a propósito:
//   - marcar verificados los correos de los dueños (los @gymtrack.demo no
//     reciben correo, ver CorreoService),
//   - dar a los miembros la contraseña de demostración (el alta del dueño les
//     pone una temporal),
//   - repartir las ventas y la bitácora en los últimos 90 días.
// Cada cuenta es @gymtrack.demo: "borrar" quita exactamente eso y lo de sus
// gimnasios, sin tocar nada más.
//
//   DEMO_API=http://localhost:8080 DEMO_MONGODB_URI=mongodb://... DEMO_PASSWORD=... node pruebas/demo-datos.mjs sembrar
//   ... node pruebas/demo-datos.mjs borrar
// Contra un servidor que no sea local hay que agregar --produccion.
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
const accion = process.argv[2];
if (!['sembrar', 'borrar'].includes(accion) || !MONGO || !CLAVE) {
  console.error('Uso: DEMO_API=... DEMO_MONGODB_URI=... DEMO_PASSWORD=... node pruebas/demo-datos.mjs sembrar|borrar [--produccion]');
  process.exit(2);
}
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(API) && !process.argv.includes('--produccion')) {
  console.error(`${API} no es local: agrega --produccion si de verdad quieres ${accion} los datos de demostración ahí.`);
  process.exit(2);
}
const DOMINIO = '@gymtrack.demo';

// ─── Azar reproducible: las mismas ventas y fechas cada vez ───
let semilla = 20261027;
const azar = () => { semilla |= 0; semilla = semilla + 0x6D2B79F5 | 0; let t = Math.imul(semilla ^ semilla >>> 15, 1 | semilla); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
const entero = (min, max) => min + Math.floor(azar() * (max - min + 1));
const uno = lista => lista[Math.floor(azar() * lista.length)];
const algunos = (lista, n) => [...lista].sort(() => azar() - 0.5).slice(0, n);
const espera = ms => new Promise(r => setTimeout(r, ms));
const quitarAcentos = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '');

async function api(metodo, ruta, cuerpo, token) {
  for (let intento = 0; ; intento++) {
    const r = await fetch(API + ruta, {
      method: metodo,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      body: cuerpo ? JSON.stringify(cuerpo) : undefined
    }).catch(e => ({ ok: false, status: 0, json: async () => ({ error: e.message }) }));
    if (r.status === 0 && intento < 3) { await espera(1500); continue; }
    let body = null; try { body = await r.json(); } catch {}
    return { status: r.status, body };
  }
}
function exigir(r, que) {
  if (r.status < 200 || r.status >= 300) throw new Error(`${que}: ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
}

const fotos = new Map();
async function foto(id, ancho) {
  const clave = id + ':' + ancho;
  if (!fotos.has(clave)) {
    const r = await fetch(`https://images.unsplash.com/photo-${id}?w=${ancho}&q=72&fm=jpg&fit=crop`);
    if (!r.ok) throw new Error('No se pudo descargar la foto ' + id);
    fotos.set(clave, 'data:image/jpeg;base64,' + Buffer.from(await r.arrayBuffer()).toString('base64'));
  }
  return fotos.get(clave);
}

// ═══════════════════════════ DATOS ═══════════════════════════

const GIMNASIOS = [
  { clave: 'maximus', nombre: 'Gym Maximus', dueno: 'Ricardo Montes Ibarra', color: '#B71C1C', cuota: 550, foto: '1637666062717-1c6bcfa4a4df',
    telefono: '771 214 3380', direccion: 'Blvd. Felipe Ángeles 1205, Venta Prieta, Pachuca de Soto, Hgo.',
    horario: 'Lun a Vie 5:00–23:00 · Sáb 7:00–16:00 · Dom 8:00–14:00',
    equipamiento: 'Zona de peso libre con 4 racks y plataformas de levantamiento, área de máquinas guiadas, 12 equipos de cardio, regaderas y lockers.',
    razon: 'Maximus Fitness Pachuca', coach: 'Coach Arturo', maquinas: 12 },
  { clave: 'alfa', nombre: 'Gym Alfa', dueno: 'Daniela Fuentes Corona', color: '#1565C0', cuota: 480, foto: '1540497077202-7c8a3999166f',
    telefono: '773 732 1840', direccion: 'Av. 5 de Mayo 48, Centro, Tula de Allende, Hgo.',
    horario: 'Lun a Vie 6:00–22:00 · Sáb 8:00–14:00',
    equipamiento: 'Gimnasio iluminado con máquinas de última generación, zona funcional con TRX y kettlebells, y clases grupales de spinning.',
    razon: 'Alfa Training Center', coach: 'Coach Mariela', maquinas: 10 },
  { clave: 'lunas', nombre: 'Gym Lunas', dueno: 'Sofía Hernández Luna', color: '#6A1B9A', cuota: 420, foto: '1571902943202-507ec2618e8f',
    telefono: '738 725 0912', direccion: 'Calle Hidalgo 22, Centro, Mixquiahuala de Juárez, Hgo.',
    horario: 'Lun a Vie 6:00–21:00 · Sáb 8:00–13:00',
    equipamiento: 'Estudio con zona de pesas, máquinas de glúteo y pierna, sala de yoga y pilates, y área de cardio con vista a la calle.',
    razon: 'Lunas Wellness Studio', coach: 'Coach Fernanda', maquinas: 9 },
  { clave: 'spartans', nombre: 'Gym Spartans', dueno: 'Héctor Ramírez Salinas', color: '#E65100', cuota: 500, foto: '1534438327276-14e5300c3a48',
    telefono: '773 733 5521', direccion: 'Av. Melchor Ocampo 310, Centro, Tepeji del Río de Ocampo, Hgo.',
    horario: 'Lun a Vie 5:30–22:30 · Sáb 7:00–15:00',
    equipamiento: 'Mancuernas de 2 a 50 kg, bancos ajustables, zona de cross training con cuerdas y cajones, y sacos de box.',
    razon: 'Spartans Fitness Club', coach: 'Coach Leónidas', maquinas: 11 },
  { clave: 'vikings', nombre: 'Gym Vikings', dueno: 'Iván Olvera Rangel', color: '#37474F', cuota: 450, foto: '1593079831268-3381b0db4a77',
    telefono: '772 727 4410', direccion: 'Av. Juárez 87, Centro, Actopan, Hgo.',
    horario: 'Lun a Vie 6:00–22:00 · Sáb 7:00–14:00 · Dom 9:00–13:00',
    equipamiento: 'Piso de 600 m² con área de powerlifting, strongman (yugo, troncos y llantas) y una línea completa de cardio.',
    razon: 'Vikings Strength House', coach: 'Coach Erik', maquinas: 10 },
  { clave: 'titanium', nombre: 'Gym Titanium Sport', dueno: 'Mónica Téllez Arriaga', color: '#00897B', cuota: 600, foto: '1558611848-73f7eb4001a1',
    telefono: '775 753 8890', direccion: 'Blvd. 21 de Marzo 1420, Tulancingo de Bravo, Hgo.',
    horario: 'Lun a Vie 5:00–23:00 · Sáb y Dom 8:00–16:00',
    equipamiento: 'Equipo de alto rendimiento, máquinas de placas, zona de rehabilitación deportiva, nutrióloga y entrenadores certificados.',
    razon: 'Titanium Sport S.A. de C.V.', coach: 'Coach Daniel', maquinas: 12 },
];

const NOMBRES = ['Valeria', 'Diego', 'Fernanda', 'Jorge', 'Ximena', 'Luis', 'Andrea', 'Carlos', 'Paola', 'Miguel', 'Camila', 'José', 'Mariana', 'Emiliano',
  'Regina', 'Sebastián', 'Natalia', 'Alejandro', 'Renata', 'Ángel', 'Daniela', 'Iván', 'Karla', 'Uriel', 'Lucía', 'Bruno', 'Itzel', 'Óscar', 'Sofía', 'Rodrigo',
  'Elena', 'Gael', 'Brenda', 'Tomás', 'Abril', 'Hugo', 'Monserrat', 'Raúl', 'Julieta', 'Adrián', 'Guadalupe', 'Mauricio', 'Alondra', 'Ernesto'];
const APELLIDOS = ['García', 'Hernández', 'Martínez', 'López', 'González', 'Pérez', 'Rodríguez', 'Sánchez', 'Ramírez', 'Cruz', 'Flores', 'Gómez', 'Morales',
  'Vázquez', 'Reyes', 'Jiménez', 'Torres', 'Díaz', 'Mendoza', 'Ortiz', 'Castillo', 'Romero', 'Islas', 'Trejo', 'Olvera', 'Monroy', 'Escamilla', 'Pérez'];

// Equipo: cada gimnasio toma una parte distinta de esta lista.
const ej = (nombre, musculo, series, repeticiones, instrucciones) => ({ nombre, musculo, series, repeticiones, instrucciones });
const MAQUINAS = [
  { nombre: 'Prensa de piernas 45°', zona: 'Pierna', ejercicios: [ej('Prensa a 45°', 'Cuádriceps', 4, '10-12', 'Baja hasta 90° sin despegar la espalda.'), ej('Prensa pies altos', 'Glúteo', 3, '12', 'Pies arriba de la plataforma para cargar glúteo.')] },
  { nombre: 'Sentadilla hack', zona: 'Pierna', ejercicios: [ej('Sentadilla hack', 'Cuádriceps', 4, '8-10', 'Rodillas en línea con la punta de los pies.')] },
  { nombre: 'Extensión de cuádriceps', zona: 'Pierna', ejercicios: [ej('Extensión de piernas', 'Cuádriceps', 3, '12-15', 'Sostén un segundo arriba.')] },
  { nombre: 'Curl femoral acostado', zona: 'Pierna', ejercicios: [ej('Curl femoral', 'Isquiotibiales', 3, '12', 'Controla la bajada en 3 segundos.')] },
  { nombre: 'Hip thrust', zona: 'Pierna', ejercicios: [ej('Hip thrust', 'Glúteo', 4, '10', 'Barbilla al pecho y aprieta arriba.')] },
  { nombre: 'Abductor y aductor', zona: 'Pierna', ejercicios: [ej('Abducción de cadera', 'Glúteo medio', 3, '15'), ej('Aducción de cadera', 'Aductores', 3, '15')] },
  { nombre: 'Rack de sentadillas', zona: 'Peso libre', ejercicios: [ej('Sentadilla trasera', 'Pierna', 5, '5', 'Profundidad paralela o más.'), ej('Press militar de pie', 'Hombro', 4, '8')] },
  { nombre: 'Banco plano con barra', zona: 'Peso libre', ejercicios: [ej('Press banca', 'Pecho', 4, '8-10', 'Escápulas juntas y pies firmes.')] },
  { nombre: 'Banco inclinado', zona: 'Peso libre', ejercicios: [ej('Press inclinado con mancuernas', 'Pecho superior', 4, '10')] },
  { nombre: 'Plataforma de peso muerto', zona: 'Peso libre', ejercicios: [ej('Peso muerto convencional', 'Cadena posterior', 5, '3-5', 'Espalda neutra, empuja el piso.')] },
  { nombre: 'Mancuernas 2 a 50 kg', zona: 'Peso libre', ejercicios: [ej('Curl de bíceps', 'Bíceps', 3, '12'), ej('Remo con mancuerna', 'Espalda', 3, '10'), ej('Elevaciones laterales', 'Hombro', 3, '15')] },
  { nombre: 'Jalón al pecho', zona: 'Máquinas', ejercicios: [ej('Jalón al pecho', 'Dorsal', 4, '10-12', 'Lleva la barra a la clavícula.')] },
  { nombre: 'Remo sentado en polea', zona: 'Máquinas', ejercicios: [ej('Remo en polea baja', 'Espalda media', 4, '10')] },
  { nombre: 'Poleas cruzadas', zona: 'Máquinas', ejercicios: [ej('Cruce de poleas', 'Pecho', 3, '12-15'), ej('Extensión de tríceps en polea', 'Tríceps', 3, '12')] },
  { nombre: 'Máquina Smith', zona: 'Máquinas', ejercicios: [ej('Sentadilla en Smith', 'Pierna', 4, '10'), ej('Press inclinado en Smith', 'Pecho', 4, '10')] },
  { nombre: 'Pec deck', zona: 'Máquinas', ejercicios: [ej('Aperturas en pec deck', 'Pecho', 3, '12'), ej('Pájaros en pec deck', 'Hombro posterior', 3, '15')] },
  { nombre: 'Caminadora', zona: 'Cardio', ejercicios: [ej('Caminata inclinada', 'Cardio', 1, '20 min', 'Inclinación 8-12 %, ritmo constante.')] },
  { nombre: 'Bicicleta de spinning', zona: 'Cardio', ejercicios: [ej('Intervalos en bici', 'Cardio', 8, '30 s fuerte / 60 s suave')] },
  { nombre: 'Escaladora', zona: 'Cardio', ejercicios: [ej('Escaladora', 'Cardio', 1, '15 min')] },
  { nombre: 'Remo ergómetro', zona: 'Cardio', ejercicios: [ej('Remo de 500 m', 'Cuerpo completo', 5, '500 m')] },
  { nombre: 'Cuerdas de batalla', zona: 'Funcional', ejercicios: [ej('Ondas alternas', 'Cuerpo completo', 6, '30 s')] },
  { nombre: 'Cajones pliométricos', zona: 'Funcional', ejercicios: [ej('Saltos al cajón', 'Pierna', 4, '8')] },
  { nombre: 'TRX', zona: 'Funcional', ejercicios: [ej('Remo en TRX', 'Espalda', 3, '12'), ej('Sentadilla con TRX', 'Pierna', 3, '15')] },
  { nombre: 'Saco de box', zona: 'Funcional', ejercicios: [ej('Combinaciones en saco', 'Cardio', 6, '2 min')] },
];

const RUTINAS = [
  { nombre: 'Pierna completa', categoria: 'Pierna', duracion: '60 min', nivel: 'Intermedio', dia: 'Lunes', descripcion: 'Fuerza e hipertrofia de cuádriceps, femoral y glúteo.',
    ejercicios: ['Sentadilla trasera 5x5', 'Prensa a 45° 4x12', 'Curl femoral 3x12', 'Extensión de piernas 3x15', 'Pantorrilla de pie 4x15'] },
  { nombre: 'Empuje: pecho, hombro y tríceps', categoria: 'Pecho', duracion: '55 min', nivel: 'Intermedio', dia: 'Martes', descripcion: 'Día de empuje con énfasis en press.',
    ejercicios: ['Press banca 4x8', 'Press inclinado con mancuernas 3x10', 'Press militar 4x8', 'Elevaciones laterales 3x15', 'Fondos 3 al fallo'] },
  { nombre: 'Jalón: espalda y bíceps', categoria: 'Espalda', duracion: '55 min', nivel: 'Intermedio', dia: 'Miércoles', descripcion: 'Espalda ancha y gruesa.',
    ejercicios: ['Dominadas 4x6-8', 'Remo con barra 4x8', 'Jalón al pecho 3x12', 'Remo en polea baja 3x10', 'Curl martillo 3x12'] },
  { nombre: 'Glúteo y femoral', categoria: 'Pierna', duracion: '50 min', nivel: 'Principiante', dia: 'Jueves', descripcion: 'Cadena posterior con máquinas y peso libre.',
    ejercicios: ['Hip thrust 4x10', 'Peso muerto rumano 3x10', 'Abducción de cadera 3x15', 'Patada de glúteo en polea 3x12'] },
  { nombre: 'Full body para empezar', categoria: 'Cuerpo completo', duracion: '45 min', nivel: 'Principiante', dia: '', descripcion: 'Rutina base para tus primeras semanas.',
    ejercicios: ['Sentadilla goblet 3x12', 'Press de pecho en máquina 3x12', 'Jalón al pecho 3x12', 'Plancha 3x30 s', 'Caminata inclinada 15 min'] },
  { nombre: 'HIIT quema grasa', categoria: 'Cardio', duracion: '30 min', nivel: 'Avanzado', dia: 'Viernes', descripcion: 'Intervalos de alta intensidad.',
    ejercicios: ['Bici 8x(30 s fuerte / 60 s suave)', 'Cuerdas de batalla 6x30 s', 'Saltos al cajón 4x8', 'Burpees 4x10'] },
  { nombre: 'Core y abdomen', categoria: 'Core', duracion: '25 min', nivel: 'Principiante', dia: 'Sábado', descripcion: 'Estabilidad y fuerza del abdomen.',
    ejercicios: ['Plancha 3x45 s', 'Crunch en polea 3x15', 'Elevación de piernas 3x12', 'Rueda abdominal 3x10'] },
  { nombre: 'Powerlifting: técnica de los tres básicos', categoria: 'Fuerza', duracion: '75 min', nivel: 'Avanzado', dia: 'Sábado', descripcion: 'Sentadilla, banca y peso muerto a bajas repeticiones.',
    ejercicios: ['Sentadilla 5x3', 'Press banca 5x3', 'Peso muerto 3x3', 'Remo Pendlay 3x6'] },
];

const PLANES = [
  { nombre: 'Visita', descripcion: 'Acceso por un día.', unidad: 'dia', cantidad: 1, factor: 0.18, beneficios: ['Acceso a todas las áreas por un día'] },
  { nombre: 'Mensual', descripcion: 'Acceso ilimitado durante un mes.', unidad: 'mes', cantidad: 1, factor: 1, beneficios: ['Acceso ilimitado', 'Rutinas en la app'], destacado: true },
  { nombre: 'Trimestral', descripcion: 'Tres meses con descuento.', unidad: 'mes', cantidad: 3, factor: 2.7, beneficios: ['Acceso ilimitado', 'Rutinas en la app', 'Valoración física'] },
  { nombre: 'Anual', descripcion: 'Un año al mejor precio.', unidad: 'mes', cantidad: 12, factor: 9.5, beneficios: ['Acceso ilimitado', 'Rutinas en la app', 'Valoración física cada trimestre', 'Playera del gimnasio'] },
];

const PRODUCTOS = [
  { nombre: 'Proteína whey', categoria: 'suplementos', foto: '1693996045300-521e9d08cabc', marca: 'NutriMax', descripcion: 'Proteína de suero con 24 g por porción. Ideal después de entrenar.',
    variantes: [['2 lb', 'Chocolate', 649], ['2 lb', 'Vainilla', 649], ['5 lb', 'Chocolate', 1299]] },
  { nombre: 'Proteína isolatada', categoria: 'suplementos', foto: '1775199603089-e953f3716737', marca: 'NutriMax', descripcion: 'Aislado de suero bajo en grasa y lactosa.',
    variantes: [['2 lb', 'Fresa', 849], ['2 lb', 'Galleta', 849]] },
  { nombre: 'Creatina monohidratada', categoria: 'suplementos', foto: '1693996045435-af7c48b9cafb', marca: 'PowerLab', descripcion: '5 g por porción, sin sabor. Mejora la fuerza y la recuperación.',
    variantes: [['300 g', null, 459], ['500 g', null, 689]] },
  { nombre: 'Preentreno', categoria: 'suplementos', foto: '1593095948071-474c5cc2989d', marca: 'PowerLab', descripcion: 'Cafeína, beta-alanina y citrulina para entrenar con energía.',
    variantes: [['30 porciones', 'Frutos rojos', 549], ['30 porciones', 'Limón', 549]] },
  { nombre: 'Electrolitos en sobre', categoria: 'bebidas', foto: '1764175098858-d206d4fbc084', marca: 'HidraFit', descripcion: 'Sales para hidratarte durante el entrenamiento.',
    variantes: [['Sobre', 'Naranja', 25], ['Caja 10 sobres', 'Surtido', 220]] },
  { nombre: 'Agua natural', categoria: 'bebidas', foto: '1523362628745-0c100150b504', marca: null, descripcion: 'Botella de agua purificada.',
    variantes: [['600 ml', null, 15], ['1 L', null, 22]] },
  { nombre: 'Barra de proteína', categoria: 'snacks', foto: '1622484212850-eb596d769edc', marca: 'FitBar', descripcion: '20 g de proteína y bajo azúcar.',
    variantes: [['Pieza', 'Chocolate', 38], ['Pieza', 'Cacahuate', 38]] },
  { nombre: 'Shaker 700 ml', categoria: 'accesorios', foto: '1642539088032-41a1fb000bc5', marca: null, descripcion: 'Con rejilla mezcladora y tapa de rosca.',
    variantes: [['700 ml', null, 129]] },
  { nombre: 'Guantes de entrenamiento', categoria: 'accesorios', foto: '1570442387127-66eb80e00938', marca: 'GripPro', descripcion: 'Palma acolchada y muñequera ajustable.',
    variantes: [['Talla M', null, 299], ['Talla L', null, 299]] },
  { nombre: 'Toalla de microfibra', categoria: 'accesorios', foto: '1638183130424-873c491cca1b', marca: null, descripcion: 'Secado rápido, 40 x 80 cm.',
    variantes: [['Única', null, 149]] },
  { nombre: 'Ligas de resistencia', categoria: 'accesorios', foto: '1584735935682-2f2b69dff9d2', marca: 'GripPro', descripcion: 'Juego de 3 ligas de distinta resistencia.',
    variantes: [['Juego de 3', null, 249]] },
];

const OPINIONES_DUENO = [
  'Dejé el cuaderno de membresías: ahora sé quién debe y cuándo vence cada uno, y la tienda vende sola.',
  'El mostrador y el reporte de ventas me ahorran horas cada semana. Mis miembros aman la app.',
  'Por fin tengo todo en un solo lugar: pagos, rutinas, máquinas y la tienda del gimnasio.',
  'Muy fácil de usar desde la recepción. La bitácora me da tranquilidad de saber quién hizo qué.',
  'Mis coaches publican las rutinas y los miembros las ven en su celular. Gran herramienta.',
  'Cobrar con tarjeta, PayPal o en tienda desde el mismo panel cambió cómo vendemos suplementos.',
];
const OPINIONES_MIEMBRO = [
  ['Compro mi proteína desde la app y la recojo en recepción con el QR. Súper práctico.', 5],
  ['Me encanta ver mis rutinas del día y registrar mis pesos. Ya no se me olvida qué hice.', 5],
  ['La app me avisa antes de que venza mi mensualidad y puedo pagar en línea.', 4],
  ['Muy buena app, lo que más uso son las rutinas y la tienda.', 4],
  ['Está bien, aunque me gustaría ver más estadísticas de mi progreso.', 3],
  ['Pagué mi anualidad en la app sin hacer fila. Todo muy claro.', 5],
  ['Ver lo más vendido me ayudó a elegir mi primera creatina.', 4],
  ['A veces tarda un poco en cargar, pero en general funciona bien.', 3],
];
const RESENAS = [
  [5, 'Excelente sabor y se disuelve muy bien.'], [5, 'Muy buena calidad por el precio.'], [4, 'Buena, la volvería a comprar.'],
  [4, 'Cumple lo que promete.'], [5, 'La mejor que he probado en el gym.'], [3, 'Está bien, nada especial.'],
];

// ═══════════════════════════ SEMBRAR ═══════════════════════════

async function sembrar(db) {
  const inicio = new Date();
  const users = db.collection('users');
  let ventasTotales = 0, miembrosTotales = 0;

  for (const [indice, g] of GIMNASIOS.entries()) {
    console.log(`\n${g.nombre}`);
    const correoDueno = `dueno.${g.clave}${DOMINIO}`;
    if (await users.findOne({ email: correoDueno })) {
      console.log('  ya existe: se omite (usa "borrar" para empezar de nuevo)');
      continue;
    }

    // Dueño: alta por la API, correo verificado en la base (no recibe correo).
    exigir(await api('POST', '/api/users/register', { nombre: g.dueno, email: correoDueno, password: CLAVE, aceptaPrivacidad: true, privacidadVersion: '1' }), 'registrar dueño');
    await users.updateOne({ email: correoDueno }, { $set: { emailVerificado: true } });
    const dueno = exigir(await api('POST', '/api/users/login', { email: correoDueno, password: CLAVE }), 'entrar como dueño');
    const t = dueno.token;
    const hashClave = (await users.findOne({ email: correoDueno })).password;

    const datosGym = {
      nombre: g.nombre, telefono: g.telefono, direccion: g.direccion, horario: g.horario, equipamiento: g.equipamiento,
      colorPrimario: g.color, enDirectorio: true, cuotaMensual: g.cuota, razonSocial: g.razon, correoContacto: `contacto.${g.clave}${DOMINIO}`
    };
    const gym = exigir(await api('POST', '/api/gyms', datosGym, t), 'crear gimnasio');
    const subir = async (id, ancho) => exigir(await api('POST', `/api/gyms/${gym.id}/imagenes`, { dataUrl: await foto(id, ancho) }, t), 'subir imagen').url;

    // Equipo y su foto.
    const equipo = algunos(MAQUINAS, g.maquinas);
    const fotoGym = await subir(g.foto, 1280);
    exigir(await api('POST', '/api/gyms', { ...datosGym, foto: fotoGym, maquinas: equipo.map(m => m.nombre + (azar() < 0.3 ? ' x' + entero(2, 4) : '')) }, t), 'foto del gimnasio');
    for (const m of equipo) {
      exigir(await api('POST', `/api/gyms/${gym.id}/machines`, { nombre: m.nombre, zona: m.zona, cantidad: entero(1, 4), ejercicios: m.ejercicios }, t), 'máquina');
    }
    console.log(`  ${equipo.length} máquinas`);

    // Rutinas.
    const rutinas = algunos(RUTINAS, entero(5, 7));
    for (const r of rutinas) exigir(await api('POST', `/api/gyms/${gym.id}/routines`, { ...r, coach: g.coach }, t), 'rutina');
    console.log(`  ${rutinas.length} rutinas`);

    // Planes, con precios a partir de la cuota.
    const planes = [];
    for (const p of PLANES) {
      const precio = Math.round(g.cuota * p.factor / 10) * 10;
      planes.push(exigir(await api('POST', `/api/gyms/${gym.id}/planes`, { nombre: p.nombre, descripcion: p.descripcion, precio,
        duracionUnidad: p.unidad, duracionCantidad: p.cantidad, beneficios: p.beneficios, destacado: !!p.destacado, activo: true, pagoUnico: false }, t), 'plan'));
    }

    // Productos con foto; precios con una pequeña variación por gimnasio.
    const productos = algunos(PRODUCTOS, entero(8, 11));
    for (const p of productos) {
      const imagen = await subir(p.foto, 700);
      const ajuste = 1 + (indice - 2) * 0.03;
      exigir(await api('POST', `/api/gyms/${gym.id}/productos`, {
        nombre: p.nombre, descripcion: p.descripcion, categoria: p.categoria, imagen, activo: true, marca: p.marca || undefined,
        variantes: p.variantes.map(([presentacion, sabor, precio]) => ({ presentacion, sabor: sabor || undefined,
          precio: Math.round(precio * ajuste), controlarInventario: true, existencias: entero(60, 140) }))
      }, t), 'producto ' + p.nombre);
    }
    console.log(`  ${planes.length} planes y ${productos.length} productos`);

    // Miembros: los da de alta el dueño; luego tienen la contraseña de demostración.
    const usados = new Set();
    const miembros = [];
    for (let i = 0, n = entero(6, 8); i < n; i++) {
      let nombre;
      do nombre = `${uno(NOMBRES)} ${uno(APELLIDOS)} ${uno(APELLIDOS)}`; while (usados.has(nombre.split(' ').slice(0, 2).join(' ')));
      usados.add(nombre.split(' ').slice(0, 2).join(' '));
      const [n1, a1] = quitarAcentos(nombre).toLowerCase().split(' ');
      const email = `${n1}.${a1}.${g.clave}${DOMINIO}`;
      const m = exigir(await api('POST', `/api/gyms/${gym.id}/members`, { nombre, email }, t), 'alta de miembro');
      miembros.push({ id: m.id, nombre, email });
    }
    await users.updateMany({ email: { $in: miembros.map(m => m.email) } },
      { $set: { password: hashClave, contrasenaTemporal: false, emailVerificado: true, privacidadVersion: '1', privacidadAceptadaEn: new Date() } });
    for (const m of miembros) m.token = exigir(await api('POST', '/api/users/login', { email: m.email, password: CLAVE }), 'entrar como miembro').token;
    miembrosTotales += miembros.length;
    console.log(`  ${miembros.length} miembros`);

    // Pagos de membresía: de 1 a 3 en los últimos 90 días, el último reciente.
    const mensual = planes.find(p => p.nombre === 'Mensual');
    for (const m of miembros) {
      const cuantos = entero(1, 3);
      for (let k = cuantos - 1; k >= 0; k--) {
        const dias = k * 30 + entero(0, 20);
        const fecha = new Date(Date.now() - dias * 86400000).toISOString().slice(0, 10);
        const plan = azar() < 0.8 ? mensual : uno(planes.slice(2));
        await api('POST', `/api/gyms/${gym.id}/members/${m.id}/payments`, { monto: plan.precioDesde ?? plan.variantes?.[0]?.precio ?? g.cuota,
          metodo: uno(['Efectivo', 'Efectivo', 'Transferencia', 'Tarjeta']), fechaPago: fecha, planId: plan.id, nota: 'Datos de demostración' }, t);
      }
    }

    // Ventas de mostrador; después se reparten en los últimos 90 días.
    const catalogo = exigir(await api('GET', `/api/tienda/${gym.id}/productos`, null, t), 'catálogo').filter(p => p.tipo !== 'membresia');
    const favoritos = algunos(catalogo, 3);
    const ventas = entero(28, 42);
    const vendidoA = new Map();
    for (let i = 0; i < ventas; i++) {
      const partidas = [];
      for (let k = 0, n = entero(1, 3); k < n; k++) {
        const p = azar() < 0.55 ? uno(favoritos) : uno(catalogo);
        const v = uno(p.variantes);
        if (!partidas.some(x => x.varianteId === v.id)) partidas.push({ varianteId: v.id, cantidad: v.precio < 60 ? entero(1, 4) : 1, precio: v.precio, productoId: p.id });
      }
      const total = Math.round(partidas.reduce((s, x) => s + x.precio * x.cantidad, 0) * 100) / 100;
      const cliente = azar() < 0.7 ? uno(miembros) : null;
      const r = await api('POST', `/api/gyms/${gym.id}/mostrador/cobrar`, { partidas: partidas.map(({ varianteId, cantidad }) => ({ varianteId, cantidad })),
        clienteId: cliente ? cliente.id : null, metodo: 'efectivo', recibido: Math.ceil(total / 50) * 50 + (azar() < 0.4 ? 50 : 0), totalVisto: total }, t);
      if (r.status === 200 && cliente) {
        const lista = vendidoA.get(cliente.id) || new Set();
        partidas.forEach(x => lista.add(x.productoId));
        vendidoA.set(cliente.id, lista);
      }
      if (r.status === 200) ventasTotales++;
    }
    console.log(`  ${ventas} ventas de mostrador`);

    // Reseñas de lo que compró cada miembro.
    let resenas = 0;
    for (const m of miembros) {
      for (const productoId of [...(vendidoA.get(m.id) || [])].slice(0, 2)) {
        if (azar() < 0.6) {
          const [estrellas, comentario] = uno(RESENAS);
          const r = await api('PUT', `/api/tienda/${gym.id}/productos/${productoId}/resenas/mia`, { estrellas, comentario }, m.token);
          if (r.status === 200) resenas++;
        }
      }
    }

    // Opiniones sobre GymTrack: el dueño y algunos miembros.
    exigir(await api('PUT', '/api/opiniones/mia', { estrellas: azar() < 0.7 ? 5 : 4, texto: OPINIONES_DUENO[indice] }, t), 'opinión del dueño');
    const opinan = algunos(miembros, entero(2, 3));
    for (const m of opinan) {
      const [texto, estrellas] = uno(OPINIONES_MIEMBRO);
      await api('PUT', '/api/opiniones/mia', { estrellas, texto }, m.token);
    }
    console.log(`  ${resenas} reseñas de productos y ${opinan.length + 1} opiniones`);
  }

  // Reparte en los últimos 90 días lo que se creó ahora: ventas (y su bitácora)
  // y opiniones. Más ventas en las semanas recientes, en horario de gimnasio.
  const demo = await idsDemo(db);
  // Cada gimnasio reparte sus propias ventas (en orden de folio), y las dos
  // últimas quedan en el día de hoy.
  for (const gymId of demo.gyms) {
    const pedidos = await db.collection('pedidos').find({ gymId, creadoEn: { $gte: inicio } }).sort({ folio: 1 }).toArray();
    const fechas = pedidos.map((_, i) => {
      if (i >= pedidos.length - 2) return new Date(Date.now() - entero(1, 6) * 3600000);
      const dias = 1 + Math.floor(Math.pow(azar(), 1.6) * 88);
      const f = new Date(Date.now() - dias * 86400000);
      f.setUTCHours(entero(12, 27) % 24, entero(0, 59), entero(0, 59), 0);   // 6:00 a 21:59 en México
      return f;
    }).sort((a, b) => a - b);
    for (const [i, p] of pedidos.entries()) {
      await db.collection('pedidos').updateOne({ _id: p._id }, { $set: { creadoEn: fechas[i], pagadoEn: fechas[i], ...(p.entregadoEn ? { entregadoEn: fechas[i] } : {}) } });
    }
  }
  for (const o of await db.collection('opiniones').find({ userId: { $in: demo.users } }).toArray()) {
    const f = new Date(Date.now() - entero(1, 60) * 86400000);
    await db.collection('opiniones').updateOne({ _id: o._id }, { $set: { creadaEn: f, actualizadaEn: f } });
  }
  console.log(`\nListo: ${GIMNASIOS.length} gimnasios, ${miembrosTotales} miembros y ${ventasTotales} ventas repartidas en 90 días.`);
  console.log(`Cuentas: dueno.<gimnasio>${DOMINIO} (maximus, alfa, lunas, spartans, vikings, titanium) y sus miembros, con la contraseña DEMO_PASSWORD.`);
}

// ═══════════════════════════ BORRAR ═══════════════════════════

async function idsDemo(db) {
  const users = await db.collection('users').find({ email: { $regex: DOMINIO.replace('.', '\\.') + '$' } }, { projection: { _id: 1 } }).toArray();
  const ids = users.map(u => String(u._id));
  const gyms = await db.collection('gyms').find({ ownerId: { $in: ids } }, { projection: { _id: 1 } }).toArray();
  return { users: ids, userOids: users.map(u => u._id), gyms: gyms.map(g => String(g._id)), gymOids: gyms.map(g => g._id) };
}

async function borrar(db) {
  const d = await idsDemo(db);
  if (!d.users.length) { console.log('No hay datos de demostración.'); return; }
  const porGym = { gymId: { $in: d.gyms } };
  const porUsuario = { userId: { $in: d.users } };
  const borrados = {};
  for (const [col, filtro] of [
    ['machines', porGym], ['routines', porGym], ['productos', porGym], ['pedidos', porGym], ['payments', porGym], ['imagenes', porGym],
    ['resenas', porGym], ['carritos', porUsuario], ['opiniones', porUsuario], ['sesiones', porUsuario], ['tokens_cuenta', porUsuario],
    ['bitacora', { $or: [porGym, porUsuario] }], ['workouts', porUsuario],
  ]) {
    borrados[col] = (await db.collection(col).deleteMany(filtro)).deletedCount;
  }
  borrados.gyms = (await db.collection('gyms').deleteMany({ _id: { $in: d.gymOids } })).deletedCount;
  borrados.users = (await db.collection('users').deleteMany({ _id: { $in: d.userOids } })).deletedCount;
  console.log('Borrado:', Object.entries(borrados).filter(([, n]) => n).map(([c, n]) => `${c} ${n}`).join(', '));
}

const cliente = new MongoClient(MONGO);
try {
  await cliente.connect();
  const db = cliente.db();
  console.log(`${accion === 'sembrar' ? 'Sembrando' : 'Borrando'} datos de demostración en ${API} (base ${db.databaseName})`);
  if (accion === 'sembrar') await sembrar(db); else await borrar(db);
} catch (e) {
  console.error('\nError:', e.message);
  process.exitCode = 1;
} finally {
  await cliente.close();
}
