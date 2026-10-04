# GymTrack — Página web

**GymTrack** es un ecosistema tecnológico **B2B2C** diseñado para modernizar la administración y la experiencia de usuario en gimnasios locales y medianos.

El sistema integra una **plataforma web administrativa**, una **aplicación móvil**, servicios backend y un **módulo físico IoT basado en tecnología RFID** para automatizar el control de acceso al gimnasio.

La **página web** es la herramienta principal para los dueños de los gimnasios: les permite conocer y contratar el servicio de GymTrack (modelo SaaS), gestionar a sus usuarios, registrar pagos, administrar membresías y diseñar rutinas de entrenamiento. Por su parte, los usuarios finales utilizan la **aplicación móvil** para consultar el estado de su membresía, visualizar las rutinas asignadas y registrar su progreso, accediendo físicamente al gimnasio mediante una credencial RFID.

El sistema busca centralizar los principales procesos del gimnasio en un único ecosistema tecnológico, conectando la administración web, el control de acceso IoT y la experiencia deportiva móvil del usuario.

> **Este repositorio contiene solo la página web:** el backend en Java Spring Boot, que expone la API REST y a la vez sirve la página (HTML, CSS y JavaScript). La aplicación móvil consume esta misma API y se trabaja en un repositorio aparte ([GTApp](https://github.com/JoseOE/GTApp)).

🌐 **Página y API publicadas (Render):** **https://gtweb.onrender.com**

Ahí funciona todo: la página principal, el catálogo, el registro, el inicio de sesión, el panel y la API que usa la app móvil (ver [Despliegue](#-despliegue)).

📄 **Versión estática (GitHub Pages):** https://joseoe.github.io/GTWeb/

GitHub Pages solo sirve archivos estáticos, así que ahí funcionan la página principal, el formulario de contacto, el mapa y los enlaces de WhatsApp. El catálogo de soluciones, el registro, el inicio de sesión y el panel necesitan el backend: úsalos desde Render o en local (ver [Ejecución local paso a paso](#-ejecución-local-paso-a-paso)).

## Contenido

- [La página web](#-la-página-web)
- [Ejecución local paso a paso](#-ejecución-local-paso-a-paso)
- [Variables de entorno](#-variables-de-entorno)
- [Despliegue](#-despliegue)
- [Problemas comunes](#-problemas-comunes)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [API](#-api)
- [Seguridad y sesiones](#-seguridad-y-sesiones)
- [Correos de la cuenta](#-correos-de-la-cuenta)
- [Tienda](#-tienda)
- [Contexto del proyecto](#problemática)
- [Equipo y sprints](#-equipo-y-flujo-de-trabajo)

---

## 💻 La página web

| Página | Qué hace |
|---|---|
| `index.html` | Página principal: menú de secciones, slider, "Nosotros", ecosistema, catálogo de soluciones (desde MongoDB Atlas) con cotizador por volumen, franja de WhatsApp y ubicación con mapa y ruta. |
| `contacto.html` | Formulario de contacto: el mensaje llega al Gmail del equipo mediante EmailJS. |
| `registro.html` | Crear cuenta de dueño de gimnasio. |
| `verificar.html` | Verificar el correo con el código de 6 dígitos o con el botón del correo. |
| `login.html` | Iniciar sesión; incluye "¿Olvidaste tu contraseña?". |
| `recuperar.html` y `restablecer.html` | Pedir un enlace por correo y crear una contraseña nueva. |
| `bienvenido.html` | Panel del gimnasio: datos del gimnasio, miembros, pagos, máquinas, rutinas y la tienda (mostrador, ventas, productos y planes). |
| `cuenta.html` | Mi cuenta: cambiar contraseña y correo. |
| `tienda.html` | Tienda del gimnasio para sus miembros: planes, productos, categorías y búsqueda. |
| `producto.html` | Detalle de un producto o plan: presentación, sabor, precio, stock y cantidad. |
| `checkout.html` | Pagar: resumen, recoger en el gimnasio y forma de pago. |
| `confirmacion.html` · `pedidos.html` | Confirmación del pedido y "Mis pedidos" con estado y recibo. |
| `paypal-sim.html` | Simulador PayPal: el comprador aprueba o cancela un pago de prueba (también se abre desde la app). |

---

## 🚀 Ejecución local paso a paso

La página y su API se levantan juntas con un solo comando. No hace falta instalar Node.js ni nada de la aplicación móvil.

### 1. Requisitos previos

| Requisito | Para qué | Cómo comprobarlo |
|---|---|---|
| **Java 17** (JDK) | Ejecutar Spring Boot | `java -version` |
| **Maven 3.9+** | Descargar dependencias y levantar el proyecto | `mvn -version` |
| **Git** | Clonar el repositorio | `git --version` |
| Internet | MongoDB Atlas, librerías de la página y la primera descarga de dependencias | — |

> Descargas: Java 17 en https://adoptium.net y Maven en https://maven.apache.org/download.cgi (agrega la carpeta `bin` de Maven al `PATH`).

### 2. Clonar el repositorio

```bash
git clone https://github.com/JoseOE/GTWeb.git
```

```bash
cd GTWeb
```

### 3. Crear el archivo `.env`

Las contraseñas no se guardan en el código: se leen de un archivo `.env` en la raíz del proyecto (junto a `pom.xml`). Cópialo desde la plantilla.

En PowerShell (Windows):

```powershell
Copy-Item .env.example .env
```

En Git Bash, macOS o Linux:

```bash
cp .env.example .env
```

Después abre `.env` y rellena los valores (ver [Variables de entorno](#-variables-de-entorno)):

```properties
MONGODB_URI=mongodb+srv://<usuario>:<contraseña>@<cluster>.mongodb.net/gymtrackdb?retryWrites=true&w=majority
BREVO_API_KEY=<api key de Brevo>
MAIL_USERNAME=<correo>@gmail.com
```

> `.env` está en `.gitignore`: nunca se sube a GitHub. Pide al equipo los datos de MongoDB Atlas y de la cuenta de Brevo de GymTrack.

### 4. Levantar la página

Desde la carpeta del proyecto:

```bash
mvn spring-boot:run
```

La primera vez tarda un poco más porque Maven descarga las dependencias. Cuando la consola muestre `Started GymTrackApplication`, abre:

👉 **http://localhost:8080**

*(También puedes abrir el proyecto en IntelliJ IDEA o VS Code y ejecutar la clase `GymTrackApplication.java`; ejecútala con la carpeta del proyecto como directorio de trabajo para que encuentre el `.env`).*

Para detener el servidor presiona **Ctrl + C** en la terminal.

> Si la colección `servicios` está vacía, el catálogo se llena solo con 8 soluciones de ejemplo al arrancar.

### 5. Flujo de prueba sugerido

1. **Página principal:** recorre el menú, el slider, "Nosotros", el catálogo (abre un servicio y prueba el cotizador y el botón de WhatsApp) y el mapa con "Cómo llegar".
2. **Contacto:** en `contacto.html` envía un mensaje de prueba.
3. **Registro:** crea una cuenta en `registro.html`; llegará un código de 6 dígitos a tu correo.
4. **Verificación:** escribe el código en `verificar.html` (o usa el botón del correo).
5. **Login y panel:** inicia sesión y registra tu gimnasio, miembros, pagos, máquinas y rutinas en el panel.
6. **Mi cuenta:** desde el panel prueba cambiar la contraseña y el correo.
7. **Recuperación:** cierra sesión y usa "¿Olvidaste tu contraseña?".

### Opcional: cambiar el puerto

Si el puerto 8080 está ocupado:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

### Opcional: ver solo el diseño (sin backend)

Para revisar HTML y CSS sin Java ni base de datos, sirve la carpeta `static` con cualquier servidor estático, por ejemplo:

```bash
python -m http.server 5500 --directory src/main/resources/static
```

Y abre http://localhost:5500. **Ojo:** así no funcionan el catálogo, el registro, el login ni el panel, porque dependen de la API.

---

## 🔧 Variables de entorno

| Variable | ¿Obligatoria? | Para qué sirve |
|---|---|---|
| `MONGODB_URI` | **Sí** | Conexión a MongoDB Atlas (usuario, contraseña y cluster). |
| `BREVO_API_KEY` | No* | Llave de la API de Brevo, que envía los correos de la cuenta. Se genera en Brevo → **SMTP & API → API Keys**. |
| `MAIL_USERNAME` | No* | Correo remitente. Debe estar verificado en Brevo → **Senders, Domains & Dedicated IPs**. |
| `APP_URL` | No | Dirección con la que se arman los enlaces de los correos. Por defecto `http://localhost:8080`; en Render debe ser `https://gtweb.onrender.com`. |
| `PORT` | No | Puerto del servidor. Por defecto `8080`; Render lo asigna solo. |
| `EXIGIR_TOKEN` | No | `true` para que toda la API exija el token de sesión. Por defecto `false` mientras la app móvil se actualiza (ver [Seguridad y sesiones](#-seguridad-y-sesiones)). |

\* Sin `BREVO_API_KEY` y `MAIL_USERNAME` la página funciona igual: los códigos de verificación y los enlaces de recuperación se escriben en la consola en lugar de enviarse.

> Los correos salen por la API HTTP de Brevo y no por SMTP porque Render bloquea los puertos SMTP salientes en todos sus planes.

---

## 🚢 Despliegue

| Qué | Dónde | Cómo se publica |
|---|---|---|
| Página completa + API | [Render](https://gtweb.onrender.com) | Imagen de Docker construida con el `Dockerfile` del repositorio (Maven compila el jar y la imagen final solo lleva el JRE 17). Las variables de entorno se definen en el panel de Render. |
| Página estática | [GitHub Pages](https://joseoe.github.io/GTWeb/) | El workflow `.github/workflows/deploy-pages.yml` publica `src/main/resources/static` cada vez que llegan cambios a esa carpeta en `main`. |

Para probar la imagen de Docker en local:

```bash
docker build -t gtweb .
```

```bash
docker run --env-file .env -p 8080:8080 gtweb
```

---

## 🩺 Problemas comunes

| Síntoma | Solución |
|---|---|
| `Could not resolve placeholder 'MONGODB_URI'` al arrancar | Falta el `.env` o no está en la raíz del proyecto. Ejecuta `mvn spring-boot:run` desde la carpeta donde está `pom.xml`. |
| El catálogo dice "no está disponible" o la consola muestra *timeout* con MongoDB | Revisa tu conexión y que tu IP esté permitida en MongoDB Atlas → **Network Access**. |
| `Port 8080 was already in use` | Cierra el otro programa que usa el puerto o [cambia el puerto](#opcional-cambiar-el-puerto). |
| `mvn` no se reconoce como comando | Maven no está instalado o su carpeta `bin` no está en el `PATH`. |
| No llega el código de verificación | Revisa spam. Confirma que `BREVO_API_KEY` sea válida y que `MAIL_USERNAME` esté verificado como remitente en Brevo. La consola indica si el correo no se pudo enviar. |
| El botón del correo abre `localhost` y no carga | Es normal en local: los enlaces apuntan a la computadora donde corre la página. En otro equipo usa el código de 6 dígitos. |
| Cambié HTML/CSS/JS y no se ve el cambio | Detén el servidor (Ctrl + C), vuelve a ejecutarlo y recarga con Ctrl + F5. |

---

## 📁 Estructura del proyecto

```plaintext
GTWeb/
├── pom.xml                       # Dependencias y build (Maven)
├── .env.example                  # Plantilla de variables (copiar como .env)
├── Dockerfile                    # Imagen para desplegar en Render
├── .github/workflows/            # Publicación de la parte estática en GitHub Pages
└── src/main/
    ├── java/com/gymtrack/
    │   ├── GymTrackApplication.java   # Arranque y datos iniciales del catálogo
    │   ├── config/                    # CORS para la app móvil
    │   ├── controller/                # Endpoints REST (/api/...)
    │   ├── model/                     # Documentos de MongoDB
    │   ├── repository/                # Acceso a MongoDB (Spring Data)
    │   ├── service/                   # Correos, cuenta, cobranza y notificaciones push
    │   └── util/                      # Contraseñas (BCrypt), códigos y tokens
    └── resources/
        ├── application.properties     # Configuración (lee el .env)
        ├── templates/correos/         # Plantillas HTML de los correos
        └── static/                    # La página web
            ├── *.html
            ├── css/                   # Estilos de la marca
            ├── js/                    # Slider, catálogo, mapa y utilidades de la cuenta
            └── fonts/                 # Tipografías autoalojadas
```

---

## 🔌 API

### Cuenta (página y app)

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/servicios` · `/api/servicios/{id}` | Catálogo de soluciones |
| POST | `/api/users/register` | Crear cuenta (dueño desde la página; miembro desde la app con `role: "member"`) |
| POST | `/api/users/login` | Iniciar sesión → la cuenta y su `token` de sesión |
| POST | `/api/users/logout` | Cerrar la sesión del token que llega (`Authorization: Bearer`) |
| GET | `/api/users/{id}/me` | Estado vigente de la cuenta y su gimnasio |
| POST | `/api/cuenta/verificar` · `/api/cuenta/verificar/reenviar` | Verificar el correo · pedir otro código |
| POST | `/api/cuenta/recuperar` · `/api/cuenta/restablecer` | Enlace de recuperación · guardar contraseña nueva |
| POST | `/api/cuenta/contrasena` | Cambiar contraseña (Mi cuenta) |
| POST | `/api/cuenta/correo` · `/api/cuenta/correo/confirmar` | Cambiar correo con código |

### Panel del gimnasio (página)

| Método | Ruta | Uso |
|---|---|---|
| POST · GET | `/api/gyms` · `/api/gyms/{id}` | Registrar y consultar el gimnasio |
| POST | `/api/gyms/{id}/codigo` | Generar un código de invitación nuevo (invalida el anterior) |
| GET · POST | `/api/gyms/{gymId}/members` | Miembros y solicitudes; alta directa desde recepción |
| PATCH · DELETE | `/api/gyms/{gymId}/members/{userId}` | Aprobar, dar de baja o reactivar · desvincular |
| GET · POST | `/api/gyms/{gymId}/members/{userId}/payments` | Pagos de cada miembro |
| GET · POST | `/api/gyms/{gymId}/machines` · PUT · DELETE `/api/machines/{id}` | Máquinas del gimnasio |
| GET · POST | `/api/gyms/{gymId}/routines` · PUT · DELETE `/api/routines/{id}` | Rutinas del gimnasio |

### App móvil

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/gyms/directory` | Gimnasios que aceptaron aparecer en la app |
| GET | `/api/gyms/lookup?codigo=...` | Vista previa del gimnasio antes de unirse |
| POST | `/api/users/{userId}/membership/join` · `/leave` | Solicitar unirse con el código · salirse del gimnasio |
| PUT | `/api/users/{id}/push-token` | Registrar el token de Expo para las notificaciones push |
| GET | `/api/users/{userId}/routines` · `/api/users/{userId}/machines` | Rutinas y máquinas de su gimnasio |
| GET · POST · DELETE | `/api/users/{userId}/workouts` · `/{workoutId}` | Historial y registro de entrenamientos |
| GET | `/api/users/{userId}/workouts/stats` | Estadísticas de la pestaña Progreso |

Las notificaciones push (solicitud aprobada, pago por vencer, membresía vencida) se envían por el servicio de push de Expo.

---

## 🔐 Seguridad y sesiones

- **La sesión.**
  - `POST /api/users/login` y `POST /api/cuenta/verificar` devuelven un `token` al azar, que vale 30 días.
  - La página lo guarda (`js/sesion.js`) y lo manda en cada llamada a `/api/` como `Authorization: Bearer <token>`.
  - En la base solo queda su huella SHA-256 (colección `sesiones`). Quien lea la base no puede usar las sesiones.
- **La identidad sale del token, no del cliente.** Con token, el servidor llena él mismo el `X-User-Id` que usan las rutas. Si la página manda otro, responde 403.
- **Quién puede qué.** `SesionFilter` revisa, además de la sesión:
  - que el gimnasio de la ruta sea del dueño que llama (miembros, pagos, código, rutinas, máquinas, productos, planes, mostrador y ventas);
  - que cada cuenta (`/api/users/{id}/...`) sea de quien llama.

  La tienda, los recibos y los simuladores lo revisan en `AccesoService`.
- **Al cerrar o cambiar la contraseña.**
  - "Cerrar sesión" invalida el token en el servidor.
  - Restablecer la contraseña cierra todas las sesiones de la cuenta.
  - Cambiarla desde "Mi cuenta" cierra las demás y deja abierta la actual.
- **Al verificar el correo** se entra directo al panel: verificar demuestra que el correo es suyo.
- **Al registrarse** solo se aceptan nombre, correo, contraseña y `role`. Lo demás (fecha de corte, plan, gimnasio) nunca viene del cliente.
- **El detalle de un gimnasio.** `GET /api/gyms/{id}` le da todo a su dueño. A cualquier otro le da solo lo público, sin el código de acceso ni los datos internos de la tienda.

**Transición de la app móvil.**

- Mientras la app no mande el token, `EXIGIR_TOKEN=false` deja que las rutas que comparte con la página sigan aceptando el `X-User-Id` de siempre.
- Las rutas que solo usa el panel exigen token desde ya:
  - gimnasio y su código;
  - miembros y alta de pagos;
  - rutinas y máquinas (crear, editar, borrar);
  - productos, planes y mostrador;
  - ventas y "Simular pago en tienda".
- Para terminar la transición, la app debe:
  1. guardar el `token` que devuelven `/api/users/login` y `/api/users/register` (los miembros entran directo);
  2. mandarlo en cada llamada (`Authorization: Bearer <token>`) y llamar a `/api/users/logout` al salir;
  3. con eso publicado, poner `EXIGIR_TOKEN=true` en Render.

  Hasta entonces, las rutas de la app siguen tan abiertas como antes.

---

## 📧 Correos de la cuenta

Se envían por la API HTTP de Brevo con plantillas HTML (Thymeleaf). Los códigos se guardan cifrados en MongoDB Atlas y se borran solos al vencer.

| Correo | Cuándo llega |
|---|---|
| Código de verificación | Al registrarse en la página (vence en 15 min; máximo 5 intentos). |
| Bienvenida | Al verificar la cuenta. |
| Restablecer contraseña | Desde "¿Olvidaste tu contraseña?" (enlace de un solo uso, vence en 30 min). |
| Tu contraseña cambió | Al restablecerla o cambiarla en Mi cuenta. |
| Código para el correo nuevo · aviso al anterior | Al cambiar el correo en Mi cuenta. |
| Solicitud aprobada · pago por vencer · membresía vencida | Avisos de la membresía para los miembros del gimnasio. |
| Compra confirmada (con el recibo en PDF) | Al pagarse una compra de un miembro: tienda, app o mostrador. |
| Ficha de pago Paynet (con la ficha en PDF) | Al elegir pagar en efectivo en tiendas. |
| Recibimos tu pago (con el recibo en PDF y push) | Al pagarse una ficha Paynet. |
| Recibo de membresía (con el recibo en PDF) | Al registrar un pago a mano en el panel. |

> Las cuentas creadas desde la app móvil entran sin verificar el correo, porque la app todavía no tiene esa pantalla.

> **Alta en recepción.** En "Mis Usuarios → Agregar usuario" el panel propone una contraseña temporal segura (se puede cambiar o copiar para entregarla). La cuenta queda con `contrasenaTemporal: true`, que viene en el login, en `/api/users/{id}/me` y en la lista de miembros. La tienda y Mi cuenta le muestran al miembro un aviso para cambiarla; al cambiarla o restablecerla vuelve a `false`. La app móvil debe leer el mismo campo para pedir el cambio.

---

## 🛒 Tienda

Cada gimnasio tiene su propia tienda: el dueño da de alta productos (scoops y botes de proteína, preentreno, aguas, chicles, barras…) y planes de membresía desde el panel. Los miembros compran desde la página (la app GTApp se conecta después) y el dueño vende en mostrador. Los pagos son **100 % simulados** (Stripe, Paynet y PayPal de prueba, más efectivo).

```plaintext
 Página web / App GTApp                  Spring Boot (gtweb)                       Medusa v2 (medusa/)
 ───────────────────────   /api/...    ───────────────────────   Admin API    ─────────────────────────
  panel, tienda, checkout ───────────▶  MedusaClient ─────────────────────────▶  productos, carritos,
                                        (X-User-Id, reglas,      Store API      pedidos, inventario,
                                         membresías, recibos) ◀────────────────  pagos simulados
                                        Mongo: pedidos,           webhook HMAC        │
                                        pagos, imágenes                              PostgreSQL (Neon)
```

- **Spring es la única puerta.** La web y la app nunca llaman a Medusa: llaman a `/api/...` y Spring decide qué pedirle a Medusa. La llave secreta de Medusa solo vive en las variables de entorno de Spring.
- **Multi-gimnasio.** La primera vez que un gimnasio usa la tienda, Spring le crea en Medusa un canal de venta, un almacén, la opción de entrega "Recoger en el gimnasio" ($0) y una llave publicable ligada a ese canal. Los ids quedan en `Gym.tienda`.
- **Precios en MXN con IVA incluido** (región México, IVA 16 %).
- **Medusa dormida.** En el plan gratuito de Render, Medusa se duerme tras 15 minutos sin uso. Si no responde en 25 s, Spring contesta `503 {"error": "Despertando la tienda…", "despertando": true}` y la página reintenta sola.

### Levantar la tienda en local

Necesitas **Node.js 22.12 o superior** y una base de **PostgreSQL** (la de Neon del equipo o una local).

1. Configura Medusa:

   ```bash
   cd medusa
   ```

   ```bash
   cp .env.template .env
   ```

   Rellena `DATABASE_URL`, `JWT_SECRET`, `COOKIE_SECRET` y `MEDUSA_WEBHOOK_SECRET`.

2. Instala, crea las tablas y carga la configuración base (región México, IVA, tipos, categorías y la llave secreta para Spring):

   ```bash
   npm install
   ```

   ```bash
   npx medusa db:migrate
   ```

   ```bash
   npm run seed
   ```

   El seed imprime una llave `sk_...` **una sola vez**. Si la pierdes, genera otra con `npm run seed -- nueva-llave`.

3. Arranca Medusa (puerto 9000):

   ```bash
   npm run dev
   ```

4. En el `.env` de Spring (raíz del proyecto) agrega `MEDUSA_URL=http://localhost:9000`, `MEDUSA_ADMIN_TOKEN=<la sk_...>` y el mismo `MEDUSA_WEBHOOK_SECRET`, y levanta Spring como siempre con `mvn spring-boot:run`.

### Variables de entorno de la tienda

| Dónde | Variable | Para qué sirve |
|---|---|---|
| Spring | `MEDUSA_URL` | Dirección de Medusa (`http://localhost:9000` o la de Render). Sin ella la página funciona, pero la tienda responde 503. |
| Spring | `MEDUSA_ADMIN_TOKEN` | Llave secreta `sk_...` que imprime `npm run seed`. |
| Spring y Medusa | `MEDUSA_WEBHOOK_SECRET` | Clave con la que Medusa firma sus avisos (HMAC). Debe ser **idéntica** en los dos. |
| Medusa | `DATABASE_URL` | PostgreSQL de Neon con conexión directa (sin pooling), terminada en `?sslmode=require`. |
| Medusa | `JWT_SECRET` · `COOKIE_SECRET` | Secretos internos de Medusa (cadenas largas aleatorias). |
| Medusa | `STORE_CORS` · `ADMIN_CORS` · `AUTH_CORS` | Orígenes permitidos (la dirección de Spring). |
| Medusa | `SPRING_WEBHOOK_URL` | A dónde avisa Medusa: `https://gtweb.onrender.com/api/tienda/webhooks/medusa`. |

### Medusa en Render (segundo servicio)

Medusa corre como un segundo Web Service de Render, con su base de datos en Neon. El servicio `gtweb` (Spring) no cambia: solo recibe cuatro variables nuevas. Estos pasos se probaron replicando Render con `main`: base vacía, mismos comandos y mismas variables.

**1. Base de datos en Neon.** Crea un proyecto en https://console.neon.tech, en la región más cercana a la de Render. En **Connect**, apaga **Connection pooling** y copia la cadena:

```plaintext
postgresql://neondb_owner:<contraseña>@ep-<algo>.<región>.aws.neon.tech/neondb?sslmode=require
```

- Usa la conexión directa (el host **no** lleva `-pooler`): las migraciones de Medusa no van bien con el pooling.
- Si la cadena trae `&channel_binding=require` al final, quítalo: debe terminar en `?sslmode=require`.

**2. Claves secretas.** Genera tres, una para `JWT_SECRET`, otra para `COOKIE_SECRET` y otra para `MEDUSA_WEBHOOK_SECRET`:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

**3. El servicio.** En Render, **New + → Web Service** con el repositorio `JoseOE/GTWeb`:

| Campo | Valor |
|---|---|
| Language | Node |
| Branch | `main` |
| Region | La misma que `gtweb` |
| Root Directory | `medusa` |
| Build Command | `npm install --include=dev && npm run build && cd .medusa/server && npm install && npm run predeploy` |
| Start Command | `cd .medusa/server && NODE_OPTIONS=--max-old-space-size=320 npm run start` |
| Health Check Path | `/health` |
| Instance Type | Free, con una sola instancia |

Variables del servicio (se pueden pegar juntas con **Add from .env**):

```env
NODE_VERSION=24.19.0
NODE_ENV=production
DATABASE_URL=<cadena de Neon>
JWT_SECRET=<clave 1>
COOKIE_SECRET=<clave 2>
MEDUSA_WEBHOOK_SECRET=<clave 3>
STORE_CORS=https://gtweb.onrender.com
ADMIN_CORS=https://gtweb.onrender.com
AUTH_CORS=https://gtweb.onrender.com
SPRING_WEBHOOK_URL=https://gtweb.onrender.com/api/tienda/webhooks/medusa
```

Por qué los comandos son así:

- **`--include=dev`.** Con `NODE_ENV=production`, `npm install` se salta las dependencias de desarrollo (`ts-node`, `typescript`) y `medusa build` ya no puede leer `medusa-config.ts`.
- **Instalación y migraciones en el build.** El plan gratis apaga el servicio tras 15 minutos sin uso; así, al despertar solo arranca Medusa en lugar de reinstalar todo. `predeploy` (`medusa db:migrate`) aplica en Neon las migraciones pendientes; si no hay, termina en segundos.
- **`NODE_OPTIONS` solo en el Start Command.** Limita la memoria de Medusa para que quepa en los 512 MB del plan gratis. Como variable de entorno también aplicaría al build, y el compilador de TypeScript se quedaría sin memoria.
- **`NODE_VERSION`.** Fija la versión de Node con la que se probó.
- **Una sola instancia.** Sin Redis, los eventos y los workflows de Medusa viven en memoria.

Al terminar el primer deploy, los **Logs** dicen `Server is ready on port: 10000` y `https://<servicio>.onrender.com/health` responde `OK`. La raíz del servicio responde "Cannot GET /": es normal, porque el dashboard de Medusa está apagado y todo se administra desde el panel de GTWeb.

**4. Configuración base y llave para Spring (una sola vez).** Con el servicio ya desplegado (ese deploy crea las tablas), corre el seed desde tu computadora contra Neon:

```bash
cd medusa
```

```bash
npm install
```

```bash
DATABASE_URL="<cadena de Neon>" npm run seed
```

- **En PowerShell,** el último paso son dos comandos: `$env:DATABASE_URL = "<cadena de Neon>"` y luego `npm run seed`.
- **La llave.** Copia la `sk_...` que imprime; solo se muestra una vez. Si la pierdes, genera otra con `npm run seed -- nueva-llave`.
- **Tu `.env` local.** La cadena de la terminal tiene prioridad sobre tu `medusa/.env`. Cierra la terminal al terminar para no seguir apuntando a Neon.

**5. Conectar Spring.** En **Environment** del servicio `gtweb` agrega:

| Variable | Valor |
|---|---|
| `MEDUSA_URL` | La dirección del servicio de Medusa, sin `/` al final |
| `MEDUSA_ADMIN_TOKEN` | La `sk_...` del paso 4 |
| `MEDUSA_WEBHOOK_SECRET` | La misma clave que en Medusa |
| `APP_URL` | `https://gtweb.onrender.com` (con ella se arman las direcciones de las imágenes de los productos y los enlaces de los correos) |

Guarda con **Save, rebuild, and deploy**.

**6. Comprobar.** `https://gtweb.onrender.com/api/tienda/estado` responde `{"lista":true}`. Si Medusa estaba dormida, primero responde `"despertando": true`; espera un minuto y vuelve a intentar.

- **Memoria.** Medusa queda cerca del límite de 512 MB del plan gratis. Si en **Events** aparece *Ran out of memory*, revisa la sección de problemas comunes.
- **Secretos.** La cadena de Neon, la `sk_...` y las tres claves solo van en Render, nunca en GitHub.

### API de la tienda

Todas las rutas de la tienda identifican a quien llama con el encabezado **`X-User-Id`**: el `userId` que la página guarda al iniciar sesión. El servidor comprueba que ese usuario sea el dueño del gimnasio, o el comprador del pedido que pide. Los errores siempre llegan como `{"error": "..."}`, y si Medusa está despertando también traen `"despertando": true`.

**Catálogo del dueño (panel)**

| Método | Ruta | Uso |
|---|---|---|
| GET · POST | `/api/gyms/{gymId}/productos` | Listar · crear productos |
| GET · PUT · DELETE | `/api/gyms/{gymId}/productos/{id}` | Ver · editar · eliminar un producto |
| GET · POST | `/api/gyms/{gymId}/planes` | Listar · crear planes de membresía |
| PUT · DELETE | `/api/gyms/{gymId}/planes/{id}` | Editar · eliminar un plan |
| GET | `/api/gyms/{gymId}/planes/presets` | Planes sugeridos con el precio calculado desde la cuota mensual |
| POST | `/api/gyms/{gymId}/imagenes` | Subir una foto `{"dataUrl": "data:image/jpeg;base64,..."}` → `{id, url}` |
| GET | `/api/imagenes/{id}` | Ver la foto (pública, en caché un año) |
| POST | `/api/gyms/{gymId}/members/{userId}/payments` | Registrar un pago a mano; acepta `planId` para usar la duración de un plan |
| GET | `/api/gyms/{gymId}/payments?limite=12` · `?desde=AAAA-MM-DD&limite=500` | Pagos de todo el gimnasio, del más reciente al más viejo, con el nombre del miembro y `orderId` si vino de un pedido (solo el dueño). Lo usan "Últimos pagos" y el Resumen |

**Tienda del miembro (página y app)**

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/tienda/{gymId}/productos?categoria=&q=` | Planes y productos publicados (los planes primero, los destacados arriba) |
| GET | `/api/tienda/{gymId}/productos/{id}` | Detalle con variantes, precio vigente y `disponible` |
| GET · POST · DELETE | `/api/tienda/carrito` | Ver el carrito ya revisado · abrirlo · vaciarlo |
| POST | `/api/tienda/carrito/items` | Agregar `{"varianteId", "cantidad"}` |
| PATCH · DELETE | `/api/tienda/carrito/items/{id}` | Cambiar la cantidad `{"cantidad"}` (0 la quita) · quitar |
| POST | `/api/tienda/carrito/checkout` | Pagar `{"metodo", "datos", "totalVisto"}` y devolver el pedido |
| GET | `/api/tienda/pedidos` · `/api/tienda/pedidos/{orderId}` | Mis pedidos · un pedido (si está pendiente, se vuelve a leer de Medusa) |

Todas las rutas del carrito aceptan `?canal=web` (por defecto) o `?canal=app`, y cada canal tiene su carrito.

- **Ver la tienda.** Pueden verla los miembros del gimnasio (aun con la solicitud pendiente) y su dueño, como vista previa.
- **Comprar.** Solo pueden comprar los miembros con estado `active` o `inactive`.

Respuestas de `/checkout`:

- **200**: el pedido.
- **409**: el carrito cambió desde que la persona lo vio (algo se agotó, se ocultó o cambió de precio). Trae `avisos` y `carrito` ya corregido, y no se cobra nada.
- **402**: el simulador rechazó el pago, con su mensaje.

**Carrito** (lo que reciben la página y la app):

```json
{
  "id": "cart_...", "gymId": "...", "canal": "web", "articulos": 3,
  "subtotal": 1550.86, "iva": 248.14, "total": 1799.0,
  "avisos": ["El precio de «Proteína Whey Gold» cambió de $899.50 a $949.50."],
  "items": [
    {"id": "cali_...", "varianteId": "variant_...", "productoId": "prod_...", "titulo": "Proteína Whey Gold",
     "variante": "Bote 1 kg · Vainilla", "imagen": "https://...", "esPlan": false,
     "cantidad": 2, "maximo": 6, "precioUnitario": 899.5, "total": 1799.0}
  ]
}
```

**Simulador de Stripe y mostrador**

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/simuladores/stripe/tokens` | Tokenizar una tarjeta de prueba `{numero, titular, mes, anio, cvc}` → `{token, marca, ultimos4, vencimiento}` |
| POST | `/api/gyms/{gymId}/mostrador/cobrar` | Cobrar el ticket `{partidas: [{varianteId, cantidad}], clienteId, metodo, recibido, datos, totalVisto}` → `{pedido, cambio, cliente}` |

**Simulador de Stripe.**

- **Tokenización.** La tarjeta va a `/tokens`, que valida Luhn, vencimiento y CVC (4 dígitos en Amex) y devuelve un token `tok_sim_...`. Al pedido solo llega el token: el número completo y el CVC no se guardan ni se escriben en la consola.
- **El token.** Es de un solo uso y de quien lo pidió, y vence en 30 minutos. Al pagar, Spring cambia los `datos` del navegador por los del token, así el resultado no se puede forzar desde la página.
- **Tarjetas aceptadas.** Solo las de prueba; cualquier otro número responde "Usa una tarjeta de prueba".

| Tarjeta | Resultado al cobrar |
|---|---|
| 4242 4242 4242 4242 | Visa aprobada |
| 5555 5555 5555 4444 | Mastercard aprobada |
| 3782 822463 10005 | American Express aprobada |
| 4000 0000 0000 0002 | Rechazada |
| 4000 0000 0000 9995 | Fondos insuficientes |
| 4000 0000 0000 0069 | Vencida |
| 4000 0000 0000 0127 | CVC incorrecto |

Los rechazos llegan al checkout como 402 con el mensaje para el comprador, y el carrito queda intacto para intentar con otra tarjeta.

**Mostrador.**

- **El ticket.** Vive en el navegador del panel (y en `localStorage`, así que sobrevive a recargar la página): agregar, cambiar y quitar productos es instantáneo, sin esperar a Medusa, que en el plan gratis de Render tarda segundos en cada viaje.
- **Al cobrar.** El ticket llega completo. Spring lo revisa contra lo que hoy está a la venta (existencias, precios, un solo plan) y crea el carrito en Medusa de una sola vez. Si algo cambió responde 409 con `avisos`; la página recarga el catálogo y muestra el ticket corregido.
- **El cliente.** Se elige al cobrar: un miembro del gimnasio (en cualquier estado) o `null` para "Público en general".
- **Efectivo.** `metodo: "efectivo"` exige `recibido` ≥ total, calcula el cambio y captura el pago al momento (proveedor manual de Medusa).
- **Tarjeta.** `metodo: "tarjeta"` usa el token del simulador de Stripe como terminal.
- **Entrega.** La venta se marca como entregada en el acto, así que bajan las existencias del almacén.
- **Plan.** Si la venta lleva un plan y el cliente es miembro, su membresía se extiende igual que en la tienda.

**Recibos y simulador de Paynet**

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/recibos/pedidos/{orderId}.pdf` | Recibo tamaño carta; con `?formato=ticket`, ticket de 80 mm para el mostrador |
| GET | `/api/recibos/paynet/{orderId}.pdf` · `/codigo.png` | Ficha Paynet en PDF · código de barras de la referencia (para la web y la app) |
| GET | `/api/recibos/pagos/{paymentId}.pdf` | Recibo de una mensualidad registrada en el panel |
| POST | `/api/recibos/pedidos/{orderId}/enviar` · `/api/recibos/pagos/{paymentId}/enviar` | Reenviar el recibo (o la ficha) por correo |
| POST | `/api/simuladores/paynet/{orderId}/pagar` | "Simular pago en tienda" (solo el dueño; lo usa el dashboard de ventas) |
| POST | `/api/simuladores/paynet/vencidas` | Cancelar ahora las fichas vencidas (también corre sola cada 15 minutos) |

**Recibos.**

- **Quién los ve.** Solo el comprador y el dueño del gimnasio. Se piden con `fetch` y `X-User-Id`, porque un enlace no puede mandar el encabezado.
- **Contenido.** Todos los PDF llevan:
  - logo y datos del gimnasio, folio, fecha y cliente;
  - las partidas, con subtotal, IVA 16 % desglosado y total (los precios ya incluyen IVA);
  - la forma de pago y el estado;
  - un QR con el folio y la leyenda **"Comprobante simulado, sin validez fiscal"**.
- **Pedidos sin recibo.** Un pedido pendiente o cancelado responde 409.

**Paynet.**

- **La ficha.** Al pagar, el proveedor genera una referencia de 18 dígitos (convenio `93` y dígito verificador de Luhn) con fecha límite de 72 horas. El pago queda autorizado sin capturar: el pedido está "Pendiente de pago" y no activa ningún plan.
- **Dónde se ve.** `confirmacion.html` muestra el código de barras, la referencia copiable, las tiendas (marcadas como simulación) y las instrucciones. La ficha también llega por correo en PDF.
- **El pago.** "Simular pago en tienda" captura el pago en Medusa y el pedido queda pagado: se activa el plan, llegan el recibo por correo y el push "Recibimos tu pago".
- **Vencimiento.** Cada 15 minutos se cancelan las fichas vencidas y Medusa libera el inventario que tenían apartado.
- **En la vista del pedido.** Viene `paynet: {referencia, vence}` para pintar la ficha en la web y en la app.

**Correos de la tienda.**

- Salen en segundo plano, con el PDF adjunto por la API de Brevo (base64).
- Cada correo automático se registra en `correos_enviados` antes de salir, así que un pedido sincronizado varias veces no lo repite.
- Solo se escribe a miembros: en una venta de mostrador al público no se manda nada.
- Sin Brevo configurado, la plantilla se arma igual y el aviso se escribe en la consola.

**Simulador de PayPal**

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/simuladores/paypal/ordenes?canal=web\|app` | Crea la orden por el total del carrito `{returnUrl, cancelUrl}` → `{id, monto, moneda, aprobarUrl}` |
| GET | `/api/simuladores/paypal/ordenes/{id}` | Lo que muestra el simulador: comercio, monto, artículos, estado y cuentas de prueba |
| POST | `/api/simuladores/paypal/ordenes/{id}/aprobar` | `{cuenta}` → `{redirect}`: `returnUrl` con `token` y `PayerID` |
| POST | `/api/simuladores/paypal/ordenes/{id}/cancelar` | → `{redirect}`: `cancelUrl` con `token` |

**PayPal.**

- **El flujo.** Como en PayPal, el pago se aprueba fuera de la tienda:
  1. Al pulsar "Pagar", Spring crea una orden `PAYID-SIM-...` por el total del carrito y la página va a `paypal-sim.html?token=...`.
  2. Ahí se elige una cuenta de prueba (o se escribe un correo) y se aprueba o se cancela. No se pide contraseña y no lleva logotipos de PayPal: es una simulación.
  3. Al aprobar, vuelve a `returnUrl` con `token` y `PayerID`, y el checkout paga con `{"metodo": "paypal", "datos": {"token", "payerId"}}`.
- **Cancelar.** Vuelve a `cancelUrl` y no se crea ningún pedido; el carrito queda igual.
- **Cuentas de prueba.** `ana.compradora@sim-paypal.test` y `luis.prueba@sim-paypal.test` aprueban; `sin-saldo@sim-paypal.test` aprueba, pero PayPal rechaza el cobro (402 con el mensaje). Cualquier otro correo también aprueba.
- **Seguridad.**
  - Crear la orden y pagar con ella exigen al comprador (`X-User-Id`).
  - Ver, aprobar y cancelar no: el simulador se abre también desde el navegador de la app, que no tiene la sesión de la página. El id de la orden hace de llave, como el token de la URL de PayPal.
  - Al pagar, Spring revisa que la orden sea del comprador, de su gimnasio y canal, que esté aprobada y que sea por el total de hoy (si el carrito cambió, responde 409 y hay que volver a aprobar). Se usa una sola vez y cambia por sus datos guardados, así que el resultado no se puede inventar desde el navegador.
  - Las órdenes viven en `ordenes_paypal` y se borran a las 3 horas.
- **Regreso.** `returnUrl` y `cancelUrl` pueden ser una página de esta web (relativa o con su dominio) o un enlace de la app (`gymtrack://...`, `exp://...`). Otro sitio web responde 400: el simulador no debe servir para mandar a nadie a una página ajena.
- **En la app (GTApp).** Crea la orden con `?canal=app` y su enlace de regreso, abre `aprobarUrl` en el navegador (por ejemplo, `WebBrowser.openAuthSessionAsync`) y, al volver por el enlace con `token` y `PayerID`, paga con `POST /api/tienda/carrito/checkout?canal=app`.

**Ventas (pestaña del panel)**

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/gyms/{gymId}/ventas/resumen?desde=&hasta=` | KPIs de hoy y del mes, y gráficas del periodo (por defecto, los últimos 30 días; hasta un año) |
| GET | `/api/gyms/{gymId}/ventas/pedidos` | Pedidos con filtros `metodo` (tarjeta, paynet, paypal, efectivo), `estado`, `canal` (web, app, mostrador), `desde`, `hasta`, `buscar` (folio, cliente o correo), `pagina` y `tamano` → `{pedidos, total, pagina, paginas, cobrado}` |
| GET | `/api/gyms/{gymId}/ventas/pedidos/{orderId}` | Detalle con las `acciones` que admite: `recibo`, `ficha`, `reenviar`, `simularPaynet`, `reembolsar`, `cancelar` |
| POST | `/api/gyms/{gymId}/ventas/pedidos/{orderId}/reembolsar` | Reembolso simulado de un pedido pagado → el detalle, más `membresia: {miembro, ajustada, vence}` si había extendido una |
| POST | `/api/gyms/{gymId}/ventas/pedidos/{orderId}/cancelar` | Cancela un pedido pendiente de pago (una ficha Paynet) |

**Ventas.**

- **Solo el dueño** del gimnasio. Todo sale de la copia local de los pedidos (`pedidos`), así que abrir la pestaña no despierta a Medusa; solo las acciones pasan por ella.
- **Qué cuenta como venta.** Un pedido pagado, en el día en que se pagó (hora de México). Los reembolsados y cancelados no suman.
- **KPIs.** Ventas de hoy y del mes, ticket promedio del mes, fichas Paynet por cobrar y membresías vendidas en la tienda este mes. Las gráficas son por día y por forma de pago, con lo más vendido y los canales del periodo (Chart.js desde su CDN; sin él, se muestran como lista).
- **Acciones del detalle.**
  - Recibo y ficha Paynet en PDF, reenviar el correo y "Simular pago en tienda" usan las rutas de `/api/recibos` y `/api/simuladores/paynet`.
  - **Reembolsar** devuelve todo lo cobrado (simulado: no se mueve dinero real). El pedido queda "Reembolsado" y conserva su total y su recibo. Lo vendido no regresa al inventario, porque ya se entregó.
  - **Cancelar** es para lo que todavía no se paga; uno pagado se reembolsa.
- **Reembolso de un plan.** El `Payment` queda marcado con `reembolsadoEn` (el panel lo muestra en "Últimos pagos"). Si fue el último pago que extendió la membresía, la fecha de corte vuelve a la que tenía antes (`Payment.corteAnterior`). Si después hubo otro pago, no se toca y el panel avisa para revisarla.

**Generales**

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/tienda/estado` | `{"lista": true}` o 503 mientras Medusa despierta. Ábrela al cargar la página para despertarla. |
| GET | `/api/tienda/categorias` | `[{handle, nombre}]`: suplementos, bebidas, snacks, accesorios, membresias |
| POST | `/api/tienda/webhooks/medusa` | Solo para Medusa (firmado). Ver "Avisos de Medusa". |

**Producto** (lo que reciben y mandan el panel y la app):

```json
{
  "id": "prod_...", "nombre": "Proteína Whey Gold", "descripcion": "24 g por porción",
  "categoria": {"handle": "suplementos", "nombre": "Suplementos"},
  "imagen": "https://gtweb.onrender.com/api/imagenes/...", "activo": true, "precioDesde": 35.0,
  "variantes": [
    {"id": "variant_...", "nombre": "Scoop · Vainilla", "presentacion": "Scoop", "sabor": "Vainilla",
     "precio": 35.0, "controlarInventario": false, "existencias": null, "disponible": null},
    {"id": "variant_...", "nombre": "Bote 1 kg · Vainilla", "presentacion": "Bote 1 kg", "sabor": "Vainilla",
     "precio": 899.5, "controlarInventario": true, "existencias": 8, "disponible": 6}
  ]
}
```

Para crear o editar se manda `nombre`, `descripcion`, `categoria` (el handle), `imagen` (la `url` que devolvió `/imagenes`), `activo` y `variantes` con `id` (vacío si es nueva), `presentacion`, `sabor`, `precio`, `controlarInventario` y `existencias`. Las variantes que ya no vienen se borran. `disponible` = existencias menos lo apartado en pedidos sin entregar.

**Plan:**

```json
{
  "id": "prod_...", "varianteId": "variant_...", "nombre": "Trimestral", "descripcion": "",
  "precio": 1215.0, "pagoUnico": false, "duracionUnidad": "mes", "duracionCantidad": 3,
  "duracionTexto": "3 meses", "beneficios": ["3 meses de acceso"], "destacado": false, "activo": true
}
```

`duracionUnidad` es `dia`, `semana` o `mes`. Con `pagoUnico: true` (la inscripción) no hay duración y no se mueve la fecha de corte.

### Reglas de los planes

- Máximo **1 plan por carrito**, cantidad 1 y sin inventario.
- Lo pueden comprar los miembros vinculados al gimnasio con estado `active` o `inactive`; `pending` no.
- Cuando el pedido queda **pagado** (`captured`), `BillingService` extiende la membresía:
  - los planes por mes respetan el día de pago, como la mensualidad;
  - los de días o semanas suman días;
  - si alguien con una visita o semana todavía vigente paga un mes, ese mes empieza **al terminar lo que ya pagó** y su día de pago pasa a ser ese día (semana del 1 al 8 + mes pagado el 3 → cubre del 8 al 8 del mes siguiente). Pagar tarde no mueve el día de pago.
- Se guarda un `Payment` con `metodo`, `plan` y `orderId`. `orderId` tiene **índice único**, así que un aviso repetido nunca extiende dos veces. El usuario guarda `planActual`.
- Un pedido Paynet pendiente **no** activa nada hasta que se captura.
- Si el pedido se reembolsa desde Ventas, la membresía vuelve a su fecha de corte anterior (ver "Ventas").

### Contratos para los demás bloques

**Carrito (`CarritoService`).**

- **Persistencia.** El carrito vive en Medusa. En Mongo (colección `carritos`) solo se guarda su `cartId` por usuario, gimnasio y canal. Por eso es el mismo en cualquier dispositivo donde la persona inicie sesión, y al pagar se abre uno nuevo. La página guarda en localStorage solo el contador, como caché.
- **Al crearlo.** Usa la llave `Gym.tienda.publishableKey`, la región México y el correo del comprador, con `metadata: {"userId", "gymId", "canal"}`. Medusa lo copia al pedido.
- **Planes.** Cada plan viaja con `metadata: {"duracionUnidad", "duracionCantidad"}` en su partida, para que el pedido conserve la duración aunque después se borre el plan. Hay máximo un plan con duración por carrito, de uno en uno; la inscripción sí puede ir junto.
- **Revisión.** Antes de mostrar el carrito y antes de cobrar, se compara con lo que hoy está a la venta:
  - lo que ya no se vende se quita;
  - lo que no alcanza se ajusta al disponible;
  - un precio que cambió se actualiza.

  Cada cambio genera un aviso.
- **Al cobrar.** Pone "Recoger en el gimnasio", crea la sesión de pago con los `datos` del módulo JS, completa el carrito y llama a `PedidoService.sincronizar(orderId)`. Así la membresía se activa sin esperar el aviso de Medusa.

**Pedidos (colección `pedidos`).** Copia local de cada pedido de Medusa:

- `orderId`, `folio`, `gymId`, `userId` (null en mostrador a público en general), `canal`;
- `estado`: `pendiente_pago`, `pagado`, `cancelado` o `reembolsado`;
- `proveedorPago`, `total`, `subtotal` (sin IVA), `iva` y `partidas`;
- `datosPago`: lo que guardó el simulador, sin nada sensible (`marca` y `ultimos4`; `recibido` y `cambio` en efectivo). `PedidoService.detallePago(pedido)` lo convierte en texto ("Visa •••• 4242", "Efectivo · recibido $500.00, cambio $42.00", "Paynet · referencia 9301 2345 …", "PayPal · ana.compradora@sim-paypal.test");
- `cliente` y `vendedorId` en ventas de mostrador. En una venta al público, el correo del pedido es el del dueño: solo se manda correo al comprador si `userId` no es nulo;
- `creadoEn`, `pagadoEn`, `canceladoEn` y `planAplicado`.

`AccesoService.exigirAccesoAPedido(pedido, userId)` deja verlo solo al comprador y al dueño del gimnasio.

**Métodos de pago (bloques 3 a 5).** Están registrados en `medusa/medusa-config.ts`. `MetodosPago` (Java) les da nombre en los recibos y el panel.

| Proveedor en Medusa | Bloque | Comportamiento base |
|---|---|---|
| `pp_sim-stripe_default` | 3 · Eduardo | Hecho: cobra en el acto si el token es de una tarjeta aprobada (ids `pi_sim_...` y cargo `ch_sim_...`) y rechaza con su mensaje las demás |
| `pp_sim-paynet_default` | 4 · Valeria | Hecho: referencia con dígito verificador y 72 h; queda **autorizado sin capturar** hasta "Simular pago en tienda" |
| `pp_sim-paypal_default` | 5 · Edwin | Hecho: con la orden aprobada en `paypal-sim.html` cobra en el acto (captura `CAPTURE-SIM_...`); la cuenta sin saldo se rechaza con su mensaje |
| `pp_system_default` | 3 · Eduardo | Hecho: efectivo en mostrador; Spring captura el pago al cobrar |

Cada simulador está en `medusa/src/modules/sim-*/service.ts` y extiende `SimuladorPago` (`medusa/src/lib/simulador-pago.ts`), que ya implementa toda la interfaz `AbstractPaymentProvider` de Medusa 2.21:

- **Spring** crea la sesión con `POST /store/payment-collections/{id}/payment-sessions` y `{"provider_id", "data": {...}}`. Ese `data` (token de la tarjeta, cuenta PayPal…) llega a `initiatePayment` y se conserva hasta `autorizar`.
- **Cada bloque** sobrescribe `autorizar(datos)`. Ahí valida y devuelve `{status: CAPTURED}` (cobro inmediato) o `{status: AUTHORIZED}` (pendiente), más los datos extra para el recibo (`marca`, `ultimos4`, `referencia`…). Para rechazar, lanza `new MedusaError(MedusaError.Types.PAYMENT_AUTHORIZATION_ERROR, "mensaje para el comprador")`: Medusa no crea el pedido y Spring responde 402 con ese mensaje.
- **Capturar después** (Paynet): `POST /admin/payments/{paymentId}/capture`. Medusa emite `payment.captured` y Spring recibe el aviso.
- **Reembolsar:** `POST /admin/payments/{paymentId}/refund` con `{"amount"}`. **Cancelar:** `POST /admin/orders/{orderId}/cancel`.
- Nunca guardes ni registres en logs el número completo de una tarjeta ni el CVC: al pedido solo llega el token.

### Formas de pago en el checkout (bloques 3 a 5)

`checkout.html` no sabe cómo cobra cada método: carga `js/pagos/stripe-sim.js`, `paynet-sim.js` y `paypal-sim.js`, y cada uno se registra con `MetodosPago.registrar({...})`. La documentación completa está en `js/pagos/metodos.js`. Si un archivo todavía no existe, ese método no aparece.

| Miembro | Para qué |
|---|---|
| `id` · `nombre` · `descripcion` · `icono` · `orden` | `id` es lo que Spring recibe en `metodo` (`stripe`, `paynet`, `paypal`); los demás son para el selector |
| `montar(contenedor, resumen)` | Dibuja el formulario. `resumen` = `{total, subtotal, iva, articulos, items, gym}` |
| `obtenerDatos(): Promise` | Valida y resuelve con los `datos` que van a Medusa. Para un error, rechaza con `new Error("mensaje")` |
| `despues(pedido)` (opcional) | Ya creado el pedido. Puede devolver otra URL a la que ir en lugar de `confirmacion.html` |
| `confirmacion(contenedor, pedido)` (opcional) | En `confirmacion.html`, lo propio del método (p. ej. la ficha de Paynet) |
| `desmontar()` (opcional) | Al cambiar a otro método |

Un método que sale de la página (PayPal) regresa a `checkout.html?metodo=<id>&continuar=1`. El checkout lo preselecciona y vuelve a pulsar "Pagar"; en esa segunda llamada, `obtenerDatos()` lee lo que trae la URL.

El recibo se descarga de `GET /api/recibos/pedidos/{orderId}.pdf` con `fetch` y el encabezado `X-User-Id`.

### Avisos de Medusa

`medusa/src/subscribers/avisar-pedidos.ts` escucha tres eventos y avisa a Spring en `POST /api/tienda/webhooks/medusa`:

| Evento | Cuándo |
|---|---|
| `order.placed` | Se completó un carrito |
| `payment.captured` | Se capturó un pago que estaba pendiente |
| `order.canceled` | Se canceló un pedido |

- **Firma.** Cada aviso lleva `X-GymTrack-Firma: t=<segundos>,v1=<HMAC-SHA256 de "t.cuerpo">`. Spring rechaza (401) las firmas inválidas o de hace más de 5 minutos.
- **Reintentos.** Si Spring no responde, Medusa reintenta a los 2, 10 y 45 segundos.
- **Idempotencia.** Spring guarda cada evento procesado en `avisos_tienda` (evento + pedido o pago). Si llega repetido, responde 200 sin volver a aplicarlo.

### Estructura de `medusa/`

```plaintext
medusa/
├── medusa-config.ts             # Admin apagado, CORS y los tres simuladores registrados
├── .env.template                # Variables de la tienda (copiar como .env)
└── src/
    ├── lib/
    │   ├── simulador-pago.ts    # Base común de los simuladores de pago
    │   └── avisar-spring.ts     # Aviso firmado (HMAC) con reintentos
    ├── modules/sim-stripe · sim-paynet · sim-paypal
    ├── subscribers/avisar-pedidos.ts
    └── scripts/seed.ts          # Región México, IVA, tipos, categorías y llave para Spring
```

### Problemas comunes de la tienda

| Síntoma | Solución |
|---|---|
| "La tienda no está configurada: faltan MEDUSA_URL o MEDUSA_ADMIN_TOKEN" | Agrega esas variables al `.env` de Spring (en Render, en **Environment** del servicio `gtweb`) y reinícialo. |
| "La tienda todavía no tiene su configuración base" | Corre `npm run seed` en `medusa/`. |
| "Llave de Medusa inválida" | La `sk_...` de `MEDUSA_ADMIN_TOKEN` se revocó o es de otra base. Genera otra con `npm run seed -- nueva-llave`. |
| "Despertando la tienda…" que no termina | Revisa en Render que el servicio de Medusa esté desplegado y sano (`/health`). |
| Se paga, pero la membresía no se extiende | Revisa que `MEDUSA_WEBHOOK_SECRET` sea idéntica en Spring y en Medusa, y que `SPRING_WEBHOOK_URL` apunte a Spring. La consola de Medusa avisa si un aviso no llegó. |
| El build de Medusa en Render falla con `Cannot find module '.../medusa-config'` | Falta `--include=dev` en el Build Command. |
| `JavaScript heap out of memory` durante el build | `NODE_OPTIONS` está como variable de entorno: quítala de ahí y déjala solo en el Start Command. |
| Render marca *Ran out of memory* en Medusa | Revisa que el Start Command lleve `NODE_OPTIONS=--max-old-space-size=320`. Si sigue pasando, Medusa necesita una instancia con más memoria. |
| `npm run seed` contra Neon falla con *timeout* | La red bloquea el puerto de PostgreSQL (5432), algo común en redes escolares. Prueba desde otra red. |

---

# Problemática

Actualmente, muchos gimnasios medianos y locales presentan una desconexión entre sus procesos administrativos, el control de acceso y la gestión de los entrenamientos.

Los administradores pueden depender de procesos manuales o sistemas independientes para:

* Registrar clientes.
* Controlar y cobrar membresías.
* Verificar pagos en tiempo real.
* Controlar el acceso físico.
* Distribuir rutinas basadas en su equipamiento real.

Esta situación puede generar problemas como:

* Permitir el acceso a usuarios con membresías vencidas, generando pérdidas económicas.
* Dificultad para gestionar e identificar rápidamente a los usuarios.
* Uso de tarjetas físicas sin validación automatizada en la nube.
* Registros manuales de acceso (papel o Excel).
* Falta de integración entre la membresía y el control de acceso.
* Uso de aplicaciones genéricas que no consideran el equipamiento disponible en el gimnasio específico.

Por su parte, los usuarios pueden experimentar una experiencia fragmentada al depender de diferentes medios para acceder al gimnasio, consultar sus rutinas y registrar su progreso.

GymTrack busca solucionar esta problemática mediante la integración de estos procesos en una sola plataforma.

---

# Objetivo General

Desarrollar e implementar un **ecosistema tecnológico integral denominado GymTrack**, que combine una plataforma web administrativa, una aplicación móvil, servicios backend y un dispositivo IoT basado en tecnología RFID para centralizar la gestión de membresías, automatizar el control de acceso físico mediante validación en tiempo real y permitir la distribución de rutinas de entrenamiento personalizadas, mejorando la administración del gimnasio y la experiencia del usuario final.

---

# Objetivos Específicos

1. Diseñar y desarrollar una **plataforma web administrativa** para que los dueños de los gimnasios puedan contratar el servicio (SaaS), registrar su sucursal, dar de alta usuarios, gestionar membresías y crear rutinas.

2. Diseñar y desarrollar una **aplicación móvil** para que los usuarios finales consulten su progreso, rutinas y estado de cuenta.

3. Implementar una base de datos y servicios backend (Spring Boot + MongoDB) que permitan centralizar y gestionar de forma segura la información de múltiples gimnasios bajo una arquitectura multi-tenant.

4. Desarrollar un sistema de control de acceso IoT mediante ESP32 y tecnología RFID, capaz de validar en tiempo real el estado de la membresía de los usuarios.

5. Evaluar el funcionamiento e impacto de GymTrack mediante una implementación piloto, utilizando indicadores relacionados con la automatización, reducción de morosidad y experiencia de los usuarios.

---

# 🧩 Componentes Principales

GymTrack está compuesto por cuatro componentes tecnológicos principales:

```plaintext
                         GYMTRACK
                            │
      ┌─────────────┬───────┴────────┬─────────────┐
      │             │                │             │
      ▼             ▼                ▼             ▼
  Plataforma    Aplicación        Backend         IoT
     Web          Móvil       + Base de Datos  Control de
 (Gimnasios)    (Usuarios)     (Spring Boot)   Acceso RFID
      │             │                │             │
      └─────────────┴───────┬────────┴─────────────┘
                            │
                            ▼
                         Gimnasio
```

💻 1. Plataforma Web (Administración) — **este repositorio**
La plataforma web es el núcleo de administración comercial y operativa del proyecto. Aquí es donde los dueños de gimnasios interactúan con el ecosistema.

El dueño o Administrador podrá:

* Contratar el servicio GymTrack (Suscripción SaaS).
* Configurar la información de su gimnasio (equipamiento disponible).
* Agregar y registrar nuevos usuarios/clientes.
* Gestionar planes y membresías.
* Registrar pagos de mensualidades.
* Consultar estados de cuenta y usuarios morosos.
* Asignar credenciales RFID a los usuarios.
* Crear y administrar ejercicios y rutinas exclusivas de su gimnasio.
* Consultar el historial de accesos registrados por el dispositivo IoT.

📱 2. Aplicación Móvil
La aplicación móvil está desarrollada utilizando React Native, Expo y TypeScript. Estará enfocada principalmente en el cliente final.

El Usuario podrá:

* Iniciar sesión.
* Consultar su perfil.
* Consultar el estado de su membresía y la fecha de vencimiento.
* Consultar las rutinas creadas por su gimnasio (adaptadas al equipo real).
* Registrar entrenamientos (series, repeticiones, peso).
* Consultar récords personales y progreso a lo largo del tiempo.
* Consultar su historial de entrenamientos.
* Su acceso físico a las instalaciones se realizará mediante una credencial RFID física.

🔑 3. Sistema de identificación RFID
El sistema utilizará tecnología RFID (Radio Frequency Identification) para identificar a los usuarios en la entrada.

La credencial podrá tomar diferentes formas:

* Tarjeta RFID.
* Llavero RFID.
* Pulsera RFID.
* Otro dispositivo compatible.

El usuario únicamente deberá acercar su credencial al lector instalado en la entrada.

🚪 4. Control de acceso IoT (Próximamente)
El módulo IoT estará instalado físicamente en la entrada del gimnasio, conectado a un torniquete o puerta.

Su función será:

* Detectar una credencial RFID.
* Obtener el identificador asociado.
* Enviar la solicitud de validación segura al backend en Java.
* Consultar la base de datos (MongoDB) para verificar el estado de pago.
* Autorizar o rechazar el acceso.
* Accionar el mecanismo de apertura (relé) cuando la membresía esté activa.
* Registrar el acceso.

🗄️ 5. Backend y Base de Datos
GymTrack utiliza **Java Spring Boot** como API backend principal y **MongoDB Atlas** como base de datos NoSQL.

La base de datos centralizada permite que tanto la plataforma web como la app móvil (y eventualmente el dispositivo IoT) consuman y actualicen la misma información en tiempo real.

Una estructura conceptual:

```plaintext
Gimnasio
   │
   ├── Administradores (Web)
   │
   ├── Usuarios (App Móvil)
   │     │
   │     ├── Membresía
   │     ├── Credencial RFID
   │     ├── Rutinas
   │     └── Entrenamientos
   │
   ├── Máquinas y Ejercicios
   │
   └── Registros de acceso
```

🔐 Seguridad y Multi-tenancy
Dado que la plataforma web permitirá a múltiples gimnasios contratar el servicio, la información se aísla a nivel lógico y de base de datos para asegurar que cada administrador solo pueda ver y modificar los datos de su propio gimnasio. Las contraseñas se guardan cifradas con BCrypt y las cuentas de la página confirman su correo antes de entrar.

🌐 Comunicación IoT (Próximamente)
La comunicación entre el dispositivo IoT y los servicios backend utilizará protocolos ligeros como MQTT sobre redes Wi-Fi, asegurados mediante encriptación TLS.

---

# 🛠️ Stack tecnológico

**Página web (frontend)**

| Tecnología | Uso |
|---|---|
| HTML5, CSS3 y JavaScript (Vanilla) | Interfaz y lógica de la página, servida directamente por Spring Boot |
| Bootstrap 5.3 y Boxicons | Diseño responsivo, componentes e íconos |
| Leaflet + Leaflet Routing Machine | Mapa de ubicación y ruta desde la ubicación del usuario |
| EmailJS | Envío del formulario de contacto al Gmail del equipo |
| Barlow Condensed, DM Sans y Manrope | Tipografías de la marca, autoalojadas |

**Backend y seguridad**

| Tecnología | Uso |
|---|---|
| Java 17 + Spring Boot 3.2 | API REST y servidor de la página |
| MongoDB Atlas + Spring Data MongoDB | Base de datos NoSQL en la nube |
| API de Brevo + Thymeleaf | Correos de la cuenta con plantillas HTML |
| BCrypt (spring-security-crypto) | Cifrado de contraseñas |
| Medusa v2 + PostgreSQL (Neon) | Motor de la tienda de cada gimnasio (segundo servicio en Render) |
| iText Core 9 (kernel, layout, barcodes) | Recibos, tickets de 80 mm y fichas Paynet en PDF, con QR y código de barras (licencia AGPL) |
| Expo Push | Notificaciones push a la app móvil |
| Docker + Render | Publicación de la página y la API |
| GitHub Actions + GitHub Pages | Publicación de la parte estática |

**Aplicación móvil** (repositorio aparte)

| Tecnología | Uso |
|---|---|
| React Native | Desarrollo multiplataforma |
| Expo y Expo Router | Framework de desarrollo y manejo de navegación |
| TypeScript | Tipado estático |

**IoT (arquitectura planeada)**

| Tecnología | Uso |
|---|---|
| ESP32 | Microcontrolador |
| RFID | Identificación física |
| MQTT + TLS | Comunicación remota segura |

---

# 🗺️ Roadmap

Fase 1 — Planeación
- [x] Definir problemática.
- [x] Definir objetivo general.
- [x] Definir modelo B2B2C (SaaS).
- [x] Diseñar arquitectura multidisciplinaria.

Fase 2 — Plataforma Web & Backend
- [x] Configurar proyecto en Spring Boot y conectar MongoDB Atlas.
- [x] Crear endpoints básicos (Usuarios, Gimnasios, Máquinas, Rutinas, Workouts).
- [x] Desarrollar Landing Page comercial funcional (HTML/CSS/JS + Bootstrap).
- [x] Desarrollar y conectar el panel administrativo para dueños de gimnasios.
- [x] Formulario de contacto (EmailJS) y mensajes de WhatsApp.
- [x] Verificación por correo, recuperación y cambio de contraseña.
- [x] Reemplazar SHA-256 por BCrypt en las contraseñas.
- [ ] Implementar sesiones seguras con tokens (JWT).
- [x] Publicar la parte estática de la página en GitHub Pages.
- [x] Publicar la página y la API en Render (Docker) para que el catálogo, el registro y el panel también funcionen en línea.
- [x] Enviar los correos por la API de Brevo (Render bloquea SMTP).

Fase 3 — Aplicación Móvil (Usuarios)
- [x] Crear proyecto base en Expo.
- [x] Diseñar pantallas clave (Auth, Tabs principales: Progreso, Rutinas, Entrenar).
- [x] Implementar capa de API y conectar `fetch` hacia el backend en Spring Boot.
- [x] Unirse a un gimnasio con código de invitación y directorio de gimnasios.
- [x] Registro de entrenamientos, progreso e historial con datos reales.
- [x] Notificaciones push de la membresía (Expo).
- [x] Conectar la app al backend publicado en Render.
- [ ] Pantalla de verificación de correo en la app.
- [ ] Mejorar el manejo de la sesión persistente y seguridad (Tokens).

Fase 4 — IoT (Control de Acceso)
- [ ] Configurar el microcontrolador ESP32 y Lector RFID.
- [ ] Implementar un Broker MQTT y conectar el flujo IoT hacia Spring Boot.
- [ ] Lógica para accionar relés de apertura al validar membresía.

Fase 5 — Integración e Investigación
- [ ] Pruebas E2E del ecosistema (Web -> Backend -> MongoDB -> IoT -> App).
- [ ] Instalación piloto en un gimnasio real.
- [ ] Evaluación de impacto (reducción de morosidad y adopción).

---

# 📊 Indicadores de impacto
* Tiempo de validación: Medir rapidez del acceso
* Accesos automatizados: Medir funcionamiento del sistema
* Membresías vencidas detectadas: Evaluar control administrativo
* Accesos rechazados: Evaluar validación
* Procesos manuales reducidos: Medir automatización
* Uso de rutinas digitales: Medir adopción
* Uso de la aplicación: Medir participación
* Satisfacción del usuario: Evaluar experiencia
* Retención de clientes: Evaluar impacto comercial

---

# 📌 Estado del proyecto
Estado: 🚧 En desarrollo activo

La página web está publicada completa en Render (https://gtweb.onrender.com):
- página principal;
- catálogo conectado a MongoDB Atlas;
- contacto;
- registro con verificación por correo;
- inicio de sesión;
- panel del gimnasio;
- correos de la cuenta (Brevo).

La aplicación móvil consume por defecto la misma API publicada en Render. Faltan tres cosas:
- implementar sesiones con tokens (JWT);
- agregar la verificación de correo en la app;
- integrar la capa de hardware IoT.

```plaintext
        Desarrollo de Apps
                │
                ▼
          Aplicación móvil
                │
                │
Negocios ─── GymTrack ─── IoT
                │
                │
                ▼
         Backend (Java) + BD (MongoDB)
                │
                ▼
        Redes y Seguridad
                │
                ▼
          Investigación
```

# 👥 Proyecto académico
* Proyecto: GymTrack
* Modelo de negocio: B2B2C (SaaS)
* Plataforma Administrativa: Web (HTML/JS/Bootstrap)
* Aplicación Usuarios: React Native + Expo
* Backend: Java Spring Boot + MongoDB
* Control de acceso: RFID + ESP32
* Comunicación IoT: MQTT + TLS

Áreas académicas involucradas:
* Desarrollo de Aplicaciones Móviles
* Negocios Electrónicos
* Internet de las Cosas
* Administración y Seguridad de Redes
* Taller de Investigación II

# 🤝 Equipo y flujo de trabajo

| Integrante | GitHub | Rama |
|---|---|---|
| Yael | [@YaellCh](https://github.com/YaellCh) | `yael` |
| Eduardo | [@EduardoGomezTics](https://github.com/EduardoGomezTics) | `eduardo` |
| José María | [@JoseOE](https://github.com/JoseOE) | `jose` |
| Valeria | [@ValeSot0](https://github.com/ValeSot0) | `valeria` |
| Edwin | [@EdwinSotoHz](https://github.com/EdwinSotoHz) | `edwin` |

Cada integrante trabaja en su propia rama:
1. Antes de empezar, actualiza su rama con `main`.
2. Al terminar, sus cambios llegan a `main` mediante un Pull Request.

**Sprint 1** — ✅ terminado

| Integrante | Entregable | PR |
|---|---|---|
| Yael | Menú de secciones del proyecto y ubicación en el mapa | [#1](https://github.com/JoseOE/GTWeb/pull/1) |
| Eduardo | Slider del proyecto | [#2](https://github.com/JoseOE/GTWeb/pull/2) |
| José | Sección "Nosotros" en el frontend | [#3](https://github.com/JoseOE/GTWeb/pull/3) |
| Valeria | Conexión de la base de datos de MongoDB Atlas a la página | [#4](https://github.com/JoseOE/GTWeb/pull/4) |
| Edwin | Backend de la página web funcional para el catálogo del proyecto | [#5](https://github.com/JoseOE/GTWeb/pull/5) |

**Sprint 2** — ✅ terminado

| Integrante | Entregable | PR |
|---|---|---|
| Yael | Formulario de contacto con Gmail (EmailJS) | [#10](https://github.com/JoseOE/GTWeb/pull/10) |
| Eduardo | Formulario de registro con verificación por correo electrónico (MongoDB Atlas, JavaScript y HTML5) | [#6](https://github.com/JoseOE/GTWeb/pull/6), [#11](https://github.com/JoseOE/GTWeb/pull/11) |
| José | Formulario de inicio de sesión | [#7](https://github.com/JoseOE/GTWeb/pull/7) |
| Valeria | Mensaje de WhatsApp | [#8](https://github.com/JoseOE/GTWeb/pull/8) |

**Fuera de sprint:** panel del gimnasio y API de la app móvil ([#9](https://github.com/JoseOE/GTWeb/pull/9)).

**Después del Sprint 2**

| Integrante | Cambio | PR |
|---|---|---|
| José | README con la ejecución local de la página web | [#12](https://github.com/JoseOE/GTWeb/pull/12) |
| José | Publicación de la página en GitHub Pages | [#13](https://github.com/JoseOE/GTWeb/pull/13) |
| Edwin | Corrección de la ruta del mapa hacia las oficinas | [#14](https://github.com/JoseOE/GTWeb/pull/14) |
| Yael | La ruta al gimnasio se traza sola y el panel de indicaciones se ve a su tamaño | [#15](https://github.com/JoseOE/GTWeb/pull/15) |
| José | Sección "Nosotros" ampliada con el origen y la misión | [#16](https://github.com/JoseOE/GTWeb/pull/16), [#17](https://github.com/JoseOE/GTWeb/pull/17) |
| Yael | Plantilla de EmailJS y confirmación animada del formulario de contacto | [#18](https://github.com/JoseOE/GTWeb/pull/18), [#19](https://github.com/JoseOE/GTWeb/pull/19) |
| Edwin | Dockerfile para desplegar en Render y puerto configurable | [#20](https://github.com/JoseOE/GTWeb/pull/20) |
| Valeria | Envío de correos por la API de Brevo en lugar de SMTP | [#21](https://github.com/JoseOE/GTWeb/pull/21) |

🏋️ GymTrack
Administra. Identifica. Accede. Entrena. Analiza. Mejora.
