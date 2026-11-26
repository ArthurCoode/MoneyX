import type { components } from "@/lib/api/schema";

type EventType = NonNullable<components["schemas"]["Event"]["type"]>;

/**
 * Tipos de evento, com rótulo em português. `satisfies Record<EventType, ...>` faz o TypeScript reclamar se a API
 * (o contrato gerado) ganhar um tipo novo sem rótulo aqui.
 */
export const WEBHOOK_EVENT_LABELS = {
  "payment.created": "Pagamento criado",
  "payment.paid": "Pagamento pago",
  "payment.canceled": "Pagamento cancelado",
  "payment.expired": "Pagamento expirado",
} as const satisfies Record<EventType, string>;

export const WEBHOOK_EVENT_TYPES = Object.keys(WEBHOOK_EVENT_LABELS) as EventType[];

export function eventLabel(type: string | undefined): string {
  return (type && (WEBHOOK_EVENT_LABELS as Record<string, string>)[type]) ?? type ?? "—";
}
