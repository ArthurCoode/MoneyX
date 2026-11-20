package com.moneyx.shared.observability;

import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Acrescenta o requestId ao corpo do erro padrão do Spring Boot (/error), usado quando uma exceção não foi tratada
 * por nenhum @ExceptionHandler (500 inesperado). Assim a loja consegue informar o id mesmo nesse caso.
 *
 * Lê o id do atributo da requisição (não do MDC): o /error é montado depois que o RequestIdFilter já terminou.
 */
@Component
public class RequestIdErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        Object requestId = webRequest.getAttribute(RequestIds.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (requestId != null) {
            attributes.put(RequestIds.MDC_KEY, requestId);
        }
        return attributes;
    }
}
