import { SimuladorPago } from "../../lib/simulador-pago"

// Tarjeta de crédito o débito simulada (pp_sim-stripe_default).
// Cobra en el acto: el pedido nace pagado. También es la "terminal" del mostrador.
class SimuladorStripe extends SimuladorPago {
  static identifier = "sim-stripe"
  protected readonly prefijo = "pi_sim"
}

export default SimuladorStripe
