import type { Metadata } from "next";
import Link from "next/link";
import { cancelPayment, simulatePayment } from "@/app/actions/dashboard";
import { ActionButton } from "@/components/dashboard/ActionButton";
import { CopyButton } from "@/components/dashboard/CopyButton";
import { StatusBadge } from "@/components/payment/StatusBadge";
import { formatCents, formatDateTime } from "@/lib/format";
import { requireData, sessionApi } from "@/lib/server/session-api";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Pagamento — MoneyX" };

export default async function PaymentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/payments/{id}", { params: { path: { id } } });
  const payment = requireData(data, response, "GET /v1/payments/{id}");
  const pending = payment.status === "PENDING";

  return (
    <div className={ui.page}>
      <Link href="/painel/pagamentos" className={ui.backLink}>
        ← Pagamentos
      </Link>
      <header className={ui.pageHeader}>
        <div>
          <h1 className={ui.pageTitle}>{formatCents(payment.amount)}</h1>
          <p className={`${ui.muted} ${ui.mono}`}>{payment.id}</p>
        </div>
        {payment.status && <StatusBadge status={payment.status} />}
      </header>

      <section className={ui.card} aria-labelledby="details-title">
        <h2 id="details-title" className={ui.cardTitle}>
          Detalhes
        </h2>
        <dl className={ui.facts}>
          <Fact label="Descrição" value={payment.description} />
          <Fact label="Referência externa" value={payment.externalReference} mono />
          <Fact label="Criado em" value={formatDateTime(payment.createdAt)} />
          <Fact label="Vence em" value={formatDateTime(payment.expiresAt)} />
          {payment.paidAt && <Fact label="Pago em" value={formatDateTime(payment.paidAt)} />}
          {payment.canceledAt && <Fact label="Cancelado em" value={formatDateTime(payment.canceledAt)} />}
          <Fact label="txid" value={payment.pix?.txid} mono />
        </dl>
      </section>

      {payment.pix?.copyPaste && (
        <section className={ui.card} aria-labelledby="pix-title">
          <h2 id="pix-title" className={ui.cardTitle}>
            Pix copia e cola
          </h2>
          <p className={ui.muted}>
            É o código que o pagador cola no app do banco. No modo de teste ele tem o formato real, mas não é pagável:
            use “Simular pagamento”.
          </p>
          <code className={ui.secretValue} aria-label="Código Pix copia e cola">
            {payment.pix.copyPaste}
          </code>
          <div className={ui.row}>
            <CopyButton value={payment.pix.copyPaste} label="Copiar código" />
          </div>
        </section>
      )}

      {pending && (
        <section className={ui.card} aria-labelledby="actions-title">
          <h2 id="actions-title" className={ui.cardTitle}>
            Ações
          </h2>
          <div className={ui.row}>
            <ActionButton
              action={simulatePayment.bind(null, id)}
              label="Simular pagamento"
              pendingLabel="Simulando…"
              variant="primary"
            />
            <ActionButton
              action={cancelPayment.bind(null, id)}
              label="Cancelar cobrança"
              pendingLabel="Cancelando…"
              confirmMessage="Cancelar esta cobrança? O pagador não conseguirá mais pagá-la."
              variant="danger"
            />
          </div>
        </section>
      )}
    </div>
  );
}

function Fact({ label, value, mono }: { label: string; value?: string; mono?: boolean }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd className={mono ? ui.mono : undefined}>{value || "—"}</dd>
    </div>
  );
}
