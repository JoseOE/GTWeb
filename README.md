# Propuesta de Proyecto: GymTrack

## Descripción General

**GymTrack** es un ecosistema tecnológico **B2B2C** diseñado para modernizar la administración y la experiencia de usuario en gimnasios locales y medianos.

El sistema integra una **plataforma web administrativa**, una **aplicación móvil**, servicios backend y un **módulo físico IoT basado en tecnología RFID** para automatizar el control de acceso al gimnasio.

La **página web** será la herramienta principal para los dueños de los gimnasios, permitiéndoles contratar el servicio de GymTrack (modelo SaaS), gestionar a sus usuarios, registrar pagos, administrar membresías y diseñar rutinas de entrenamiento. Por su parte, los usuarios finales utilizarán la **aplicación móvil** para consultar el estado de su membresía, visualizar las rutinas asignadas y registrar su progreso, accediendo físicamente al gimnasio mediante una credencial RFID.

El sistema busca centralizar los principales procesos del gimnasio en un único ecosistema tecnológico, conectando la administración web, el control de acceso IoT y la experiencia deportiva móvil del usuario.


---

## 🚀 Ejecución y pruebas locales (paso a paso)

Este repositorio contiene la **página web de GymTrack**: el backend en Spring Boot expone la API REST y además sirve la página (HTML/CSS/JS).

### 1. Requisitos previos
* **Java 17** instalado (`java -version`).
* **Maven** instalado (`mvn -version`).
* Conexión activa a Internet (para conectar con la base de datos en MongoDB Atlas).

### 2. Configurar MongoDB Atlas y el correo
1. Copia el archivo `.env.example` como `.env` en la raíz del proyecto (junto a `pom.xml`).
2. En `MONGODB_URI` sustituye los valores entre `< >` por el usuario, la contraseña y el cluster de MongoDB Atlas.
3. En `MAIL_USERNAME` y `MAIL_PASSWORD` pon el Gmail de GymTrack y su **contraseña de aplicación** (se crea en https://myaccount.google.com/apppasswords con la verificación en dos pasos activada). Con esa cuenta se envían los correos de verificación, recuperación de contraseña y avisos.

> El archivo `.env` está en `.gitignore`: las contraseñas nunca se suben a GitHub.
> Si dejas vacío el correo, la página funciona igual y los códigos de verificación aparecen en la consola.

### 3. Levantar la página web
1. En una terminal, dentro de la carpeta del proyecto, ejecuta:
   ```bash
   mvn spring-boot:run
   ```
   *(También puedes abrir el proyecto en IntelliJ o VS Code y ejecutar la clase `GymTrackApplication.java`).*
2. Cuando la consola indique que inició (puerto 8080), abre en tu navegador:
   👉 **http://localhost:8080**

**Flujo de prueba sugerido:**
1. Recorre la página principal: menú, slider, nosotros, catálogo de soluciones y mapa de ubicación.
2. Entra a `http://localhost:8080/registro.html` y crea una cuenta.
3. Escribe el código de 6 dígitos que llega a tu correo (o usa el botón del correo) para verificarla.
4. Inicia sesión en `http://localhost:8080/login.html` con la cuenta que acabas de crear.

> La aplicación móvil (AppMovil) consume esta misma API y se trabaja en un repositorio aparte.

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

GymTrack estará compuesto por cuatro componentes tecnológicos principales:

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

💻 1. Plataforma Web (Administración)
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
Dado que la plataforma web permitirá a múltiples gimnasios contratar el servicio, la información se aisla a nivel lógico y de base de datos para asegurar que cada administrador solo pueda ver y modificar los datos de su propio gimnasio.

🌐 Comunicación IoT (Próximamente)
La comunicación entre el dispositivo IoT y los servicios backend utilizará protocolos ligeros como MQTT sobre redes Wi-Fi, asegurados mediante encriptación TLS.

---

# 🛠️ Stack tecnológico

**Plataforma Web (SaaS y Landing)**
* Tecnología: HTML5, CSS3, JavaScript (Vanilla)
* Uso: Interfaz y lógica del frontend web (Servida directamente por Spring Boot)
* Tecnología: Bootstrap 5
* Uso: Framework principal para diseño responsivo

**Aplicación Móvil**
* Tecnología: React Native
* Uso: Desarrollo multiplataforma
* Tecnología: Expo y Expo Router
* Uso: Framework de desarrollo y manejo de navegación
* Tecnología: TypeScript
* Uso: Tipado estático

**Backend y Seguridad**
* Tecnología: Java Spring Boot
* Uso: Framework principal para construir la API REST
* Tecnología: MongoDB (Atlas)
* Uso: Base de datos NoSQL en la nube

**IoT (Arquitectura Planeada)**
* Tecnología: ESP32
* Uso: Microcontrolador
* Tecnología: RFID
* Uso: Identificación física
* Tecnología: MQTT + TLS
* Uso: Comunicación remota segura

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
- [ ] Refactorizar seguridad (Reemplazar SHA-256 plano por Bcrypt e implementar JWT).
- [ ] Desarrollar y conectar el Dashboard administrativo para dueños de gimnasios.

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

GymTrack se encuentra actualmente desarrollando su plataforma base; la comunicación entre la API (Spring Boot) y la Aplicación Móvil ya está establecida en un ambiente local, y se prepara para recibir la capa de hardware y mejorar la seguridad en producción.

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

Cada integrante trabaja en su propia rama. Antes de empezar, actualiza su rama con `main`; al terminar, sus cambios llegan a `main` mediante un Pull Request.

**Sprint 1**

| Integrante | Entregable |
|---|---|
| Yael | Menú de secciones del proyecto y ubicación en el mapa |
| Eduardo | Slider del proyecto |
| José | Sección "Nosotros" en el frontend |
| Valeria | Conexión de la base de datos de MongoDB Atlas a la página |
| Edwin | Backend de la página web funcional para el catálogo del proyecto |

**Sprint 2**

| Integrante | Entregable |
|---|---|
| Yael | Formulario de contacto con Gmail (EmailJS) |
| Eduardo | Formulario de registro con verificación por correo electrónico (MongoDB Atlas, JavaScript y HTML5) |
| José | Formulario de inicio de sesión |
| Valeria | Mensaje de WhatsApp |

🏋️ GymTrack
Administra. Identifica. Accede. Entrena. Analiza. Mejora.
