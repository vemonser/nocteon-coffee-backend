package com.nocteon.nocteon_api.auth.oauth2;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.exception.invalid.InvalidVerificationException;

import lombok.RequiredArgsConstructor;

/**
 * Short-lived, single-use code exchanged by the frontend after an OAuth2
 * redirect. This keeps the access token out of the browser URL/history.
 */
@Service
@RequiredArgsConstructor
public class OAuth2CodeService {

    private static final String KEY_PREFIX = "oauth2:exchange:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Pattern CODE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final StringRedisTemplate redisTemplate;

    public String issue(Long userId) {
        Objects.requireNonNull(userId, "userId");
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, userId.toString(), TTL);
        return code;
    }

    public Long consume(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new InvalidVerificationException();
        }
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
        if (value == null) {
            throw new InvalidVerificationException();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidVerificationException();
        }
    }
}
