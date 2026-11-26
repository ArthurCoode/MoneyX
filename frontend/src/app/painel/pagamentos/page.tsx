import type { Metadata } from "next";
import Link from "next/link";
import { PaymentsTable } from "@/components/dashboard/PaymentsTable";
import type { PaymentStatus } from "@/lib/api/types";
import { requireData, sessionApi } from "@/lib/server/session-api";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Pagamentos — MoneyX" };

const PAGE_SIZE = 20;

const FILTERS: { status?: PaymentStatus; label: string }[] = [
  { label: "Todos" },
  { status: "PENDING", label: "Aguardando" },
  { status: "PAID", label: "Pagos" },
  { status: "CANCELED", label: "Cancelados" },
  { status: "EXPIRED", label: "Expirados" },
];

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/** Lista com filtro e paginação por cursor (`startingAfter` = último id da página, como na API e no Stripe). */
export default async function PaymentsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const status = FILTERS.find((filter) => filter.status && filter.status === params.status)?.status;
  const after = typeof params.depois === "string" ? params.depois : undefined;

  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/payments", {
    params: { query: { limit: PAGE_SIZE, status, startingAfter: after } },
  });
  const page = requireData(data, response, "GET /v1/payments");
  const payments = page.data ?? [];
  const lastId = payments.at(-1)?.id;

  const hrefFor = (query: Record<string, string | undefined>) => {
    const search = new URLSearchParams(Object.entries(query).filter((entry): entry is [string, string] => !!entry[1]));
    const text = search.toString();
    return text ? `/painel/pagamentos?${text}` : "/painel/pagamentos";
  };

  return (
    <div className={ui.page}>
      <header className={ui.pageHeader}>
        <h1 className={ui.pageTitle}>Pagamentos</h1>
        <Link href="/painel/pagamentos/nova" className={ui.primary}>
          Nova cobrança Pix
        </Link>
      </header>

      <nav aria-label="Filtrar por status" className={ui.filters}>
        {FILTERS.map((filter) => (
          <Link
            key={filter.label}
            href={hrefFor({ status: filter.status })}
            className={ui.filter}
            aria-current={filter.status === status ? "page" : undefined}
          >
            {filter.label}
          </Link>
        ))}
      </nav>

      <PaymentsTable payments={payments} />

      <nav aria-label="Paginação" className={ui.pagination}>
        {after ? <Link href={hrefFor({ status })}>← Mais recentes</Link> : <span />}
        {page.hasMore && lastId && <Link href={hrefFor({ status, depois: lastId })}>Próxima página →</Link>}
      </nav>
    </div>
  );
}
