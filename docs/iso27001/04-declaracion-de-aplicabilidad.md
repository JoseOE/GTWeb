# 04 · Declaración de aplicabilidad (SoA)

Cláusula 6.1.3 d) de la NMX-I-27001-NYCE-2015. Revisa los **114 controles del Anexo A** (numeración 2013) para el alcance de [01 · Contexto y alcance](01-contexto-y-alcance.md): si aplican, por qué, cómo se implementan en GymTrack y dónde está la evidencia. La última columna es el control equivalente de ISO/IEC 27001:2022.

**Criterios**
- **SÍ:** el riesgo que atiende el control existe dentro del alcance, aunque lo ejecute un proveedor.
- **NO:** el activo o el riesgo no existe dentro del alcance.
- **Estado:** ✅ implementado · 🟡 parcial (hay una acción en el [plan de tratamiento](03-analisis-de-riesgos.md#plan-de-tratamiento-613-e-y-83)) · ⏳ pendiente.

Las rutas de código son de GTWeb (`src/main/java/com/gymtrack/…`) salvo que digan GTApp. Los riesgos (R01…) están en [03 · Análisis de riesgos](03-analisis-de-riesgos.md).

## Resumen

| Dominio | Controles | Aplican | No aplican | ✅ | 🟡 | ⏳ |
|---|---|---|---|---|---|---|
| A.5 Políticas de seguridad | 2 | 2 | 0 | 2 | 0 | 0 |
| A.6 Organización de la seguridad | 7 | 7 | 0 | 6 | 1 | 0 |
| A.7 Seguridad de los recursos humanos | 6 | 4 | 2 | 3 | 1 | 0 |
| A.8 Gestión de activos | 10 | 7 | 3 | 7 | 0 | 0 |
| A.9 Control de acceso | 14 | 14 | 0 | 12 | 2 | 0 |
| A.10 Criptografía | 2 | 2 | 0 | 2 | 0 | 0 |
| A.11 Seguridad física y ambiental | 15 | 4 | 11 | 2 | 2 | 0 |
| A.12 Seguridad de las operaciones | 14 | 14 | 0 | 12 | 2 | 0 |
| A.13 Seguridad de las comunicaciones | 7 | 6 | 1 | 6 | 0 | 0 |
| A.14 Adquisición, desarrollo y mantenimiento | 13 | 12 | 1 | 11 | 1 | 0 |
| A.15 Relación con proveedores | 5 | 5 | 0 | 4 | 1 | 0 |
| A.16 Gestión de incidentes | 7 | 7 | 0 | 7 | 0 | 0 |
| A.17 Continuidad | 4 | 4 | 0 | 2 | 2 | 0 |
| A.18 Cumplimiento | 8 | 7 | 1 | 6 | 1 | 0 |
| **Total** | **114** | **95** | **19** | **82** | **13** | **0** |

## A.5 Políticas de seguridad de la información

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.5.1.1 | Políticas para la seguridad de la información | SÍ | Se tratan datos personales y cobros; sin reglas comunes cada quien protege a su manera | Política aprobada por el RSI y publicada en el repositorio: [02 · Política y roles](02-politica-y-roles.md) | ✅ | 5.1 |
| A.5.1.2 | Revisión de las políticas | SÍ | El producto cambia cada semana (pagos reales, mostrador) | Revisión semestral, tras incidentes o cambios de alcance; tabla de versiones en el [índice](README.md#vigencia) | ✅ | 5.1 |

## A.6 Organización de la seguridad de la información

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.6.1.1 | Roles y responsabilidades | SÍ | Cinco personas con acceso a código, llaves y proveedores | RSI y un responsable por integración: [02 · Roles](02-politica-y-roles.md#roles-y-responsabilidades-a611) | ✅ | 5.2 |
| A.6.1.2 | Segregación de tareas | SÍ | Quien escribe un cambio no debe ser el único que lo aprueba | PR más CI obligatorio antes de `main`; llaves de cada proveedor con su responsable; dueño limitado a su gimnasio | ✅ | 5.3 |
| A.6.1.3 | Contacto con las autoridades | SÍ | La LFPDPPP pide atender vulneraciones y derechos ARCO | Tabla de contactos en [02](02-politica-y-roles.md#contactos-a613-y-a614); procedimiento en [05](05-gestion-de-incidentes.md) | ✅ | 5.5 |
| A.6.1.4 | Contacto con grupos de interés especial | SÍ | Las vulnerabilidades de Spring, Expo y librerías se publican en avisos | Avisos de seguridad de GitHub y Dependabot; boletines de Spring y Expo | ✅ | 5.6 |
| A.6.1.5 | Seguridad en la gestión de proyectos | SÍ | Cada módulo nuevo (pagos, mostrador) abre riesgos nuevos | Cada PR describe el riesgo y la prueba; los cambios de seguridad llevan su prueba (`pruebas/seguridad.mjs`, `iso27001.mjs`) | ✅ | 5.8 |
| A.6.2.1 | Política de dispositivos móviles | SÍ | Los miembros usan la app en su teléfono y los dueños pueden abrir el panel desde el suyo | GTApp guarda el token en el almacenamiento seguro del teléfono y lo borra al cerrar sesión; la sesión vence a los 30 días | ✅ | 8.1 |
| A.6.2.2 | Teletrabajo | SÍ | Todo el equipo trabaja desde casa, en sus propias computadoras | Reglas: disco cifrado, bloqueo de pantalla y llaves solo en `.env`; verificación pendiente en las 5 computadoras (R14) | 🟡 | 6.7 |

## A.7 Seguridad de los recursos humanos

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.7.1.1 | Investigación de antecedentes | NO | No hay contrataciones: el equipo son estudiantes del mismo grupo asignados por la materia | — | — | 6.1 |
| A.7.1.2 | Términos y condiciones del empleo | SÍ | Quien entra al equipo recibe acceso a datos y llaves | Al entrar, cada integrante acepta la política de [02](02-politica-y-roles.md) (confidencialidad y manejo de llaves) | ✅ | 6.2 |
| A.7.2.1 | Responsabilidades de la dirección | SÍ | El RSI debe exigir que la política se cumpla | El RSI revisa los PR de seguridad, la bitácora y los respaldos ([07](07-evaluacion-y-mejora.md)) | ✅ | 5.4 |
| A.7.2.2 | Concientización, educación y capacitación | SÍ | Los errores más comunes son humanos: llaves en el chat, contraseñas en capturas | Repaso de la política al inicio del semestre; INC-002 se usó como caso práctico | 🟡 | 6.3 |
| A.7.2.3 | Proceso disciplinario | NO | No hay relación laboral; un incumplimiento se trata con el docente según el reglamento escolar | — | — | 6.4 |
| A.7.3.1 | Terminación o cambio de responsabilidades | SÍ | Si alguien deja el equipo conserva accesos a GitHub y a los proveedores | Lista de salida: quitar el acceso a GitHub, Render, Atlas y los proveedores, y rotar las llaves que conocía ([06](06-operacion.md#secretos-y-llaves)) | ✅ | 6.5 |

## A.8 Gestión de activos

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.8.1.1 | Inventario de activos | SÍ | No se protege lo que no se conoce | [Inventario AC-01 a AC-15](01-contexto-y-alcance.md#44-inventario-de-activos-a811) | ✅ | 5.9 |
| A.8.1.2 | Propiedad de los activos | SÍ | Cada activo necesita un responsable | Columna "Dueño" del inventario | ✅ | 5.9 |
| A.8.1.3 | Uso aceptable de los activos | SÍ | Las cuentas de los proveedores y la base son solo para operar GymTrack | Política, punto 4; las llaves de sandbox solo se usan para pruebas | ✅ | 5.10 |
| A.8.1.4 | Devolución de activos | SÍ | Quien sale del equipo puede conservar llaves o respaldos | Lista de salida de A.7.3.1: borrar `.env` y respaldos locales | ✅ | 5.11 |
| A.8.2.1 | Clasificación de la información | SÍ | Una llave no se trata igual que el catálogo público | Niveles Pública, Interna, Confidencial y Restringida ([01](01-contexto-y-alcance.md#44-inventario-de-activos-a811)) | ✅ | 5.12 |
| A.8.2.2 | Etiquetado de la información | SÍ | Documentos y archivos deben dejar ver su nivel | Recibos y fichas dicen "pago de prueba (sandbox)"; respaldos con nombre y extensión `.enc`; los secretos solo en variables con nombre (`*_KEY`, `*_SECRET`) | ✅ | 5.13 |
| A.8.2.3 | Manejo de activos | SÍ | Los datos confidenciales viajan en recibos, correos y respaldos | Recibos solo para el dueño del pedido o con enlace temporal de 7 días; respaldos cifrados; la API responde con `Cache-Control: no-store` | ✅ | 5.10 |
| A.8.3.1 | Gestión de medios removibles | NO | No se usan memorias ni discos: todo está en la nube | — | — | 7.10 |
| A.8.3.2 | Disposición de medios | NO | No hay medios físicos propios; los respaldos expiran solos a los 30 días | — | — | 7.10 |
| A.8.3.3 | Transferencia de medios físicos | NO | No se transportan medios físicos | — | — | 7.10 |

## A.9 Control de acceso

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.9.1.1 | Política de control de acceso | SÍ | Tres tipos de usuario (dueño, miembro, público) con permisos distintos | Reglas en `config/SesionFilter.java` (rutas públicas, del panel y dueño de cada recurso) y `service/AccesoService.java` | ✅ | 5.15 |
| A.9.1.2 | Acceso a las redes y servicios de red | SÍ | La API está en internet y la base en Atlas | Solo HTTPS; la base acepta conexiones con usuario y contraseña desde Render; la API pide sesión salvo en sus rutas públicas | ✅ | 5.15 |
| A.9.2.1 | Registro y baja de usuarios | SÍ | Cualquiera puede crear una cuenta | Registro con verificación del correo (código de 6 dígitos, 15 min); el dueño desvincula miembros (anotado en la bitácora) | ✅ | 5.16 |
| A.9.2.2 | Provisión de acceso a usuarios | SÍ | Un miembro solo debe entrar al gimnasio que lo aceptó | Solicitud con código de invitación; el dueño aprueba (`PATCH /members/{id}`), anotado en la bitácora | ✅ | 5.18 |
| A.9.2.3 | Derechos de acceso privilegiado | SÍ | El dueño puede reembolsar, cancelar y registrar pagos | Solo el dueño de ese gimnasio (`AccesoService.exigirDueno`); cada acción queda en la bitácora; en el equipo, solo el RSI administra Render y Atlas | ✅ | 8.2 |
| A.9.2.4 | Información secreta de autenticación | SÍ | Contraseñas, tokens y llaves | Contraseñas temporales que se cambian al entrar; códigos y enlaces de un solo uso guardados como hash; llaves solo en variables de entorno | ✅ | 5.17 |
| A.9.2.5 | Revisión de los derechos de acceso | SÍ | Miembros que ya no van siguen con acceso; colaboradores que salieron | El dueño revisa "Mis usuarios" y la bitácora; el RSI revisa cada semestre los accesos a GitHub, Render, Atlas y los proveedores ([07](07-evaluacion-y-mejora.md)) | ✅ | 5.18 |
| A.9.2.6 | Retiro o ajuste de los derechos de acceso | SÍ | Un miembro dado de baja o un integrante que sale | Baja de miembro (`DELETE /members/{id}`); cambio de contraseña cierra las demás sesiones; lista de salida del equipo | ✅ | 5.18 |
| A.9.3.1 | Uso de información secreta de autenticación | SÍ | Los usuarios y el equipo deben cuidar sus credenciales | Política, punto 4: nada de llaves ni contraseñas en el chat o en capturas (INC-002); la tienda recomienda no reutilizar contraseñas | 🟡 | 5.17 |
| A.9.4.1 | Restricción de acceso a la información | SÍ | Un usuario no debe ver datos de otro (IDOR, R10) | SesionFilter revisa el dueño de cada gimnasio, cuenta, máquina y rutina; la tienda, los pedidos y los recibos lo revisan en AccesoService; `pruebas/seguridad.mjs` | ✅ | 8.3 |
| A.9.4.2 | Procedimientos de inicio de sesión seguros | SÍ | Toma de cuentas (R01, R03) | Token al azar de 256 bits por sesión (solo se guarda su hash), vence a los 30 días; mismo mensaje si el correo no existe; freno tras 10 fallos; bitácora; **falta `EXIGIR_TOKEN=true` en Render (R01)** | 🟡 | 8.5 |
| A.9.4.3 | Sistema de gestión de contraseñas | SÍ | Contraseñas débiles o guardadas en texto | BCrypt con sal; 8+ caracteres con letra, número y símbolo; las de SHA-256 antiguas pasan a BCrypt al entrar (`util/PasswordUtil.java`) | ✅ | 5.17 |
| A.9.4.4 | Uso de programas utilitarios privilegiados | SÍ | La consola de Atlas y el shell de Render saltan los controles de la app | Solo el RSI tiene acceso; se usan para soporte y quedan registrados en los paneles de Atlas y Render | ✅ | 8.18 |
| A.9.4.5 | Control de acceso al código fuente | SÍ | El código revela cómo funciona la seguridad | Repositorios en GitHub con acceso solo para el equipo; `main` solo cambia por PR con el CI en verde | ✅ | 8.4 |

## A.10 Criptografía

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.10.1.1 | Política de uso de controles criptográficos | SÍ | Contraseñas, tokens, tráfico y respaldos deben protegerse | TLS en todo el tráfico (HSTS); BCrypt para contraseñas; SHA-256 para huellas de tokens y códigos; AES-256 (PBKDF2, 200 000 iteraciones) para respaldos; `SecureRandom` para tokens | ✅ | 8.24 |
| A.10.1.2 | Gestión de llaves | SÍ | Las llaves de pago y de la base permiten actuar como GymTrack | Dónde vive cada llave, quién la administra y cuándo se rota: [06 · Secretos y llaves](06-operacion.md#secretos-y-llaves) | ✅ | 8.24 |

## A.11 Seguridad física y ambiental

GymTrack no tiene instalaciones propias: los servidores están en los centros de datos de Render y MongoDB Atlas, cuya seguridad física se controla en A.15. Solo aplican los controles de los equipos que usa el equipo y de la computadora de recepción.

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.11.1.1 | Perímetro de seguridad física | NO | Sin instalaciones propias | — (proveedores, A.15) | — | 7.1 |
| A.11.1.2 | Controles físicos de entrada | NO | Sin instalaciones propias | — | — | 7.2 |
| A.11.1.3 | Seguridad de oficinas, salas e instalaciones | NO | Sin oficinas | — | — | 7.3 |
| A.11.1.4 | Protección contra amenazas externas y ambientales | NO | Sin instalaciones propias | — (proveedores, A.15) | — | 7.5 |
| A.11.1.5 | Trabajo en áreas seguras | NO | Sin áreas seguras propias | — | — | 7.6 |
| A.11.1.6 | Áreas de entrega y carga | NO | Sin instalaciones propias | — | — | 7.2 |
| A.11.2.1 | Ubicación y protección del equipo | NO | No hay servidores propios | — | — | 7.8 |
| A.11.2.2 | Servicios de suministro | NO | La energía y el enfriamiento son del proveedor | — | — | 7.11 |
| A.11.2.3 | Seguridad del cableado | NO | Sin cableado propio | — | — | 7.12 |
| A.11.2.4 | Mantenimiento del equipo | NO | Sin equipo propio de servidor | — | — | 7.13 |
| A.11.2.5 | Retiro de activos | NO | No se sacan equipos de ninguna instalación | — | — | 7.10 |
| A.11.2.6 | Seguridad del equipo fuera de las instalaciones | SÍ | Las computadoras del equipo tienen el `.env` con llaves (R14) | Disco cifrado y bloqueo de pantalla; verificación pendiente en las 5 computadoras | 🟡 | 7.9 |
| A.11.2.7 | Disposición o reutilización segura del equipo | SÍ | Una computadora vendida o formateada puede llevar llaves | Borrar el `.env` y los respaldos locales y rotar las llaves antes de deshacerse del equipo | 🟡 | 7.14 |
| A.11.2.8 | Equipo de usuario desatendido | SÍ | El panel queda abierto en la recepción del gimnasio (R15) | Botón "Cerrar sesión" siempre visible; la sesión vence a los 30 días; cada inicio de sesión queda en la bitácora del dueño | ✅ | 8.1 |
| A.11.2.9 | Escritorio y pantalla limpios | SÍ | La pantalla de recepción muestra datos de miembros y ventas | La tabla de pagos y la bitácora no muestran contraseñas ni datos de tarjeta; solo marca y últimos 4 dígitos | ✅ | 7.7 |

## A.12 Seguridad de las operaciones

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.12.1.1 | Procedimientos de operación documentados | SÍ | El despliegue, los respaldos y las llaves no pueden depender de la memoria de una persona | README (variables y despliegue) y [06 · Operación](06-operacion.md) | ✅ | 5.37 |
| A.12.1.2 | Gestión de cambios | SÍ | Un cambio mal probado tumba la tienda o abre un hueco | Rama, PR con descripción y prueba, CI y mezcla; Render despliega `main`; *rollback* desde Render | ✅ | 8.32 |
| A.12.1.3 | Gestión de la capacidad | SÍ | Render corre una sola instancia y Atlas tiene un plan con límite | Métricas de Render y Atlas; las listas del panel son paginadas; la bitácora y los tokens expiran solos (índices TTL) | 🟡 | 8.6 |
| A.12.1.4 | Separación de los entornos de desarrollo, prueba y operación | SÍ | Probar contra producción puede dañar datos reales | Local (Mongo propio), CI (Mongo de prueba, `PAGOS_MODO=simulado`) y Render (`sandbox`); las pruebas se niegan a correr fuera de local (`pruebas/entorno.mjs`) | ✅ | 8.31 |
| A.12.2.1 | Controles contra código malicioso | SÍ | Dependencias comprometidas o archivos subidos | Dependencias revisadas por Dependabot; las imágenes subidas se validan y se vuelven a codificar en el navegador; CSP que limita de dónde se cargan scripts | ✅ | 8.7 |
| A.12.3.1 | Respaldo de la información | SÍ | Pérdida de datos (R07) | `.github/workflows/respaldo.yml`: `mongodump` diario, cifrado AES-256 y guardado 30 días; falta cargar los secretos y la primera restauración | 🟡 | 8.13 |
| A.12.4.1 | Registro de eventos | SÍ | Sin registros no se puede saber qué pasó (lección de INC-001) | Bitácora de seguridad: inicios de sesión, fallos, frenos, accesos negados y cambios del panel, con IP y dispositivo (`config/BitacoraFilter.java`, `service/BitacoraService.java`) | ✅ | 8.15 |
| A.12.4.2 | Protección de la información de registro | SÍ | Un registro editable no sirve como evidencia | La bitácora no tiene rutas para editar ni borrar; solo la lee el dueño; se borra sola a los 180 días; nunca guarda contraseñas ni tokens (`pruebas/iso27001.mjs`) | ✅ | 8.15 |
| A.12.4.3 | Registros del administrador y del operador | SÍ | El dueño tiene permisos altos (reembolsos, pagos, precios) | Cada acción del dueño queda en la bitácora con su usuario, ruta e IP, y la ve en Panel → Seguridad → Bitácora | ✅ | 8.15 |
| A.12.4.4 | Sincronización de relojes | SÍ | Las fechas de pagos, vencimientos y bitácora deben coincidir | Servidores de Render y Atlas sincronizados por NTP; todo se guarda en UTC (`Instant`) y se muestra en hora de México | ✅ | 8.17 |
| A.12.5.1 | Instalación de software en sistemas operativos | SÍ | Software no autorizado en producción | Producción solo corre la imagen que construye el `Dockerfile` desde `main`; nada se instala a mano en el servidor (cada despliegue parte de cero) | ✅ | 8.19 |
| A.12.6.1 | Gestión de vulnerabilidades técnicas | SÍ | Dependencias con fallas conocidas (R08) | `.github/dependabot.yml` (Maven, npm y Actions, semanal); corregir las críticas en 7 días | ✅ | 8.8 |
| A.12.6.2 | Restricciones en la instalación de software | SÍ | Una librería nueva puede traer riesgos | Toda dependencia nueva entra por PR (`pom.xml`, `package.json`) y pasa el CI | ✅ | 8.19 |
| A.12.7.1 | Controles de auditoría de sistemas | SÍ | Las pruebas de seguridad pueden afectar la operación | Las pruebas corren en local o en el CI, nunca contra Render; la prueba de los sandbox (`sandbox.mjs`) solo usa cuentas de prueba | ✅ | 8.34 |

## A.13 Seguridad de las comunicaciones

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.13.1.1 | Controles de red | SÍ | La API está expuesta a internet | Solo HTTPS (Render) con HSTS; CORS solo en `/api/**` y sin cookies (la sesión va en el encabezado); rutas no canónicas rechazadas | ✅ | 8.20 |
| A.13.1.2 | Seguridad de los servicios de red | SÍ | Dependemos de las redes de Render, Atlas y los proveedores de pago | Conexiones cifradas (TLS) con Atlas, Brevo, Stripe, PayPal y Openpay; llaves por variables | ✅ | 8.21 |
| A.13.1.3 | Segregación en redes | NO | PaaS: no administramos redes ni subredes; lo hace cada proveedor | — | — | 8.22 |
| A.13.2.1 | Políticas y procedimientos de transferencia de información | SÍ | Recibos, fichas y datos viajan por correo y por la API | Los recibos solo los descarga el dueño del pedido o quien tenga el enlace temporal; la API responde `no-store`; la página nunca manda datos a terceros que no estén en la CSP | ✅ | 5.14 |
| A.13.2.2 | Acuerdos de transferencia de información | SÍ | Compartimos datos con los proveedores de pago y de correo | Términos de cada proveedor (encargados del tratamiento); el aviso de privacidad dice qué se comparte con cada uno | ✅ | 5.14 |
| A.13.2.3 | Mensajería electrónica | SÍ | Los correos llevan códigos y recibos | Correos por la API HTTPS de Brevo; códigos de un solo uso que vencen; el correo nunca lleva la contraseña | ✅ | 5.14 |
| A.13.2.4 | Acuerdos de confidencialidad | SÍ | El equipo ve datos y llaves | Confidencialidad incluida en la política que acepta cada integrante (A.7.1.2) | ✅ | 6.6 |

## A.14 Adquisición, desarrollo y mantenimiento de sistemas

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.14.1.1 | Análisis y especificación de requisitos de seguridad | SÍ | Cada función nueva trae riesgos | Cada PR lista qué protege y cómo se prueba; los riesgos de pagos se analizaron antes de integrarlos | ✅ | 5.8 |
| A.14.1.2 | Seguridad de servicios de aplicaciones en redes públicas | SÍ | XSS, *clickjacking*, *sniffing* (R04, R05, R12) | `config/CabecerasSeguridadFilter.java`: CSP, HSTS, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy; `pruebas/iso27001.mjs` §1 | ✅ | 8.26 |
| A.14.1.3 | Protección de transacciones de servicios de aplicaciones | SÍ | Fraude en cobros (R09) | El servidor recalcula el total; sesiones de Stripe y órdenes de PayPal de un solo uso; captura después de crear el pedido; la tarjeta nunca toca GymTrack | ✅ | 8.26 |
| A.14.2.1 | Política de desarrollo seguro | SÍ | Las fallas de seguridad nacen en el código | Reglas: validar en el servidor, escapar al pintar, sin secretos en el código, prueba automática por cada control | ✅ | 8.25 |
| A.14.2.2 | Procedimientos de control de cambios del sistema | SÍ | Cambios sin revisar | PR y CI obligatorios; commits por integrante | ✅ | 8.32 |
| A.14.2.3 | Revisión técnica tras cambios de la plataforma | SÍ | Actualizar Spring, Java o Expo puede romper controles | Las actualizaciones de Dependabot pasan toda la suite, incluidas `seguridad.mjs` e `iso27001.mjs` | ✅ | 8.32 |
| A.14.2.4 | Restricciones a cambios en paquetes de software | SÍ | Modificar librerías de terceros complica las actualizaciones | Se usan las librerías sin modificar, con versión fija en `pom.xml` y `package-lock.json` | ✅ | 8.32 |
| A.14.2.5 | Principios de ingeniería de sistemas seguros | SÍ | INC-001 mostró que comparar rutas sin normalizarlas es inseguro | Defensa en capas (filtro y servicio); rechazar lo ambiguo (rutas no canónicas); el webhook no se cree, se consulta; denegar por defecto | ✅ | 8.27 |
| A.14.2.6 | Entorno de desarrollo seguro | SÍ | Los entornos locales tienen llaves | Llaves de sandbox en `.env` no versionado; base local separada; CI sin secretos de producción | ✅ | 8.31 |
| A.14.2.7 | Desarrollo externalizado | NO | Todo lo desarrolla el equipo | — | — | 8.30 |
| A.14.2.8 | Pruebas de seguridad del sistema | SÍ | Un control sin prueba se rompe sin que nadie lo note | `pruebas/seguridad.mjs` (sesión, permisos, rutas no canónicas) e `iso27001.mjs` (cabeceras, bitácora, freno) en cada PR | ✅ | 8.29 |
| A.14.2.9 | Pruebas de aceptación del sistema | SÍ | Una función debe probarse completa antes de producción | Suite de punta a punta en el CI (más de 400 comprobaciones) y prueba manual en el navegador contra los sandbox | ✅ | 8.29 |
| A.14.3.1 | Protección de los datos de prueba | SÍ | Usar datos reales en pruebas los expone | Las pruebas crean usuarios ficticios (`@prueba.mx`); tarjetas y cuentas de prueba de cada proveedor; ningún respaldo de producción se usa en pruebas sin anonimizar | 🟡 | 8.33 |

## A.15 Relación con los proveedores

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.15.1.1 | Política de seguridad para las relaciones con proveedores | SÍ | Toda la infraestructura es de terceros | Criterios: cifrado en tránsito, certificaciones de seguridad y acceso por llaves revocables. Proveedores: Render, MongoDB Atlas, GitHub, Brevo, Stripe, PayPal, Openpay, Expo y Google Analytics | ✅ | 5.19 |
| A.15.1.2 | Seguridad en los acuerdos con proveedores | SÍ | Los proveedores tratan datos personales por nuestra cuenta | Se aceptan sus términos y acuerdos de tratamiento de datos; el aviso de privacidad los nombra como encargados | ✅ | 5.20 |
| A.15.1.3 | Cadena de suministro de TIC | SÍ | CDN y librerías de terceros entran a las páginas | Versiones fijas de cada CDN (Bootstrap, Boxicons, Leaflet, html5-qrcode); CSP con solo esos dominios | 🟡 | 5.21 |
| A.15.2.1 | Monitoreo y revisión de los servicios de los proveedores | SÍ | Un proveedor puede cambiar o caerse (R18) | Revisión semestral de su estado y sus avisos; alertas de Render y Atlas al correo del RSI | ✅ | 5.22 |
| A.15.2.2 | Gestión de cambios en los servicios de los proveedores | SÍ | Stripe cambió su API y rechazó `payment_method_types` | Los cambios de API se prueban con `pruebas/sandbox.mjs` antes de desplegarse | ✅ | 5.22 |

## A.16 Gestión de incidentes de seguridad de la información

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.16.1.1 | Responsabilidades y procedimientos | SÍ | Un incidente sin dueño se atiende tarde | [05 · Gestión de incidentes](05-gestion-de-incidentes.md): el RSI coordina; pasos y plazos | ✅ | 5.24 |
| A.16.1.2 | Reporte de eventos de seguridad | SÍ | Quien ve algo raro debe saber a quién avisar | Canal del equipo y un issue privado (*security advisory*) en GitHub; correo del responsable para usuarios | ✅ | 6.8 |
| A.16.1.3 | Reporte de debilidades de seguridad | SÍ | Una debilidad detectada (p. ej. `EXIGIR_TOKEN=false`) debe quedar registrada | Se registra como no conformidad en [07](07-evaluacion-y-mejora.md) o como incidente si ya se explotó | ✅ | 6.8 |
| A.16.1.4 | Evaluación de eventos y decisión | SÍ | No todo evento es un incidente | Tabla de severidad en [05](05-gestion-de-incidentes.md#clasificación) | ✅ | 5.25 |
| A.16.1.5 | Respuesta a incidentes | SÍ | Contener rápido limita el daño | Pasos: contener, corregir con PR urgente, desplegar, verificar y avisar; INC-001 se corrigió el mismo día | ✅ | 5.26 |
| A.16.1.6 | Aprendizaje de los incidentes | SÍ | El mismo error no debe repetirse | Cada incidente deja una prueba de regresión y una lección; INC-001 originó la bitácora | ✅ | 5.27 |
| A.16.1.7 | Recolección de evidencia | SÍ | Para saber el alcance y, si hace falta, denunciar | Bitácora (180 días), historial de Git y de PRs, logs de Render, exportación sin modificar | ✅ | 5.28 |

## A.17 Aspectos de seguridad de la información en la continuidad del negocio

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.17.1.1 | Planificación de la continuidad de la seguridad | SÍ | Si cae Render o se pierde la base, el gimnasio no puede cobrar (R11) | Plan en [06 · Operación](06-operacion.md#continuidad): *rollback*, restaurar respaldo y volver a desplegar | ✅ | 5.29 |
| A.17.1.2 | Implementación de la continuidad de la seguridad | SÍ | En una contingencia los controles deben seguir activos | Lo restaurado pasa por el mismo código y las mismas variables: sesión, permisos y cabeceras siguen iguales | ✅ | 5.29 |
| A.17.1.3 | Verificación y revisión de la continuidad | SÍ | Un plan que no se prueba no funciona | Prueba mensual de restauración; pendiente la primera (R07) | 🟡 | 5.29 |
| A.17.2.1 | Disponibilidad de las instalaciones de procesamiento | SÍ | Una sola instancia es un punto único de falla | La base en Atlas tiene réplicas; la aplicación se acepta en una instancia (riesgo R11 aceptado) | 🟡 | 8.14 |

## A.18 Cumplimiento

| Control | Nombre | Aplica | Motivo | Implementación y evidencia | Estado | 2022 |
|---|---|---|---|---|---|---|
| A.18.1.1 | Identificación de la legislación y los requisitos contractuales | SÍ | LFPDPPP, Ley Federal de Protección al Consumidor (tienda en línea) y términos de los proveedores | Aviso de privacidad (`privacidad.html`), términos de compra (`terminos.html`) y documento del Tema 3 | ✅ | 5.31 |
| A.18.1.2 | Derechos de propiedad intelectual | SÍ | Librerías, íconos e imágenes de terceros | Librerías con licencias abiertas (MIT, Apache 2.0); imágenes de Unsplash e íconos de Flaticon según sus licencias | 🟡 | 5.32 |
| A.18.1.3 | Protección de los registros | SÍ | Pedidos, recibos y bitácora deben conservarse íntegros | Los pedidos solo cambian de estado de forma condicionada; recibos con folio; la bitácora no se edita; respaldos diarios | ✅ | 5.33 |
| A.18.1.4 | Privacidad y protección de datos personales | SÍ | Se tratan datos de miembros y dueños | Aviso de privacidad con versión y consentimiento registrado; datos mínimos; sin datos de tarjeta; retención de la bitácora de 180 días; derechos ARCO | ✅ | 5.34 |
| A.18.1.5 | Regulación de los controles criptográficos | NO | No se importa ni exporta tecnología criptográfica regulada; se usa TLS y algoritmos estándar | — | — | 5.31 |
| A.18.2.1 | Revisión independiente de la seguridad | SÍ | El equipo no debe ser el único que se revisa | Revisión del docente de la materia y auditoría interna cruzada entre integrantes ([07](07-evaluacion-y-mejora.md)) | ✅ | 5.35 |
| A.18.2.2 | Cumplimiento de las políticas y normas de seguridad | SÍ | Verificar que la política se sigue | El RSI revisa los PR, la bitácora y los respaldos cada mes | ✅ | 5.36 |
| A.18.2.3 | Revisión del cumplimiento técnico | SÍ | Verificar que los controles técnicos funcionan | Pruebas automáticas en cada PR (`seguridad.mjs`, `iso27001.mjs`) y revisión de cabeceras en producción | ✅ | 8.8 |

## Exclusiones justificadas

19 controles no aplican: A.7.1.1, A.7.2.3, A.8.3.1 a A.8.3.3, A.11.1.1 a A.11.1.6, A.11.2.1 a A.11.2.5, A.13.1.3, A.14.2.7 y A.18.1.5. La razón principal es que GymTrack no tiene instalaciones, servidores, redes ni medios físicos propios: todo corre en servicios en la nube cuya seguridad física y de red se controla en A.15. Los demás no aplican porque no hay contrataciones ni desarrollo externo.

Aprobó: José María Ortiz Escamilla, responsable del SGSI. 27 de octubre de 2026.
