import { ModuleProvider, Modules } from "@medusajs/framework/utils"
import SimuladorStripe from "./service"

export default ModuleProvider(Modules.PAYMENT, {
  services: [SimuladorStripe],
})
