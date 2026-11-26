"use client";

import { useActionState } from "react";
import { createApiKey, type CreatedSecretState } from "@/app/actions/dashboard";
import { SecretReveal } from "@/components/dashboard/SecretReveal";
import { Alert } from "@/components/form/Alert";
import { SubmitButton } from "@/components/form/SubmitButton";
import formStyles from "@/components/form/Form.module.css";
import ui from "@/components/dashboard/dashboard.module.css";

export function CreateApiKeyForm() {
  const [state, action] = useActionState<CreatedSecretState, FormData>(createApiKey, {});

  return (
    <form action={action} className={formStyles.form}>
      {state.secret && <SecretReveal title="Nova API Key" secret={state.secret} />}
      {state.error && <Alert kind="error">{state.error}</Alert>}
      <fieldset className={ui.fieldset}>
        <legend>Modo</legend>
        <label className={ui.check}>
          <input type="radio" name="mode" value="test" defaultChecked />
          <span>
            Teste (<code className={ui.mono}>mx_test_</code>): cobranças simuladas
          </span>
        </label>
        <label className={ui.check}>
          <input type="radio" name="mode" value="live" />
          <span>
            Produção (<code className={ui.mono}>mx_live_</code>): exige e-mail confirmado
          </span>
        </label>
      </fieldset>
      <SubmitButton pendingLabel="Criando…">Criar chave</SubmitButton>
    </form>
  );
}
