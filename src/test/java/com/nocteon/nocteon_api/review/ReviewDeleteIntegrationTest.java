package com.nocteon.nocteon_api.review;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
@Transactional
class ReviewDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void deleteReviewReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/dashboard/reviews/2")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("review:delete"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingReviewReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/dashboard/reviews/999999")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("review:delete"))))
                .andExpect(status().isNotFound());
    }
}