package com.moneyx.shared.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Tratamento de erros comum a todos os módulos.
 *
 * Herdar de ResponseEntityExceptionHandler faz o Spring responder às exceções padrão do MVC
 * (JSON malformado, tipo de parâmetro inválido, método HTTP não suportado, etc.)
 * no formato Problem Details (RFC 9457): Content-Type application/problem+json.
 *
 * Erros de negócio específicos de cada módulo ficam no handler do próprio módulo
 * (ex.: MerchantExceptionHandler), para que este pacote não dependa dos módulos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Falha de Bean Validation (@Valid). Por padrão o Spring responde apenas
     * "Invalid request content."; aqui acrescentamos a lista de campos inválidos
     * para que o cliente da API saiba exatamente o que corrigir.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();

        ProblemDetail body = ex.getBody();
        body.setDetail("One or more fields are invalid");
        body.setProperty("errors", errors);

        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Rota inexistente. A mensagem padrão do Spring ("No static resource v1/...") expõe um
     * detalhe interno do servidor (o tratamento de arquivos estáticos). Respondemos uma
     * mensagem genérica, sem dar pistas sobre a estrutura da aplicação.
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail body = ex.getBody();
        body.setDetail("The requested resource was not found");

        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
