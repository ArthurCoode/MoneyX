"use client";

import { useState } from "react";
import styles from "./dashboard.module.css";

/** Copia um texto para a área de transferência e confirma em texto (anunciado pelo leitor de tela). */
export function CopyButton({ value, label = "Copiar" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  return (
    <button
      type="button"
      className={styles.secondary}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        } catch {
          setCopied(false);
        }
      }}
    >
      <span aria-live="polite">{copied ? "Copiado!" : label}</span>
    </button>
  );
}
