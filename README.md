# GymTrack — Página web

**GymTrack** es un ecosistema tecnológico **B2B2C** diseñado para modernizar la administración y la experiencia de usuario en gimnasios locales y medianos.

El sistema integra una **plataforma web administrativa**, una **aplicación móvil**, servicios backend y un **módulo físico IoT basado en tecnología RFID** para automatizar el control de acceso al gimnasio.

La **página web** es la herramienta principal para los dueños de los gimnasios: les permite conocer y contratar el servicio de GymTrack (modelo SaaS), gestionar a sus usuarios, registrar pagos, administrar membresías y diseñar rutinas de entrenamiento. Por su parte, los usuarios finales utilizan la **aplicación móvil** para consultar el estado de su membresía, visualizar las rutinas asignadas y registrar su progreso, accediendo físicamente al gimnasio mediante una credencial RFID.

El sistema busca centralizar los principales procesos del gimnasio en un único ecosistema tecnológico, conectando la administración web, el control de acceso IoT y la experiencia deportiva móvil del usuario.

> **Este repositorio contiene solo la página web:** el backend en Java Spring Boot, que expone la API REST y a la vez sirve la página (HTML, CSS y JavaScript). La aplicación móvil consume esta misma API y se trabaja en un repositorio aparte.

🌐 **Página publicada:** **https://joseoe.github.io/GTWeb/**

