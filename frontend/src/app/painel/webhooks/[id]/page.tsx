import type { Metadata } from "next";
import Link from "next/link";
import { deleteWebhookEndpoint, enableWebhookEndpoint, retryWebhookDelivery } from "@/app/actions/dashboard";
import { ActionButton } from "@/components/dashboard/ActionButton";
import { formatDateTime, shortId } from "@/lib/format";
import { requireData, sessionApi } from "@/lib/server/session-api";
import { eventLabel } from "@/lib/webhook-events";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Endpoint de webhook — MoneyX" };

const DELIVERY_STATUS: Record<string, { label: string; className: string }> = {
  SUCCEEDED: { label: "Entregue", className: ui.tagOk! },
  PENDING: { label: "Na fila", className: ui.tagWarn! },
  FAILED: { label: "Falhou", className: ui.tagBad! },
};

const HISTORY_ACTION: Record<string, string> = {
  CREATED: "Criado",
  DISABLED: "Desativado",
  ENABLED: "Reativado",
  DELETED: "Excluído",
};

export default async function WebhookEndpointPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const api = await sessionApi();
  const path = { params: { path: { id } } };
  const [endpointResult, deliveriesResult, historyResult] = await Promise.all([
    api.GET("/v1/webhook-endpoints/{id}", path),
    api.GET("/v1/webhook-endpoints/{id}/deliveries", { params: { path: { id }, query: { limit: 20 } } }),
    api.GET("/v1/webhook-endpoints/{id}/history", path),
  ]);
  const endpoint = requireData(endpointResult.data, endpointResult.response, "GET endpoint");
  const deliveries = requireData(deliveriesResult.data, deliveriesResult.response, "GET deliveries").data ?? [];
  const history = requireData(historyResult.data, historyResult.response, "GET history").data ?? [];

  return (
    <div className={ui.page}>
      <Link href="/painel/webhooks" className={ui.backLink}>
        ← Webhooks
      </Link>
      <header className={ui.pageHeader}>
        <div>
          <h1 className={`${ui.pageTitle} ${ui.wrapAnywhere}`}>
            {endpoint.url}
          </h1>
          <p className={`${ui.muted} ${ui.mono}`}>{endpoint.id}</p>
        </div>
        {endpoint.disabledAt ? (
          <span className={ui.tagBad}>Desativado</span>
        ) : (
          <span className={ui.tagOk}>Ativo</span>
        )}
      </header>

      <section className={ui.card} aria-labelledby="config-title">
        <h2 id="config-title" className={ui.cardTitle}>
          Configuração
        </h2>
        <dl className={ui.facts}>
          <div>
            <dt>Eventos</dt>
            <dd>{(endpoint.enabledEvents ?? []).map(eventLabel).join(", ")}</dd>
          </div>
          <div>
            <dt>Segredo</dt>
            <dd className={ui.mono}>{endpoint.secretPrefix}…</dd>
          </div>
          <div>
            <dt>Criado em</dt>
            <dd>{formatDateTime(endpoint.createdAt)}</dd>
          </div>
          {endpoint.disabledAt && (
            <div>
              <dt>Desativado em</dt>
              <dd>{formatDateTime(endpoint.disabledAt)}</dd>
            </div>
          )}
        </dl>
        {endpoint.disabledAt && (
          <p className={ui.muted}>
            Desativado depois de 3 dias seguidos de falhas (ou por você). Avisos novos não são enviados até reativar.
          </p>
        )}
        <div className={ui.row}>
          {endpoint.disabledAt && (
            <ActionButton
              action={enableWebhookEndpoint.bind(null, id)}
              label="Reativar"
              pendingLabel="Reativando…"
              variant="primary"
            />
          )}
          <ActionButton
            action={deleteWebhookEndpoint.bind(null, id)}
            label="Excluir endpoint"
            pendingLabel="Excluindo…"
            confirmMessage="Excluir este endpoint? Avisos pendentes para ele deixam de ser enviados."
            variant="danger"
          />
        </div>
      </section>

      <section className={ui.page} aria-labelledby="deliveries-title">
        <h2 id="deliveries-title" className={ui.cardTitle}>
          Entregas recentes
        </h2>
        <div className={ui.tableWrap}>
          {deliveries.length === 0 ? (
            <p className={ui.empty}>Nenhuma entrega ainda. Elas aparecem quando um pagamento muda de estado.</p>
          ) : (
            <table className={ui.table}>
              <thead>
                <tr>
                  <th scope="col">Evento</th>
                  <th scope="col">Situação</th>
                  <th scope="col" className={ui.num}>
                    Tentativas
                  </th>
                  <th scope="col">Última resposta</th>
                  <th scope="col">Próxima tentativa</th>
                  <th scope="col">
                    <span className="visually-hidden">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {deliveries.map((delivery) => {
                  const status = DELIVERY_STATUS[delivery.status ?? ""];
                  return (
                    <tr key={delivery.id}>
                      <td>
                        <Link href={`/painel/eventos/${delivery.eventId}`} className={`${ui.rowLink} ${ui.mono}`}>
                          {shortId(delivery.eventId)}
                        </Link>
                      </td>
                      <td>
                        <span className={status?.className ?? ui.tag}>{status?.label ?? delivery.status}</span>
                      </td>
                      <td className={ui.num}>{delivery.attempts ?? 0}</td>
                      <td className={ui.mono}>
                        {delivery.lastStatusCode ?? delivery.lastError ?? "—"}
                        {delivery.lastDurationMs !== undefined && ` · ${delivery.lastDurationMs} ms`}
                      </td>
                      <td>{formatDateTime(delivery.nextAttemptAt)}</td>
                      <td>
                        {delivery.status === "FAILED" && delivery.id && (
                          <ActionButton
                            action={retryWebhookDelivery.bind(null, id, delivery.id)}
                            label="Reenviar"
                            pendingLabel="Reenviando…"
                          />
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <section className={ui.card} aria-labelledby="history-title">
        <h2 id="history-title" className={ui.cardTitle}>
          Histórico
        </h2>
        <ol className={ui.timeline}>
          {history.map((entry) => (
            <li key={entry.id}>
              <strong>{HISTORY_ACTION[entry.action ?? ""] ?? entry.action}</strong>
              <span className={ui.muted}>
                {formatDateTime(entry.occurredAt)} ·{" "}
                {entry.sessionId ? "pelo painel" : entry.apiKeyId ? "pela API" : "pelo sistema"}
              </span>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
