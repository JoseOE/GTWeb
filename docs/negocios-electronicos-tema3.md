# Negocios Electrónicos I — Tema 3: Gestión y seguridad de datos

Desarrollo tecnológico de la tienda en línea de **GymTrack**: cada gimnasio vende sus productos (suplementos, bebidas, snacks) y sus planes de membresía a sus miembros.

| | |
|---|---|
| Sitio completo (página + API) | https://gtweb.onrender.com |
| Versión estática (GitHub Pages) | https://joseoe.github.io/GTWeb/ |
| Repositorio web | https://github.com/JoseOE/GTWeb |
| Repositorio de la app móvil | https://github.com/JoseOE/GTApp |

Este documento recorre la guía de observación punto por punto: primero el análisis de los cuatro módulos (criterios 02–05), luego dónde se cumple cada criterio de desarrollo (06–18) y al final las decisiones de diseño que cambian cómo se aplica alguno.

---

## 1. Análisis de los módulos (criterios 02–05)

### Modelo de datos

La tienda vive en **MongoDB Atlas** (NoSQL, documentos). Cada documento guarda junto lo que se lee junto: un pedido trae sus partidas y el resultado del pago, y un producto trae sus variantes. Así una pantalla se arma con una sola consulta.

| Colección | Qué guarda | Campos principales |
|---|---|---|
| `users` | Cuentas (dueños y miembros) | `nombre`, `email`, `password` (BCrypt), `role`, `gymId`, `membershipStatus`, `fechaProximoPago`, `privacidadVersion`, `privacidadAceptadaEn` |
| `gyms` | Gimnasio y datos del vendedor | `nombre`, `codigo`, `ownerId`, `razonSocial`, `rfc`, `correoContacto` |
| `productos` | Catálogo de cada gimnasio | `gymId`, `tipo` (producto o membresía), `categoria`, `nombre`, `marca`, `proveedor`, `requiereAdvertencia`, `activo`, `variantes[]` (`precio`, `existencias`, `apartadas`) |
| `carritos` | Un carrito por miembro, gimnasio y canal | `userId`, `gymId`, `canal`, `items[]` (`varianteId`, `cantidad`, `precioUnitario`), `version`, `cobrandoDesde` |
| `pedidos` | Compras | `orderId`, `folio`, `estado`, `proveedorPago`, `datosPago`, `partidas[]`, `subtotal`, `iva`, `total`, `consentimiento`, `entregadoEn` |
| `resenas` | Reseñas de productos comprados | `productoId`, `userId`, `estrellas`, `comentario`, `oculta` |
| `payments` | Mensualidades cobradas | `userId`, `gymId`, `monto`, `metodo`, `cubreHasta`, `orderId` |
| `sesiones` | Sesiones abiertas | `_id` = SHA-256 del token, `userId`, `expiraEn` (TTL) |
| `tokens_cuenta` | Códigos y enlaces del correo | `codigoHash`, `enlaceHash`, `intentos`, `expira` (TTL) |
| `tokens_tarjeta`, `ordenes_paypal`, `vinculos_paypal` | Simuladores de pago | Token de un solo uso, orden aprobada, cuenta vinculada |

Antes, la tienda corría en **Medusa.js con PostgreSQL** (modelo relacional, SQL). Se migró a MongoDB sin cambiar la API: la tienda se volvió más rápida y se quitó un servicio que costaba memoria en Render. `MigracionMedusaService` pasa los pedidos de las tablas de Postgres a documentos.

### 02. Catálogo y búsqueda de productos

- **Objetivo:** que el miembro encuentre lo que vende su gimnasio y sepa, antes de comprar, cuánto cuesta, si hay piezas y quién se lo vende.
- **Actores:** el dueño (da de alta productos y planes en el panel) y el miembro (los consulta en la tienda web o en la app).
- **Flujo:**
  1. El dueño crea el producto con sus variantes (presentación, sabor, precio y existencias).
  2. El miembro entra a la tienda de su gimnasio.
  3. Busca por texto o filtra por categoría.
  4. Abre la ficha y elige la variante.
