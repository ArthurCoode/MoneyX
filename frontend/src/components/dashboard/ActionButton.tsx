"use client";

import { useActionState } from "react";
import { useFormStatus } from "react-dom";
import type { FormState } from "@/lib/forms/problem";
import { Alert } from "@/components/form/Alert";
import styles from "./dashboard.module.css";

type Props = {
  action: (previous: FormState, formData: FormData) => Promise<FormState>;
  label: string;
  pendingLabel: string;
  /** Pergunta antes de ações que não têm volta (revogar, excluir, cancelar). */
  confirmMessage?: string;
  variant?: "primary" | "secondary" | "danger";
};

/** Um botão que dispara uma Server Action e mostra o resultado (erro ou sucesso) logo abaixo. */
export function ActionButton({ action, label, pendingLabel, confirmMessage, variant = "secondary" }: Props) {
  const [state, formAction] = useActionState<FormState, FormData>(action, {});

  return (
    <form
      action={formAction}
      className={styles.actionForm}
      onSubmit={(event) => {
        if (confirmMessage && !window.confirm(confirmMessage)) event.preventDefault();
      }}
    >
      <Submit label={label} pendingLabel={pendingLabel} variant={variant} />
      {state.error && <Alert kind="error">{state.error}</Alert>}
      {state.notice && <Alert kind="success">{state.notice}</Alert>}
    </form>
  );
}

function Submit({ label, pendingLabel, variant }: { label: string; pendingLabel: string; variant: string }) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" className={styles[variant]} disabled={pending}>
      {pending ? pendingLabel : label}
    </button>
  );
}
