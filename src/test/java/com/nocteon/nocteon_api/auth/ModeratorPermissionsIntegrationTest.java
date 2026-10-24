package com.nocteon.nocteon_api.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.service.RoleService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class ModeratorPermissionsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleService roleService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void moderatorPermittedEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/dashboard/reviews")
                        .with(user("moderator").authorities(
                                new SimpleGrantedAuthority("review:moderate"),
                                new SimpleGrantedAuthority("review:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void moderatorForbiddenEndpointReturns403() throws Exception {
        mockMvc.perform(put("/api/dashboard/users/1/role")
                        .with(user("moderator").authorities(new SimpleGrantedAuthority("review:moderate")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }
    @Test
    void moderatorRoleHasExpectedPermissions() {
        var perms = roleService.getPermissions("MODERATOR");
        org.assertj.core.api.Assertions.assertThat(perms).contains("review:read", "review:moderate");
        org.assertj.core.api.Assertions.assertThat(perms).doesNotContain("user:change_role", "settings:update");
    }

}
