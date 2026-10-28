# 06 · Operación del SGSI

Cláusulas 7 (soporte) y 8.1 (operación), y controles A.10.1.2, A.12, A.14.2.2 y A.17.

## Respaldos (A.12.3.1)

El flujo [`.github/workflows/respaldo.yml`](../../.github/workflows/respaldo.yml) corre todos los días a las 03:00 (hora de México):

1. Copia toda la base de MongoDB Atlas con `mongodump`.
2. La cifra con AES-256 (PBKDF2, 200 000 iteraciones) con la clave `RESPALDO_CLAVE`.
3. La guarda 30 días como artefacto de esa ejecución, junto con su huella SHA-256.

Se cifra porque los artefactos los puede descargar cualquiera con acceso al repositorio.

### Configurarlo (una sola vez)

En GitHub, en el repositorio GTWeb, ve a **Settings → Secrets and variables → Actions → New repository secret** y crea:
- `MONGODB_URI`: la misma cadena de conexión que tiene Render.
- `RESPALDO_CLAVE`: una frase larga (20 caracteres o más) que solo conoce el RSI. Guárdala también fuera de GitHub (por ejemplo, en un gestor de contraseñas): sin ella, los respaldos no se pueden abrir.

Luego ve a **Actions → Respaldo → Run workflow** para hacer el primero.

### Restaurar un respaldo

Siempre se restaura primero en una base de prueba (local o una base nueva en Atlas), nunca directo sobre producción.

1. En **Actions → Respaldo**, abre la ejecución del día que quieres y descarga el artefacto `respaldo-AAAA-MM-DD`.
2. Revisa que no se haya alterado:
   ```bash
   sha256sum -c gymtrack-AAAA-MM-DD.sha256
   ```
3. Descífralo (pide la `RESPALDO_CLAVE`):
   ```bash
   openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in gymtrack-AAAA-MM-DD.archive.enc -out gymtrack.archive
   ```
4. Cárgalo en la base de prueba:
   ```bash
   mongorestore --uri="mongodb://127.0.0.1:27017" --archive=gymtrack.archive --gzip --drop
   ```
5. Arranca Spring contra esa base, entra al panel y revisa que estén los gimnasios, miembros y pedidos. Borra `gymtrack.archive` al terminar.

### Registro de pruebas de restauración (A.17.1.3)

| Fecha | Respaldo usado | Quién | Resultado |
|---|---|---|---|
| (pendiente: primera prueba tras cargar los secretos) | | | |

## Bitácora de seguridad (A.12.4)

**Qué registra:**
- Inicios de sesión: correctos, con contraseña incorrecta y frenados por intentos.
- Contraseña cambiada o restablecida, y correo cambiado.
- Todo acceso negado (403).
- Las acciones del dueño en el panel: altas, bajas y cambios de miembros; pagos registrados; ventas de mostrador; reembolsos; cancelaciones; entregas; cambios de productos y planes; código de invitación.

Cada evento guarda la fecha, el usuario, la ruta, la IP y el dispositivo. Nunca guarda contraseñas, tokens ni datos de pago.

**Dónde:** colección `bitacora` de MongoDB. Se borra sola a los 180 días (índice TTL). Agregar una acción del panel a la bitácora es agregar una regla en `config/BitacoraFilter.java`.

**Quién la ve:**
- El dueño, en **Panel → Seguridad → Bitácora**: ve lo de su gimnasio y su propia cuenta. No ve los inicios de sesión de sus miembros.
- El RSI, en la base, para investigar incidentes.

**Revisión:**
- El dueño la revisa cuando lo necesite. Se recomienda cada semana.
- El RSI la revisa cada mes y busca muchos `LOGIN_FALLIDO` del mismo correo, `ACCESO_DENEGADO` repetidos de una IP, o reembolsos o pagos que nadie reconozca.

## Gestión de cambios (A.12.1.2, A.14.2.2)

