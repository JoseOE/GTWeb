import { SimuladorPago } from "../../lib/simulador-pago"

// Cuenta PayPal simulada (pp_sim-paypal_default). El comprador aprueba en
// paypal-sim.html y al volver al checkout se autoriza y captura en el acto.
class SimuladorPaypal extends SimuladorPago {
  static identifier = "sim-paypal"
  protected readonly prefijo = "PAYID-SIM"
}

export default SimuladorPaypal
