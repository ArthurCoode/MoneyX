import { CopyButton } from "./CopyButton";
import styles from "./dashboard.module.css";

/**
 * Segredo recém-criado (API Key ou segredo de assinatura de webhook). Aparece UMA vez: a API só guarda o hash e
 * nunca devolve o valor de novo. Não é gravado no navegador.
 */
export function SecretReveal({ title, secret, children }: { title: string; secret: string; children?: React.ReactNode }) {
  return (
    <div className={styles.secretBox} role="status">
      <strong>{title}</strong>
      <p className={styles.muted}>
        Copie e guarde agora em um lugar seguro (ex.: variável de ambiente do seu servidor). Por segurança, ele não
        será mostrado de novo.
      </p>
      <code className={styles.secretValue} aria-label={title}>
        {secret}
      </code>
      <div className={styles.row}>
        <CopyButton value={secret} />
      </div>
      {children}
    </div>
  );
}
