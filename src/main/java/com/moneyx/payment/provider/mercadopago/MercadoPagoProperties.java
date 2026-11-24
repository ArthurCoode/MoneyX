package com.moneyx.payment.provider.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * Configuração do Mercado Pago (moneyx.pix.mercadopago.*).
 *
 * @param baseUrl         raiz da API (os testes apontam para um servidor HTTP falso local)
 * @param accessToken     credencial (MERCADOPAGO_ACCESS_TOKEN no .env). Nunca aparece em log nem em toString
 * @param connectTimeout  tempo máximo para abrir a conexão
 * @param requestTimeout  tempo máximo para a resposta
 * @param payerEmail      e-mail enviado como pagador (o MP exige um; a API do MoneyX ainda não recebe o do pagador)
 * @param testPayerFirstName SÓ NO SANDBOX: "APRO" faz o MP aprovar a order sozinho (~5 s). Vazio em produção
 */
@ConfigurationProperties("moneyx.pix.mercadopago")
public record MercadoPagoProperties(
        @DefaultValue("https://api.mercadopago.com") URI baseUrl,
        @DefaultValue("") String accessToken,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration requestTimeout,
        @DefaultValue("test@testuser.com") String payerEmail,
        @DefaultValue("") String testPayerFirstName
) {

    @Override
    public String toString() {
        return "MercadoPagoProperties[baseUrl=" + baseUrl + ", accessToken=***, connectTimeout=" + connectTimeout
                + ", requestTimeout=" + requestTimeout + ", testPayerFirstName=" + testPayerFirstName + "]";
    }
}
