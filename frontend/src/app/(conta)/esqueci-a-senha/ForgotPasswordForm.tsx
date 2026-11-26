"use client";

import { useActionState } from "react";
import { requestPasswordReset } from "@/app/actions/auth";
import { Alert } from "@/components/form/Alert";
import { Field } from "@/components/form/Field";
import { SubmitButton } from "@/components/form/SubmitButton";
import type { FormState } from "@/lib/forms/problem";
import formStyles from "@/components/form/Form.module.css";

export function ForgotPasswordForm() {
  const [state, action] = useActionState<FormState, FormData>(requestPasswordReset, {});

  if (state.notice) return <Alert kind="success">{state.notice}</Alert>;

  return (
    <form action={action} className={formStyles.form} noValidate>
      {state.error && <Alert kind="error">{state.error}</Alert>}
      <Field
        name="email"
        label="E-mail"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
        error={state.fieldErrors?.email}
      />
      <SubmitButton pendingLabel="Enviando…">Enviar link</SubmitButton>
    </form>
  );
}
