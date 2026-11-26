import Link from "next/link";
import styles from "./conta.module.css";

/** Moldura das telas de conta (entrar, cadastro, senha): marca no topo e um cartão central. */
export default function AccountLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className={styles.page}>
      <Link href="/" className={styles.brand}>
        Money<span>X</span>
      </Link>
      <main className={styles.card}>{children}</main>
    </div>
  );
}
