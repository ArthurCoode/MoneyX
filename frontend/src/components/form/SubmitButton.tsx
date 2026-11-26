"use client";

import { useFormStatus } from "react-dom";
import styles from "./Form.module.css";

/** Botão que se desliga enquanto o formulário é enviado (evita envio duplo e mostra que algo está acontecendo). */
export function SubmitButton({ children, pendingLabel }: { children: React.ReactNode; pendingLabel: string }) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" className={styles.button} disabled={pending} aria-disabled={pending}>
      {pending ? pendingLabel : children}
    </button>
  );
}
