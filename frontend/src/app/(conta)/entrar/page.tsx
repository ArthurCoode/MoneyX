import type { Metadata } from "next";
import Link from "next/link";
import { Alert } from "@/components/form/Alert";
import { LoginForm } from "./LoginForm";
import styles from "../conta.module.css";

export const metadata: Metadata = { title: "Entrar — MoneyX" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const NOTICES: Record<string, string> = {
  redefinida: "Senha redefinida. Entre com a nova senha.",
  saiu: "Você saiu da sua conta.",
  expirada: "Sua sessão terminou. Entre de novo para continuar.",
};

export default async function LoginPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const notice =
    (params.senha === "redefinida" && NOTICES.redefinida) ||
    (params.saiu && NOTICES.saiu) ||
    (params.sessao === "expirada" && NOTICES.expirada) ||
    undefined;
  const next = typeof params.proximo === "string" ? params.proximo : undefined;

  return (
    <>
      <header>
        <h1 className={styles.title}>Entrar</h1>
        <p className={styles.lead}>Acesse o painel da sua conta MoneyX.</p>
      </header>
      {notice && <Alert kind="success">{notice}</Alert>}
      <LoginForm next={next} />
      <p className={styles.footer}>
        Ainda não tem conta? <Link href="/cadastro">Criar conta</Link>
      </p>
    </>
  );
}
