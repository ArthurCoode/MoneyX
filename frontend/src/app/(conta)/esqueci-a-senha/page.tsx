import type { Metadata } from "next";
import Link from "next/link";
import { ForgotPasswordForm } from "./ForgotPasswordForm";
import styles from "../conta.module.css";

export const metadata: Metadata = { title: "Esqueci a senha — MoneyX" };

export default function ForgotPasswordPage() {
  return (
    <>
      <header>
        <h1 className={styles.title}>Esqueci a senha</h1>
        <p className={styles.lead}>Informe o e-mail da conta. Enviaremos um link para criar uma nova senha.</p>
      </header>
      <ForgotPasswordForm />
      <p className={styles.footer}>
        <Link href="/entrar">Voltar para entrar</Link>
      </p>
    </>
  );
}
