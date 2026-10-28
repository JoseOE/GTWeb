# 01 · Contexto y alcance del SGSI

Cláusula 4 de la NMX-I-27001-NYCE-2015 (ISO/IEC 27001:2013).

## 4.1 La organización y su contexto

GymTrack es una plataforma para administrar gimnasios, desarrollada por un equipo de cinco estudiantes de Ingeniería en TIC del ITSOEH como proyecto académico. Tiene dos partes:

- **GTWeb**: la página pública, la tienda en línea y el panel del dueño (miembros, membresías y pagos, mostrador, ventas, productos, planes, rutinas y máquinas). Es una aplicación Spring Boot con MongoDB, desplegada en Render.
- **GTApp**: la app móvil del miembro (Expo / React Native), que usa la misma API: membresía, rutinas, tienda, pedidos y avisos.

**Cuestiones externas que afectan la seguridad**
- La plataforma trata datos personales de miembros y dueños, por lo que aplica la Ley Federal de Protección de Datos Personales en Posesión de los Particulares (LFPDPPP).
- Cobra con Stripe, PayPal y Openpay (Paynet). Se usa el modo de prueba (sandbox) porque es un proyecto académico, pero el flujo es el mismo que en producción.
- Toda la infraestructura es de terceros, en la nube: Render (aplicación), MongoDB Atlas (base de datos), Brevo (correo), Expo (avisos push), GitHub (código y CI) y Google Analytics (medición, solo con consentimiento).
- Los atacantes más probables son automatizados: bots que prueban contraseñas filtradas, escanean rutas o buscan llaves publicadas por error en repositorios.

**Cuestiones internas**
- El equipo es pequeño y trabaja a distancia, cada quien en su propia computadora.
- No hay instalaciones propias. Los "usuarios internos" son los dueños de gimnasio, que usan el panel en la computadora de recepción.
- El código se revisa por PR y se prueba en el CI antes de llegar a producción.

## 4.2 Partes interesadas y sus requisitos

| Parte interesada | Qué espera en seguridad |
|---|---|
| Miembros del gimnasio | Que sus datos y su cuenta estén protegidos; que nadie más vea sus pedidos o pagos; poder ejercer sus derechos ARCO |
| Dueños de gimnasio | Que solo ellos administren su gimnasio; saber quién hizo cada cambio; que la tienda cobre bien y no se pierdan ventas |
| Proveedores de pago (Stripe, PayPal, Openpay) | Uso correcto de sus API y de sus llaves; que los datos de tarjeta no pasen por GymTrack |
| Autoridad de protección de datos (Secretaría Anticorrupción y Buen Gobierno, que sustituyó al INAI con la LFPDPPP de 2025) | Cumplimiento de la LFPDPPP: aviso de privacidad, consentimiento, seguridad de los datos, aviso de vulneraciones |
| Docentes del ITSOEH | Evidencia de que la norma se aplicó al proyecto |
| Equipo de desarrollo | Reglas claras para manejar llaves, hacer cambios y atender incidentes |

## 4.3 Alcance del SGSI

> El SGSI cubre **el desarrollo, la operación y el soporte de la plataforma GymTrack**: la aplicación GTWeb (página, tienda, panel y API), la app GTApp, su base de datos, las integraciones de pago (Stripe, PayPal y Openpay) y de correo, y la información de miembros, dueños, pedidos y pagos que tratan; incluye los repositorios, el CI y las cuentas de los servicios en la nube usados para operarla.

**Fuera del alcance**
- Las instalaciones físicas de los centros de datos de Render, MongoDB Atlas y los demás proveedores. Su seguridad física la gestiona cada proveedor y aquí se controla con la gestión de proveedores (A.15).
- La computadora de recepción y la red de cada gimnasio, que son del dueño. GymTrack solo da recomendaciones de uso (A.11.2.8, A.11.2.9).
- Los teléfonos de los miembros. La app se protege a sí misma: la sesión guardada se borra al salir.

**Interfaces y dependencias:** la API pública (`/api/**`), los webhooks de Openpay, el CI de GitHub Actions, las variables de entorno de Render y los paneles de cada proveedor.

## 4.4 Inventario de activos (A.8.1.1)

| Id | Activo | Tipo | Dueño | Clasificación | Dónde vive |
|---|---|---|---|---|---|
| AC-01 | Base de datos (usuarios, gimnasios, pedidos, pagos, bitácora) | Información | Jose | Confidencial | MongoDB Atlas |
| AC-02 | API y panel (Spring Boot) | Software | Jose | Interna | Render |
| AC-03 | Página pública y tienda | Software | Yael | Pública | Render (estáticos de Spring) |
| AC-04 | App móvil GTApp | Software | Yael | Interna | Expo / teléfonos |
| AC-05 | Llaves y secretos (MongoDB, Brevo, Stripe, PayPal, Openpay, clave de respaldos) | Información | Jose | Restringida | Variables de Render, secretos de GitHub, `.env` local (no versionado) |
| AC-06 | Código fuente (GTWeb, GTApp) | Software | Jose | Interna | GitHub |
| AC-07 | Cuentas de los proveedores (Render, Atlas, GitHub, Stripe, PayPal, Openpay, Brevo, Google) | Servicio | Jose | Restringida | Paneles de cada proveedor |
| AC-08 | Sesiones de los usuarios (tokens) | Información | Jose | Restringida | Navegador o teléfono del usuario; en la base solo su huella SHA-256 |
| AC-09 | Respaldos de la base | Información | Jose | Confidencial | Artefactos cifrados de GitHub Actions (30 días) |
| AC-10 | Integración de pagos con tarjeta (Stripe) | Servicio | Eduardo | Restringida | Stripe |
| AC-11 | Integración con PayPal | Servicio | Edwin | Restringida | PayPal |
| AC-12 | Integración con Openpay (Paynet), inventario y recibos | Servicio | Valeria | Restringida | Openpay |
| AC-13 | Computadoras del equipo | Equipo | Cada integrante | Interna | Casa de cada integrante |
| AC-14 | Correo transaccional (verificación, recibos, fichas) | Servicio | Jose | Interna | Brevo |
| AC-15 | Documentación del SGSI | Información | Jose | Interna | `docs/iso27001/` |

**Clasificación**
- **Pública:** se puede publicar sin daño (catálogo, página de inicio).
- **Interna:** solo el equipo; publicarla no expone datos personales.
- **Confidencial:** datos personales y de negocio de los gimnasios.
- **Restringida:** llaves, contraseñas y tokens. Su exposición permite actuar como GymTrack o como el usuario.
