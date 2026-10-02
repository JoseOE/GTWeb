import { createHmac, randomUUID } from "node:crypto"
import type { Logger } from "@medusajs/framework/types"

// Lo que recibe Spring en POST /api/tienda/webhooks/medusa.
export type AvisoTienda = {
  id: string // id único del aviso; Spring lo usa para no procesarlo dos veces
  evento: "order.placed" | "payment.captured" | "order.canceled"
  orderId: string
  paymentId?: string
  enviadoEn: string
}

// Esperas entre reintentos. Spring vive en el plan gratuito de Render y puede
// tardar casi un minuto en despertar, así que el último intento espera más.
const ESPERAS_MS = [2_000, 10_000, 45_000]

// Firma igual que Stripe: HMAC-SHA256 de "<timestamp>.<cuerpo>". Spring
// rechaza firmas viejas (más de 5 minutos), así que un aviso capturado no se
// puede reenviar después.
export function firmar(secreto: string, timestamp: number, cuerpo: string): string {
  return createHmac("sha256", secreto).update(`${timestamp}.${cuerpo}`).digest("hex")
}

export async function avisarASpring(
  aviso: Omit<AvisoTienda, "id" | "enviadoEn">,
  logger: Logger
): Promise<void> {
  const url = process.env.SPRING_WEBHOOK_URL
  const secreto = process.env.MEDUSA_WEBHOOK_SECRET
  if (!url || !secreto) {
    logger.warn(
      `[tienda] Falta SPRING_WEBHOOK_URL o MEDUSA_WEBHOOK_SECRET: no se avisó ${aviso.evento} del pedido ${aviso.orderId}.`
    )
    return
  }

  const completo: AvisoTienda = { ...aviso, id: randomUUID(), enviadoEn: new Date().toISOString() }
  const cuerpo = JSON.stringify(completo)

  for (let intento = 0; intento <= ESPERAS_MS.length; intento++) {
    // La firma se rehace en cada intento para que el timestamp siga vigente.
    const timestamp = Math.floor(Date.now() / 1000)
    try {
      const respuesta = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-GymTrack-Firma": `t=${timestamp},v1=${firmar(secreto, timestamp, cuerpo)}`,
        },
        body: cuerpo,
        signal: AbortSignal.timeout(60_000),
      })
      // 2xx = procesado o ya procesado antes. 4xx = Spring lo rechazó (firma o
      // datos): reintentar no lo arregla.
      if (respuesta.ok || (respuesta.status >= 400 && respuesta.status < 500)) {
        if (!respuesta.ok) {
          logger.warn(`[tienda] Spring rechazó ${aviso.evento} del pedido ${aviso.orderId}: HTTP ${respuesta.status}`)
        }
        return
      }
      logger.warn(`[tienda] Spring respondió HTTP ${respuesta.status} a ${aviso.evento} (intento ${intento + 1}).`)
    } catch (e) {
      logger.warn(`[tienda] No se pudo avisar ${aviso.evento} a Spring (intento ${intento + 1}): ${(e as Error).message}`)
    }
    if (intento < ESPERAS_MS.length) {
      await new Promise((r) => setTimeout(r, ESPERAS_MS[intento]))
    }
  }
  logger.error(
    `[tienda] Se agotaron los reintentos de ${aviso.evento} del pedido ${aviso.orderId}. ` +
      `Spring lo recupera la próxima vez que alguien consulte ese pedido.`
  )
}
