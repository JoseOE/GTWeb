// Prueba manual de los pagos reales (PAGOS_MODO=sandbox): crea gimnasio,
// producto y miembro, y recorre Stripe Checkout, PayPal y Paynet (Openpay)
// contra sus APIs de prueba hasta donde se puede sin navegador. No está en el
// CI: necesita las llaves de los tres proveedores en el .env.
//   node pruebas/sandbox.mjs
import { API, leerLog } from './entorno.mjs';
const api = async (m, r, b, t) => { const res = await fetch(API + r, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined }); let j = null; try { j = await res.json(); } catch {} return { s: res.status, b: j }; };
let fallos = 0;
const ok = (c, msg, extra) => { console.log((c ? '  ✔ ' : '  ✘ ') + msg + (extra !== undefined ? ' → ' + JSON.stringify(extra).slice(0, 220) : '')); if (!c) fallos++; };
const modo = (await api('GET', '/api/pagos/modo')).b;
if (!modo || modo.modo !== 'sandbox') {
  console.error('Spring no está en PAGOS_MODO=sandbox: esta prueba usa las APIs reales de Stripe, PayPal y Openpay.');
  process.exit(2);
}
const suf = Date.now().toString(36), correo = `dueno-${suf}@prueba.mx`;
const CONSENT = { terminosVersion: '1', aceptaTerminos: true, aceptaAdvertencia: false };

await api('POST', '/api/users/register', { nombre: 'Dueño Sandbox', email: correo, password: 'Clave#2026', aceptaPrivacidad: true, privacidadVersion: '1' });
await new Promise(r => setTimeout(r, 500));
const linea = leerLog().split(/\r?\n/).filter(l => l.includes(correo) && /\b\d{6}\b/.test(l)).pop();
const o = (await api('POST', '/api/cuenta/verificar', { email: correo, codigo: linea.match(/\b(\d{6})\b/)[1] })).b;
const gym = (await api('POST', '/api/gyms', { nombre: 'Gym Sandbox', cuotaMensual: 400 }, o.token)).b;
await api('POST', `/api/gyms/${gym.id}/productos`, { nombre: 'Proteína', categoria: 'suplementos', activo: true, requiereAdvertencia: false, variantes: [{ presentacion: 'Scoop', precio: 35, controlarInventario: true, existencias: 10 }] }, o.token);
const socio = (await api('POST', `/api/gyms/${gym.id}/members`, { nombre: 'socio' + suf, email: `socio-${suf}@prueba.mx` }, o.token)).b;
const m = (await api('POST', '/api/users/login', { email: socio.email, password: socio.nombre })).b;
const v = (await api('GET', `/api/tienda/${gym.id}/productos`, null, m.token)).b.find(p => p.nombre === 'Proteína').variantes[0];
const carrito = async (n = 2) => { await api('DELETE', '/api/tienda/carrito?canal=web', null, m.token); return (await api('POST', '/api/tienda/carrito/items?canal=web', { varianteId: v.id, cantidad: n }, m.token)).b; };
const RET = API + '/checkout.html?metodo=x&continuar=1', CAN = API + '/checkout.html';

