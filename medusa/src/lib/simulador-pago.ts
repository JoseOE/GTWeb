import {
  AbstractPaymentProvider,
  BigNumber,
  PaymentSessionStatus,
} from "@medusajs/framework/utils"
import type {
  AuthorizePaymentInput,
  AuthorizePaymentOutput,
  CancelPaymentInput,
  CancelPaymentOutput,
  CapturePaymentInput,
  CapturePaymentOutput,
  DeletePaymentInput,
  DeletePaymentOutput,
  GetPaymentStatusInput,
  GetPaymentStatusOutput,
  InitiatePaymentInput,
  InitiatePaymentOutput,
  ProviderWebhookPayload,
  RefundPaymentInput,
  RefundPaymentOutput,
  RetrievePaymentInput,
  RetrievePaymentOutput,
  UpdatePaymentInput,
  UpdatePaymentOutput,
  WebhookActionResult,
} from "@medusajs/framework/types"
import { randomBytes } from "node:crypto"

// Estado del cobro dentro del simulador. Viaja en payment_session.data y en
// payment.data, así que Spring (y los recibos) lo pueden leer del pedido.
export type EstadoSimulado =
  | "pendiente"
  | "autorizado"
  | "capturado"
  | "cancelado"
  | "reembolsado"

export type DatosSimulados = Record<string, unknown> & {
  id: string
  simulador: string
  monto: number
  moneda: string
  estado: EstadoSimulado
}

export type ResultadoAutorizacion = {
  // "captured" cobra en el acto (tarjeta, PayPal); "authorized" deja el pedido
  // pendiente de pago hasta que alguien capture (Paynet).
  status: PaymentSessionStatus
  data?: Record<string, unknown>
}

// Base de los tres simuladores (sim-stripe, sim-paynet y sim-paypal).
//
// Ninguno habla con una pasarela real: todo vive en el `data` de la sesión de
// pago. Lo que manda Spring al crear la sesión (POST /store/payment-collections/
// {id}/payment-sessions con { provider_id, data }) llega aquí en initiatePayment
// y se conserva hasta authorizePayment. Cada simulador solo decide su prefijo
// de ids y qué pasa al autorizar; lo demás (capturar, cancelar, reembolsar)
// es igual para los tres.
export abstract class SimuladorPago extends AbstractPaymentProvider {
  // Prefijo de los ids que genera, p. ej. "pi_sim" → pi_sim_8f2a...
  protected abstract readonly prefijo: string

  // Se llama al completar el carrito. Puede lanzar un MedusaError con un
  // mensaje para el comprador (tarjeta rechazada, pago cancelado...).
  protected async autorizar(datos: DatosSimulados): Promise<ResultadoAutorizacion> {
    return { status: PaymentSessionStatus.CAPTURED }
  }

  protected nuevoId(prefijo = this.prefijo): string {
    return `${prefijo}_${randomBytes(9).toString("hex")}`
  }

  protected ahora(): string {
    return new Date().toISOString()
  }

  async initiatePayment(input: InitiatePaymentInput): Promise<InitiatePaymentOutput> {
    const id = this.nuevoId()
    const data: DatosSimulados = {
      ...(input.data ?? {}),
      id,
      simulador: this.getIdentifier(),
      monto: new BigNumber(input.amount).numeric,
      moneda: input.currency_code,
      estado: "pendiente",
    }
    return { id, data }
  }

  async updatePayment(input: UpdatePaymentInput): Promise<UpdatePaymentOutput> {
    return {
      data: {
        ...(input.data ?? {}),
        monto: new BigNumber(input.amount).numeric,
        moneda: input.currency_code,
      },
    }
  }

  async authorizePayment(input: AuthorizePaymentInput): Promise<AuthorizePaymentOutput> {
    const datos = input.data as DatosSimulados
    const resultado = await this.autorizar(datos)
    const capturado = resultado.status === PaymentSessionStatus.CAPTURED
    return {
      status: resultado.status,
      data: {
        ...datos,
        ...(resultado.data ?? {}),
        estado: capturado ? "capturado" : "autorizado",
        autorizadoEn: this.ahora(),
        ...(capturado ? { capturadoEn: this.ahora() } : {}),
      },
    }
  }

  // Medusa la llama al capturar desde la Admin API (POST /admin/payments/{id}/capture)
  // y también justo después de autorizar cuando el simulador cobró en el acto.
  async capturePayment(input: CapturePaymentInput): Promise<CapturePaymentOutput> {
    const datos = input.data ?? {}
    return {
      data: {
        ...datos,
        estado: "capturado",
        capturadoEn: (datos.capturadoEn as string) ?? this.ahora(),
      },
    }
  }

  async refundPayment(input: RefundPaymentInput): Promise<RefundPaymentOutput> {
    const datos = input.data ?? {}
    const reembolsos = Array.isArray(datos.reembolsos) ? datos.reembolsos : []
    return {
      data: {
        ...datos,
        estado: "reembolsado",
        reembolsos: [
          ...reembolsos,
          {
            id: this.nuevoId("re_sim"),
            monto: new BigNumber(input.amount).numeric,
            fecha: this.ahora(),
          },
        ],
      },
    }
  }

  async cancelPayment(input: CancelPaymentInput): Promise<CancelPaymentOutput> {
    return { data: { ...(input.data ?? {}), estado: "cancelado", canceladoEn: this.ahora() } }
  }

  async deletePayment(input: DeletePaymentInput): Promise<DeletePaymentOutput> {
    return { data: input.data ?? {} }
  }

  async retrievePayment(input: RetrievePaymentInput): Promise<RetrievePaymentOutput> {
    return { data: input.data ?? {} }
  }

  async getPaymentStatus(input: GetPaymentStatusInput): Promise<GetPaymentStatusOutput> {
    const estado = (input.data?.estado as EstadoSimulado) ?? "pendiente"
    const equivalencias: Record<EstadoSimulado, PaymentSessionStatus> = {
      pendiente: PaymentSessionStatus.PENDING,
      autorizado: PaymentSessionStatus.AUTHORIZED,
      capturado: PaymentSessionStatus.CAPTURED,
      reembolsado: PaymentSessionStatus.CAPTURED,
      cancelado: PaymentSessionStatus.CANCELED,
    }
    return { status: equivalencias[estado] ?? PaymentSessionStatus.PENDING, data: input.data }
  }

  // Los simuladores no reciben webhooks de ninguna pasarela: los cambios
  // (p. ej. "Simular pago en tienda" de Paynet) entran por la Admin API.
  async getWebhookActionAndData(
    _payload: ProviderWebhookPayload["payload"]
  ): Promise<WebhookActionResult> {
    return { action: "not_supported" }
  }
}
