import { ModuleProvider, Modules } from "@medusajs/framework/utils"
import SimuladorPaypal from "./service"

export default ModuleProvider(Modules.PAYMENT, {
  services: [SimuladorPaypal],
})
