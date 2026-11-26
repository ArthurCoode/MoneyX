import type { Metadata } from "next";
import Link from "next/link";
import { formatDateTime } from "@/lib/format";
import { requireData, sessionApi } from "@/lib/server/session-api";
import { eventLabel } from "@/lib/webhook-events";
import { CreateWebhookForm } from "./CreateWebhookForm";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Webhooks — MoneyX" };

export default async function WebhooksPage() {
  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/webhook-endpoints");
  const endpoints = requireData(data, response, "GET /v1/webhook-endpoints").data ?? [];

  return (
    <div className={ui.page}>
      <header>
        <h1 className={ui.pageTitle}>Webhooks</h1>
        <p className={ui.muted}>
          O MoneyX avisa o seu servidor quando um pagamento muda de estado, com assinatura HMAC no header{" "}
          <code className={ui.mono}>MoneyX-Signature</code>. Falhas são repetidas por até ~3 dias. Até 5 endpoints.
        </p>
      </header>

      <section className={`${ui.card} ${ui.narrow}`} aria-labelledby="new-endpoint-title">
        <h2 id="new-endpoint-title" className={ui.cardTitle}>
          Novo endpoint
        </h2>
        <CreateWebhookForm />
      </section>

      <div className={ui.tableWrap}>
        {endpoints.length === 0 ? (
          <p className={ui.empty}>Nenhum endpoint cadastrado.</p>
        ) : (
          <table className={ui.table}>
            <thead>
              <tr>
                <th scope="col">URL</th>
                <th scope="col">Eventos</th>
                <th scope="col">Situação</th>
                <th scope="col">Criado em</th>
              </tr>
            </thead>
            <tbody>
              {endpoints.map((endpoint) => (
                <tr key={endpoint.id}>
                  <td>
                    <Link href={`/painel/webhooks/${endpoint.id}`} className={ui.rowLink}>
                      {endpoint.url}
                    </Link>
                  </td>
                  <td>{(endpoint.enabledEvents ?? []).map(eventLabel).join(", ")}</td>
                  <td>
                    {endpoint.disabledAt ? (
                      <span className={ui.tagBad}>Desativado</span>
                    ) : (
                      <span className={ui.tagOk}>Ativo</span>
                    )}
                  </td>
                  <td>{formatDateTime(endpoint.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
