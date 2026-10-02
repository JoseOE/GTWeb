import { PaymentSessionStatus } from "@medusajs/framework/utils"
import { DatosSimulados, ResultadoAutorizacion, SimuladorPago } from "../../lib/simulador-pago"

// Pago en efectivo en tiendas de conveniencia, simulado (pp_sim-paynet_default).
// Al completar el carrito el pago queda autorizado pero SIN capturar: el pedido
// existe como "Pendiente de pago" y no activa ninguna membresía hasta que se
// capture (POST /admin/payments/{id}/capture → evento payment.captured).
class SimuladorPaynet extends SimuladorPago {
  static identifier = "sim-paynet"
  protected readonly prefijo = "paynet_sim"

  protected async autorizar(_datos: DatosSimulados): Promise<ResultadoAutorizacion> {
    return { status: PaymentSessionStatus.AUTHORIZED }
  }
}

export default SimuladorPaynet
