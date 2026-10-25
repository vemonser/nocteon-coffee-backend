package com.nocteon.nocteon_api.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP address for rate limiting and auditing.
 *
 * <p>The {@code X-Real-IP} header is only honored when the immediate remote
 * address ({@link HttpServletRequest#getRemoteAddr()}) is a configured trusted
 * proxy. This prevents clients from spoofing an arbitrary source IP to evade
 * per-IP rate limits. With no trusted proxies configured (the default), the
 * header is ignored entirely and the remote address is always used.
 *
 * <p>Trusted proxies are configured via {@code rate-limit.trusted-proxies} as a
 * comma-separated list of IPv4/IPv6 addresses or CIDR ranges (e.g.
 * {@code 127.0.0.1,10.0.0.0/8}). Malformed entries fail fast at startup.
 */
@Component
public class ClientIpResolver {

    private final List<TrustedProxy> trustedProxies;

    public ClientIpResolver(@Value("${rate-limit.trusted-proxies:}") String trustedProxiesConfig) {
        this.trustedProxies = parseTrustedProxies(trustedProxiesConfig);
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getRemoteAddr(), request.getHeader("X-Real-IP"), trustedProxies);
    }

    /** Testable core logic. */
    static String resolve(String remoteAddr, String forwarded, List<TrustedProxy> trusted) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return remoteAddr;
        }
        if (forwarded != null && !forwarded.isBlank() && isTrustedProxy(remoteAddr, trusted)) {
            String candidate = forwarded.trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return remoteAddr.trim();
    }

    static List<TrustedProxy> parseTrustedProxies(String config) {
        List<TrustedProxy> list = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return list;
        }
        for (String part : config.split(",")) {
            String spec = part.trim();
            if (spec.isEmpty()) {
                continue;
            }
            list.add(TrustedProxy.parse(spec));
        }
        return list;
    }

    private static boolean isTrustedProxy(String remoteAddr, List<TrustedProxy> trusted) {
        if (trusted.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr.trim());
            for (TrustedProxy tp : trusted) {
                if (tp.matches(addr)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return false;
    }

    private static boolean isValidIp(String value) {
        // Explicitly reject any whitespace/control characters first; InetAddress.getByName
        // also rejects them, but this makes the intent clear and avoids relying on parser
        // quirks. Only IPv4/IPv6 characters are permitted.
        if (value == null || value.isEmpty() || !value.matches("^[0-9a-fA-F:.]+$")) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Visible for testing. */
    static class TrustedProxy {
        private final InetAddress network;
        private final int prefix;

        static TrustedProxy parse(String spec) {
            if (spec.contains("/")) {
                String[] parts = spec.split("/", 2);
                try {
                    InetAddress net = InetAddress.getByName(parts[0].trim());
                    int p = Integer.parseInt(parts[1].trim());
                    if (p < 0 || p > net.getAddress().length * 8) {
                        throw new IllegalArgumentException("Invalid CIDR prefix: " + spec);
                    }
                    return new TrustedProxy(net, p);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid trusted proxy spec: " + spec, e);
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("Invalid trusted proxy IP: " + spec, e);
                }
            } else {
                try {
                    return new TrustedProxy(InetAddress.getByName(spec.trim()), -1);
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("Invalid trusted proxy IP: " + spec, e);
                }
            }
        }

        TrustedProxy(InetAddress network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        boolean matches(InetAddress addr) {
            if (prefix < 0) {
                return network.equals(addr);
            }
            byte[] netBytes = network.getAddress();
            byte[] addrBytes = addr.getAddress();
            if (netBytes.length != addrBytes.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (netBytes[i] != addrBytes[i]) {
                    return false;
                }
            }
            if (remBits > 0 && fullBytes < netBytes.length) {
                int mask = 0xFF << (8 - remBits);
                if ((netBytes[fullBytes] & mask) != (addrBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
