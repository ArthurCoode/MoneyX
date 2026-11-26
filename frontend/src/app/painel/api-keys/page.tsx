import type { Metadata } from "next";
import { revokeApiKey } from "@/app/actions/dashboard";
import { ActionButton } from "@/components/dashboard/ActionButton";
import { formatDateTime } from "@/lib/format";
import { requireData, sessionApi } from "@/lib/server/session-api";
import { CreateApiKeyForm } from "./CreateApiKeyForm";
import ui from "@/components/dashboard/dashboard.module.css";

export const metadata: Metadata = { title: "API Keys — MoneyX" };

export default async function ApiKeysPage() {
  const api = await sessionApi();
  const { data, response } = await api.GET("/v1/api-keys");
  const keys = requireData(data, response, "GET /v1/api-keys").data ?? [];
  const active = keys.filter((key) => key.active);

  return (
    <div className={ui.page}>
      <header>
        <h1 className={ui.pageTitle}>API Keys</h1>
        <p className={ui.muted}>
          Chaves para o SEU servidor chamar a API do MoneyX (<code className={ui.mono}>Authorization: Bearer …</code>).
          Nunca coloque uma chave no navegador ou no app do celular. Até 5 chaves ativas.
        </p>
      </header>

      <section className={`${ui.card} ${ui.narrow}`} aria-labelledby="new-key-title">
        <h2 id="new-key-title" className={ui.cardTitle}>
          Criar chave
        </h2>
        <CreateApiKeyForm />
      </section>

      <div className={ui.tableWrap}>
        <table className={ui.table}>
          <caption className={ui.caption}>
            {active.length} de 5 chaves ativas
          </caption>
          <thead>
            <tr>
              <th scope="col">Prefixo</th>
              <th scope="col">Modo</th>
              <th scope="col">Situação</th>
              <th scope="col">Criada em</th>
              <th scope="col">
                <span className="visually-hidden">Ações</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {keys.map((key) => (
              <tr key={key.id}>
                <td className={ui.mono}>{key.prefix}…</td>
                <td>{key.mode === "live" ? "Produção" : "Teste"}</td>
                <td>
                  {key.active ? (
                    <span className={ui.tagOk}>Ativa</span>
                  ) : (
                    <span className={ui.tag}>Revogada em {formatDateTime(key.revokedAt)}</span>
                  )}
                </td>
                <td>{formatDateTime(key.createdAt)}</td>
                <td>
                  {key.active && key.id && (
                    <ActionButton
                      action={revokeApiKey.bind(null, key.id)}
                      label="Revogar"
                      pendingLabel="Revogando…"
                      confirmMessage={`Revogar a chave ${key.prefix}…? Integrações que a usam param de funcionar na hora.`}
                      variant="danger"
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