GitHub Pages solo sirve archivos estáticos, así que ahí funcionan la página principal, el formulario de contacto, el mapa y los enlaces de WhatsApp. El catálogo de soluciones, el registro, el inicio de sesión y el panel necesitan el backend, que por ahora se ejecuta en local (ver [Ejecución local paso a paso](#-ejecución-local-paso-a-paso)).

## Contenido

- [La página web](#-la-página-web)
- [Ejecución local paso a paso](#-ejecución-local-paso-a-paso)
- [Variables de entorno](#-variables-de-entorno)
- [Problemas comunes](#-problemas-comunes)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [API que usa la página](#-api-que-usa-la-página)
- [Correos de la cuenta](#-correos-de-la-cuenta)
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
| `bienvenido.html` | Panel del gimnasio: datos del gimnasio, miembros, pagos, máquinas y rutinas. |
| `cuenta.html` | Mi cuenta: cambiar contraseña y correo. |

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
MAIL_USERNAME=<correo>@gmail.com
MAIL_PASSWORD=<contraseña de aplicación>
```

> `.env` está en `.gitignore`: nunca se sube a GitHub. Pide los datos de MongoDB Atlas y del Gmail de GymTrack al equipo.

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
| `MAIL_USERNAME` | No* | Gmail de GymTrack que envía los correos de la cuenta. |
| `MAIL_PASSWORD` | No* | **Contraseña de aplicación** de ese Gmail (16 letras, no la contraseña normal). Se crea en https://myaccount.google.com/apppasswords con la verificación en dos pasos activada. Puede pegarse con o sin espacios. |
| `APP_URL` | No | Dirección con la que se arman los enlaces de los correos. Por defecto `http://localhost:8080`; cámbiala al publicar la página. |

\* Sin `MAIL_USERNAME` la página funciona igual: los códigos de verificación y los enlaces de recuperación se escriben en la consola en lugar de enviarse.

---

## 🩺 Problemas comunes

| Síntoma | Solución |
|---|---|
| `Could not resolve placeholder 'MONGODB_URI'` al arrancar | Falta el `.env` o no está en la raíz del proyecto. Ejecuta `mvn spring-boot:run` desde la carpeta donde está `pom.xml`. |
| El catálogo dice "no está disponible" o la consola muestra *timeout* con MongoDB | Revisa tu conexión y que tu IP esté permitida en MongoDB Atlas → **Network Access**. |
| `Port 8080 was already in use` | Cierra el otro programa que usa el puerto o [cambia el puerto](#opcional-cambiar-el-puerto). |
| `mvn` no se reconoce como comando | Maven no está instalado o su carpeta `bin` no está en el `PATH`. |
| No llega el código de verificación | Revisa spam. Confirma que `MAIL_PASSWORD` sea una contraseña de aplicación. La consola indica si el correo no se pudo enviar. |
| El botón del correo abre `localhost` y no carga | Es normal en local: los enlaces apuntan a la computadora donde corre la página. En otro equipo usa el código de 6 dígitos. |
| Cambié HTML/CSS/JS y no se ve el cambio | Detén el servidor (Ctrl + C), vuelve a ejecutarlo y recarga con Ctrl + F5. |

---

## 📁 Estructura del proyecto

```plaintext
GTWeb/
├── pom.xml                       # Dependencias y build (Maven)
├── .env.example                  # Plantilla de variables (copiar como .env)
└── src/main/
    ├── java/com/gymtrack/
    │   ├── GymTrackApplication.java   # Arranque y datos iniciales del catálogo
    │   ├── config/                    # CORS para la app y cliente de correo
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

## 🔌 API que usa la página

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/servicios` | Catálogo de soluciones |
| POST | `/api/users/register` | Crear cuenta |
| POST | `/api/users/login` | Iniciar sesión |
| GET | `/api/users/{id}/me` | Datos de la cuenta |
| POST | `/api/cuenta/verificar` · `/api/cuenta/verificar/reenviar` | Verificar el correo · pedir otro código |
| POST | `/api/cuenta/recuperar` · `/api/cuenta/restablecer` | Enlace de recuperación · guardar contraseña nueva |
| POST | `/api/cuenta/contrasena` | Cambiar contraseña (Mi cuenta) |
| POST | `/api/cuenta/correo` · `/api/cuenta/correo/confirmar` | Cambiar correo con código |
| GET/POST | `/api/gyms`, `/api/gyms/{gymId}/members`, `/api/gyms/{gymId}/machines`, `/api/gyms/{gymId}/routines` | Panel del gimnasio |
| GET/POST | `/api/gyms/{gymId}/members/{userId}/payments` | Pagos de cada miembro |
| POST | `/api/billing/run` | Revisar vencimientos ahora (también corre sola todos los días a las 6:00) |

---

## 📧 Correos de la cuenta

Se envían por el SMTP de Gmail con plantillas HTML (Thymeleaf). Los códigos se guardan cifrados en MongoDB Atlas y se borran solos al vencer.

| Correo | Cuándo llega |
|---|---|
| Código de verificación | Al registrarse en la página (vence en 15 min; máximo 5 intentos). |
| Bienvenida | Al verificar la cuenta. |
| Restablecer contraseña | Desde "¿Olvidaste tu contraseña?" (enlace de un solo uso, vence en 30 min). |
| Tu contraseña cambió | Al restablecerla o cambiarla en Mi cuenta. |
| Código para el correo nuevo · aviso al anterior | Al cambiar el correo en Mi cuenta. |
| Solicitud aprobada · pago por vencer · membresía vencida | Avisos de la membresía para los miembros del gimnasio. |

> Las cuentas creadas desde la app móvil entran sin verificar el correo, porque la app todavía no tiene esa pantalla.

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
| Spring Boot Mail (SMTP de Gmail) + Thymeleaf | Correos de la cuenta con plantillas HTML |
| BCrypt (spring-security-crypto) | Cifrado de contraseñas |

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
- [ ] Publicar la API en un hosting para que el catálogo, el registro y el panel también funcionen en línea.

Fase 3 — Aplicación Móvil (Usuarios)
- [x] Crear proyecto base en Expo.
- [x] Diseñar pantallas clave (Auth, Tabs principales: Progreso, Rutinas, Entrenar).
- [x] Implementar capa de API y conectar `fetch` hacia el backend en Spring Boot.
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

La página web funciona completa en local:
- página principal;
- catálogo conectado a MongoDB Atlas;
- contacto;
- registro con verificación por correo;
- inicio de sesión;
- panel del gimnasio;
- correos de la cuenta.

La aplicación móvil ya consume la misma API en un ambiente local. Faltan tres cosas:
- publicar la página y la API en un hosting;
- implementar sesiones con tokens (JWT);
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

🏋️ GymTrack
Administra. Identifica. Accede. Entrena. Analiza. Mejora.
