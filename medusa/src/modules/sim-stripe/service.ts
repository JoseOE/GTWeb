import { MedusaError, PaymentSessionStatus } from "@medusajs/framework/utils"
import { DatosSimulados, ResultadoAutorizacion, SimuladorPago } from "../../lib/simulador-pago"

// Mensajes que ve el comprador cuando la tarjeta de prueba simula un rechazo.
const RECHAZOS: Record<string, string> = {
  rechazada: "Tu tarjeta fue rechazada. Intenta con otra tarjeta.",
  fondos_insuficientes: "La tarjeta no tiene fondos suficientes.",
  vencida: "La tarjeta está vencida.",
  cvc_incorrecto: "El código de seguridad (CVC) es incorrecto.",
}

// Tarjeta de crédito o débito simulada (pp_sim-stripe_default). También es la
// "terminal" del mostrador.
//
// Spring tokeniza la tarjeta (POST /api/simuladores/stripe/tokens) y al crear
// la sesión de pago cambia lo que mandó la página por los datos guardados del
// token: { token, marca, ultimos4, vencimiento, titular, resultado }. Aquí solo
// se decide según "resultado": aprobada cobra en el acto; lo demás se rechaza
// con el mensaje de la tarjeta de prueba y Medusa no crea el pedido.
class SimuladorStripe extends SimuladorPago {
  static identifier = "sim-stripe"
  protected readonly prefijo = "pi_sim"

  protected async autorizar(datos: DatosSimulados): Promise<ResultadoAutorizacion> {
    if (!datos.token || !datos.resultado) {
      throw new MedusaError(
        MedusaError.Types.PAYMENT_AUTHORIZATION_ERROR,
        "Falta la tarjeta. Vuelve a capturar sus datos."
      )
    }
    if (datos.resultado !== "aprobada") {
      throw new MedusaError(
        MedusaError.Types.PAYMENT_AUTHORIZATION_ERROR,
        RECHAZOS[datos.resultado as string] ?? "Tu tarjeta fue rechazada. Intenta con otra tarjeta."
      )
    }
    // Como Stripe: el PaymentIntent (pi_sim_...) genera un cargo (ch_sim_...).
    return { status: PaymentSessionStatus.CAPTURED, data: { cargo: this.nuevoId("ch_sim") } }
  }
}

export default SimuladorStripe
