import type { Metadata } from "next";
import Link from "next/link";
import { NewPaymentForm } from "./NewPaymentForm";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "Nova cobrança — MoneyX" };

export default function NewPaymentPage() {
  // Uma chave de idempotência por formulário montado: o mesmo envio repetido devolve o mesmo pagamento.
  const idempotencyKey = crypto.randomUUID();

  return (
    <div className={ui.page}>
      <Link href="/painel/pagamentos" className={ui.backLink}>
        ← Pagamentos
      </Link>
      <h1 className={ui.pageTitle}>Nova cobrança Pix</h1>
      <section className={`${ui.card} ${ui.narrow}`}>
        <NewPaymentForm idempotencyKey={idempotencyKey} />
      </section>
    </div>
  );
}
