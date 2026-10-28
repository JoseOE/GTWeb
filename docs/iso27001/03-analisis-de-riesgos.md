# 03 · Análisis y tratamiento de riesgos

Cláusulas 6.1.2 (valoración), 6.1.3 (tratamiento), 8.2 y 8.3 de la norma. Los activos (AC-xx) son los de [01 · Contexto y alcance](01-contexto-y-alcance.md#44-inventario-de-activos-a811).

## Metodología

Cada riesgo es una **amenaza** que aprovecha una **vulnerabilidad** de un **activo**. Se califica así:

| Valor | Probabilidad (P) | Impacto (I) |
|---|---|---|
| 1 · Bajo | Poco probable en un año; requiere acceso interno o un ataque dirigido | Molestia sin datos expuestos ni dinero perdido; se corrige en horas |
| 2 · Medio | Puede pasar en el año; hay herramientas automáticas que lo intentan | Expone datos de un gimnasio o detiene la tienda por horas |
| 3 · Alto | Pasa seguido en internet o ya pasó | Expone datos de varios gimnasios, permite cobrar mal o tomar cuentas, o hay incumplimiento legal |

**Nivel = P × I.** De 1 a 3 es **aceptable**, 4 es **medio** (se trata en el semestre) y de 6 a 9 es **alto** (se trata de inmediato).

**Criterio de aceptación:** el responsable del SGSI acepta un riesgo residual de nivel 3 o menos. Uno mayor solo se acepta por escrito, con su justificación, como R11.

**Opciones de tratamiento:** mitigar (aplicar controles), transferir (a un proveedor o seguro), evitar (quitar la funcionalidad) o aceptar.

## Matriz de riesgos

| Id | Activo | Amenaza / vulnerabilidad | P | I | Nivel | Tratamiento y controles (Anexo A) | P' | I' | Residual | Estado |
|---|---|---|---|---|---|---|---|---|---|---|
| R01 | AC-01, AC-08 | Suplantación: las rutas que comparten la web y la app aceptan el encabezado `X-User-Id` sin token mientras `EXIGIR_TOKEN=false` | 3 | 3 | **9** | Mitigar: la web y la app ya mandan `Authorization: Bearer`; activar `EXIGIR_TOKEN=true` en Render (A.9.4.2, A.9.4.1) | 1 | 3 | 3 | Pendiente: cambiar la variable (NC-01) |
| R02 | AC-02 | Evasión del filtro de sesión escribiendo la ruta de otra forma (`/api;x/`, `/%61pi/`) | 3 | 3 | **9** | Mitigar: el filtro compara la ruta canónica y rechaza las demás con 400; prueba de regresión (A.14.2.5, A.14.2.8) | 1 | 3 | 3 | Hecho (INC-001, PR #59) |
| R03 | AC-01, AC-08 | Toma de cuentas por contraseñas adivinadas o filtradas (fuerza bruta, *credential stuffing*) | 2 | 3 | **6** | Mitigar: BCrypt, contraseña de 8+ caracteres con letra, número y símbolo, freno tras 10 fallos en 15 min, bitácora de fallos y avisos por correo al cambiar la contraseña (A.9.4.2, A.9.4.3, A.12.4.1) | 1 | 3 | 3 | Hecho. Mejora: segundo factor para dueños (NC-03) |
| R04 | AC-02, AC-03 | XSS: un nombre de producto, reseña o dato de miembro con código malicioso se ejecuta en el panel | 2 | 3 | **6** | Mitigar: todo dato se escapa al pintarlo (`escapeHtml`, Thymeleaf); CSP que impide mandar datos a otros dominios (A.14.2.5, A.14.1.2) | 1 | 3 | 3 | Hecho. Mejora: quitar `'unsafe-inline'` (NC-04) |
| R05 | AC-02 | *Clickjacking*: el panel metido en un iframe de otro sitio | 2 | 2 | 4 | Mitigar: `frame-ancestors 'none'` y `X-Frame-Options: DENY` (A.14.1.2) | 1 | 2 | 2 | Hecho |
| R06 | AC-05 | Fuga de llaves (en un commit, una captura o el chat) | 2 | 3 | **6** | Mitigar: `.env` fuera de Git; llaves solo en Render y en GitHub Secrets; llaves de sandbox; rotación al exponerse (A.9.2.4, A.10.1.2) | 1 | 3 | 3 | Hecho. Mejora: activar *push protection* de GitHub (NC-06) |
| R07 | AC-01 | Pérdida de datos por borrado, error o falla del proveedor | 2 | 3 | **6** | Mitigar: respaldo diario cifrado (AES-256), 30 días, y prueba mensual de restauración (A.12.3.1) | 1 | 3 | 3 | Pendiente: cargar los secretos del respaldo (NC-02) |
| R08 | AC-02, AC-04 | Dependencia con una vulnerabilidad conocida | 2 | 3 | **6** | Mitigar: Dependabot semanal (Maven, npm, Actions) y CI antes de mezclar (A.12.6.1) | 1 | 3 | 3 | Hecho |
| R09 | AC-10, AC-11, AC-12 | Fraude en el cobro: cambiar el total, reusar un pago aprobado o fingir el aviso de "pagado" | 2 | 3 | **6** | Mitigar: el servidor recalcula el total; cada sesión de Stripe u orden de PayPal es de un solo uso y del mismo comprador, gimnasio y monto; el webhook de Openpay no se cree, se consulta el cargo (A.14.1.3) | 1 | 3 | 3 | Hecho |
| R10 | AC-01 | Un dueño ve o cambia datos de otro gimnasio (IDOR) | 2 | 3 | **6** | Mitigar: SesionFilter y AccesoService revisan a quién pertenece cada recurso; pruebas por recurso (A.9.4.1) | 1 | 3 | 3 | Hecho |
| R11 | AC-02 | Indisponibilidad: Render corre una sola instancia; un despliegue fallido o una caída detiene todo | 3 | 2 | **6** | Aceptar y mitigar en parte: *rollback* en Render, CI antes de desplegar, base con réplicas en Atlas (A.17.1.1, A.17.2.1) | 2 | 2 | 4 | Aceptado: proyecto académico sin presupuesto para alta disponibilidad |
| R12 | AC-08 | Interceptación del tráfico | 1 | 3 | 3 | Mitigar: HTTPS en Render y HSTS de un año (A.13.1.1, A.10.1.1) | 1 | 2 | 2 | Hecho |
| R13 | AC-14 | Enumeración de cuentas o abuso del correo de recuperación | 2 | 1 | 2 | Mitigar: la misma respuesta exista o no la cuenta; un correo por minuto; códigos de 15 a 30 min y 5 intentos (A.9.4.2) | 1 | 1 | 1 | Hecho |
| R14 | AC-13, AC-05 | Robo o pérdida de la computadora de un integrante con el `.env` | 2 | 3 | **6** | Mitigar: cifrado de disco y bloqueo de pantalla; solo llaves de sandbox en local; rotar las llaves si se pierde (A.11.2.6, A.6.2.2) | 1 | 3 | 3 | Parcial: confirmar el cifrado de disco de cada integrante |
| R15 | AC-08 | Sesión del panel abierta en la computadora de recepción | 2 | 2 | 4 | Mitigar: "Cerrar sesión" visible; la sesión vence a los 30 días; la bitácora muestra cada inicio de sesión; guía al dueño (A.11.2.8, A.9.4.2) | 2 | 1 | 2 | Hecho. Mejora: vencimiento por inactividad en el panel |
| R16 | AC-01 | Sin trazabilidad: no saber quién hizo un reembolso o cambió un precio | 2 | 2 | 4 | Mitigar: bitácora de seguridad (180 días) visible al dueño (A.12.4.1, A.12.4.3) | 1 | 1 | 1 | Hecho |
| R17 | AC-01 | Incumplir la LFPDPPP (datos de más, sin aviso, sin derechos ARCO) | 1 | 3 | 3 | Mitigar: aviso de privacidad con su versión, consentimiento registrado, datos mínimos, retención limitada y ARCO por correo (A.18.1.4) | 1 | 2 | 2 | Hecho |
| R18 | AC-07 | Un proveedor (Atlas, Render, Brevo) sufre una brecha o cambia sus condiciones | 1 | 3 | 3 | Transferir y vigilar: proveedores con certificaciones (Stripe PCI DSS nivel 1, MongoDB Atlas ISO/IEC 27001); revisión semestral (A.15.1.1, A.15.2.1) | 1 | 3 | 3 | Aceptado |

**Resumen:**
- **Antes del tratamiento:** 18 riesgos: 11 de nivel alto, 3 medios y 4 aceptables.
- **Después del tratamiento:** ninguno alto. R11 queda en nivel medio (4), aceptado por escrito.
- **Con tratamiento pendiente:** R01, R07 y R14 dependen de acciones de configuración que están en el plan.

## Plan de tratamiento (6.1.3 e y 8.3)

| Acción | Riesgo | Responsable | Fecha | Estado |
|---|---|---|---|---|
| Activar `EXIGIR_TOKEN=true` en Render y confirmar que una petición con solo `X-User-Id` responde 401 | R01 | Jose | 31-oct-2026 | Pendiente |
| Cargar `MONGODB_URI` y `RESPALDO_CLAVE` en los secretos de GitHub, correr el respaldo y hacer la primera prueba de restauración | R07 | Jose | 31-oct-2026 | Pendiente |
| Verificar el cifrado de disco (BitLocker o FileVault) en las 5 computadoras | R14 | Cada integrante | 7-nov-2026 | Pendiente |
| Activar *secret scanning* y *push protection* en los dos repositorios | R06 | Jose | 7-nov-2026 | Pendiente |
| Segundo factor (código por correo) para cuentas de dueño | R03 | Jose y Yael | Próximo semestre | Planeado |
| Mover los scripts en línea a archivos y quitar `'unsafe-inline'` de la CSP | R04 | Eduardo | Próximo semestre | Planeado |
| Vencer la sesión del panel tras 8 horas sin uso | R15 | Jose | Próximo semestre | Planeado |

**Aprobación del riesgo residual (6.1.3 f):** el responsable del SGSI aprueba este plan y acepta los riesgos residuales indicados (incluido R11). 27 de octubre de 2026.
