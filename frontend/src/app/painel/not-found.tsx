import Link from "next/link";
import ui from "@/components/dashboard/dashboard.module.css";

/** Id inexistente, malformado ou de OUTRO comerciante (a API responde 404 nos dois casos, sem revelar qual). */
export default function PanelNotFound() {
  return (
    <div className={ui.page}>
      <h1 className={ui.pageTitle}>Não encontramos este item</h1>
      <p className={ui.muted}>Ele pode ter sido removido, ou o endereço está incompleto.</p>
      <p>
        <Link href="/painel">Voltar para a visão geral</Link>
      </p>
    </div>
  );
}
