import { PaymentSessionStatus } from "@medusajs/framework/utils"
import { randomInt } from "node:crypto"
import { DatosSimulados, ResultadoAutorizacion, SimuladorPago } from "../../lib/simulador-pago"

// Horas que tiene el comprador para pagar en tienda antes de que la ficha venza.
const VIGENCIA_HORAS = 72

// Dígito verificador de Luhn (módulo 10): el último dígito de la referencia
// permite detectar al capturarla si se equivocó en un número.
export function digitoVerificador(digitos: string): number {
  let suma = 0
  let doblar = true
  for (let i = digitos.length - 1; i >= 0; i--) {
    let d = Number(digitos[i])
    if (doblar) {
      d *= 2
      if (d > 9) d -= 9
    }
    suma += d
    doblar = !doblar
  }
  return (10 - (suma % 10)) % 10
}

// Pago en efectivo en tiendas de conveniencia, simulado (pp_sim-paynet_default).
//
// Al completar el carrito se genera una referencia de 18 dígitos con su dígito
// verificador y una fecha límite de 72 h. El pago queda autorizado pero SIN
// capturar: el pedido existe como "Pendiente de pago" y no activa ninguna
// membresía. Cuando la tienda "cobra" (en la simulación, "Simular pago en
// tienda": POST /admin/payments/{id}/capture) Medusa emite payment.captured y
// Spring marca el pedido como pagado. Si vence sin pagarse, Spring lo cancela.
class SimuladorPaynet extends SimuladorPago {
  static identifier = "sim-paynet"
  protected readonly prefijo = "paynet_sim"

  protected async autorizar(_datos: DatosSimulados): Promise<ResultadoAutorizacion> {
    // "93" identifica al convenio simulado de GymTrack; luego 15 dígitos al azar.
    let base = "93"
    for (let i = 0; i < 15; i++) base += randomInt(10).toString()
    const referencia = base + digitoVerificador(base)
    const vence = new Date(Date.now() + VIGENCIA_HORAS * 60 * 60 * 1000).toISOString()
    return { status: PaymentSessionStatus.AUTHORIZED, data: { referencia, vence } }
  }
}

export default SimuladorPaynet
