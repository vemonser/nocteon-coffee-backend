package com.nocteon.nocteon_api.common.redis;

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
            redisTemplate.opsForValue().set(
                    "blacklist:" + accessToken,
                    "true",
                    Duration.ofMillis(remainingTtl));
        }
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + accessToken));
    }
}