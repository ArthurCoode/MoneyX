package com.moneyx.docs;

/**
 * Textos longos da documentação (Markdown, renderizado pela Swagger UI). Ficam aqui para não poluir o OpenApiConfig.
 */
final class DocsText {

    private DocsText() {
    }

    static final String INTRODUCTION = """
            Infraestrutura de pagamentos Pix do MoneyX. **Ambiente sandbox**: dados de teste, nenhum dinheiro real.

            ## Autenticação
            Cadastre-se em `POST /v1/merchants`: a resposta traz a primeira API Key **uma única vez**. Envie-a em todas
            as outras chamadas no header `Authorization: Bearer mx_test_...`. O comerciante é sempre identificado pela
            chave, nunca por um id na URL.

            ## Valores
            Sempre em **centavos** (inteiro): R$ 49,90 = `4990`. Decimais são recusados.

            ## Idempotência
            `POST /v1/payments` exige o header `Idempotency-Key` (1 a 255 caracteres ASCII visíveis, válida por 24 h).
            Repetir a mesma chave com o mesmo corpo devolve o pagamento original com `Idempotent-Replayed: true`;
            com outro corpo, `422 idempotency_key_reused`. Gere uma chave nova (ex.: UUID) por pagamento e reutilize-a
            apenas para repetir a MESMA requisição (ex.: depois de um timeout).

            ## Paginação
            Listagens usam cursor: `?limit=10&startingAfter=<id do último item da página anterior>`.
            A resposta é `{"data": [...], "hasMore": true}`.

            ## Erros
            Formato Problem Details (RFC 9457, `application/problem+json`). Use o campo `code` (estável) para decidir
            o que fazer; `detail` é para humanos e pode mudar. Recurso de outro comerciante responde 404, igual a um
            recurso inexistente.

            ## Limites
            O cadastro público aceita 5 tentativas por IP em rajada e recupera 1 a cada 2 minutos. Acima disso:
            `429 rate_limit_exceeded` com `Retry-After` (segundos).

            ## Webhooks
            Cadastre uma URL em `POST /v1/webhook-endpoints` para receber os eventos (seção **Webhooks** deste documento).
            Cada envio é assinado com o segredo do endpoint (`whsec_...`, mostrado uma única vez no cadastro).
            A entrega é "pelo menos uma vez": descarte eventos repetidos pelo `id`.
            """;

    static final String WEBHOOK_VERIFICATION = """
            Enviado por `POST` para cada endpoint ativo que assina este tipo de evento. Responda **2xx** em até 10 s;
            qualquer outra resposta gera nova tentativa (1 min, 5 min, 30 min, 2 h, 5 h, 10 h, 24 h, 24 h). Depois de
            3 dias só com falhas o endpoint é desativado; reative com `POST /v1/webhook-endpoints/{id}/enable`.

            ### Como verificar a assinatura
            Header: `MoneyX-Signature: t=1763650000,v1=5f2b9c...`

            1. Separe `t` (timestamp em segundos) e `v1` (assinatura em hexadecimal).
            2. Recuse se `|agora - t|` for maior que 5 minutos (proteção contra replay).
            3. Calcule `HMAC-SHA256(segredo, t + "." + corpo)` sobre os **bytes recebidos**, sem reformatar o JSON.
            4. Compare com `v1` em **tempo constante**.
            5. Descarte o evento se o `id` já foi processado.

            ```java
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            mac.update((t + ".").getBytes(UTF_8));
            byte[] expected = mac.doFinal(rawBody);
            boolean valid = MessageDigest.isEqual(expected, HexFormat.of().parseHex(v1));
            ```
            """;
}
