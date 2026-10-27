package com.nocteon.nocteon_api.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidVerificationException;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OAuth2CodeServiceTest {

    @Autowired
    private OAuth2CodeService codeService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void codeIsSingleUse() {
        String code = codeService.issue(42L);

        assertThat(codeService.consume(code)).isEqualTo(42L);
        assertThatThrownBy(() -> codeService.consume(code))
                .isInstanceOf(InvalidVerificationException.class);
    }

    @Test
    void malformedOrUnknownCodeIsRejected() {
        assertThatThrownBy(() -> codeService.consume("not-a-uuid"))
                .isInstanceOf(InvalidVerificationException.class);
        assertThatThrownBy(() -> codeService.consume("00000000-0000-0000-0000-000000000000"))
                .isInstanceOf(InvalidVerificationException.class);
    }
}
