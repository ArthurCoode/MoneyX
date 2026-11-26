import type { Metadata } from "next";
import Link from "next/link";
import { formatDateTime } from "@/lib/format";
import { requireData, sessionApi } from "@/lib/server/session-api";
import { eventLabel } from "@/lib/webhook-events";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Evento — MoneyX" };

export default async function EventPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/events/{id}", { params: { path: { id } } });
  const event = requireData(data, response, "GET /v1/events/{id}");
  const paymentId = (event.data as { id?: unknown } | undefined)?.id;

  return (
    <div className={ui.page}>
      <Link href="/painel/eventos" className={ui.backLink}>
        ← Eventos
      </Link>
      <header>
        <h1 className={ui.pageTitle}>{eventLabel(event.type)}</h1>
        <p className={`${ui.muted} ${ui.mono}`}>
          {event.id} · {formatDateTime(event.createdAt)}
        </p>
      </header>
      {typeof paymentId === "string" && (
        <p>
          <Link href={`/painel/pagamentos/${paymentId}`}>Ver o pagamento</Link>
        </p>
      )}
      <section className={ui.card} aria-labelledby="payload-title">
        <h2 id="payload-title" className={ui.cardTitle}>
          Conteúdo (igual ao enviado no webhook)
        </h2>
        {/* Texto puro dentro de <pre>: o React escapa tudo, nenhum HTML vindo dos dados é interpretado. */}
        <pre className={ui.codeBlock}>{JSON.stringify(event, null, 2)}</pre>
      </section>
    </div>
  );
}
