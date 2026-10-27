package com.nocteon.nocteon_api.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static List<ClientIpResolver.TrustedProxy> proxies(String config) {
        return ClientIpResolver.parseTrustedProxies(config);
    }

    @Test
    void untrustedSourceWithSpoofedHeaderUsesRemoteAddress() {
        String result = ClientIpResolver.resolve("203.0.113.9", "10.0.0.5", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxyWithValidHeaderUsesForwardedIp() {
        String result = ClientIpResolver.resolve("127.0.0.1", "203.0.113.9", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxyCidrWithValidHeaderUsesForwardedIp() {
        String result = ClientIpResolver.resolve("10.0.0.5", "203.0.113.9", proxies("10.0.0.0/8"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void missingHeaderUsesRemoteAddress() {
        String result = ClientIpResolver.resolve("203.0.113.9", null, proxies("127.0.0.1"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void blankHeaderUsesRemoteAddress() {
        String result = ClientIpResolver.resolve("203.0.113.9", "   ", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void invalidHeaderFallsBackToRemoteAddress() {
        String result = ClientIpResolver.resolve("127.0.0.1", "not-an-ip", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void commaSeparatedHeaderIsRejected() {
        String result = ClientIpResolver.resolve("127.0.0.1", "10.0.0.1, 10.0.0.2", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void emptyTrustedProxyConfigTrustsNoOne() {
        String result = ClientIpResolver.resolve("127.0.0.1", "203.0.113.9", proxies(""));
        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void nullRemoteAddressIsHandled() {
        String result = ClientIpResolver.resolve(null, "203.0.113.9", proxies("127.0.0.1"));
        assertThat(result).isNull();
    }

    @Test
    void trustedProxyUsesFirstValidXForwardedForEntry() {
        String result = ClientIpResolver.resolve("127.0.0.1", null,
                "203.0.113.9, 10.0.0.5", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void unTrustedSourceCannotSpoofXForwardedFor() {
        String result = ClientIpResolver.resolve("203.0.113.9", null,
                "198.51.100.1", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("203.0.113.9");
    }

    @Test
    void xRealIpWinsOverXForwardedFor() {
        String result = ClientIpResolver.resolve("127.0.0.1", "198.51.100.7",
                "198.51.100.1, 10.0.0.5", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("198.51.100.7");
    }

    @Test
    void untrustedRemoteWithIpv6Proxy() {
        String result = ClientIpResolver.resolve("2001:db8::1", "203.0.113.9", proxies("127.0.0.1"));
        assertThat(result).isEqualTo("2001:db8::1");
    }
}
