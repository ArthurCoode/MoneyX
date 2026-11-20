package com.moneyx.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeyTest {

    @Test
    void ipv4IsUsedAsIs() {
        assertThat(ClientKey.of("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    /** Endereços do mesmo /64 são da mesma pessoa/rede: compartilham o limite. */
    @Test
    void ipv6IsGroupedByItsSlash64Prefix() {
        String a = ClientKey.of("2001:db8:abcd:12::1");
        String b = ClientKey.of("2001:db8:abcd:12:ffff:ffff:ffff:ffff");
        String otherNetwork = ClientKey.of("2001:db8:abcd:13::1");

        assertThat(a).isEqualTo(b).isEqualTo("ipv6:20010db8abcd0012/64");
        assertThat(otherNetwork).isNotEqualTo(a);
    }

    @Test
    void ipv4MappedIpv6IsTreatedAsIpv4() {
        assertThat(ClientKey.of("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void missingAddressFallsIntoASingleBucket() {
        assertThat(ClientKey.of(null)).isEqualTo("unknown");
        assertThat(ClientKey.of(" ")).isEqualTo("unknown");
    }
}
