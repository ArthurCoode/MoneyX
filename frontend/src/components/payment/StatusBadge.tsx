import type { PaymentStatus } from "@/lib/api/types";
import styles from "./StatusBadge.module.css";

type Tone = "success" | "warning" | "danger" | "neutral";

/**
 * Como cada status aparece. `satisfies Record<PaymentStatus, ...>`: se o backend criar um status novo, o TypeScript
 * acusa que ele falta aqui (o tipo vem do contrato OpenAPI gerado).
 *
 * Acessibilidade: o estado NUNCA é comunicado só pela cor. Sempre texto + ícone (quem não distingue verde de vermelho
 * continua entendendo).
 */
const STATUS = {
  CREATING: { label: "Criando", icon: "…", tone: "neutral" },
  PENDING: { label: "Aguardando", icon: "◷", tone: "warning" },
  PAID: { label: "Pago", icon: "✓", tone: "success" },
  CANCELED: { label: "Cancelado", icon: "✕", tone: "danger" },
  EXPIRED: { label: "Expirado", icon: "⌛", tone: "danger" },
} satisfies Record<PaymentStatus, { label: string; icon: string; tone: Tone }>;

export function StatusBadge({ status }: { status: PaymentStatus }) {
  const { label, icon, tone } = STATUS[status];
  return (
    <span className={`${styles.badge} ${styles[tone]}`} data-status={status}>
      <span aria-hidden="true">{icon}</span>
      {label}
    </span>
  );
}
