import type { Metadata } from "next";
import { headers } from "next/headers";
import { Inter, JetBrains_Mono, Space_Grotesk } from "next/font/google";
import "@/styles/globals.css";

// Fontes baixadas no BUILD e servidas pelo próprio site (o navegador do visitante não fala com o Google).
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });
const spaceGrotesk = Space_Grotesk({ subsets: ["latin"], variable: "--font-space-grotesk", display: "swap" });
const jetbrainsMono = JetBrains_Mono({ subsets: ["latin"], variable: "--font-jetbrains-mono", display: "swap" });

export const metadata: Metadata = {
  title: "MoneyX — Pix para o seu negócio",
  description: "Infraestrutura de pagamentos Pix: API, webhooks assinados e conciliação automática.",
};

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  // CSP com nonce (proxy.ts) exige renderizar cada página NA HORA: uma página pré-gerada no build não teria o nonce
  // desta requisição e o navegador bloquearia os scripts. Ler os headers deixa todas as rotas dinâmicas.
  await headers();
  return (
    <html lang="pt-BR" className={`${inter.variable} ${spaceGrotesk.variable} ${jetbrainsMono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
