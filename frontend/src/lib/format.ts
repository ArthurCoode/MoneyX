/**
 * Formatação para a tela. Dinheiro trafega em CENTAVOS (inteiro, como na API); nunca vira número com vírgula
 * flutuante. Datas sempre no fuso de São Paulo (o mesmo dos limites de volume da API).
 */

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dateTime = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "America/Sao_Paulo",
});

/** 123456 → "R$ 1.234,56". A divisão por 100 só acontece aqui, para exibir. */
export function formatCents(cents: number | undefined): string {
  if (cents === undefined) return "—";
  return brl.format(cents / 100);
}

/**
 * Texto digitado em reais → centavos, SEM passar por float: "1.234,56" → 123456, "10" → 1000, "0,5" → 50.
 * Aceita vírgula como decimal e ponto como milhar (padrão brasileiro). Devolve null se não for um valor válido.
 */
export function parseReaisToCents(input: string): number | null {
  const text = input.trim().replace(/^R\$\s*/, "");
  const match = /^(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?$/.exec(text);
  if (!match) return null;
  const reais = match[1]!.replaceAll(".", "");
  const centavos = (match[2] ?? "").padEnd(2, "0");
  const cents = Number(reais + centavos);
  return Number.isSafeInteger(cents) ? cents : null;
}

export function formatDateTime(iso: string | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "—" : dateTime.format(date);
}

/** Primeiros 8 caracteres de um UUID, para listas (o id completo fica no detalhe). */
export function shortId(id: string | undefined): string {
  return id ? id.slice(0, 8) : "—";
}
