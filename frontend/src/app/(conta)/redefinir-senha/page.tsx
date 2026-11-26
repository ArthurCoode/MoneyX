import type { Metadata } from "next";
import Link from "next/link";
import { Alert } from "@/components/form/Alert";
import { ResetPasswordForm } from "./ResetPasswordForm";
import styles from "../conta.module.css";

export const metadata: Metadata = { title: "Nova senha — MoneyX" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/**
 * Destino do link do e-mail (`/redefinir-senha?token=...`, montado pela API com MONEYX_DASHBOARD_BASE_URL).
 * O token vai num campo oculto do formulário; a `Referrer-Policy: no-referrer` impede que ele vaze para outros sites.
 */
export default async function ResetPasswordPage({ searchParams }: { searchParams: SearchParams }) {
  const { token } = await searchParams;

  return (
    <>
      <header>
        <h1 className={styles.title}>Criar nova senha</h1>
        <p className={styles.lead}>Ao salvar, todas as sessões abertas da conta são encerradas.</p>
      </header>
      {typeof token === "string" && token.length > 0 ? (
        <ResetPasswordForm token={token} />
      ) : (
        <Alert kind="error">Este link está incompleto. Peça um novo em “Esqueci a senha”.</Alert>
      )}
      <p className={styles.footer}>
        <Link href="/esqueci-a-senha">Pedir um novo link</Link>
      </p>
    </>
  );
}
