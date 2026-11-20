package com.moneyx.shared.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc-123", "req_2026.11.20", "a"})
    void acceptsSafeIdsFromTheClient(String id) {
        assertThat(RequestIds.acceptOrGenerate(id)).isEqualTo(id);
    }

    /** Quebra de linha permitiria forjar linhas no log (log injection); id gigante encheria os logs. */
    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "line\nINFO fake log line", "tab\there", "<script>", "ção"})
    void replacesUnsafeIdsWithAUuid(String id) {
        assertThat(RequestIds.acceptOrGenerate(id)).matches("^[0-9a-f-]{36}$");
    }

    @Test
    void replacesMissingOrTooLongIds() {
        assertThat(RequestIds.acceptOrGenerate(null)).matches("^[0-9a-f-]{36}$");
        assertThat(RequestIds.acceptOrGenerate("x".repeat(64))).isEqualTo("x".repeat(64));
        assertThat(RequestIds.acceptOrGenerate("x".repeat(65))).matches("^[0-9a-f-]{36}$");
    }

    @Test
    void jobRunsWithItsOwnIdAndCleansUpEvenOnError() {
        MDC.remove(RequestIds.MDC_KEY);

        String seen = RequestIds.runWithNewId("job", RequestIds::current);
        assertThat(seen).matches("^job-[0-9a-f]{8}$");
        assertThat(RequestIds.current()).isNull();

        assertThatThrownBy(() -> RequestIds.runWithNewId("job", () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");
        assertThat(RequestIds.current()).isNull(); // a thread não fica "contaminada" com o id
    }

    @Test
    void restoresAnOuterIdAfterANestedRun() {
        MDC.put(RequestIds.MDC_KEY, "outer");
        try {
            RequestIds.runWithNewId("job", RequestIds::current);
            assertThat(RequestIds.current()).isEqualTo("outer");
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    /** Erro 500 inesperado (/error): o corpo também traz o requestId, lido do atributo da requisição. */
    @Test
    void errorAttributesIncludeTheRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "req-500");

        var attributes = new RequestIdErrorAttributes()
                .getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(attributes).containsEntry("requestId", "req-500");
    }
}
