// Prueba del bloque 2 (API del miembro) contra Spring y MongoDB locales.
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
const { gymId, owner, member, planes, prod } = n;
const sufijo = Date.now().toString(36);

console.log('\n1. Catálogo del miembro');
let r = await api('GET', `/api/tienda/${gymId}/productos`, null, member);
ok(r.status === 200, 'listado', r.body.map?.(p => `${p.tipo}:${p.nombre}`));
ok(r.body[0].tipo === 'membresia', 'los planes van primero');
const whey = r.body.find(p => p.nombre.startsWith('Proteína'));
ok(whey && whey.variantes.some(v => v.disponible != null), 'variantes con disponible', whey?.variantes.map(v => `${v.nombre}:${v.disponible}`));
r = await api('GET', `/api/tienda/${gymId}/productos?q=proteina`, null, member);
ok(r.body.length === 1, 'búsqueda sin acentos "proteina"', r.body.map(p => p.nombre));
r = await api('GET', `/api/tienda/${gymId}/productos?categoria=membresias`, null, member);
ok(r.body.every(p => p.tipo === 'membresia'), 'filtro por categoría membresías', r.body.length);
r = await api('GET', `/api/tienda/${gymId}/productos/${planes.Trimestral.id}`, null, member);
ok(r.body.plan?.duracionTexto === '3 meses', 'detalle de plan con duración', r.body.plan);

console.log('\n2. Quién puede comprar');
r = await api('GET', '/api/tienda/carrito', null, owner);
ok(r.status === 403, 'el dueño no usa el carrito web', r.body.error);
const pend = await api('POST', '/api/users/register', { nombre: 'Pendiente', email: `pend-${sufijo}@prueba.mx`, password: 'Clave#2026', role: 'member' });
const gym = (await api('GET', `/api/gyms/${gymId}`, null, owner)).body;
await api('POST', `/api/users/${pend.body.id}/membership/join`, { codigo: gym.codigo });
r = await api('GET', `/api/tienda/${gymId}/productos`, null, pend.body.id);
ok(r.status === 200, 'un pendiente puede ver la tienda');
r = await api('POST', '/api/tienda/carrito/items', { varianteId: whey.variantes[0].id }, pend.body.id);
ok(r.status === 403, 'pero no comprar', r.body.error);
r = await api('GET', `/api/tienda/${gymId}/productos`, null, 'usuario-que-no-existe');
ok(r.status === 401, 'usuario inexistente → 401');

console.log('\n3. Carrito');
await api('DELETE', '/api/tienda/carrito', null, member);
const bote = whey.variantes.find(v => v.controlarInventario && v.disponible > 2);
r = await api('POST', '/api/tienda/carrito/items', { varianteId: bote.id, cantidad: 2 }, member);
ok(r.status === 200 && r.body.articulos === 2, 'agregar 2 botes', r.body.total);
const cartId = r.body.id;
r = await api('GET', '/api/tienda/carrito', null, member);
ok(r.body.id === cartId && r.body.articulos === 2, 'otro dispositivo ve el mismo carrito', r.body.id);
r = await api('GET', '/api/tienda/carrito?canal=app', null, member);
ok(r.body.articulos === 0, 'la app tiene su propio carrito', r.body.canal);
r = await api('POST', '/api/tienda/carrito/items', { varianteId: planes.Mensual.varianteId }, member);
ok(r.status === 200, 'agregar plan Mensual', r.body.items?.map(i => `${i.titulo}/${i.variante}`));
r = await api('POST', '/api/tienda/carrito/items', { varianteId: planes.Trimestral.varianteId }, member);
ok(r.status === 409, 'un segundo plan → 409', r.body.error);
r = await api('POST', '/api/tienda/carrito/items', { varianteId: planes.Mensual.varianteId }, member);
ok(r.status === 409, 'el mismo plan dos veces → 409', r.body.error);
r = await api('POST', '/api/tienda/carrito/items', { varianteId: planes['Inscripción'].varianteId }, member);
ok(r.status === 200, 'la inscripción sí se puede junto al plan', r.body.articulos);
const itemPlan = r.body.items.find(i => i.titulo === 'Mensual');
r = await api('PATCH', `/api/tienda/carrito/items/${itemPlan.id}`, { cantidad: 2 }, member);
ok(r.status === 400, 'plan con cantidad 2 → 400', r.body.error);
const itemBote = (await api('GET', '/api/tienda/carrito', null, member)).body.items.find(i => i.varianteId === bote.id);
r = await api('PATCH', `/api/tienda/carrito/items/${itemBote.id}`, { cantidad: bote.disponible + 5 }, member);
ok(r.status === 409, 'más piezas de las disponibles → 409', r.body.error);
r = await api('PATCH', `/api/tienda/carrito/items/${itemBote.id}`, { cantidad: 1 }, member);
ok(r.status === 200 && r.body.items.find(i => i.id === itemBote.id).cantidad === 1, 'cambiar a 1 pieza');

