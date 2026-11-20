package com.moneyx.shared.api;

import com.moneyx.shared.observability.RequestIds;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Acrescenta {@code requestId} a TODO erro em Problem Details, num único lugar (em vez de em cada handler).
 *
 * ResponseBodyAdvice é chamado logo antes de o corpo ser escrito, inclusive para o que os @ExceptionHandler devolvem
 * (e para os 401/403/429 dos filtros, que chegam aos handlers pelo HandlerExceptionResolver).
 */
@RestControllerAdvice
public class ProblemDetailRequestIdAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true; // o tipo real só é conhecido em beforeBodyWrite (o handler pode devolver ResponseEntity<ProblemDetail>)
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            String requestId = RequestIds.current();
            if (requestId != null) {
                problem.setProperty(RequestIds.MDC_KEY, requestId);
            }
        }
        return body;
    }
}
