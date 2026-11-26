import type { PaymentStatus } from "@/lib/api/types";
import styles from "./StatusBadge.module.css";

type Tone = "success" | "warning" | "danger" | "neutral";
type Icon = "dots" | "clock" | "check" | "cross" | "hourglass";

/**
 * Como cada status aparece. `satisfies Record<PaymentStatus, ...>`: se o backend criar um status novo, o TypeScript
 * acusa que ele falta aqui (o tipo vem do contrato OpenAPI gerado).
 *
 * Acessibilidade: o estado NUNCA é comunicado só pela cor. Sempre texto + ícone (quem não distingue verde de vermelho
 * continua entendendo). Ícones em SVG (o caractere ⌛ virava emoji colorido em alguns sistemas).
 */
const STATUS = {
  CREATING: { label: "Criando", icon: "dots", tone: "neutral" },
  PENDING: { label: "Aguardando", icon: "clock", tone: "warning" },
  PAID: { label: "Pago", icon: "check", tone: "success" },
  CANCELED: { label: "Cancelado", icon: "cross", tone: "danger" },
  EXPIRED: { label: "Expirado", icon: "hourglass", tone: "danger" },
} satisfies Record<PaymentStatus, { label: string; icon: Icon; tone: Tone }>;

const PATHS: Record<Icon, React.ReactNode> = {
  dots: (
    <>
      <circle cx="3" cy="8" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="8" cy="8" r="1.4" fill="currentColor" stroke="none" />
      <circle cx="13" cy="8" r="1.4" fill="currentColor" stroke="none" />
    </>
  ),
  clock: (
    <>
      <circle cx="8" cy="8" r="6.25" />
      <path d="M8 4.5V8l2.5 1.5" />
    </>
  ),
  check: <path d="M3 8.5l3.2 3L13 4.5" />,
  cross: <path d="M4 4l8 8M12 4l-8 8" />,
  hourglass: <path d="M4 2h8M4 14h8M5 2c0 3.5 6 4.5 6 6s-6 2.5-6 6M11 2c0 3.5-6 4.5-6 6s6 2.5 6 6" />,
};

export function StatusBadge({ status }: { status: PaymentStatus }) {
  const { label, icon, tone } = STATUS[status];
  return (
    <span className={`${styles.badge} ${styles[tone]}`} data-status={status}>
      <svg
        aria-hidden="true"
        width="14"
        height="14"
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {PATHS[icon]}
      </svg>
      {label}
    </span>
  );
}
