import type { ExecArgs } from "@medusajs/framework/types"
import { ContainerRegistrationKeys, Modules } from "@medusajs/framework/utils"
import {
  createApiKeysWorkflow,
  createProductCategoriesWorkflow,
  createProductTypesWorkflow,
  createRegionsWorkflow,
  createShippingProfilesWorkflow,
  createTaxRegionsWorkflow,
  updateStoresWorkflow,
} from "@medusajs/medusa/core-flows"

// Lo que comparten todos los gimnasios. Lo propio de cada gimnasio (canal de
// venta, almacén, llave publicable y "Recoger en el gimnasio") lo crea Spring
// la primera vez que ese gimnasio usa la tienda.
//
// Se puede correr las veces que sea: solo crea lo que falta.
//   npm run seed                 → configura la tienda
//   npm run seed -- nueva-llave  → además genera otra llave secreta para Spring

const REGION = "México"
const PROVEEDORES_DE_PAGO = [
  "pp_system_default", // efectivo en mostrador (proveedor manual de Medusa)
  "pp_sim-stripe_default",
  "pp_sim-paynet_default",
  "pp_sim-paypal_default",
]
const TIPOS = ["producto", "membresia"]
const CATEGORIAS = [
  { name: "Suplementos", handle: "suplementos" },
  { name: "Bebidas", handle: "bebidas" },
  { name: "Snacks", handle: "snacks" },
  { name: "Accesorios", handle: "accesorios" },
  { name: "Membresías", handle: "membresias" },
]
const TITULO_LLAVE = "Spring Boot (GymTrack)"

export default async function configurarTienda({ container, args }: ExecArgs) {
  const logger = container.resolve(ContainerRegistrationKeys.LOGGER)
  const storeService = container.resolve(Modules.STORE)
  const regionService = container.resolve(Modules.REGION)
  const taxService = container.resolve(Modules.TAX)
  const productService = container.resolve(Modules.PRODUCT)
  const fulfillmentService = container.resolve(Modules.FULFILLMENT)
  const apiKeyService = container.resolve(Modules.API_KEY)

  // ─── Tienda: pesos mexicanos con IVA incluido en los precios ───
  const [store] = await storeService.listStores()
  await updateStoresWorkflow(container).run({
    input: {
      selector: { id: store.id },
      update: {
        name: "GymTrack",
        supported_currencies: [{ currency_code: "mxn", is_default: true, is_tax_inclusive: true }],
      },
    },
  })
  logger.info("Tienda en MXN con IVA incluido.")

  // ─── Región México ───
  const [regionExistente] = await regionService.listRegions({ name: REGION })
  if (!regionExistente) {
    await createRegionsWorkflow(container).run({
      input: {
        regions: [
          {
            name: REGION,
            currency_code: "mxn",
            countries: ["mx"],
            automatic_taxes: true,
            is_tax_inclusive: true,
            payment_providers: PROVEEDORES_DE_PAGO,
          },
        ],
      },
    })
    logger.info(`Región ${REGION} creada con ${PROVEEDORES_DE_PAGO.length} métodos de pago.`)
  } else {
    logger.info(`La región ${REGION} ya existía.`)
  }

  // ─── IVA 16 % ───
  const [taxRegion] = await taxService.listTaxRegions({ country_code: "mx" })
  if (!taxRegion) {
    await createTaxRegionsWorkflow(container).run({
      input: [
        {
          country_code: "mx",
          provider_id: "tp_system",
          default_tax_rate: { rate: 16, code: "IVA", name: "IVA" },
        },
      ],
    })
    logger.info("Región fiscal de México con IVA al 16 %.")
  }

  // ─── Tipos de producto: lo que se vende y los planes de membresía ───
  const tiposExistentes = await productService.listProductTypes({}, { take: 100 })
  const tiposFaltantes = TIPOS.filter((t) => !tiposExistentes.some((e) => e.value === t))
  if (tiposFaltantes.length) {
    await createProductTypesWorkflow(container).run({
      input: { product_types: tiposFaltantes.map((value) => ({ value })) },
    })
    logger.info(`Tipos de producto creados: ${tiposFaltantes.join(", ")}.`)
  }

  // ─── Categorías ───
  const categoriasExistentes = await productService.listProductCategories({
    handle: CATEGORIAS.map((c) => c.handle),
  })
  const categoriasFaltantes = CATEGORIAS.filter(
    (c) => !categoriasExistentes.some((e) => e.handle === c.handle)
  )
  if (categoriasFaltantes.length) {
    await createProductCategoriesWorkflow(container).run({
      input: {
        product_categories: categoriasFaltantes.map((c) => ({ ...c, is_active: true, is_internal: false })),
      },
    })
    logger.info(`Categorías creadas: ${categoriasFaltantes.map((c) => c.name).join(", ")}.`)
  }

  // ─── Perfil de envío por defecto (todos los productos usan "Recoger en el gimnasio") ───
  const perfiles = await fulfillmentService.listShippingProfiles({ type: "default" })
  if (!perfiles.length) {
    await createShippingProfilesWorkflow(container).run({
      input: { data: [{ name: "Recoger en el gimnasio", type: "default" }] },
    })
    logger.info("Perfil de envío por defecto creado.")
  }

  // ─── Llave secreta para Spring ───
  // Medusa solo muestra el valor completo al crearla. Si se pierde, corre
  // "npm run seed -- nueva-llave" y actualiza MEDUSA_ADMIN_TOKEN en Spring.
  const llaves = (await apiKeyService.listApiKeys({ type: "secret", title: TITULO_LLAVE })).filter((k) => !k.revoked_at)
  if (!llaves.length || (args ?? []).includes("nueva-llave")) {
    const { result } = await createApiKeysWorkflow(container).run({
      input: { api_keys: [{ title: TITULO_LLAVE, type: "secret", created_by: "seed" }] },
    })
    logger.info("──────────────────────────────────────────────────────────────")
    logger.info("Llave secreta para Spring (cópiala en MEDUSA_ADMIN_TOKEN; no se vuelve a mostrar):")
    logger.info(result[0].token)
    logger.info("──────────────────────────────────────────────────────────────")
  } else {
    logger.info(`Ya existe la llave "${TITULO_LLAVE}". Para generar otra: npm run seed -- nueva-llave`)
  }

  logger.info("Tienda lista.")
}
