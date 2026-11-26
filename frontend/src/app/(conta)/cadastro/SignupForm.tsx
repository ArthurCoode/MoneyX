"use client";

import Link from "next/link";
import { useActionState } from "react";
import { signup, type SignupState } from "@/app/actions/auth";
import { Alert } from "@/components/form/Alert";
import { Field } from "@/components/form/Field";
import { SubmitButton } from "@/components/form/SubmitButton";
import formStyles from "@/components/form/Form.module.css";
import styles from "../conta.module.css";

export function SignupForm() {
  const [state, action] = useActionState<SignupState, FormData>(signup, {});

  if (state.createdKey !== undefined || state.signedIn !== undefined) {
    return <AccountCreated apiKey={state.createdKey} signedIn={Boolean(state.signedIn)} />;
  }

  return (
    <form action={action} className={formStyles.form} noValidate>
      {state.error && <Alert kind="error">{state.error}</Alert>}
      <Field
        name="legalName"
        label="Razão social ou nome"
        autoComplete="organization"
        required
        defaultValue={state.values?.legalName}
        error={state.fieldErrors?.legalName}
      />
      <Field
        name="document"
        label="CPF ou CNPJ"
        inputMode="numeric"
        required
        defaultValue={state.values?.document}
        error={state.fieldErrors?.document}
      />
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
        autoComplete="new-password"
        hint="De 12 a 128 caracteres. Uma frase longa é mais segura que símbolos."
        required
        error={state.fieldErrors?.password}
      />
      <Field
        name="passwordConfirmation"
        label="Confirme a senha"
        type="password"
        autoComplete="new-password"
        required
        error={state.fieldErrors?.passwordConfirmation}
      />
      <SubmitButton pendingLabel="Criando conta…">Criar conta</SubmitButton>
    </form>
  );
}

/** Mostra a chave de teste criada no cadastro UMA única vez; ela não volta a aparecer em lugar nenhum. */
function AccountCreated({ apiKey, signedIn }: { apiKey?: string; signedIn: boolean }) {
  return (
    <div className={styles.secret}>
      <Alert kind="success">Conta criada. Enviamos um link de confirmação para o seu e-mail.</Alert>
      {apiKey && (
        <>
          <p className={styles.lead}>
            Esta é a sua <strong>chave de API de teste</strong>. Guarde-a agora em um lugar seguro: por segurança, ela
            não será mostrada de novo.
          </p>
          <code className={styles.secretValue} aria-label="Chave de API de teste">
            {apiKey}
          </code>
        </>
      )}
      <Link href={signedIn ? "/painel" : "/entrar"} className={styles.primaryLink}>
        {signedIn ? "Ir para o painel" : "Entrar"}
      </Link>
    </div>
  );
}
