"use client";

import { useActionState } from "react";
import { confirmPasswordReset } from "@/app/actions/auth";
import { Alert } from "@/components/form/Alert";
import { Field } from "@/components/form/Field";
import { SubmitButton } from "@/components/form/SubmitButton";
import type { FormState } from "@/lib/forms/problem";
import formStyles from "@/components/form/Form.module.css";

export function ResetPasswordForm({ token }: { token: string }) {
  const [state, action] = useActionState<FormState, FormData>(confirmPasswordReset, {});

  return (
    <form action={action} className={formStyles.form} noValidate>
      {state.error && <Alert kind="error">{state.error}</Alert>}
      <input type="hidden" name="token" value={token} />
      <Field
        name="newPassword"
        label="Nova senha"
        type="password"
        autoComplete="new-password"
        hint="De 12 a 128 caracteres. Uma frase longa é mais segura que símbolos."
        required
        error={state.fieldErrors?.newPassword}
      />
      <Field
        name="passwordConfirmation"
        label="Confirme a nova senha"
        type="password"
        autoComplete="new-password"
        required
        error={state.fieldErrors?.passwordConfirmation}
      />
      <SubmitButton pendingLabel="Salvando…">Salvar nova senha</SubmitButton>
    </form>
  );
}
