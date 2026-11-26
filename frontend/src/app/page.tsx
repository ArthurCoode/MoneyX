import type { Metadata } from "next";
import Link from "next/link";
import { cookies } from "next/headers";
import { HeroVisual } from "@/components/landing/HeroVisual";
import { ScrollReveal } from "@/components/landing/ScrollReveal";
import { StatusBadge } from "@/components/payment/StatusBadge";
import { SESSION_COOKIE } from "@/lib/session-cookie";
import styles from "@/components/landing/landing.module.css";

export const metadata: Metadata = {
  title: "MoneyX — Pix para o seu negócio",
  description: "Cobranças Pix por API, webhooks assinados e conciliação automática com o provedor.",
};

// Endereço PÚBLICO da API (documentação Swagger). Server-side: não vai para o bundle do navegador.
const publicApiUrl = process.env.MONEYX_PUBLIC_API_URL ?? "http://localhost:8080";

const STEPS = [
  {
    title: "Crie a cobrança",
    text: "Seu servidor chama POST /v1/payments com o valor em centavos e uma Idempotency-Key. Repetir a chamada não duplica a cobrança.",
  },
  {
    title: "O cliente paga o Pix",
    text: "Você recebe o código copia e cola (BR Code) para mostrar no checkout. O MoneyX acompanha o pagamento junto ao provedor.",
  },
  {
    title: "Seu sistema é avisado",
    text: "Um webhook assinado com HMAC chega ao seu endpoint. Se ele estiver fora do ar, o MoneyX tenta de novo por até 3 dias.",
  },
];

const FEATURES = [
  { title: "Idempotência de verdade", text: "Mesma chave, mesmo pagamento: cliques duplos e quedas de rede não viram cobrança em dobro." },
  { title: "Webhooks assinados", text: "Assinatura HMAC com carimbo de tempo, reenvio com espera crescente e histórico de cada entrega." },
  { title: "Conciliação automática", text: "Rotinas comparam o estado de cada cobrança com o provedor e corrigem divergências sozinhas." },
  { title: "Limites de volume", text: "Teto diário e mensal por conta, no horário de Brasília, para conter abuso e erro de integração." },
  { title: "Chaves isoladas", text: "Chaves de teste e de produção separadas, até 5 ativas, guardadas só como hash. Revogue quando quiser." },
  { title: "Modo de teste", text: "Cobranças simuladas com o formato real do Pix: integre e teste o fluxo inteiro sem dinheiro de verdade." },
];

const CODE = `curl ${publicApiUrl}/v1/payments \\
  -H "Authorization: Bearer mx_test_..." \\
  -H "Idempotency-Key: pedido-1042" \\
  -H "Content-Type: application/json" \\
  -d '{ "amount": 8990, "currency": "BRL",
        "description": "Bolo de aniversário" }'`;

export default async function Home() {
  const signedIn = (await cookies()).has(SESSION_COOKIE);

  return (
    <div className={styles.page}>
      <a href="#conteudo" className={styles.skip}>
        Pular para o conteúdo
      </a>
      <header className={styles.header}>
        <Link href="/" className={styles.brand}>
          Money<span>X</span>
        </Link>
        <nav aria-label="Principal" className={styles.headerNav}>
          <a href="#como-funciona">Como funciona</a>
          <a href="#recursos">Recursos</a>
          <a href={`${publicApiUrl}/swagger-ui.html`}>API</a>
        </nav>
        <div className={styles.headerActions}>
          {signedIn ? (
            <Link href="/painel" className={styles.ctaSmall}>
              Ir para o painel
            </Link>
          ) : (
            <>
              <Link href="/entrar" className={styles.linkQuiet}>
                Entrar
              </Link>
              <Link href="/cadastro" className={styles.ctaSmall}>
                Criar conta
              </Link>
            </>
          )}
        </div>
      </header>

      <main id="conteudo">
        <section className={styles.hero} aria-labelledby="hero-title">
          <HeroVisual />
          <div className={styles.heroCopy}>
            <p className={styles.eyebrow}>
              <span className={styles.dot} aria-hidden="true" />
              Pagamentos Pix por API
            </p>
            <h1 id="hero-title" className={styles.title}>
              Pix para o seu negócio, <span className={styles.gradient}>sem atrito.</span>
            </h1>
            <p className={styles.lead}>
              Crie cobranças com uma chamada, receba avisos assinados quando forem pagas e deixe a conciliação com a
              gente.
            </p>
            <div className={styles.heroActions}>
              <Link href={signedIn ? "/painel" : "/cadastro"} className={styles.cta}>
                {signedIn ? "Ir para o painel" : "Começar no modo de teste"}
              </Link>
              <a href="#como-funciona" className={styles.ctaGhost}>
                Ver como funciona
              </a>
            </div>
            <div className={styles.statusRow} aria-label="Estados de uma cobrança">
              <StatusBadge status="PENDING" />
              <span aria-hidden="true" className={styles.arrow}>
                →
              </span>
              <StatusBadge status="PAID" />
            </div>
          </div>
        </section>

        <section id="como-funciona" className={styles.section} aria-labelledby="how-title">
          <h2 id="how-title" className={styles.sectionTitle} data-reveal>
            Como funciona
          </h2>
          <ol className={styles.steps}>
            {STEPS.map((step, index) => (
              <li key={step.title} className={styles.step} data-reveal>
                <div className={styles.cardInner}>
                  <span className={styles.stepNumber} aria-hidden="true">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <h3>{step.title}</h3>
                  <p>{step.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className={`${styles.section} ${styles.split}`} aria-labelledby="code-title">
          <div data-reveal>
            <h2 id="code-title" className={styles.sectionTitle}>
              Uma chamada para cobrar
            </h2>
            <p className={styles.sectionLead}>
              API REST com erros no formato Problem Details, paginação por cursor e documentação OpenAPI. Valores sempre
              em centavos, nunca em ponto flutuante.
            </p>
            <a href={`${publicApiUrl}/swagger-ui.html`} className={styles.textLink}>
              Abrir a documentação da API →
            </a>
          </div>
          <pre className={styles.code} data-reveal aria-label="Exemplo de chamada para criar uma cobrança">
            <code>{CODE}</code>
          </pre>
        </section>

        <section id="recursos" className={styles.section} aria-labelledby="features-title">
          <h2 id="features-title" className={styles.sectionTitle} data-reveal>
            Feito para não perder dinheiro
          </h2>
          <ul className={styles.features}>
            {FEATURES.map((feature) => (
              <li key={feature.title} className={styles.feature} data-reveal>
                <div className={styles.cardInner}>
                  <h3>{feature.title}</h3>
                  <p>{feature.text}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>

        <section className={`${styles.section} ${styles.final}`} aria-labelledby="final-title" data-reveal>
          <h2 id="final-title" className={styles.sectionTitle}>
            Comece agora, sem custo
          </h2>
          <p className={styles.sectionLead}>
            O modo de teste usa cobranças simuladas: nenhum dinheiro de verdade circula até você ativar a produção.
          </p>
          <Link href={signedIn ? "/painel" : "/cadastro"} className={styles.cta}>
            {signedIn ? "Ir para o painel" : "Criar conta de teste"}
          </Link>
        </section>
      </main>

      <footer className={styles.footer}>
        <span>
          Money<span className={styles.footerX}>X</span>
        </span>
        <span>Infraestrutura de pagamentos Pix.</span>
      </footer>
      <ScrollReveal />
    </div>
  );
}
