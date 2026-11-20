package com.moneyx.shared.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Servidor REAL em portas aleatórias (API e gerenciamento), para provar a separação:
 * - na porta de gerenciamento: health, metrics e prometheus, sem API Key;
 * - na porta da API: nenhuma rota do Actuator (as métricas nunca ficam públicas junto com a API).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
// Nos testes o Spring Boot DESLIGA a exportação de métricas (o /actuator/prometheus nem existe). Aqui queremos
// justamente provar que ele existe na porta certa, então ligamos só para esta classe.
@AutoConfigureMetrics
class ManagementPortIntegrationTest {

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void portsAreDifferent() {
        assertThat(managementPort).isNotEqualTo(apiPort);
    }

    @Test
    void managementPortServesHealthAndPrometheusMetrics() throws Exception {
        HttpResponse<String> health = get(managementPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> prometheus = get(managementPort, "/actuator/prometheus");
        assertThat(prometheus.statusCode()).isEqualTo(200);
        assertThat(prometheus.body())
                .contains("moneyx_rate_limit_rejections_total")   // métrica de negócio
                .contains("http_server_requests")                 // automática: requisições por rota e status
                .contains("hikaricp_connections");                // automática: pool de conexões
    }

    @Test
    void apiPortDoesNotExposeTheActuator() throws Exception {
        assertThat(get(apiPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        assertThat(get(apiPort, "/actuator/metrics").statusCode()).isEqualTo(404);
        assertThat(get(apiPort, "/actuator/health").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