- **Reglas:**
  - solo se ven productos `activo` del gimnasio del miembro;
  - los precios incluyen IVA;
  - "Agotado" o "Quedan N" se calcula como existencias menos apartadas;
  - los suplementos piden la advertencia sanitaria.
- **API:** `GET /api/tienda/{gymId}/productos?categoria=&q=`, `GET /api/tienda/{gymId}/productos/{id}`, `GET /api/tienda/categorias`.

### 03. Gestión de cuenta de usuario

- **Objetivo:** que cada persona controle su cuenta y sus datos.
- **Flujo:**
  1. Registro con nombre, correo y contraseña segura, más la casilla del Aviso de privacidad.
  2. Verificación del correo con un código de 6 dígitos.
  3. Inicio de sesión, que entrega un token.
  4. Desde Mi cuenta: cambiar la contraseña o el correo (los dos con código), ver los pedidos y cerrar sesión.
- **Reglas:**
  - la contraseña lleva al menos 8 caracteres, con una letra, un número y un símbolo;
  - el correo se reconoce sin distinguir mayúsculas;
  - después de 10 contraseñas incorrectas, ese correo espera 15 minutos;
  - quien entra con la contraseña temporal de recepción debe cambiarla;
  - al cambiar la contraseña se cierran las demás sesiones.
- **API:** `POST /api/users/register`, `POST /api/users/login`, `POST /api/cuenta/verificar`, `POST /api/cuenta/recuperar`, `POST /api/cuenta/restablecer/codigo`, `GET /api/tienda/pedidos`.

### 04. Carrito de compras

- **Objetivo:** juntar lo que se va a comprar y mostrar siempre el total real.
- **Flujo:**
  1. Agregar una variante.
  2. Cambiar la cantidad o quitarla.
  3. Ver el desglose.
  4. Ir a pagar.
- **Reglas:**
  - nunca se piden más piezas de las disponibles (máximo 20 por partida);
  - se lleva un solo plan por compra;
  - antes de mostrar el carrito y antes de cobrar, se compara contra el catálogo: si algo se agotó, se ocultó o cambió de precio, se corrige y se avisa en lugar de cobrar otro total;
  - el carrito es el mismo en cualquier dispositivo;
  - dos sesiones cambiándolo a la vez no pierden cambios, gracias al control de versión.
- **API:** `GET/POST/DELETE /api/tienda/carrito`, `POST /api/tienda/carrito/items`, `PATCH/DELETE /api/tienda/carrito/items/{id}`.

### 05. Proceso de pago (checkout)

- **Objetivo:** cobrar el total que la persona vio, con su consentimiento, y entregarle su comprobante en ese momento.
- **Flujo:**
  1. Elegir la forma de pago: tarjeta (en la página de Stripe), PayPal o efectivo en tiendas (ficha de Paynet).
  2. Si el carrito lleva suplementos, aceptar la advertencia sanitaria.
  3. En **¿Confirmas tu compra?**, revisar el desglose, la forma de pago, la entrega y quién vende, y aceptar los Términos y el Aviso de privacidad.
  4. Pagar.
  5. Ver la confirmación con folio y QR para recoger, y recibir el recibo en PDF por correo.
- **Reglas:**
  - el servidor vuelve a calcular el total, y si no coincide con el que se vio, no cobra;
  - un doble clic no cobra dos veces, porque el carrito se bloquea mientras se cobra;
  - el pedido guarda qué versión de los términos se aceptó y cuándo;
  - la ficha de efectivo vence a las 72 horas y libera lo apartado;
  - en recepción solo se entregan pedidos pagados.
- **API:** `POST /api/pagos/stripe/sesiones` (Stripe Checkout), `POST /api/pagos/paypal/ordenes` (PayPal Orders v2), `POST /api/tienda/carrito/checkout` (cobra: captura en Stripe o PayPal, o pide la ficha a Openpay), `POST /api/webhooks/openpay`, `GET /api/recibos/pedidos/{id}.pdf`.

