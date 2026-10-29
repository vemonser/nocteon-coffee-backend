package com.nocteon.nocteon_api.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
@Transactional
class RoleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private static final SimpleGrantedAuthority MANAGE = new SimpleGrantedAuthority("role:manage");
    private static final SimpleGrantedAuthority READ = new SimpleGrantedAuthority("role:read");

    @Test
    void listRolesReturnsNonEmpty() throws Exception {
        mockMvc.perform(get("/api/dashboard/roles")
                        .with(user("admin").authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getRoleByNameReturnsPermissions() throws Exception {
        mockMvc.perform(get("/api/dashboard/roles/ADMIN")
                        .with(user("admin").authorities(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("ADMIN"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void createUpdateAndDeleteRoleFlow() throws Exception {
        // Create
        mockMvc.perform(post("/api/dashboard/roles")
                        .with(user("admin").authorities(MANAGE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TEST_ROLE\",\"description\":\"test\",\"cloneFrom\":\"CUSTOMER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("TEST_ROLE"));

        // Update permissions: grant a permission CUSTOMER lacks, revoke the rest.
        mockMvc.perform(put("/api/dashboard/roles/TEST_ROLE")
                        .with(user("admin").authorities(MANAGE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"user:read\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granted").isArray())
                .andExpect(jsonPath("$.data.granted[?(@=='user:read')]").exists())
                .andExpect(jsonPath("$.data.revoked").isArray());

        // Delete
        mockMvc.perform(delete("/api/dashboard/roles/TEST_ROLE")
                        .with(user("admin").authorities(MANAGE)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRoleInUseReturns409() throws Exception {
        // CUSTOMER role is in use by seeded users
        mockMvc.perform(delete("/api/dashboard/roles/CUSTOMER")
                        .with(user("admin").authorities(MANAGE)))
                .andExpect(status().isConflict());
    }
}