package com.moneyx.docs;

import com.moneyx.event.domain.EventType;
import com.moneyx.webhook.domain.WebhookSignature;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Contrato OpenAPI da API (gerado a partir do código pelo springdoc e completado aqui).
 *
 * Aqui fica o que é COMUM a toda a API, para não repetir em cada controller:
 * - informações gerais e o guia de integração (autenticação, idempotência, paginação, erros, limites, webhooks);
 * - o esquema de autenticação (API Key no header Authorization: Bearer);
 * - o schema de erro (Problem Details, RFC 9457) e as respostas de erro reutilizáveis;
 * - 401/403 em toda rota protegida e 429 no cadastro público (OpenApiCustomizer);
 * - a seção "webhooks" do OpenAPI 3.1: o que o MoneyX ENVIA para a loja.
 *
 * Os detalhes de cada rota (resumo, descrição, erros específicos) ficam nos próprios controllers.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";
    static final String PROBLEM = "Problem";
    static final String PROBLEM_JSON = "application/problem+json";

    private static final java.util.Map<EventType, String> WEBHOOK_SUMMARIES = java.util.Map.of(
            EventType.PAYMENT_CREATED, "Enviado quando um pagamento é criado (PENDING)",
            EventType.PAYMENT_PAID, "Enviado quando o Pix é pago (PAID)",
            EventType.PAYMENT_CANCELED, "Enviado quando o comerciante cancela o pagamento (CANCELED)",
            EventType.PAYMENT_EXPIRED, "Enviado quando o prazo termina sem pagamento (EXPIRED)");

    @Bean
    public OpenAPI moneyxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MoneyX API")
                        .version("v1")
                        .description(DocsText.INTRODUCTION))
                // URL fixa: o contrato não muda conforme quem o pede (e o teste de contrato fica estável).
                .servers(List.of(new Server().url("http://localhost:8080").description("Sandbox local")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .tags(List.of(
                        new Tag().name("Merchants").description("Cadastro e consulta do próprio comerciante"),
                        new Tag().name("API Keys").description("Chaves de acesso do comerciante"),
                        new Tag().name("Payments").description("Cobranças Pix: criação, consulta, listagem e cancelamento"),
                        new Tag().name("Sandbox").description("Simulações que, em produção, acontecem fora do MoneyX"),
                        new Tag().name("Events").description("Histórico de eventos (o mesmo conteúdo enviado por webhook)"),
                        new Tag().name("Webhook Endpoints").description("Para onde o MoneyX envia os eventos"),
                        new Tag().name("Webhook Deliveries").description("Entregas de webhook: depuração e reenvio")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API Key (mx_test_...)")
                                .description("Envie a API Key no header `Authorization: Bearer mx_test_...`."))
                        .addSchemas(PROBLEM, problemSchema())
                        .addResponses("BadRequest", problem("Requisição inválida (campos, parâmetros ou JSON)."))
                        .addResponses("Unauthorized", problem("API Key ausente, inválida ou revogada.")
                                .addHeaderObject("WWW-Authenticate", new Header().schema(new StringSchema()._default("Bearer"))))
                        .addResponses("Forbidden", problem("Proibido: comerciante suspenso ou encerrado, ou operação não permitida para esta chave/conta (ver `code`, ex.: `email_not_verified`, `sandbox_requires_test_key`)."))
                        .addResponses("NotFound", problem("Recurso inexistente ou de outro comerciante (indistinguíveis de propósito)."))
                        .addResponses("Conflict", problem("Regra de negócio violada; veja `code`."))
                        .addResponses("UnprocessableContent", problem("Idempotency-Key já usada com outro corpo (`idempotency_key_reused`) ou limite de volume do dia/mês atingido (`volume_limit_exceeded`, com `period`, `limit`, `remaining` e `resetsAt`)."))
                        .addResponses("TooManyRequests", problem("Limite de tentativas atingido.")
                                .addHeaderObject("Retry-After", new Header()
                                        .description("Segundos até poder tentar de novo.")
                                        .schema(new IntegerSchema())))
                        .addResponses("BadGateway", problem("O provedor de Pix recusou a cobrança (`payment_provider_error`). "
                                + "Nada foi criado e a Idempotency-Key ficou livre."))
                        .addResponses("ServiceUnavailable", problem("O provedor de Pix está fora do ar "
                                + "(`payment_provider_unavailable`). O pagamento existe com status `CREATING`: repita com a "
                                + "MESMA Idempotency-Key (ou consulte depois) para recebê-lo pronto.")
                                .addHeaderObject("Retry-After", new Header()
                                        .description("Segundos até tentar de novo.")
                                        .schema(new IntegerSchema()))));
    }

    /**
     * Ajustes que valem para todas as rotas:
     * - o cadastro público não exige API Key (e recebe 429 no limite);
     * - toda rota protegida pode responder 401 e 403.
     */
    @Bean
    public OpenApiCustomizer moneyxCommonResponses() {
        return openApi -> {
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                ApiResponses responses = operation.getResponses();
                boolean publicSignup = path.equals("/v1/merchants") && method == PathItem.HttpMethod.POST;
                if (publicSignup) {
                    operation.setSecurity(List.of());
                    responses.addApiResponse("429", ref("TooManyRequests"));
                } else {
                    responses.addApiResponse("401", ref("Unauthorized"));
                    responses.addApiResponse("403", ref("Forbidden"));
                }
            }));
            sortPaths(openApi);
            addWebhooks(openApi);
        };
    }

    /**
     * Ordem previsível na Swagger UI: caminhos em ordem alfabética (/x, /x/{id}, /x/{id}/acao). Dentro de cada caminho
     * os métodos seguem a ordem padrão do OpenAPI (GET, PUT, POST, DELETE). Sem isso, a ordem dependeria da ordem
     * interna em que o springdoc encontra os métodos, que não é garantida.
     */
    private static void sortPaths(OpenAPI openApi) {
        io.swagger.v3.oas.models.Paths sorted = new io.swagger.v3.oas.models.Paths();
        openApi.getPaths().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> sorted.addPathItem(entry.getKey(), entry.getValue()));
        openApi.setPaths(sorted);
    }

    // ---------- webhooks (OpenAPI 3.1) ----------

    private static void addWebhooks(OpenAPI openApi) {
        Schema<?> envelope = new ObjectSchema()
                .description("Envelope do evento. Idêntico a GET /v1/events/{id}.")
                .addProperty("id", new StringSchema().format("uuid").description("Use para descartar repetições."))
                .addProperty("type", new StringSchema()._enum(Arrays.stream(EventType.values()).map(EventType::value).toList()))
                .addProperty("createdAt", new StringSchema().format("date-time"))
                .addProperty("data", new ObjectSchema().description("Retrato do pagamento no momento do evento (mesmo formato de GET /v1/payments/{id})."));
        openApi.getComponents().addSchemas("Event", envelope);

        // Na ordem do ciclo de vida (a ordem do enum), não em ordem alfabética.
        for (EventType type : EventType.values()) {
            Operation operation = new Operation()
                    .operationId(webhookOperationId(type))
                    .summary(WEBHOOK_SUMMARIES.get(type))
                    .description(WEBHOOK_SUMMARIES.get(type) + ".\n\n" + DocsText.WEBHOOK_DETAILS)
                    .tags(List.of("Webhook Endpoints"))
                    .security(List.of())
                    .addParametersItem(header(WebhookSignature.HEADER, "t=<timestamp>,v1=<HMAC-SHA256 em hexadecimal>", true))
                    .addParametersItem(header("MoneyX-Event-Id", "Id do evento (mesmo valor de `id` no corpo).", true))
                    .addParametersItem(header("MoneyX-Event-Type", type.value(), true))
                    .requestBody(new RequestBody().required(true).content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/Event")))))
                    .responses(new ApiResponses()
                            .addApiResponse("2XX", new ApiResponse().description("Recebido. Qualquer outra resposta (ou timeout de 10 s) gera nova tentativa."))
                            .addApiResponse("default", new ApiResponse().description("Falha: nova tentativa com backoff exponencial, por até ~2,7 dias.")));
            openApi.addWebhooks(type.value(), new PathItem().post(operation));
        }
    }

    // ---------- auxiliares ----------

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("Erro no formato Problem Details (RFC 9457).")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Conflict"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().description("Mensagem para humanos (pode mudar)."))
                .addProperty("instance", new StringSchema().example("/v1/payments/…/cancel"))
                .addProperty("code", new StringSchema().description("Código estável para máquinas, ex.: `payment_not_pending`."))
                .addProperty("requestId", new StringSchema().description("Id da requisição (igual ao header `X-Request-Id`). Informe-o ao suporte."))
                .addProperty("errors", new ArraySchema()
                        .description("Somente em erros de validação.")
                        .items(new ObjectSchema()
                                .addProperty("field", new StringSchema())
                                .addProperty("message", new StringSchema())));
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_JSON,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
    }

    private static ApiResponse ref(String name) {
        return new ApiResponse().$ref("#/components/responses/" + name);
    }

    /** payment.paid -> paymentPaidWebhook (nome usado por geradores de cliente e pelos links da Swagger UI). */
    private static String webhookOperationId(EventType type) {
        String[] parts = type.value().split("\\.");
        StringBuilder id = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            id.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return id.append("Webhook").toString();
    }

    private static HeaderParameter header(String name, String description, boolean required) {
        HeaderParameter parameter = new HeaderParameter();
        parameter.setName(name);
        parameter.setDescription(description);
        parameter.setRequired(required);
        parameter.setSchema(new StringSchema());
        return parameter;
    }
}
