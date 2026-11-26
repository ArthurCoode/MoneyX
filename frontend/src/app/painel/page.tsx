import type { Metadata } from "next";
import Link from "next/link";
import { resendEmailVerification } from "@/app/actions/dashboard";
import { ActionButton } from "@/components/dashboard/ActionButton";
import { PaymentsTable } from "@/components/dashboard/PaymentsTable";
import { formatCents } from "@/lib/format";
import { requireMerchant } from "@/lib/server/merchant";
import { requireData, sessionApi } from "@/lib/server/session-api";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Visão geral — MoneyX" };

export default async function PanelHome() {
  const { merchant } = await requireMerchant();
  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/payments", { params: { query: { limit: 5 } } });
  const recent = requireData(data, response, "GET /v1/payments").data ?? [];

  return (
    <div className={ui.page}>
      <header className={ui.pageHeader}>
        <h1 className={ui.pageTitle}>Olá, {merchant.legalName}</h1>
        <Link href="/painel/pagamentos/nova" className={ui.primary}>
          Nova cobrança Pix
        </Link>
      </header>

      {!merchant.emailVerifiedAt && (
        <section className={ui.card} aria-labelledby="verify-title">
          <h2 id="verify-title" className={ui.cardTitle}>
            Confirme o seu e-mail
          </h2>
          <p className={ui.muted}>
            Enviamos um link para <strong>{merchant.email}</strong>. A confirmação é necessária para criar chaves de
            produção (<code className={ui.mono}>mx_live_</code>).
          </p>
          <ActionButton action={resendEmailVerification} label="Reenviar e-mail" pendingLabel="Enviando…" />
        </section>
      )}

      <div className={ui.grid2}>
        <section className={ui.card} aria-labelledby="account-title">
          <h2 id="account-title" className={ui.cardTitle}>
            Conta
          </h2>
          <dl className={ui.facts}>
            <div>
              <dt>E-mail</dt>
              <dd>
                {merchant.email} {merchant.emailVerifiedAt ? "(confirmado)" : "(aguardando confirmação)"}
              </dd>
            </div>
            <div>
              <dt>Situação</dt>
              <dd>{merchant.status === "ACTIVE" ? "Ativa" : merchant.status}</dd>
            </div>
            <div>
              <dt>Documento</dt>
              <dd className={ui.mono}>{merchant.document}</dd>
            </div>
          </dl>
        </section>
        <section className={ui.card} aria-labelledby="limits-title">
          <h2 id="limits-title" className={ui.cardTitle}>
            Limites de volume
          </h2>
          <dl className={ui.facts}>
            <div>
              <dt>Por dia</dt>
              <dd className={ui.stat}>{formatCents(merchant.volumeLimits?.daily)}</dd>
            </div>
            <div>
              <dt>Por mês</dt>
              <dd className={ui.stat}>{formatCents(merchant.volumeLimits?.monthly)}</dd>
            </div>
          </dl>
          <p className={ui.muted}>Soma das cobranças criadas e não canceladas, no horário de Brasília.</p>
        </section>
      </div>

      <section className={ui.page} aria-labelledby="recent-title">
        <div className={ui.pageHeader}>
          <h2 id="recent-title" className={ui.cardTitle}>
            Pagamentos recentes
          </h2>
          <Link href="/painel/pagamentos">Ver todos</Link>
        </div>
        <PaymentsTable payments={recent} />
      </section>
    </div>
  );
}
