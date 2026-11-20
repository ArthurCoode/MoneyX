package com.moneyx.security.ratelimit;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * Converte o endereço da CONEXÃO (request.getRemoteAddr()) na chave do limite.
 *
 * - IPv4: o próprio endereço.
 * - IPv6: o prefixo /64. Uma pessoa ou empresa costuma receber um bloco /64 inteiro (2^64 endereços); se cada
 *   endereço tivesse seu próprio balde, bastaria trocar o final do IP a cada requisição para nunca ser barrado.
 * - IPv4 escrito como IPv6 (::ffff:1.2.3.4): tratado como o IPv4.
 *
 * NUNCA usar X-Forwarded-For aqui: o cliente pode inventar esse header. Ele só será confiável quando houver um
 * proxy NOSSO na frente da aplicação (Fase 7), configurado com a lista de proxies confiáveis.
 */
public final class ClientKey {

    private ClientKey() {
    }

    public static String of(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        InetAddress address;
        try {
            // remoteAddr é sempre um IP literal (vem da conexão TCP), então não há consulta DNS aqui.
            address = InetAddress.getByName(remoteAddress.trim());
        } catch (UnknownHostException e) {
            return "unknown";
        }
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        if (address instanceof Inet6Address) {
            byte[] prefix = new byte[8];
            System.arraycopy(address.getAddress(), 0, prefix, 0, 8);
            return "ipv6:" + HexFormat.of().formatHex(prefix) + "/64";
        }
        return address.getHostAddress();
    }
}
