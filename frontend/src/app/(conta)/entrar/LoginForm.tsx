"use client";

import Link from "next/link";
import { useActionState } from "react";
import { login } from "@/app/actions/auth";
import { Alert } from "@/components/form/Alert";
import { Field } from "@/components/form/Field";
import { SubmitButton } from "@/components/form/SubmitButton";
import type { FormState } from "@/lib/forms/problem";
import formStyles from "@/components/form/Form.module.css";

export function LoginForm({ next }: { next?: string }) {
  const [state, action] = useActionState<FormState, FormData>(login, {});

  return (
    <form action={action} className={formStyles.form} noValidate>
      {state.error && <Alert kind="error">{state.error}</Alert>}
      {next && <input type="hidden" name="next" value={next} />}
      <Field
        name="email"
        label="E-mail"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
        error={state.fieldErrors?.email}
      />
      <Field
        name="password"
        label="Senha"
        type="password"
        autoComplete="current-password"
        required
        error={state.fieldErrors?.password}
      />
      <SubmitButton pendingLabel="Entrando…">Entrar</SubmitButton>
      <Link href="/esqueci-a-senha">Esqueci a senha</Link>
    </form>
  );
}
