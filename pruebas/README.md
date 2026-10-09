# Pruebas de punta a punta

Scripts de Node que recorren los flujos reales de GymTrack contra el entorno local: Spring y MongoDB. Cada uno imprime ✔ o ✘ por comprobación y termina con código 1 si algo falló.

> **Solo en local.** Las pruebas crean cuentas, gimnasios, productos y pedidos, y `bloque4` cambia un pedido directo en MongoDB. `entorno.mjs` se niega a correr si la API o la base no apuntan a `localhost`.

## Qué cubre cada una

| Prueba | Qué revisa |
|---|---|
| `bloque1` | Registro y verificación del dueño, gimnasio, miembro, productos, planes, la primera compra con tarjeta y una ficha Paynet que activa la membresía una sola vez. Deja `ultima-prueba.json` para las demás. |
| `bloque2` | Carrito: existencias, cambios de precio y total visto antes de pagar. |
| `bloque3` | Simulador de tarjeta (tokens, rechazos, un solo uso) y cobro en mostrador. |
| `bloque4` | Paynet (ficha, pago y vencidas), recibos en PDF y correos. |
| `bloque5` | Simulador de PayPal, ventas del panel, reembolsos y su efecto en la membresía. |
| `seguridad` | Sesión con token, permisos por ruta y cierre de sesión. |
| `visita-mes` | Historial de pagos del gimnasio y la regla "Visita / Semana + mes". |
| `alta-recepcion` | Contraseña temporal del alta en recepción y su cambio. |

## Cómo correrlas

1. Levanta MongoDB y Spring. Guarda la salida de Spring en un archivo, porque ahí se leen los códigos de verificación (en local no hay correo):

   ```bash
   mvn -q -DskipTests package
   java -jar target/gymtrack-web-0.0.1-SNAPSHOT.jar > pruebas/spring.log 2>&1
   ```

2. Instala la única dependencia (el cliente de MongoDB que usa `bloque4`):

   ```bash
   cd pruebas && npm install
   ```

3. Corre todas en orden, o solo algunas (el bloque 1 debe haber corrido antes al menos una vez):

   ```bash
   npm run todas
   ```

   ```bash
   node correr-todas.mjs 3 4 seguridad
   ```

## Configuración

Todo sale de variables de entorno o del `.env` de la raíz, con estos valores por defecto:

| Variable | Por defecto | Para qué |
|---|---|---|
| `GYMTRACK_API` | `http://localhost:8080` | Spring |
| `MONGODB_URI` | del `.env` | Base que usa Spring (solo `bloque4` la toca directo) |
| `SPRING_LOG` | `pruebas/spring.log` | Salida de Spring, para leer los códigos de verificación |

`ultima-prueba.json`, `spring.log`, `pdfs/` y `node_modules/` se generan aquí y no se suben al repositorio.

## En GitHub

El flujo `.github/workflows/ci.yml` corre en cada PR a `main`: compila Spring, lo levanta junto a un MongoDB de prueba y corre todas estas pruebas. Conviene correrlas también en local antes de pedir la mezcla.
