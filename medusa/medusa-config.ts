import { loadEnv, defineConfig } from "@medusajs/framework/utils"

loadEnv(process.env.NODE_ENV || "development", process.cwd())

// Medusa es el motor de la tienda de cada gimnasio, pero nadie la usa
// directamente: la página web y la app hablan solo con Spring Boot, y Spring
// llama a la Store API y a la Admin API con su llave secreta. Por eso:
//  - el dashboard de Medusa está apagado (el dueño administra desde el panel de GTWeb);
//  - no hay Redis: el bus de eventos, la caché y los workflows corren en memoria,
//    que alcanza para un solo servidor en Render;
//  - los pagos son simuladores propios (src/modules/sim-*) más el proveedor
//    manual de Medusa (pp_system_default) para el efectivo en mostrador.
module.exports = defineConfig({
  projectConfig: {
    databaseUrl: process.env.DATABASE_URL,
    http: {
      storeCors: process.env.STORE_CORS || "http://localhost:8080",
      adminCors: process.env.ADMIN_CORS || "http://localhost:8080",
      authCors: process.env.AUTH_CORS || "http://localhost:8080",
      jwtSecret: process.env.JWT_SECRET || "supersecret",
      cookieSecret: process.env.COOKIE_SECRET || "supersecret",
    },
  },
  admin: {
    disable: true,
  },
  modules: [
    {
      resolve: "@medusajs/medusa/payment",
      options: {
        // El id de cada proveedor en Medusa queda como pp_<identifier>_<id>:
        // pp_sim-stripe_default, pp_sim-paynet_default y pp_sim-paypal_default.
        providers: [
          { resolve: "./src/modules/sim-stripe", id: "default" },
          { resolve: "./src/modules/sim-paynet", id: "default" },
          { resolve: "./src/modules/sim-paypal", id: "default" },
        ],
      },
    },
  ],
})
