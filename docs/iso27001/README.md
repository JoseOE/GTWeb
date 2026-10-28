# Sistema de Gestión de Seguridad de la Información (SGSI) de GymTrack

Aplicación de la norma **NMX-I-27001-NYCE-2015** (equivalente a ISO/IEC 27001:2013) a GymTrack: la página web y el panel del gimnasio (GTWeb), la app móvil (GTApp) y los servicios de los que dependen.

Los controles del Anexo A se citan con la numeración de 2013, la de la norma mexicana. Como ISO/IEC 27001:2013 fue sustituida por la versión 2022 (la transición terminó el 31 de octubre de 2025), la declaración de aplicabilidad incluye el control equivalente de 2022.

## Documentos

| Cláusula de la norma | Documento | Qué contiene |
|---|---|---|
| 4. Contexto de la organización | [01 · Contexto y alcance](01-contexto-y-alcance.md) | Qué es GymTrack, partes interesadas y sus requisitos, alcance del SGSI, inventario de activos |
| 5. Liderazgo | [02 · Política y roles](02-politica-y-roles.md) | Política de seguridad, roles y responsabilidades del equipo |
| 6.1 Riesgos y oportunidades, 8.2 y 8.3 | [03 · Análisis y tratamiento de riesgos](03-analisis-de-riesgos.md) | Metodología, 18 riesgos evaluados, tratamiento y riesgo residual |
| 6.1.3 d) Declaración de aplicabilidad | [04 · Declaración de aplicabilidad](04-declaracion-de-aplicabilidad.md) | Los 114 controles del Anexo A: si aplican, por qué, cómo se implementan y su evidencia |
| A.16 Incidentes | [05 · Gestión de incidentes](05-gestion-de-incidentes.md) | Procedimiento y registro de incidentes (INC-001: evasión de SesionFilter) |
| 7 Soporte, 8.1 Operación | [06 · Operación](06-operacion.md) | Respaldos y cómo restaurar, bitácora, cambios, vulnerabilidades, secretos |
| 9 Evaluación, 10 Mejora | [07 · Evaluación y mejora](07-evaluacion-y-mejora.md) | Indicadores, auditoría interna, revisión por la dirección y no conformidades |

## Controles técnicos implementados en el código

| Control | Dónde | Cómo se comprueba |
|---|---|---|
| Sesión con token y permisos por recurso (A.9.4.1, A.9.4.2) | `config/SesionFilter.java`, `service/AccesoService.java` | `pruebas/seguridad.mjs` |
| Contraseñas con BCrypt, política de contraseñas y freno por intentos (A.9.4.3) | `util/PasswordUtil.java`, `service/IntentosLoginService.java` | `pruebas/seguridad.mjs`, `pruebas/iso27001.mjs` §3 |
| Cabeceras de seguridad: CSP, HSTS, X-Frame-Options, nosniff, Referrer y Permissions-Policy (A.13.1.1, A.14.1.2) | `config/CabecerasSeguridadFilter.java` | `pruebas/iso27001.mjs` §1 |
| Bitácora de seguridad con retención de 180 días (A.12.4.1, A.12.4.3) | `config/BitacoraFilter.java`, `service/BitacoraService.java`, panel → Seguridad → Bitácora | `pruebas/iso27001.mjs` §2 |
| Respaldo diario cifrado de la base de datos (A.12.3.1) | `.github/workflows/respaldo.yml` | Ejecución manual en GitHub Actions; restauración en [06 · Operación](06-operacion.md#restaurar-un-respaldo) |
| Revisión de dependencias vulnerables (A.12.6.1) | `.github/dependabot.yml` | PRs de Dependabot probados por el CI |
| Revisión de cada cambio antes de producción (A.12.1.2, A.14.2.2) | `.github/workflows/ci.yml`, ramas y PRs | CI en cada PR |
| Pagos sin que la tarjeta toque GymTrack (A.14.1.3) | `service/PagosService.java` (Stripe Checkout, PayPal Orders v2, Openpay) | `pruebas/sandbox.mjs` |

## Vigencia

| Versión | Fecha | Cambios | Aprobó |
|---|---|---|---|
| 1.0 | 27 de octubre de 2026 | Primera versión del SGSI | José María Ortiz Escamilla (responsable del SGSI) |

Se revisa cada semestre, después de un incidente y cuando cambia algo del alcance (un proveedor nuevo, un módulo nuevo).
