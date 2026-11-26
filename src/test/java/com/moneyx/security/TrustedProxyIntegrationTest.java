package com.moneyx.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-Forwarded-For / X-Forwarded-Proto num Tomcat REAL. O efeito observável do IP é o rate limit do cadastro público
 * (5 tentativas por IP; nos outros testes a capacidade é enorme); o do protocolo é o HSTS (o Spring Security só o
 * envia em HTTPS).
 */
class TrustedProxyIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Padrão da aplicação: nenhum proxy confiável. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"management.server.port=0", "moneyx.rate-limit.signup.capacity=5"})
    @AutoConfigureMetrics
    class WithoutTrustedProxies {

        @LocalServerPort
        int port;

        @Test
        void forwardedHeadersFromAnUntrustedAddressAreIgnored() throws Exception {
            // Cada tentativa finge vir de outro IP: se o XFF valesse, nunca haveria 429.
            List<Integer> statuses = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                statuses.add(signup(port, randomIp(), "https").statusCode());
            }
            assertThat(statuses).contains(429);
            assertThat(signup(port, randomIp(), "https").headers().firstValue("Strict-Transport-Security")).isEmpty();
        }
    }

    /** Proxy confiável = o próprio teste (loopback). */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"management.server.port=0", "moneyx.rate-limit.signup.capacity=5",
                    "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1|0:0:0:0:0:0:0:1"})
    @AutoConfigureMetrics
    class WithATrustedProxy {

        @LocalServerPort
        int port;

        @Test
        void theClientIpComesFromXForwardedForSoEachRealClientHasItsOwnLimit() throws Exception {
            for (int i = 0; i < 6; i++) {
                assertThat(signup(port, randomIp(), null).statusCode()).isNotEqualTo(429);
            }
            String sameClient = randomIp();
            List<Integer> statuses = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                statuses.add(signup(port, sameClient, null).statusCode());
            }
            assertThat(statuses).containsOnlyOnce(429).last().isEqualTo(429);
        }

        @Test
        void httpsAnnouncedByTheProxyEnablesHsts() throws Exception {
            HttpResponse<String> https = signup(port, randomIp(), "https");
            assertThat(https.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
                    value -> assertThat(value).contains("max-age="));
            assertThat(signup(port, randomIp(), "http").headers().firstValue("Strict-Transport-Security")).isEmpty();
        }
    }

    /** Cadastro com corpo inválido (400): conta para o rate limit sem criar nada no banco. */
    private static HttpResponse<String> signup(int port, String forwardedFor, String forwardedProto) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/merchants"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("{}"));
        if (forwardedProto != null) {
            request.header("X-Forwarded-Proto", forwardedProto);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** IP fictício diferente a cada chamada (16 milhões de combinações: repetição desprezível). */
    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }
}
