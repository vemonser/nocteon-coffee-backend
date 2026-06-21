package com.nocteon.nocteon_api.auth.security;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.config.JwtProperties;
import com.nocteon.nocteon_api.common.util.HashUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final PrivateKey jwtPrivateKey;
    private final PublicKey jwtPublicKey;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(UserPrincipal userPrincipal) {
        return Jwts.builder()
                .subject(String.valueOf(userPrincipal.getUserId()))
                .claim("email", userPrincipal.getUsername())
                .claim("role", userPrincipal.getUser().getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiration()))
                .signWith(jwtPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRawRefreshToken() {
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        return HashUtil.sha256(rawToken);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}