---

## 2. Dónde se cumple cada criterio

| # | Criterio | Evidencia en GymTrack |
|---|---|---|
| 06 | Catálogo: precios netos, stock y garantías | Ficha del producto (`producto.html`): precio con la leyenda "IVA incluido", estado Disponible, Quedan N o Agotado, y una fila **Garantía** con la política de cambios y el enlace a Términos. |
| 07 | Cuenta: aviso de privacidad, opt-in e historial | `privacidad.html` es el aviso integral: responsable, datos, finalidades, transferencias, derechos ARCO y cambios. El opt-in es una casilla obligatoria al crear la cuenta, y el servidor guarda la versión y la fecha (`privacidadVersion`, `privacidadAceptadaEn`); al pagar se acepta otra vez. El historial está en `pedidos.html` (Mis pedidos), con estado, QR y recibos. |
| 08 | Carrito: desglose, cambios y políticas | Subtotal sin IVA, IVA 16 % y total, con botones + y − que recalculan al momento. El pie de la tienda enlaza a Términos y al Aviso de privacidad. |
| 09 | Pago: cifrado, PCI-DSS y confirmación | Todo va por HTTPS. La tarjeta se escribe en la página de Stripe (Stripe Checkout) y nunca llega a GymTrack: solo se guardan la marca y los últimos 4 dígitos que devuelve Stripe. La confirmación (`confirmacion.html`) y el recibo por correo llegan al momento. Ver la [decisión 1](#decisiones-de-diseño). |
| 10 | Reseñas después de comprar | "Califica tu compra" en Mis pedidos: de 1 a 5 estrellas y un comentario, solo con una compra pagada de ese producto. Hay una reseña por producto, se puede editar, y el dueño puede ocultarla. |
| 11 | Frontend + backend + APIs | Spring Boot expone la API REST y sirve las páginas; las páginas usan `fetch`. Se usan MongoDB Atlas, Brevo (correo), iText (PDF) y los simuladores de pago, y la app móvil consume la misma API. |
| 12 | Repositorio con Git | Una rama por integrante, PRs a `main` y revisión automática en cada PR (`.github/workflows/ci.yml`: compila y corre las pruebas de punta a punta). |
| 13 | Enlace al hosting | https://gtweb.onrender.com |
| 14 | Credenciales de APIs | `MONGODB_URI` (MongoDB Atlas), `BREVO_API_KEY`, las llaves de Stripe (`STRIPE_SECRET_KEY`), PayPal (`PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`) y Openpay/Paynet (`OPENPAY_MERCHANT_ID`, `OPENPAY_PRIVATE_KEY`) viven como variables de entorno en Render, nunca en el repositorio (ver `.env.example`). Las de pago son del modo de prueba (sandbox) de cada proveedor. |
| 15 | GitHub Pages | Lo publica `.github/workflows/deploy-pages.yml` en https://joseoe.github.io/GTWeb/. Ver la [decisión 2](#decisiones-de-diseño). |
| 16 | Métodos seguros para los datos | Ver la sección 3. |
| 17 | Compatibilidad entre navegadores | Bootstrap 5 y APIs estándar, con alternativas donde un navegador no tiene algo: compartir con Web Share o, si no existe, con WhatsApp o correo; escanear con la cámara o escribir el folio. Ver la tabla de pruebas en la sección 4. |
| 18 | Google Analytics | GA4 (`G-G24WE0GZ4K`) en `js/analitica.js`, cargado en las páginas públicas y de la tienda. Solo se activa si la persona acepta el aviso de cookies, y se puede cambiar desde el Aviso de privacidad. Además de las visitas, mide el embudo de compra con los eventos de comercio electrónico de GA4: `view_item`, `add_to_cart`, `begin_checkout` y `purchase` (con folio, total, IVA y productos; nunca datos personales). Se revisa en Analytics → Informes → Tiempo real y en Monetización → Compras de comercio electrónico. |

## 3. Seguridad de los datos (criterio 16)

- **Contraseñas** con BCrypt. Nunca se guardan ni se muestran tal cual.
- **Sesiones con token** aleatorio de 256 bits. En la base solo queda su huella SHA-256 y vencen a los 30 días.
- **Permisos en el servidor:** cada ruta revisa que el gimnasio sea de quien lo administra y que el pedido sea del comprador o de su dueño. Las rutas escritas de forma rara (`;`, `%xx`, `..`) se rechazan.
- **Códigos del correo** de 6 dígitos, de un solo uso: duran de 15 a 30 minutos, permiten 5 intentos y solo se guarda su hash.
- **Freno a ataques de fuerza bruta** en el login: 10 intentos por correo cada 15 minutos.
- **Datos de pago:** la tarjeta se valida (Luhn, vencimiento, CVC), se cambia por un token de un solo uso y se olvida. El cobro usa los datos del token, no lo que mande el navegador.
- **Consentimiento registrado:** el pedido guarda la versión de los términos aceptada y la fecha, y la cuenta guarda la del Aviso de privacidad.
- **Credenciales fuera del código**, en variables de entorno.
- **Fechas en la zona de la Ciudad de México**, para que los cortes de pago no se adelanten un día.

## 4. Pruebas en navegadores (criterio 17)

| Navegador | Dispositivo | Portada | Registro / login | Tienda y carrito | Checkout | Panel |
|---|---|---|---|---|---|---|
| Chrome | Windows | | | | | |
| Edge | Windows | | | | | |
| Firefox | Windows | | | | | |
| Chrome | Android | | | | | |
| Safari | iPhone | | | | | |

Marca ✔ en cada celda y guarda la captura en `docs/capturas/`.

---

## Decisiones de diseño

Hay criterios que no se aplican tal cual por la forma en que se usa la página. Estas son las razones.

**1. Stripe, PayPal y Paynet reales, en su modo de prueba (criterios 09 y 14).** La tienda usa las APIs de los tres proveedores en su ambiente de pruebas (sandbox): un proyecto escolar no debe mover dinero real, y pasar a producción pide RFC, cuenta bancaria y verificación del negocio. Funciona igual que en producción:

- **Tarjeta:** Stripe Checkout. La tarjeta se escribe en la página de Stripe; GymTrack recibe la sesión pagada y captura el cargo al crear el pedido.
- **PayPal:** Orders v2. La orden se aprueba en la página de PayPal (con una cuenta compradora de prueba) y GymTrack la captura.
- **Efectivo en tiendas:** Openpay genera la referencia y la ficha de Paynet. Cuando se paga (en el sandbox, desde el panel de Openpay), Openpay avisa por webhook y el pedido pasa a pagado.

Como la tarjeta nunca pasa por el servidor de GymTrack, el alcance de PCI-DSS es el más bajo (el que corresponde a usar la página de pago del proveedor). Para las pruebas automáticas del CI siguen los simuladores internos (`PAGOS_MODO=simulado`), sin páginas ni llaves.

**2. Render como hosting principal y GitHub Pages solo estático (criterios 13 y 15).** GitHub Pages solo sirve archivos estáticos (HTML, CSS y JS). La tienda necesita un servidor para el carrito, los pagos, las sesiones, los recibos y la base de datos, así que el sitio completo vive en Render, con Spring Boot en Docker. GitHub Pages está configurado y publica la parte estática (portada, contacto, mapa y WhatsApp) cada vez que cambia `main`.

**3. La web es del dueño y la app es del miembro.** El dueño usa la web como panel y como mostrador. En el mostrador se cobra en efectivo, con tarjeta (Stripe) o con PayPal y el producto se entrega en ese momento; con pago en tienda se genera la ficha de Paynet y el producto queda apartado hasta que se paga. Los miembros compran sobre todo desde la app y recogen en recepción con el QR de su pedido. La tienda web para miembros también existe y cumple los criterios 06 a 10.
