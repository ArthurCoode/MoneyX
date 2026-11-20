package com.moneyx.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Primeiro filtro de toda requisição: define o id de correlação ANTES da segurança e do rate limit, para que até
 * 401 e 429 tenham id.
 *
 * - Aceita o X-Request-Id do cliente se for seguro (RequestIds); senão, gera um UUID.
 * - Coloca o id no MDC (aparece em todas as linhas de log desta requisição), num atributo da requisição e no header
 *   X-Request-Id da resposta (definido ANTES de processar: vale também para respostas de erro).
 * - Uma exceção que escapou de tudo (500 inesperado) é logada AQUI, ainda com o id no MDC; depois o Tomcat monta a
 *   resposta de erro (RequestIdErrorAttributes acrescenta o id a ela).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = RequestIds.acceptOrGenerate(request.getHeader(RequestIds.HEADER));
        MDC.put(RequestIds.MDC_KEY, requestId);
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
            throw e;
        } finally {
            MDC.remove(RequestIds.MDC_KEY); // a thread volta para o pool do servidor: nunca deixar o id "vazar"
        }
    }
}
