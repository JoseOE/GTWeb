import type { SubscriberArgs, SubscriberConfig } from "@medusajs/framework"
import { ContainerRegistrationKeys } from "@medusajs/framework/utils"
import { avisarASpring } from "../lib/avisar-spring"

// Avisa a Spring de los tres momentos que le importan a GymTrack:
//  - order.placed: se completó un carrito (tarjeta y PayPal ya llegan pagados;
//    Paynet llega pendiente);
//  - payment.captured: se capturó un pago después (el "Simular pago en tienda"
//    de Paynet). Medusa no lo emite cuando el cobro fue en el acto;
//  - order.canceled: se canceló un pedido (p. ej. una ficha Paynet vencida).
// Spring vuelve a leer el pedido completo por la Admin API, así que aquí solo
// viaja el id.
export default async function avisarPedidos({
  event,
  container,
}: SubscriberArgs<{ id: string }>) {
  const logger = container.resolve(ContainerRegistrationKeys.LOGGER)
  const nombre = event.name as "order.placed" | "payment.captured" | "order.canceled"

  if (nombre !== "payment.captured") {
    await avisarASpring({ evento: nombre, orderId: event.data.id }, logger)
    return
  }

  // payment.captured trae el id del pago: se busca a qué pedido pertenece.
  const query = container.resolve(ContainerRegistrationKeys.QUERY)
  const { data: pagos } = await query.graph({
    entity: "payment",
    fields: ["id", "payment_collection.order.id"],
    filters: { id: event.data.id },
  })
  const orderId = (pagos[0] as any)?.payment_collection?.order?.id as string | undefined
  if (!orderId) {
    logger.warn(`[tienda] El pago ${event.data.id} se capturó pero no pertenece a ningún pedido.`)
    return
  }
  await avisarASpring({ evento: nombre, orderId, paymentId: event.data.id }, logger)
}

export const config: SubscriberConfig = {
  event: ["order.placed", "payment.captured", "order.canceled"],
}
