package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.nocteon.nocteon_api.auth.dto.request.AdminUpdateUserRequest;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.exception.AdminAccountProtectedException;
import com.nocteon.nocteon_api.auth.exception.EmailChangeForbiddenException;
import com.nocteon.nocteon_api.auth.repository.RoleRepository;
import com.nocteon.nocteon_api.auth.repository.UserProfileRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.auth.service.UserService;

/**
 * The identity guards on the admin user routes.
 *
 * <p>{@code PUT /api/dashboard/users/{id}} and {@code .../toggle-active} are gated on
 * {@code user:update}, which MODERATOR holds. Without these checks a moderator could change an
 * administrator's e-mail (then reset the password) or simply block the account that owns the
 * store.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserIdentityGuardTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    private User targetAdmin;

    @BeforeEach
    void setUp() {
        targetAdmin = User.builder()
                .id(2L)
                .username("owner")
                .email("owner@example.com")
                .role("ADMIN")
                .enabled(true)
                .isActive(true)
                .build();
        UserProfile profile = UserProfile.builder().user(targetAdmin).firstName("Owner").build();
        targetAdmin.setProfile(profile);
    }

    private UserPrincipal actor(String role, String... permissions) {
        UserPrincipal principal = mock(UserPrincipal.class);
        List<GrantedAuthority> authorities = java.util.Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        lenient().doReturn(authorities).when(principal).getAuthorities();
        lenient().when(principal.getUserId()).thenReturn(99L);
        lenient().when(principal.getUser()).thenReturn(User.builder().id(99L).role(role).build());
        return principal;
    }

    private AdminUpdateUserRequest emailRequest(String email) {
        AdminUpdateUserRequest request = new AdminUpdateUserRequest();
        request.setEmail(email);
        return request;
    }

    @Test
    void aModeratorCannotChangeAnAdministratorsEmail() {
        when(userRepository.findByIdWithProfile(2L)).thenReturn(Optional.of(targetAdmin));

        UserPrincipal moderator = actor("MODERATOR", "user:update");

        assertThatThrownBy(() -> userService.adminUpdate(2L, emailRequest("attacker@example.com"), moderator))
                .isInstanceOf(EmailChangeForbiddenException.class);
        assertThat(targetAdmin.getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void aModeratorCannotChangeACustomersEmailEither() {
        User customer = User.builder().id(3L).email("customer@example.com").role("CUSTOMER").build();
        customer.setProfile(UserProfile.builder().user(customer).build());
        when(userRepository.findByIdWithProfile(3L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> userService.adminUpdate(3L, emailRequest("attacker@example.com"),
                actor("MODERATOR", "user:update")))
                .isInstanceOf(EmailChangeForbiddenException.class);
    }

    @Test
    void anAdministratorMayChangeAnEmail() {
        when(userRepository.findByIdWithProfile(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        UserPrincipal admin = actor("ADMIN", "user:update", "user:change_role");

        var response = userService.adminUpdate(2L, emailRequest("new@example.com"), admin);

        assertThat(response.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void aModeratorCannotBlockAnAdministrator() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> userService.toggleActive(2L, actor("MODERATOR", "user:update")))
                .isInstanceOf(AdminAccountProtectedException.class);
        assertThat(targetAdmin.isActive()).isTrue();
    }

    @Test
    void aModeratorMayStillBlockACustomer() {
        User customer = User.builder().id(3L).role("CUSTOMER").isActive(true).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(customer));
        when(userRepository.save(customer)).thenReturn(customer);

        var response = userService.toggleActive(3L, actor("MODERATOR", "user:update"));

        assertThat(response.isActive()).isFalse();
        assertThat(customer.isActive()).isFalse();
    }
}
