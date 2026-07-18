package com.nocteon.nocteon_api.common.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.security.JwtService;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtService jwtService;

    public void blacklist(String accessToken) {
        Claims claims = jwtService.extractAllClaims(accessToken);
        long remainingTtl = claims.getExpiration().getTime() - System.currentTimeMillis();

        if (remainingTtl > 0) {
            String tokenHash = hashToken(accessToken);
            redisTemplate.opsForValue().set(
                    "blacklist:" + tokenHash,
                    "true",
                    Duration.ofMillis(remainingTtl));
        }
    }

    public boolean isBlacklisted(String accessToken) {
        String tokenHash = hashToken(accessToken);
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + tokenHash));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
