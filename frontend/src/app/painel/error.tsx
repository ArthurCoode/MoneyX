"use client";

import ui from "@/components/dashboard/dashboard.module.css";

/**
 * Falha inesperada numa página do painel (ex.: API fora do ar). Não mostra detalhes técnicos: só o `digest`, que o
 * Next também registra no log do servidor, para o suporte cruzar.
 */
export default function PanelError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className={ui.page} role="alert">
      <h1 className={ui.pageTitle}>Não foi possível carregar esta página</h1>
      <p className={ui.muted}>
        O MoneyX pode estar instável agora. Tente de novo em instantes.
        {error.digest && (
          <>
            {" "}
            Código: <code className={ui.mono}>{error.digest}</code>
          </>
        )}
      </p>
      <div className={ui.row}>
        <button type="button" className={ui.primary} onClick={reset}>
          Tentar de novo
        </button>
      </div>
    </div>
  );
}
