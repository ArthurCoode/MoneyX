import Link from "next/link";
import { logout } from "@/app/actions/auth";
import { requireMerchant } from "@/lib/server/merchant";
import styles from "./painel.module.css";

export default async function PanelLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const { merchant } = await requireMerchant();

  return (
    <div className={styles.shell}>
      <header className={styles.topbar}>
        <Link href="/painel" className={styles.brand}>
          Money<span>X</span>
        </Link>
        <span className={styles.mode}>Modo de teste</span>
        <div className={styles.account}>
          <span className={styles.accountName}>{merchant.legalName}</span>
          <form action={logout}>
            <button type="submit" className={styles.logout}>
              Sair
            </button>
          </form>
        </div>
      </header>
      <main className={styles.content}>{children}</main>
    </div>
  );
}
