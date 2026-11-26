import styles from "./Form.module.css";

/**
 * Mensagem do formulário. Erro usa `role="alert"` (o leitor de tela lê na hora); sucesso usa `role="status"`.
 * O tipo aparece em texto ("Erro:"/"Pronto:") e não só pela cor.
 */
export function Alert({ kind, children }: { kind: "error" | "success"; children: React.ReactNode }) {
  const isError = kind === "error";
  return (
    <div className={isError ? styles.alertError : styles.alertSuccess} role={isError ? "alert" : "status"}>
      <strong>{isError ? "Erro:" : "Pronto:"}</strong>
      <span>{children}</span>
    </div>
  );
}
