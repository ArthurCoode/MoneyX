"use client";

import { useActionState } from "react";
import { createPayment } from "@/app/actions/dashboard";
import { Alert } from "@/components/form/Alert";
import { Field } from "@/components/form/Field";
import { SubmitButton } from "@/components/form/SubmitButton";
import type { FormState } from "@/lib/forms/problem";
import formStyles from "@/components/form/Form.module.css";
import ui from "@/components/dashboard/dashboard.module.css";

export function NewPaymentForm({ idempotencyKey }: { idempotencyKey: string }) {
  const [state, action] = useActionState<FormState, FormData>(createPayment, {});

  return (
    <form action={action} className={formStyles.form} noValidate>
      {state.error && <Alert kind="error">{state.error}</Alert>}
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <Field
        name="amount"
        label="Valor (R$)"
        inputMode="decimal"
        placeholder="25,90"
        hint="Use vírgula para os centavos."
        required
        defaultValue={state.values?.amount}
        error={state.fieldErrors?.amount}
      />
      <Field
        name="description"
        label="Descrição (opcional)"
        defaultValue={state.values?.description}
        error={state.fieldErrors?.description}
      />
      <Field
        name="externalReference"
        label="Referência do seu sistema (opcional)"
        hint="Ex.: o número do pedido. Dá para buscar por ela na API."
        defaultValue={state.values?.externalReference}
        error={state.fieldErrors?.externalReference}
      />
      <div className={formStyles.field}>
        <label htmlFor="expiresInSeconds" className={formStyles.label}>
          Prazo para pagar
        </label>
        <select
          id="expiresInSeconds"
          name="expiresInSeconds"
          className={ui.select}
          defaultValue={state.values?.expiresInSeconds ?? "3600"}
        >
          <option value="900">15 minutos</option>
          <option value="3600">1 hora</option>
          <option value="86400">24 horas</option>
        </select>
      </div>
      <SubmitButton pendingLabel="Criando cobrança…">Criar cobrança</SubmitButton>
    </form>
  );
}
