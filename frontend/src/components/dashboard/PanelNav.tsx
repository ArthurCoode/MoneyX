"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import styles from "./dashboard.module.css";

const LINKS = [
  { href: "/painel", label: "Visão geral", exact: true },
  { href: "/painel/pagamentos", label: "Pagamentos" },
  { href: "/painel/eventos", label: "Eventos" },
  { href: "/painel/webhooks", label: "Webhooks" },
  { href: "/painel/api-keys", label: "API Keys" },
];

/** Navegação do painel. A página atual é marcada com `aria-current` (o leitor de tela anuncia "página atual"). */
export function PanelNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="Painel" className={styles.nav}>
      {LINKS.map(({ href, label, exact }) => {
        const active = exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link key={href} href={href} className={styles.navLink} aria-current={active ? "page" : undefined}>
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
