package com.nocteon.nocteon_api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.service.RoleService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class RoleControllerValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleService roleService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void createRoleWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/dashboard/roles")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("role:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void createRoleWithInvalidPatternNameReturns400() throws Exception {
        mockMvc.perform(post("/api/dashboard/roles")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("role:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
}