console.log('\n1. Stripe Checkout');
let c = await carrito(2);
let r = await api('POST', '/api/pagos/stripe/sesiones?canal=web', { returnUrl: RET, cancelUrl: CAN }, m.token);
ok(r.s === 200 && /^https:\/\/checkout\.stripe\.com\//.test(r.b.url) && r.b.id.startsWith('cs_test_'), 'sesión de Stripe por el total del carrito', { monto: r.b?.monto, url: (r.b?.url || '').slice(0, 40) });
const sesion = r.b.id;
r = await api('POST', '/api/tienda/carrito/checkout?canal=web', { metodo: 'stripe', datos: { sesion }, totalVisto: c.total, consentimiento: CONSENT }, m.token);
ok(r.s === 402 && /no se completó/.test(r.b.error), 'cobrar sin pagar en Stripe → 402', r.b?.error);
r = await api('POST', '/api/pagos/stripe/sesiones?canal=web', { returnUrl: 'https://otro-sitio.example/x', cancelUrl: CAN }, m.token);
ok(r.s === 400, 'returnUrl de otro sitio → 400', r.b?.error);
r = await api('POST', '/api/pagos/stripe/sesiones?canal=app', { returnUrl: 'exp://192.168.1.5:8081/--/pago-stripe', cancelUrl: 'exp://192.168.1.5:8081/--/pago-stripe-cancelado' }, m.token);
ok(r.s === 400 && /vacío/.test(r.b?.error || ''), 'la app con su carrito vacío → 400', r.b?.error);

console.log('\n2. PayPal');
r = await api('POST', '/api/pagos/paypal/ordenes?canal=web', { returnUrl: RET, cancelUrl: CAN }, m.token);
ok(r.s === 200 && /sandbox\.paypal\.com/.test(r.b.aprobarUrl), 'orden de PayPal con su página de aprobación', { id: r.b?.id, monto: r.b?.monto, url: (r.b?.aprobarUrl || '').slice(0, 45) });
const orden = r.b.id;
r = await api('POST', '/api/tienda/carrito/checkout?canal=web', { metodo: 'paypal', datos: { token: orden }, totalVisto: c.total, consentimiento: CONSENT }, m.token);
ok(r.s === 400 && /apruebas/.test(r.b.error), 'cobrar sin aprobar en PayPal → 400', r.b?.error);
r = await api('POST', '/api/tienda/carrito/checkout?canal=web', { metodo: 'paypal', datos: { token: orden }, totalVisto: c.total, consentimiento: CONSENT }, m.token);
ok(r.s === 400 && /ya se usó/.test(r.b.error), 'la misma orden no se reusa → 400', r.b?.error);

console.log('\n3. Paynet (Openpay)');
c = await carrito(1);
r = await api('POST', '/api/tienda/carrito/checkout?canal=web', { metodo: 'paynet', datos: {}, totalVisto: c.total, consentimiento: CONSENT }, m.token);
ok(r.s === 200 && r.b.estado === 'pendiente_pago' && r.b.paynet && r.b.paynet.fichaUrl, 'ficha real de Openpay', { estado: r.b?.estado, ref: r.b?.paynet?.referencia, ficha: (r.b?.paynet?.fichaUrl || '').slice(0, 50) });
const ped = r.b;
r = await api('GET', `/api/tienda/pedidos/${ped.orderId}`, null, m.token);
ok(r.s === 200 && r.b.estado === 'pendiente_pago', 'al abrir el pedido se revisa en Openpay: sigue pendiente', r.b?.estado);
const pdf = await fetch(`${API}/api/recibos/paynet/${ped.orderId}.pdf`, { headers: { Authorization: 'Bearer ' + m.token } });
ok(pdf.status === 200, 'la ficha en PDF de GymTrack sale', pdf.status);
r = await api('POST', '/api/webhooks/openpay', { type: 'verification', verification_code: 'ABC123' });
ok(r.s === 200, 'el webhook de Openpay responde (verificación)', r.b);

console.log('\n4. Mostrador con tarjeta');
r = await api('POST', `/api/gyms/${gym.id}/mostrador/stripe/sesion`, { partidas: [{ varianteId: v.id, cantidad: 1 }], totalVisto: 35, returnUrl: API + '/bienvenido.html?cobro=tarjeta', cancelUrl: API + '/bienvenido.html?cobro=cancelado' }, o.token);
ok(r.s === 200 && /checkout\.stripe\.com/.test(r.b.url), 'sesión de Stripe para el ticket del mostrador', { monto: r.b?.monto });
r = await api('POST', `/api/gyms/${gym.id}/mostrador/stripe/sesion`, { partidas: [{ varianteId: v.id, cantidad: 1 }], totalVisto: 35 }, m.token);
ok(r.s === 403, 'un miembro no abre cobros del mostrador → 403', r.s);

console.log('\n5. Mostrador con PayPal y pago en tienda');
const ticket = { partidas: [{ varianteId: v.id, cantidad: 1 }], totalVisto: 35 };
r = await api('POST', `/api/gyms/${gym.id}/mostrador/paypal/orden`, { ...ticket, returnUrl: API + '/bienvenido.html?cobro=paypal', cancelUrl: API + '/bienvenido.html?cobro=cancelado&metodo=paypal' }, o.token);
ok(r.s === 200 && /sandbox\.paypal\.com/.test(r.b.aprobarUrl), 'orden de PayPal para el ticket del mostrador', { monto: r.b?.monto });
r = await api('POST', `/api/gyms/${gym.id}/mostrador/cobrar`, { ...ticket, metodo: 'paypal', datos: { token: r.b.id } }, o.token);
ok(r.s === 400 && /apruebas/.test(r.b.error), 'cobrar sin aprobar en PayPal → 400', r.b?.error);
r = await api('POST', `/api/gyms/${gym.id}/mostrador/paypal/orden`, { ...ticket, returnUrl: API + '/x', cancelUrl: API + '/x' }, m.token);
ok(r.s === 403, 'un miembro no abre la orden del mostrador → 403', r.s);
r = await api('POST', `/api/gyms/${gym.id}/mostrador/cobrar`, { ...ticket, metodo: 'tienda', clienteId: socio.id }, o.token);
const ficha = r.b?.pedido;
ok(r.s === 200 && ficha.estado === 'pendiente_pago' && ficha.paynet?.fichaUrl && ficha.entrega.estado === 'no_aplica',
  'pago en tienda: ficha real de Openpay, pendiente y sin entregar', { estado: ficha?.estado, ref: ficha?.paynet?.referencia, entrega: ficha?.entrega?.estado });
r = await api('POST', `/api/gyms/${gym.id}/ventas/pedidos/${ficha.orderId}/entregar`, null, o.token);
ok(r.s === 409 && /todavía no está pagado/.test(r.b.error), 'no se entrega antes de pagar la ficha → 409', r.b?.error);

console.log('\n6. PayPal: simular un rechazo (PayPal-Mock-Response del sandbox)');
r = await api('GET', '/api/pagos/modo');
ok(r.b?.simularPaypal === true, 'con PayPal en sandbox se puede simular', r.b);
c = await carrito(1);
for (const [simular, patron, que] of [['sin_fondos', /fondos suficientes/, 'sin fondos (INSTRUMENT_DECLINED)'], ['cuenta_invalida', /no puede pagar/, 'cuenta inválida (PAYER_CANNOT_PAY)']]) {
  const o = (await api('POST', '/api/pagos/paypal/ordenes?canal=web', { returnUrl: RET, cancelUrl: CAN }, m.token)).b;
  r = await api('POST', '/api/tienda/carrito/checkout?canal=web', { metodo: 'paypal', datos: { token: o.id, simular }, totalVisto: c.total, consentimiento: CONSENT }, m.token);
  ok(r.s === 402 && patron.test(r.b?.error), 'PayPal responde ' + que + ' y no se cobra', r.b?.error);
}

console.log(fallos ? `\n${fallos} prueba(s) fallaron`
  : '\nTodo bien. Falta lo que pide navegador: pagar en la página de Stripe, aprobar en PayPal y simular la ficha en Openpay (ver el README).');
process.exit(fallos ? 1 : 0);
