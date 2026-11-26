import type { Metadata } from "next";
import Link from "next/link";
import { formatDateTime, shortId } from "@/lib/format";
import { requireData, sessionApi } from "@/lib/server/session-api";
import { eventLabel, WEBHOOK_EVENT_TYPES } from "@/lib/webhook-events";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Eventos — MoneyX" };

const PAGE_SIZE = 20;

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export default async function EventsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const type = WEBHOOK_EVENT_TYPES.find((candidate) => candidate === params.tipo);
  const after = typeof params.depois === "string" ? params.depois : undefined;

  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/events", {
    params: { query: { limit: PAGE_SIZE, type, startingAfter: after } },
  });
  const page = requireData(data, response, "GET /v1/events");
  const events = page.data ?? [];
  const lastId = events.at(-1)?.id;

  const hrefFor = (query: Record<string, string | undefined>) => {
    const search = new URLSearchParams(Object.entries(query).filter((entry): entry is [string, string] => !!entry[1]));
    const text = search.toString();
    return text ? `/painel/eventos?${text}` : "/painel/eventos";
  };

  return (
    <div className={ui.page}>
      <header>
        <h1 className={ui.pageTitle}>Eventos</h1>
        <p className={ui.muted}>Tudo o que aconteceu com os seus pagamentos, na ordem. É o mesmo conteúdo dos webhooks.</p>
      </header>

      <nav aria-label="Filtrar por tipo" className={ui.filters}>
        <Link href={hrefFor({})} className={ui.filter} aria-current={!type ? "page" : undefined}>
          Todos
        </Link>
        {WEBHOOK_EVENT_TYPES.map((candidate) => (
          <Link
            key={candidate}
            href={hrefFor({ tipo: candidate })}
            className={ui.filter}
            aria-current={candidate === type ? "page" : undefined}
          >
            {eventLabel(candidate)}
          </Link>
        ))}
      </nav>

      <div className={ui.tableWrap}>
        {events.length === 0 ? (
          <p className={ui.empty}>Nenhum evento por aqui.</p>
        ) : (
          <table className={ui.table}>
            <thead>
              <tr>
                <th scope="col">Evento</th>
                <th scope="col">Tipo</th>
                <th scope="col">Quando</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>
                    <Link href={`/painel/eventos/${event.id}`} className={`${ui.rowLink} ${ui.mono}`}>
                      {shortId(event.id)}
                    </Link>
                  </td>
                  <td>
                    {eventLabel(event.type)} <code className={`${ui.mono} ${ui.muted}`}>{event.type}</code>
                  </td>
                  <td>{formatDateTime(event.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <nav aria-label="Paginação" className={ui.pagination}>
        {after ? <Link href={hrefFor({ tipo: type })}>← Mais recentes</Link> : <span />}
        {page.hasMore && lastId && <Link href={hrefFor({ tipo: type, depois: lastId })}>Próxima página →</Link>}
      </nav>
    </div>
  );
}
