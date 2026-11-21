package com.moneyx.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endurecimento HTTP verificado num servidor Tomcat REAL (porta aleatória), não numa simulação:
 * - um header malformado é recusado sem que o seu conteúdo (que pode ser uma API Key) vá para o log;
 * - os headers de segurança chegam nas respostas da API e da Swagger UI.
 *
 * As anotações são IGUAIS às do ManagementPortIntegrationTest de propósito: mesma configuração = mesmo contexto em
 * cache (sem abrir outro pool de conexões com o banco).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@AutoConfigureMetrics
@ExtendWith(OutputCaptureExtension.class)
class HttpHardeningIntegrationTest {

    /** Texto único com formato de API Key: se aparecer em qualquer linha de log, houve vazamento. */
    private static final String LEAK_CANARY = "mx_test_LeakCanary7f3a9c2e5b1d8f4a6c0e2b9d7f5a3c1e";

    @LocalServerPort
    private int apiPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void malformedHeaderIsRejectedWithoutLoggingItsContent(CapturedOutput output) throws Exception {
        // Linha de header sem ":" (o cliente "quebrou" o Authorization em duas linhas). Enviada por socket cru, pois
        // o HttpClient do Java se recusa a montar uma requisição inválida.
        String request = "GET /v1/merchants/me HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Authorization: Bearer\r\n"
                + LEAK_CANARY + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        String statusLine;
        try (Socket socket = new Socket("127.0.0.1", apiPort)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                    .readLine();
        }

        assertThat(statusLine).startsWith("HTTP/1.1 400");
        assertThat(output.getAll()).doesNotContain(LEAK_CANARY);
        // O Tomcat registra só a 1ª ocorrência em INFO e as seguintes em DEBUG: os DOIS níveis precisam estar cortados.
        var tomcatParserLog = LoggerFactory.getLogger("org.apache.coyote.http11.Http11Processor");
        assertThat(tomcatParserLog.isInfoEnabled()).isFalse();
        assertThat(tomcatParserLog.isDebugEnabled()).isFalse();
    }

    @Test
    void apiResponsesCarryTheStrictSecurityHeaders() throws Exception {
        // Rota protegida sem chave (401), rota pública (400 de validação) e o contrato OpenAPI (200, JSON).
        assertApiSecurityHeaders(send(HttpRequest.newBuilder(uri("/v1/payments")).GET()));
        assertApiSecurityHeaders(send(HttpRequest.newBuilder(uri("/v1/merchants"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))));
        assertApiSecurityHeaders(send(HttpRequest.newBuilder(uri("/v3/api-docs")).GET()));
    }

    @Test
    void swaggerUiGetsItsOwnContentSecurityPolicy() throws Exception {
        HttpResponse<String> page = send(HttpRequest.newBuilder(uri("/swagger-ui/index.html")).GET());

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Security-Policy"))
                .hasValue(SecurityConfig.DOCS_CONTENT_SECURITY_POLICY);
        assertThat(page.headers().allValues("Content-Security-Policy")).hasSize(1);
        assertThat(page.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(page.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    }

    private static void assertApiSecurityHeaders(HttpResponse<String> response) {
        var headers = response.headers();
        assertThat(headers.allValues("Content-Security-Policy"))
                .containsExactly(SecurityConfig.API_CONTENT_SECURITY_POLICY);
        assertThat(headers.firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(headers.firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(headers.firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(headers.firstValue("Cache-Control")).hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        // Sem HTTPS não há HSTS (correto: o navegador ignoraria; e não revelamos o servidor).
        assertThat(headers.firstValue("Strict-Transport-Security")).isEmpty();
        assertThat(headers.firstValue("Server")).isEmpty();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + apiPort + path);
    }
}
