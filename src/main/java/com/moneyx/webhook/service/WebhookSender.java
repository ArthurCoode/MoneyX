package com.moneyx.webhook.service;

import com.moneyx.webhook.domain.WebhookDeliveryError;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;

/**
 * Faz o POST do webhook. Usa o HttpClient do próprio JDK (sem dependência nova).
 *
 * - followRedirects(NEVER): um "302 Location: http://127.0.0.1" driblaria toda a proteção contra SSRF.
 *   Redirecionamento conta como falha (não é 2xx).
 * - Timeouts curtos: uma loja lenta não pode prender o job.
 * - O corpo da resposta é DESCARTADO: pode conter dados da loja que o MoneyX não deve guardar.
 *
 * Chamado FORA de transação: nenhuma conexão do banco fica presa esperando a loja.
 */
@Component
class WebhookSender {

    static final String USER_AGENT = "MoneyX-Webhooks/1.0";

    private final HttpClient client;
    private final WebhookDeliveryProperties properties;
    private final WebhookDestinationGuard guard;

    WebhookSender(WebhookDeliveryProperties properties, WebhookDestinationGuard guard) {
        this.properties = properties;
        this.guard = guard;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    record Outcome(boolean success, Integer statusCode, WebhookDeliveryError error, long durationMs) {
    }

    Outcome send(String url, byte[] body, Map<String, String> headers) {
        long start = System.nanoTime();
        URI uri = URI.create(url);

        WebhookDestinationGuard.Verdict verdict = guard.check(uri);
        if (verdict == WebhookDestinationGuard.Verdict.NOT_ALLOWED) {
            return new Outcome(false, null, WebhookDeliveryError.DESTINATION_NOT_ALLOWED, elapsedMs(start));
        }
        if (verdict == WebhookDestinationGuard.Verdict.UNRESOLVABLE) {
            return new Outcome(false, null, WebhookDeliveryError.CONNECTION_FAILED, elapsedMs(start));
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);

        try {
            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            boolean ok = status >= 200 && status < 300;
            return new Outcome(ok, status, ok ? null : WebhookDeliveryError.NON_2XX_RESPONSE, elapsedMs(start));
        } catch (HttpTimeoutException e) {
            return new Outcome(false, null, WebhookDeliveryError.TIMEOUT, elapsedMs(start));
        } catch (IOException e) {
            return new Outcome(false, null, WebhookDeliveryError.CONNECTION_FAILED, elapsedMs(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // a aplicação está encerrando: preserva o sinal de interrupção
            return new Outcome(false, null, WebhookDeliveryError.CONNECTION_FAILED, elapsedMs(start));
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
