import type { Metadata } from "next";
import Link from "next/link";
import { SignupForm } from "./SignupForm";
import styles from "../conta.module.css";

export const metadata: Metadata = { title: "Criar conta — MoneyX" };

export default function SignupPage() {
  return (
    <>
      <header>
        <h1 className={styles.title}>Criar conta</h1>
        <p className={styles.lead}>Comece no modo de teste: cobranças Pix simuladas, sem dinheiro de verdade.</p>
      </header>
      <SignupForm />
      <p className={styles.footer}>
        Já tem conta? <Link href="/entrar">Entrar</Link>
      </p>
    </>
  );
}
