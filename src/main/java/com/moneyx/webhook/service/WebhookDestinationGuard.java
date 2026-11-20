package com.moneyx.webhook.service;

import com.moneyx.webhook.domain.WebhookUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Barreira DEFINITIVA contra SSRF, executada imediatamente antes de CADA envio.
 *
 * O cadastro só olha o texto da URL. Aqui resolvemos o DNS de verdade: "loja.com" pode ter passado a apontar para
 * 127.0.0.1 depois do cadastro (DNS rebinding). Se QUALQUER endereço resolvido for interno, o envio é recusado
 * (um atacante poderia devolver um IP público e um interno e torcer para o cliente HTTP escolher o interno).
 *
 * Risco residual conhecido: entre esta checagem e a conexão do HttpClient existe uma janela mínima em que o DNS
 * poderia mudar. O cache de DNS da JVM a reduz muito, mas não a elimina. A solução completa é um proxy de saída
 * dedicado (ex.: smokescreen, usado pelo Stripe), previsto para a infraestrutura de produção (Fase 7).
 */
@Component
public class WebhookDestinationGuard {

    private final boolean allowLocalUrls;

    public WebhookDestinationGuard(@Value("${moneyx.webhooks.allow-local-urls:false}") boolean allowLocalUrls) {
        this.allowLocalUrls = allowLocalUrls;
    }

    public enum Verdict { ALLOWED, NOT_ALLOWED, UNRESOLVABLE }

    public Verdict check(URI url) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(url.getHost());
        } catch (UnknownHostException e) {
            return Verdict.UNRESOLVABLE;
        }
        boolean allLoopback = true;
        for (InetAddress address : addresses) {
            allLoopback &= address.isLoopbackAddress();
        }
        // A exceção de testes locais libera SOMENTE loopback (a própria máquina), nunca outras redes internas.
        if (allowLocalUrls && allLoopback) {
            return Verdict.ALLOWED;
        }
        for (InetAddress address : addresses) {
            if (WebhookUrlValidator.isInternalAddress(address)) {
                return Verdict.NOT_ALLOWED;
            }
        }
        return Verdict.ALLOWED;
    }
}
