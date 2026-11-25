package com.moneyx.docs;

/**
 * Textos da documentação (Markdown renderizado pela Swagger UI). Curtos de propósito: o topo da página é um guia
 * rápido; os detalhes ficam em cada operação (que aparece recolhida até alguém clicar nela).
 */
final class DocsText {

    private DocsText() {
    }

    static final String INTRODUCTION = """
            API de pagamentos Pix do **MoneyX**, em ambiente **sandbox**: dados de teste, nenhum dinheiro real.

            #### Primeiros passos
            1. **Cadastre-se** em `POST /v1/merchants` e guarde a API Key da resposta. Ela aparece uma única vez.
            2. Clique em **Authorize** e cole a chave (`mx_test_...`).
            3. **Crie um pagamento** em `POST /v1/payments` e mostre o `pix.copyPaste` ao pagador.
            4. **Simule o pagamento** em `POST /v1/sandbox/payments/{id}/pay`.
            5. Para **receber avisos**, cadastre uma URL em `POST /v1/webhook-endpoints` (veja *Webhooks*, no fim da página).

            #### Regras gerais
            | Tema | Como funciona |
            |---|---|
            | Valores | Sempre em **centavos**: R$ 49,90 = `4990` |
            | Idempotência | `POST /v1/payments` exige o header `Idempotency-Key`. Repetir a mesma chave devolve o mesmo pagamento |
            | Status | `CREATING` (cobrança sendo emitida pelo provedor; `pix` nulo) → `PENDING` → `PAID`, `CANCELED` ou `EXPIRED`. Provedor fora do ar: **503** e o pagamento fica `CREATING`; repita com a mesma chave |
            | Paginação | `?limit=10&startingAfter=<id>` e a resposta traz `data` + `hasMore` |
            | Erros | Formato Problem Details. Decida pelo campo `code`; o `detail` é só para leitura |
            | Isolamento | Recurso de outro comerciante responde **404**, como se não existisse |
            | Retenção | Eventos e entregas de webhook ficam disponíveis por **30 dias**; depois, **404**. Pagamentos não expiram da API |
            | Limite | Cadastro: 5 tentativas por IP e +1 a cada 2 min. Acima disso, **429** com `Retry-After` |
            | Volume | Pagamentos criados e não cancelados somam no máximo **R$ 200 mil por dia** e **R$ 2 milhões por mês** (horário de Brasília). Acima disso, **422** `volume_limit_exceeded` |
            | Suporte | Toda resposta traz o header `X-Request-Id` (e os erros, o campo `requestId`). Informe-o ao suporte. Você pode enviar o seu próprio (até 64 caracteres `A-Z a-z 0-9 . _ -`) |
            """;

    /** Comum aos 4 webhooks; cada um recebe antes uma frase própria (quando é enviado). */
    static final String WEBHOOK_DETAILS = """
            Responda **2xx** em até 10 s. Qualquer outra resposta gera nova tentativa (1 min, 5 min, 30 min, 2 h, 5 h,
            10 h, 24 h, 24 h). Depois de 3 dias só com falhas, o endpoint é desativado.

            **Como verificar a assinatura** (`MoneyX-Signature: t=1763650000,v1=5f2b9c...`)
            1. Recuse se `t` tiver mais de 5 minutos de diferença do seu relógio.
            2. Calcule `HMAC-SHA256(segredo, t + "." + corpo)` sobre os **bytes recebidos**.
            3. Compare com `v1` em tempo constante.
            4. Descarte eventos cujo `id` você já processou.

            ```java
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            mac.update((t + ".").getBytes(UTF_8));
            boolean valid = MessageDigest.isEqual(mac.doFinal(rawBody), HexFormat.of().parseHex(v1));
            ```
            """;
}
