# 05 · Gestión de incidentes de seguridad

Controles A.16.1.1 a A.16.1.7.

## Definiciones

- **Evento de seguridad:** algo que podría afectar la seguridad. Por ejemplo, muchos inicios de sesión fallidos o un acceso negado en la bitácora.
- **Debilidad:** una falla que todavía nadie ha aprovechado. Se registra como no conformidad en [07](07-evaluacion-y-mejora.md#no-conformidades-y-acciones-correctivas-101).
- **Incidente:** un evento que sí afectó, o pudo afectar de verdad, la confidencialidad, integridad o disponibilidad. Por ejemplo, una falla explotable en producción, una llave publicada o una caída.

## Cómo reportar (A.16.1.2, A.16.1.3)

- **Integrantes del equipo:** avisan en el canal del equipo y abren un *security advisory* privado en el repositorio de GitHub (Security → Advisories). Nunca se reporta en un issue público.
- **Usuarios y dueños:** escriben al correo del responsable que aparece en el aviso de privacidad.
- El reporte dice qué se vio, cuándo, dónde (ruta, pantalla, captura **sin contraseñas ni llaves**) y si alguien más lo sabe.

## Clasificación (A.16.1.4)

| Severidad | Ejemplos | Respuesta |
|---|---|---|
| **Crítica** | Acceso a cuentas o datos de otros sin permiso; llave de producción o de la base publicada; cobros alterados | Contener en menos de 4 horas; corrección urgente el mismo día |
| **Alta** | Falla explotable sin evidencia de uso; caída total de la plataforma | Corrección en 24 horas |
| **Media** | Llave de sandbox expuesta; un control que no funciona del todo | Corrección en 7 días |
| **Baja** | Evento sin impacto (intentos de fuerza bruta frenados) | Se registra y se revisa en la reunión mensual |

## Procedimiento de respuesta (A.16.1.1, A.16.1.5)

1. **Registrar:** el RSI abre el registro del incidente (formato abajo) y asigna un responsable.
2. **Contener:**
   - Rotar la llave expuesta.
   - Cerrar las sesiones afectadas (cambiar la contraseña cierra las demás).
   - Desactivar la función con una variable de Render, o hacer *rollback* al último despliegue bueno.
3. **Preservar la evidencia (A.16.1.7):**
   - Exportar los eventos de la bitácora del periodo, sin editarlos.
   - Guardar los logs de Render.
   - Anotar los commits y PRs involucrados.
4. **Corregir:** PR urgente con la corrección y su prueba de regresión, CI en verde, mezcla y despliegue.
5. **Verificar:** probar contra producción que la falla ya no se reproduce y que lo demás sigue funcionando.
6. **Avisar:**
   - Si se vulneraron datos personales que afecten de forma significativa los derechos de sus titulares, se les avisa sin demora, como pide la LFPDPPP: qué pasó, qué datos y qué deben hacer.
   - Si hubo fraude con pagos, se reporta al proveedor de pago.
7. **Aprender (A.16.1.6):** en menos de una semana se escribe la causa raíz, la lección y la acción correctiva (prueba, control o cambio en la política).

## Registro de incidentes

### INC-001 · Evasión de la sesión escribiendo la ruta de otra forma

| Campo | Detalle |
|---|---|
| Fecha de detección | 15 de octubre de 2026, durante la revisión conjunta de GTWeb y GTApp |
| Reportó | Revisión de código del equipo |
| Severidad | **Crítica** (falla explotable en producción) |
| Activo | AC-02 API y panel; AC-01 base de datos |
| Descripción | `SesionFilter` comparaba la ruta tal como llegaba, pero Spring enruta igual `/api;x/...` y `/%61pi/...` que `/api/...`. Escribiendo la ruta así, sin ningún token, se podía mandar un `X-User-Id` inventado (por ejemplo, el de un dueño) y el controlador lo creía, aun con `EXIGIR_TOKEN=true`. Con cualquier sesión se podía leer o cambiar la cuenta de otro, los miembros de otro gimnasio, registrar pagos (membresías gratis) o regenerar su código. |
| Contención y corrección | PR [JoseOE/GTWeb#59](https://github.com/JoseOE/GTWeb/pull/59), mezclado y desplegado el 15 de octubre (21:33, hora de México): las reglas se comparan contra la ruta que de verdad enruta Spring; una ruta con `;`, `%xx`, `//`, `.` o `..` responde 400; en las rutas públicas se descarta el `X-User-Id`. |
| Verificación | `pruebas/seguridad.mjs` §9: cuatro formas de escribir la ruta, sin token y con la sesión de otro dueño → 400; la ruta normal sigue en 200. Corre en cada PR. |
| ¿Se explotó? | No hay evidencia, pero **tampoco se podía comprobar**: no existía una bitácora de accesos. No se encontraron cambios anómalos en pagos ni en membresías. |
| Causa raíz | Se confió en que la ruta cruda era la misma que la ruta enrutada (falta de normalización), y la seguridad dependía de una sola capa. |
| Lecciones y acciones | 1) Rechazar lo ambiguo en lugar de interpretarlo (A.14.2.5). 2) Toda corrección de seguridad lleva su prueba de regresión (A.14.2.8). 3) Sin registros no se puede saber el alcance de un incidente → se implementó la **bitácora de seguridad** (A.12.4.1). 4) Retirar la transición con `X-User-Id` → `EXIGIR_TOKEN=true` (NC-01). |
| Estado | Cerrado (acciones 3 y 4 en el plan de tratamiento) |

### INC-002 · Contraseña de una cuenta de prueba visible en una captura

| Campo | Detalle |
|---|---|
| Fecha | 27 de octubre de 2026 |
| Severidad | **Media** (cuenta de sandbox de PayPal, sin dinero real) |
| Descripción | Al probar el pago con PayPal se compartió en el chat de trabajo una captura con el correo y la contraseña de una cuenta *Business* de sandbox. |
| Contención | Cambiar la contraseña de esa cuenta en developer.paypal.com → Sandbox accounts → View/Edit account. |
| Lección | Las capturas para pedir ayuda se recortan o se tapan las credenciales antes de compartirlas (política, punto 4; A.9.3.1). Se usó como caso en el repaso de seguridad del equipo (A.7.2.2). |
| Estado | Abierto hasta confirmar el cambio de contraseña |

## Formato para nuevos incidentes

```
### INC-00X · Título corto
| Fecha de detección | |
| Reportó | |
| Severidad | Crítica / Alta / Media / Baja |
| Activo | AC-xx |
| Descripción | Qué pasó y cómo se descubrió |
| Contención y corrección | Qué se hizo, PR y hora del despliegue |
| Verificación | Cómo se comprobó que quedó corregido |
| ¿Se explotó? | Evidencia revisada (bitácora, logs) |
| Causa raíz | |
| Lecciones y acciones | |
| Estado | Abierto / Cerrado |
```
