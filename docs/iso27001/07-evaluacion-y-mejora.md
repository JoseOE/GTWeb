# 07 · Evaluación del desempeño y mejora

Cláusulas 9 (seguimiento, auditoría interna, revisión por la dirección) y 10 (no conformidades y mejora continua).

## Seguimiento y medición (9.1)

| Indicador | Meta | Fuente | Frecuencia | Responsable |
|---|---|---|---|---|
| PRs mezclados a `main` con el CI en verde | 100 % | Historial de PRs de GitHub | Mensual | Jose |
| Vulnerabilidades críticas de dependencias abiertas más de 7 días | 0 | Dependabot | Semanal | Jose |
| Días con respaldo exitoso | ≥ 95 % | Actions → Respaldo | Mensual | Jose |
| Pruebas de restauración hechas | 1 al mes | [Registro de restauraciones](06-operacion.md#registro-de-pruebas-de-restauración-a1713) | Mensual | Jose |
| Inicios de sesión frenados por intentos | Se revisa la tendencia; un pico se investiga | Bitácora (`LOGIN_BLOQUEADO`) | Mensual | Jose |
| Accesos negados (403) repetidos desde la misma IP | Se investiga cada caso | Bitácora (`ACCESO_DENEGADO`) | Mensual | Jose |
| Incidentes cerrados con su lección y su prueba de regresión | 100 % | [Registro de incidentes](05-gestion-de-incidentes.md#registro-de-incidentes) | Por incidente | Jose |
| Cabeceras de seguridad presentes en producción | 100 % | `curl -I https://gtweb.onrender.com/` | Tras cada despliegue | Responsable del PR |

## Auditoría interna (9.2)

Una vez por semestre, auditoría cruzada: quien revisa un control no es su responsable.

| Área | Audita | Qué revisa |
|---|---|---|
| Sesión, permisos y bitácora (A.9, A.12.4) | Eduardo | Que una ruta sin token responda 401; que un dueño no vea otro gimnasio; que la bitácora registre los eventos de la tabla de [06](06-operacion.md#bitácora-de-seguridad-a124) |
| Pagos (A.14.1.3) | Valeria | Que no se pueda cobrar con un total alterado ni reusar un pago; que no se guarden datos de tarjeta |
| Respaldos y continuidad (A.12.3, A.17) | Edwin | Que haya respaldos de los últimos 30 días y que uno se pueda restaurar |
| Secretos y proveedores (A.10.1.2, A.15) | Yael | Que no haya llaves en el repositorio ni en el historial; quién tiene acceso a cada proveedor |
| Documentación y riesgos (cláusulas 4 a 6) | Jose | Que el inventario, los riesgos y la SoA sigan vigentes |

Los hallazgos se registran como no conformidades (abajo).

**Programa del semestre:** primera auditoría del 9 al 13 de noviembre de 2026.

## Revisión por la dirección (9.3)

La hacen el RSI y el equipo al cierre de cada semestre, con el docente como revisor independiente (A.18.2.1). Se revisa:

1. Estado de las acciones de la revisión anterior.
2. Cambios en el contexto: proveedores nuevos, módulos nuevos, leyes.
3. Indicadores de la tabla de 9.1, incidentes y no conformidades.
4. Resultados de la auditoría interna.
5. Riesgos: ¿hay nuevos? ¿cambió algún nivel?
6. Oportunidades de mejora y decisiones (presupuesto, plazos).

El acta queda en este archivo, con fecha y acuerdos.

| Fecha | Asistentes | Acuerdos |
|---|---|---|
| 27-oct-2026 | Equipo GymTrack | Aprobar el SGSI v1.0; ejecutar el plan de tratamiento de riesgos ([03](03-analisis-de-riesgos.md#plan-de-tratamiento-613-e-y-83)); primera auditoría interna en noviembre |

## No conformidades y acciones correctivas (10.1)

| Id | No conformidad | Origen | Acción correctiva | Responsable | Fecha | Estado |
|---|---|---|---|---|---|---|
| NC-01 | Las rutas compartidas con la app aceptan `X-User-Id` sin token (`EXIGIR_TOKEN=false` en Render) | Análisis de riesgos (R01) | La web y la app ya mandan el token: poner `EXIGIR_TOKEN=true` en Render y verificar que una petición sin token responde 401 | Jose | 31-oct-2026 | Abierta |
| NC-02 | El respaldo diario está programado pero no tiene sus secretos: todavía no hay ningún respaldo | Implementación de A.12.3.1 | Cargar `MONGODB_URI` y `RESPALDO_CLAVE` en GitHub, correr el flujo y hacer la primera restauración | Jose | 31-oct-2026 | Abierta |
| NC-03 | Las cuentas de dueño solo tienen contraseña (un factor) | Análisis de riesgos (R03) | Segundo factor por correo para dueños | Jose y Yael | Próximo semestre | Planeada |
| NC-04 | La CSP permite scripts en línea (`'unsafe-inline'`) | Implementación de A.14.1.2 | Mover los scripts en línea de las páginas a archivos `.js` y quitar `'unsafe-inline'` | Eduardo | Próximo semestre | Planeada |
| NC-05 | No se ha verificado el cifrado de disco de las computadoras del equipo | Análisis de riesgos (R14) | Cada integrante confirma BitLocker o FileVault y lo anota aquí | Todos | 7-nov-2026 | Abierta |
| NC-06 | El repositorio no tiene activado *push protection* de secretos | Análisis de riesgos (R06) | Settings → Code security → Secret scanning y Push protection en GTWeb y GTApp | Jose | 7-nov-2026 | Abierta |

## Mejora continua (10.2)

Mejoras ya hechas por el SGSI:
- La bitácora nació de la lección de INC-001.
- Las cabeceras de seguridad, Dependabot y el respaldo cifrado se agregaron al aplicar la SoA.
- Cada control técnico tiene su prueba en el CI, así que no se pierde al cambiar el código.

Mejoras propuestas para el siguiente ciclo: las acciones NC-03 y NC-04, vencimiento de la sesión del panel por inactividad (R15) y una página de estado de la plataforma.
