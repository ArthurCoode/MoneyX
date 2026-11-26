import Link from "next/link";
import type { Payment } from "@/lib/api/types";
import { StatusBadge } from "@/components/payment/StatusBadge";
import { formatCents, formatDateTime, shortId } from "@/lib/format";
import ui from "./dashboard.module.css";

export function PaymentsTable({ payments }: { payments: Payment[] }) {
  if (payments.length === 0) {
    return (
      <div className={ui.tableWrap}>
        <p className={ui.empty}>Nenhum pagamento por aqui ainda.</p>
      </div>
    );
  }
  return (
    <div className={ui.tableWrap}>
      <table className={ui.table}>
        <thead>
          <tr>
            <th scope="col">Pagamento</th>
            <th scope="col">Status</th>
            <th scope="col" className={ui.num}>
              Valor
            </th>
            <th scope="col" className={ui.hideSm}>
              Descrição
            </th>
            <th scope="col" className={ui.hideSm}>
              Criado em
            </th>
          </tr>
        </thead>
        <tbody>
          {payments.map((payment) => (
            <tr key={payment.id}>
              <td>
                <Link href={`/painel/pagamentos/${payment.id}`} className={`${ui.rowLink} ${ui.mono}`}>
                  {shortId(payment.id)}
                </Link>
              </td>
              <td>{payment.status && <StatusBadge status={payment.status} />}</td>
              <td className={ui.num}>{formatCents(payment.amount)}</td>
              <td className={ui.hideSm}>{payment.description ?? "—"}</td>
              <td className={ui.hideSm}>{formatDateTime(payment.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
