export interface Plan {
  code: string; name: string; price: number; currency: string; interval: string
  entitlements: { code: string; enabled: boolean; limit: number | null; unlimited: boolean }[]
}
export interface Subscription {
  id: string; plan: string; status: string; startsAt: string; endsAt: string | null
  graceUntil: string | null; autoRenew: boolean; demoManaged: boolean; effectiveNow: string
  pendingSubscriptionId: string | null; pendingStatus: string | null; simulatorEnabled: boolean; timezone: string
}
export interface Receipt {
  demonstration: boolean; notice: string
  order: { id: string; userId: string; subscriptionId: string; amount: number; currency: string; status: string; kind: string; createdAt: string }
  transaction: { id: string; orderId: string; scenario: string; status: string; authorizationCode: string | null; createdAt: string; resolvedAt: string | null } | null
  events: { event_type: string; created_at: string; detail: string }[]
}
export interface Usage { quotaTimezone: string; practices: { used: number; limit: number; unlimited: boolean }; simulations: { used: number; limit: number; unlimited: boolean } }
export const scenarios = [
  { id: 'DEMO_APPROVED', suffix: '4242', title: 'Aprobada', detail: 'Activa Premium al confirmar.' },
  { id: 'DEMO_DECLINED', suffix: '0002', title: 'Rechazada', detail: 'Conserva FREE. No se aprueba el pago.' },
  { id: 'DEMO_PENDING', suffix: '0003', title: 'Pendiente', detail: 'Requiere confirmación de un administrador de pruebas.' },
] as const
export const statusLabels: Record<string, string> = { APPROVED: 'Aprobado', REJECTED: 'Rechazado', PENDING: 'Pendiente', ACTIVE: 'Activa', GRACE: 'Periodo de gracia', PENDING_PAYMENT: 'Pendiente de pago', CANCELED: 'Cancelada', EXPIRED: 'Vencida' }
export function money(amount: number, currency: string) { return new Intl.NumberFormat('es-BO', { style: 'currency', currency }).format(amount) }
export function date(value: string | null) { return value ? new Intl.DateTimeFormat('es-BO', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/La_Paz' }).format(new Date(value)) : 'Sin vencimiento' }
