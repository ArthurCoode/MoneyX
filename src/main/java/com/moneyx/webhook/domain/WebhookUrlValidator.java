package com.moneyx.webhook.domain;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras da URL de um endpoint de webhook, com proteção contra SSRF (Server-Side Request Forgery).
 *
 * SSRF: quem faz a chamada HTTP é o SERVIDOR do MoneyX. Sem estas regras, um comerciante poderia cadastrar
 * http://localhost:8080/..., http://169.254.169.254/ (credenciais da nuvem) ou http://10.0.0.5/ (rede interna)
 * e usar o MoneyX como porta de entrada para a própria rede dele.
 *
 * Este validador é a PRIMEIRA barreira (no cadastro, sem consultar DNS): rejeita o que já é visivelmente interno.
 * A barreira DEFINITIVA fica na entrega (Etapa 3): o nome "loja.com" pode apontar para um IP público hoje e para
 * 127.0.0.1 amanhã (DNS rebinding), por isso o IP resolvido precisa ser checado a cada envio (isInternalAddress).
 *
 * Classe pura (sem rede, sem Spring): cada regra é testada isoladamente.
 */
public final class WebhookUrlValidator {

    public static final int MAX_LENGTH = 2048;

    /** IPv4 na forma canônica: 4 números de 0 a 255, SEM zeros à esquerda (0177.0.0.1 é octal em alguns clientes). */
    private static final Pattern CANONICAL_IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    /**
     * Host que "parece número": só dígitos e pontos, ou notação hexadecimal. Formas como 2130706433 ou 0x7f.1
     * significam 127.0.0.1 para alguns clientes HTTP; aceitamos números apenas na forma canônica.
     */
    private static final Pattern NUMERIC_LOOKING_HOST = Pattern.compile("^(0x[0-9a-f.x]*|[0-9.]+)$");

    private WebhookUrlValidator() {
    }

    /**
     * @param allowLocalUrls permite http(s)://localhost e loopback (somente testes locais; NUNCA em produção)
     * @return a URL sem espaços nas pontas
     * @throws IllegalArgumentException com o motivo, em inglês (mensagem da API)
     */
    public static String validate(String raw, boolean allowLocalUrls) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String url = raw.trim();
        if (url.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("url must have at most " + MAX_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url is not a valid URL");
        }
        if (!uri.isAbsolute() || uri.isOpaque()) {
            throw new IllegalArgumentException("url must be an absolute URL");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("url must have a valid host");
        }
        // usuario:senha@host: credenciais ficariam gravadas e apareceriam em logs; e "https://loja.com@10.0.0.1"
        // engana quem lê (o host real é o que vem depois do @).
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("url must not contain credentials");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("url must not contain a fragment");
        }

        String host = normalizeHost(uri.getHost());
        InetAddress literal = parseIpLiteral(host);
        boolean local = isLocalHostName(host) || (literal != null && literal.isLoopbackAddress());

        if (allowLocalUrls && local) {
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("url must use https");
            }
            return url;
        }
        if (!scheme.equals("https")) {
            throw new IllegalArgumentException("url must use https");
        }
        if (isLocalHostName(host)) {
            throw new IllegalArgumentException("url must not point to localhost");
        }
        if (literal != null && isInternalAddress(literal)) {
            throw new IllegalArgumentException("url must not point to a private or internal address");
        }
        return url;
    }

    /**
     * O endereço pertence a uma rede interna/reservada? Usado aqui (IP escrito na URL) e na entrega (IP resolvido).
     */
    public static boolean isInternalAddress(InetAddress address) {
        if (address.isAnyLocalAddress()       // 0.0.0.0, ::
                || address.isLoopbackAddress() // 127.0.0.0/8, ::1
                || address.isLinkLocalAddress() // 169.254.0.0/16 (metadados da nuvem), fe80::/10
                || address.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                    // 0.0.0.0/8 ("esta rede")
                    || (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 (CGNAT, interno de provedores)
                    || first >= 240;                             // 240.0.0.0/4 reservado e 255.255.255.255
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc;                        // fc00::/7 (unique local, o "privado" do IPv6)
        }
        return false;
    }

    private static String normalizeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1); // IPv6 vem entre colchetes na URL
        }
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1); // "localhost." é o mesmo que "localhost"
        }
        return h;
    }

    private static boolean isLocalHostName(String host) {
        return host.equals("localhost") || host.endsWith(".localhost");
    }

    /**
     * Converte o host em endereço SE ele for um IP escrito diretamente (sem consultar DNS).
     *
     * @return o endereço, ou null se o host for um nome comum
     * @throws IllegalArgumentException se parecer um número mas não estiver na forma canônica
     */
    private static InetAddress parseIpLiteral(String host) {
        boolean ipv6 = host.contains(":");
        if (!ipv6 && !NUMERIC_LOOKING_HOST.matcher(host).matches()) {
            return null;
        }
        if (!ipv6 && !CANONICAL_IPV4.matcher(host).matches()) {
            throw new IllegalArgumentException("url host is not a valid IP address");
        }
        try {
            // Para um IP literal (já conferido acima), getByName apenas converte o texto; não há consulta DNS.
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("url host is not a valid IP address");
        }
    }
}
