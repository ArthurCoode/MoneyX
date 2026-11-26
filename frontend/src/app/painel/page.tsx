import type { Metadata } from "next";
import { requireMerchant } from "@/lib/server/merchant";
import styles from "./painel.module.css";

export const metadata: Metadata = { title: "Painel — MoneyX" };

export default async function PanelHome() {
  const { merchant } = await requireMerchant();

  return (
    <section className={styles.section}>
      <h1 className={styles.title}>Olá, {merchant.legalName}</h1>
      <dl className={styles.facts}>
        <div>
          <dt>E-mail</dt>
          <dd>
            {merchant.email} {merchant.emailVerifiedAt ? "(confirmado)" : "(aguardando confirmação)"}
          </dd>
        </div>
        <div>
          <dt>Situação da conta</dt>
          <dd>{merchant.status === "ACTIVE" ? "Ativa" : merchant.status}</dd>
        </div>
      </dl>
    </section>
  );
}
