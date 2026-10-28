# 02 · Política de seguridad y roles

Cláusula 5 de la norma y controles A.5.1.1, A.5.1.2, A.6.1.1 y A.6.1.2.

## Política de seguridad de la información de GymTrack

GymTrack guarda los datos de los gimnasios, sus miembros y sus ventas. Nuestro compromiso es protegerlos para que **solo quien debe pueda verlos o cambiarlos (confidencialidad e integridad) y para que la plataforma esté disponible cuando el gimnasio la necesita (disponibilidad)**.

Para cumplirlo, el equipo de GymTrack se compromete a:

1. **Cumplir la ley y los contratos:** la LFPDPPP y las condiciones de uso de Stripe, PayPal, Openpay y los demás proveedores.
2. **Dar a cada quien solo el acceso que necesita.** El dueño administra solo su gimnasio y el miembro solo ve lo suyo. En el equipo, el acceso a producción y a las llaves se limita a quien opera el servicio.
3. **No dejar pasar datos de tarjeta por nuestros servidores:** se capturan en la página de cada proveedor de pago.
4. **Guardar los secretos solo en lugares seguros** (variables de Render, secretos de GitHub y el `.env` local que no se versiona). Nunca se guardan en el código, en el chat ni en capturas de pantalla.
5. **Revisar todo cambio antes de producción:** rama, PR y CI en verde. Un cambio de seguridad lleva su prueba automática.
6. **Registrar lo importante** (inicios de sesión, accesos negados, cambios del panel) y revisarlo.
7. **Respaldar la base de datos cada día, cifrada**, y probar que se puede restaurar.
8. **Atender todo incidente con el procedimiento de [05 · Gestión de incidentes](05-gestion-de-incidentes.md)** y aprender de él.
9. **Mejorar continuamente:** revisar esta política, los riesgos y los controles cada semestre.

Incumplir esta política se trata en el equipo y con el docente responsable de la materia.

**Objetivos de seguridad para el semestre (6.2)**

| Objetivo | Meta | Cómo se mide |
|---|---|---|
| Ninguna ruta de la API usable sin sesión, salvo las públicas | 100 % con `EXIGIR_TOKEN=true` | `pruebas/seguridad.mjs` y la variable en Render |
| Vulnerabilidades altas o críticas de dependencias corregidas a tiempo | ≤ 7 días | PRs de Dependabot |
| Respaldo diario exitoso | ≥ 95 % de los días | Historial del flujo "Respaldo" |
| Prueba de restauración | 1 al mes | Registro en [06 · Operación](06-operacion.md) |
| Cambios a producción revisados por el CI | 100 % | PRs mezclados con CI en verde |

Aprobó: José María Ortiz Escamilla, responsable del SGSI, el 27 de octubre de 2026. **Revisión (A.5.1.2):** cada semestre, tras un incidente o cuando cambie el alcance.

## Roles y responsabilidades (A.6.1.1)

| Rol | Quién | Responsabilidades |
|---|---|---|
| Responsable del SGSI (RSI) | Jose (JoseOE) | Aprueba la política y los riesgos; administra Render, MongoDB Atlas, GitHub y las llaves; coordina los incidentes; revisa la bitácora y los respaldos. Mantiene el control de sesión (SesionFilter) y la API de pagos. |
| Responsable de pagos con tarjeta | Eduardo (EduardoGomezTics) | Integración con Stripe y su llave; que la tarjeta nunca pase por GymTrack; mostrador del panel. |
| Responsable de PayPal | Edwin (EdwinSotoHz) | Integración con PayPal (Orders v2), su app de sandbox y sus credenciales. |
| Responsable de Openpay e inventario | Valeria (ValeSot0) | Fichas de Paynet, webhook de Openpay, inventario y recibos. |
| Responsable de la app y la documentación | Yael (YaellCh) | GTApp (manejo de la sesión en el teléfono); documentación, pruebas y aviso de privacidad. |
| Dueño de gimnasio (usuario) | Cada cliente | Cuidar su contraseña; cerrar sesión en la computadora de recepción; revisar su bitácora; dar de baja a quien ya no es miembro. |

## Segregación de tareas (A.6.1.2)

- Quien escribe un cambio no lo aprueba solo: el CI debe pasar y otro integrante revisa el PR antes de mezclar a `main`.
- Las llaves de cada proveedor de pago las administra su responsable. Solo el RSI tiene acceso a las variables de Render y a la base de producción.
- El dueño de un gimnasio no puede ver ni cambiar otro gimnasio. Las acciones sensibles (reembolsos, cancelaciones, pagos registrados) quedan en la bitácora, a la vista del dueño.

## Contactos (A.6.1.3 y A.6.1.4)

| Para qué | Contacto |
|---|---|
| Vulneración de datos personales | Los titulares afectados y, si corresponde, la Secretaría Anticorrupción y Buen Gobierno (autoridad de la LFPDPPP de 2025) |
| Fraude o problemas con un cobro | Soporte de Stripe, PayPal u Openpay, desde su panel |
| Delitos informáticos | Policía Cibernética (SSPC) |
| Avisos de seguridad | Avisos de seguridad de GitHub (Dependabot), boletines de Spring y de Expo |
