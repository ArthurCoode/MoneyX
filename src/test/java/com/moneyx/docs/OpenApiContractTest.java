package com.moneyx.docs;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de CONTRATO: o OpenAPI gerado a partir do código precisa ser idêntico ao arquivo versionado
 * src/test/resources/openapi/moneyx-api.json.
 *
 * Se alguém mudar uma rota, um campo de DTO ou uma validação SEM QUERER, este teste falha no CI. Se a mudança for
 * intencional, regenere o arquivo e revise o diff no commit (mudar o contrato vira uma decisão explícita):
 *
 *   .\mvnw.cmd test -Dtest=OpenApiContractTest -Dopenapi.update=true
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("src", "test", "resources", "openapi", "moneyx-api.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void generatedContractMatchesTheVersionedFile() throws Exception {
        String current = normalize(apiDocs());

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, current, StandardCharsets.UTF_8);
            return;
        }

        assertThat(CONTRACT).as("Contrato versionado ausente. Gere com -Dopenapi.update=true").exists();
        String versioned = normalize(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(current)
                .as("O contrato da API mudou. Se foi intencional: .\\mvnw.cmd test -Dtest=OpenApiContractTest "
                        + "-Dopenapi.update=true e revise o diff de " + CONTRACT)
                .isEqualTo(versioned);
    }

    @Test
    void documentationIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void contractHasTheEssentials() throws Exception {
        String docs = apiDocs();

        assertThat(JsonPath.<String>read(docs, "$.openapi")).startsWith("3.1");
        assertThat(JsonPath.<String>read(docs, "$.components.securitySchemes.bearerAuth.scheme")).isEqualTo("bearer");
        // o cadastro é público (sem API Key) e tem limite de tentativas
        assertThat(JsonPath.<List<?>>read(docs, "$.paths['/v1/merchants'].post.security")).isEmpty();
        assertThat(JsonPath.<Map<String, ?>>read(docs, "$.paths['/v1/merchants'].post.responses")).containsKey("429");
        // rotas protegidas documentam 401
        assertThat(JsonPath.<Map<String, ?>>read(docs, "$.paths['/v1/payments'].post.responses")).containsKey("401");
        // os 4 eventos de webhook estão na seção "webhooks" (OpenAPI 3.1)
        assertThat(JsonPath.<Map<String, ?>>read(docs, "$.webhooks"))
                .containsOnlyKeys("payment.created", "payment.paid", "payment.canceled", "payment.expired");
    }

    private String apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * JSON estável para comparar e versionar: chaves de objetos em ordem alfabética (em todos os níveis) e fim de
     * linha \n (o Git no Windows pode trocar por \r\n).
     *
     * A ordenação é feita AQUI, e não na saída da API: na Swagger UI queremos a ordem natural das operações
     * (GET antes de DELETE, webhooks no ciclo de vida), mas a comparação não pode depender dessa ordem.
     * Listas (ex.: parâmetros) mantêm a ordem, que é parte do contrato.
     */
    private String normalize(String json) {
        Object sorted = sortKeys(jsonMapper.readValue(json, Object.class));
        return jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(sorted)
                .replace("\r\n", "\n") + "\n";
    }

    @SuppressWarnings("unchecked")
    private static Object sortKeys(Object node) {
        if (node instanceof Map<?, ?> map) {
            java.util.TreeMap<String, Object> sorted = new java.util.TreeMap<>();
            ((Map<String, Object>) map).forEach((key, value) -> sorted.put(key, sortKeys(value)));
            return sorted;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(OpenApiContractTest::sortKeys).toList();
        }
        return node;
    }
}
