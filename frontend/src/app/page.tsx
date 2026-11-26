import { StatusBadge } from "@/components/payment/StatusBadge";
import styles from "./page.module.css";

/** Página provisória da Etapa 8a: confirma paleta, fontes e componentes base. A landing com 3D vem na Etapa 8c. */
export default function Home() {
  return (
    <main className={styles.main}>
      <p className={styles.eyebrow}>MoneyX</p>
      <h1 className={styles.title}>
        Pix para o seu negócio, <span className={styles.gradient}>sem atrito.</span>
      </h1>
      <p className={styles.lead}>API, webhooks assinados e conciliação automática com o PSP.</p>
      <div className={styles.badges} aria-label="Estados de um pagamento">
        <StatusBadge status="PENDING" />
        <StatusBadge status="PAID" />
        <StatusBadge status="EXPIRED" />
      </div>
      <code className={styles.code}>POST /v1/payments</code>
    </main>
  );
}