console.log('\n4. Cambios del dueño mientras está en el carrito');
const p = (await api('GET', `/api/gyms/${gymId}/productos/${prod}`, null, owner)).body;
const precioAntes = p.variantes.find(v => v.id === bote.id).precio;
await api('PUT', `/api/gyms/${gymId}/productos/${prod}`, { ...p, categoria: p.categoria.handle, variantes: p.variantes.map(v => ({ ...v, precio: v.id === bote.id ? precioAntes + 50 : v.precio })) }, owner);
r = await api('GET', '/api/tienda/carrito', null, member);
const nuevo = r.body.items.find(i => i.varianteId === bote.id)?.precioUnitario;
ok(r.body.avisos.length === 1 && nuevo === precioAntes + 50, 'aviso de cambio de precio y precio actualizado', { avisos: r.body.avisos, nuevo });
const totalVisto = r.body.total;
const agua = (await api('POST', `/api/gyms/${gymId}/productos`, { nombre: 'Chicles', categoria: 'snacks', variantes: [{ precio: 10, existencias: 5 }] }, owner)).body;
await api('POST', '/api/tienda/carrito/items', { varianteId: agua.variantes[0].id }, member);
await api('PUT', `/api/gyms/${gymId}/productos/${agua.id}`, { nombre: 'Chicles', categoria: 'snacks', activo: false, variantes: agua.variantes.map(v => ({ ...v, existencias: 5 })) }, owner);
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: 'tok_prueba' }, totalVisto: totalVisto + 10 }, member);
ok(r.status === 409 && r.body.avisos?.some(a => a.includes('Chicles')), 'producto ocultado → 409 con aviso y carrito corregido', r.body.avisos);
const totalCorregido = r.body.carrito.total;
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: {}, totalVisto: totalCorregido + 1 }, member);
ok(r.status === 409, 'total distinto al que vio → 409', r.body.error);

console.log('\n5. Checkout');
const antes = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'efectivo', datos: {}, totalVisto: totalCorregido }, member);
ok(r.status === 400, 'método no válido en línea → 400', r.body.error);
const tok = (await api('POST', '/api/simuladores/stripe/tokens', { numero: '4242424242424242', titular: 'Ana', mes: '12', anio: '30', cvc: '123' }, member)).body.token;
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'stripe', datos: { token: tok }, totalVisto: totalCorregido }, member);
ok(r.status === 200 && r.body.estado === 'pagado', 'pago con tarjeta simulada', { folio: r.body.folio, estado: r.body.estadoTexto, total: r.body.total, metodo: r.body.metodo });
const pedido = r.body;
const despues = (await api('GET', `/api/users/${member}/me`)).body;
ok(despues.fechaProximoPago !== antes && despues.planActual === 'Mensual', 'membresía extendida al instante (sin esperar el aviso)', { antes, despues: despues.fechaProximoPago });
r = await api('GET', '/api/tienda/carrito', null, member);
ok(r.body.articulos === 0 && r.body.id === null, 'carrito nuevo y vacío después de pagar');
r = await api('GET', '/api/tienda/pedidos', null, member);
ok(r.body[0]?.orderId === pedido.orderId, 'aparece en Mis pedidos', r.body.length);
r = await api('GET', `/api/tienda/pedidos/${pedido.orderId}`, null, pend.body.id);
ok(r.status === 404, 'otra persona no ve el pedido → 404');
r = await api('GET', `/api/tienda/pedidos/${pedido.orderId}`, null, owner);
ok(r.status === 200, 'el dueño del gimnasio sí lo ve');

console.log('\n6. Paynet pendiente');
await api('POST', '/api/tienda/carrito/items', { varianteId: planes.Visita.varianteId }, member);
const c = (await api('GET', '/api/tienda/carrito', null, member)).body;
r = await api('POST', '/api/tienda/carrito/checkout', { metodo: 'paynet', datos: {}, totalVisto: c.total }, member);
ok(r.status === 200 && r.body.estado === 'pendiente_pago', 'pedido Paynet queda pendiente de pago', r.body.estadoTexto);
const otra = (await api('GET', `/api/users/${member}/me`)).body.fechaProximoPago;
ok(otra === despues.fechaProximoPago, 'y no mueve la membresía');

console.log(fallos ? `\n${fallos} prueba(s) fallaron` : '\nTodo bien');
