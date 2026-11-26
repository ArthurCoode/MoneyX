"use client";

import { useActionState } from "react";
import { createWebhookEndpoint, type CreatedSecretState } from "@/app/actions/dashboard";
import { SecretReveal } from "@/components/dashboard/SecretReveal";
import { Alert } from "@/components/form/Alert";
import { Field } from "@/components/form/Field";
import { SubmitButton } from "@/components/form/SubmitButton";
import { WEBHOOK_EVENT_LABELS, WEBHOOK_EVENT_TYPES } from "@/lib/webhook-events";
import formStyles from "@/components/form/Form.module.css";
import ui from "@/components/dashboard/dashboard.module.css";

export function CreateWebhookForm() {
  const [state, action] = useActionState<CreatedSecretState, FormData>(createWebhookEndpoint, {});

  return (
    <form action={action} className={formStyles.form} noValidate>
      {state.secret && (
        <SecretReveal title="Segredo de assinatura" secret={state.secret}>
          <p className={ui.muted}>Use-o no seu servidor para conferir o header MoneyX-Signature de cada aviso.</p>
        </SecretReveal>
      )}
      {state.error && <Alert kind="error">{state.error}</Alert>}
      <Field
        name="url"
        label="URL do endpoint"
        type="url"
        placeholder="https://loja.com.br/webhooks/moneyx"
        required
        defaultValue={state.values?.url}
        error={state.fieldErrors?.url}
      />
      <fieldset
        className={ui.fieldset}
        aria-describedby={state.fieldErrors?.enabledEvents ? "events-error" : undefined}
      >
        <legend>Eventos</legend>
        {WEBHOOK_EVENT_TYPES.map((type) => (
          <label key={type} className={ui.check}>
            <input type="checkbox" name="enabledEvents" value={type} defaultChecked />
            <span>
              {WEBHOOK_EVENT_LABELS[type]} <code className={ui.mono}>{type}</code>
            </span>
          </label>
        ))}
        {state.fieldErrors?.enabledEvents && (
          <span id="events-error" className={formStyles.fieldError}>
            {state.fieldErrors.enabledEvents}
          </span>
        )}
      </fieldset>
      <SubmitButton pendingLabel="Criando…">Criar endpoint</SubmitButton>
    </form>
  );
}