1. Cada cambio se hace en una rama propia y entra por un PR a `main` con su descripción: qué cambia, cómo se probó y qué riesgo atiende.
2. El CI ([`ci.yml`](../../.github/workflows/ci.yml)) compila y corre toda la suite de punta a punta, incluidas las pruebas de seguridad. Si falla, no se mezcla.
3. Otro integrante revisa el PR y lo aprueba.
4. Al mezclarlo, Render despliega `main` solo. Se verifica en producción.
5. Si algo sale mal: **Render → Deploys → Rollback** al despliegue anterior y se abre un incidente si afectó a usuarios.

## Gestión de vulnerabilidades (A.12.6.1)

Dependabot ([`.github/dependabot.yml`](../../.github/dependabot.yml)) revisa cada semana las dependencias de Maven y npm y cada mes las de GitHub Actions. Cuando encuentra una versión nueva abre un PR, que el CI prueba como cualquier otro.

| Severidad del aviso | Plazo para mezclar la actualización |
|---|---|
| Crítica | 7 días |
| Alta | 14 días |
| Media o baja | En la siguiente revisión mensual |

## Secretos y llaves (A.9.2.4, A.10.1.2)

| Secreto | Dónde vive | Quién lo administra | Rotación |
|---|---|---|---|
| `MONGODB_URI` (usuario y contraseña de Atlas) | Render, GitHub Secrets (respaldo) | Jose | Cada semestre, al salir alguien del equipo o si se expone |
| `BREVO_API_KEY` | Render | Jose | Cada semestre o si se expone |
| `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` | Render, `.env` local | Eduardo | Si se expone (Stripe → Desarrolladores → Claves de API → *Roll key*) |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | Render, `.env` local | Edwin | Si se expone (developer.paypal.com → la app → regenerar el *secret*) |
| `OPENPAY_MERCHANT_ID`, `OPENPAY_PRIVATE_KEY`, `OPENPAY_PUBLIC_KEY` | Render, `.env` local | Valeria | Si se expone (panel de Openpay → regenerar llaves) |
| `RESPALDO_CLAVE` | GitHub Secrets y gestor de contraseñas del RSI | Jose | Cada año; los respaldos anteriores siguen con la clave vieja hasta que expiran |
| Tokens de sesión de usuarios | Navegador y teléfono (SecureStore); en la base solo su huella SHA-256 | Sistema | Vencen a los 30 días; cambiar la contraseña cierra las demás |

**Reglas:**
- Nunca se suben al repositorio: `.env` está en `.gitignore`.
- Nunca se pegan en el chat, en capturas, en un issue ni en un PR.
- Si alguien deja el equipo: se quita su acceso a GitHub, Render, Atlas y los proveedores, y se rotan las llaves que conocía.
- Hoy todas las llaves de pago son de sandbox. Pasar a producción exige llaves nuevas que solo vivan en Render.

## Separación de entornos (A.12.1.4)

| Entorno | Base de datos | Pagos | Para qué |
|---|---|---|---|
| Local | MongoDB local | `simulado` o `sandbox` con el `.env` | Desarrollo y pruebas manuales |
| CI (GitHub Actions) | MongoDB de prueba, se borra al terminar | `simulado` (sin llaves) | Pruebas automáticas de cada PR |
| Producción (Render) | MongoDB Atlas | `sandbox` (las APIs reales, en modo de prueba) | Uso real del proyecto |

Las pruebas automáticas se niegan a correr contra algo que no sea local (`pruebas/entorno.mjs`).

## Continuidad (A.17)

| Situación | Qué hacer | Tiempo objetivo |
|---|---|---|
| Un despliegue rompió la plataforma | Render → *Rollback* al anterior; corregir por PR | 30 min |
| Render no responde | Revisar status.render.com; esperar o desplegar la misma imagen en otro servicio con las mismas variables | 4 h |
| Se borraron o dañaron datos | Restaurar el último respaldo en una base de prueba, verificar y restaurar en producción solo las colecciones afectadas | 8 h (se pierde como máximo un día de datos) |
| Se expuso una llave | Rotarla en el proveedor, actualizar Render y abrir un incidente | 1 h |
| Un proveedor de pago no responde | La tienda muestra el error y se puede cobrar con otro método (efectivo en mostrador, otro proveedor) | Inmediato |
