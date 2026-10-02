import { MedusaError, PaymentSessionStatus } from "@medusajs/framework/utils"
import { DatosSimulados, ResultadoAutorizacion, SimuladorPago } from "../../lib/simulador-pago"

// Mensajes que ve el comprador cuando la cuenta de prueba simula un rechazo.
const RECHAZOS: Record<string, string> = {
  rechazada: "PayPal rechazó el pago con esa cuenta. Intenta con otra cuenta u otro método.",
}

// Cuenta PayPal simulada (pp_sim-paypal_default).
//
// Como en PayPal, el comprador aprueba el pago fuera de la tienda: Spring crea
// la orden (PAYID-SIM-...), la persona la aprueba o la cancela en
// paypal-sim.html y vuelve al checkout con token y PayerID. Al crear la sesión
// de pago Spring cambia lo que mandó la página por los datos guardados de la
// orden aprobada: { orden, payerId, cuenta, resultado }. Aquí solo se decide
// según "resultado": aprobada autoriza y captura en el acto; lo demás se
// rechaza con su mensaje y Medusa no crea el pedido.
class SimuladorPaypal extends SimuladorPago {
  static identifier = "sim-paypal"
  protected readonly prefijo = "PAYID-SIM"

  protected async autorizar(datos: DatosSimulados): Promise<ResultadoAutorizacion> {
    if (!datos.orden || !datos.resultado) {
      throw new MedusaError(
        MedusaError.Types.PAYMENT_AUTHORIZATION_ERROR,
        "Falta aprobar el pago en PayPal. Vuelve a intentarlo."
      )
    }
    if (datos.resultado !== "aprobada") {
      throw new MedusaError(
        MedusaError.Types.PAYMENT_AUTHORIZATION_ERROR,
        RECHAZOS[datos.resultado as string] ?? RECHAZOS.rechazada
      )
    }
    // Como PayPal: la orden aprobada se captura y deja un id de captura.
    return { status: PaymentSessionStatus.CAPTURED, data: { captura: this.nuevoId("CAPTURE-SIM") } }
  }
}

export default SimuladorPaypal
