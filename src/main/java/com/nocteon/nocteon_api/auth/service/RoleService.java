package com.nocteon.nocteon_api.auth.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.entity.RoleEntity;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.RoleRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.exception.UnknownPermissionException;
import com.nocteon.nocteon_api.auth.exception.RoleNotFoundException;
import com.nocteon.nocteon_api.auth.exception.RoleExistsException;
import com.nocteon.nocteon_api.auth.exception.RoleImmutableException;
import com.nocteon.nocteon_api.auth.exception.RoleInUseException;
import com.nocteon.nocteon_api.common.enums.Permission;
import com.nocteon.nocteon_api.common.security.AuditActor;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{2,31}$");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    // static holder for UserPrincipal fallback
    private static RoleService instance;

    @jakarta.annotation.PostConstruct
    void init() {
        instance = this;
    }

    public static RoleService getInstance() {
        return instance;
    }

    @Transactional(readOnly = true)
    public List<RoleEntity> getAllRoles() {
        return roleRepository.findAllWithPermissions();
    }

    @Transactional(readOnly = true)
    public RoleEntity getRole(String name) {
        return roleRepository.findWithPermissionsByName(name)
                .orElseThrow(() -> new com.nocteon.nocteon_api.auth.exception.RoleNotFoundException());
    }

    @Transactional(readOnly = true)
    public Set<String> getPermissions(String roleName) {
        if (roleName == null) return Set.of();
        // system enum first? prefer DB
        return roleRepository.findWithPermissionsByName(roleName)
                .map(RoleEntity::getPermissions)
                .orElseGet(() -> {
                    try {
                        return Role.valueOf(roleName).getPermissions().stream()
                                .map(Permission::getPermission).collect(java.util.stream.Collectors.toSet());
                    } catch (IllegalArgumentException e) {
                        return Set.of();
                    }
                });
    }

    @Transactional(readOnly = true)
    public long userCount(String name) {
        return userRepository.countByRole(name);
    }

    @Transactional(readOnly = true)
    public boolean exists(String name) {
        return roleRepository.existsById(name);
    }

    @Transactional
    public RoleEntity create(String name, String description, String cloneFrom, UserPrincipal actor) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid role name");
        }
        if (roleRepository.existsById(name)) {
            throw new RoleExistsException();
        }
        RoleEntity source = null;
        if (cloneFrom != null && !cloneFrom.isBlank()) {
            source = roleRepository.findWithPermissionsByName(cloneFrom).orElse(null);
        }
        Set<String> perms = new java.util.HashSet<>();
        if (source != null) {
            perms.addAll(source.getPermissions());
        }
        RoleEntity role = RoleEntity.builder()
                .name(name)
                .description(description)
                .system(false)
                .permissions(perms)
                .build();
        RoleEntity saved = roleRepository.save(role);
        AuditActor auditActor = AuditActor.of(actor);
        auditLogService.logSuccess(AuditAction.ROLE_CREATED, auditActor.userId(), auditActor.userEmail(),
                "Role", null, saved.getName(), null, null, null, null, null);
        return saved;
    }

    @Transactional
    public RoleEntity update(String name, String description, List<String> permissions,
                             List<String> sensitivePermissions, UserPrincipal actor) {
        RoleEntity role = roleRepository.findWithPermissionsByName(name)
                .orElseThrow(() -> new RoleNotFoundException());
        boolean system = role.isSystem();
        // system roles ADMIN, CUSTOMER: permissions read-only
        if (system && (name.equals("ADMIN") || name.equals("CUSTOMER")) && permissions != null) {
            throw new RoleImmutableException();
        }
        if (description != null) {
            role.setDescription(description);
        }
        if (permissions != null) {
            // validate slugs
            List<String> unknown = new ArrayList<>();
            for (String slug : permissions) {
                if (!isValidPermission(slug)) unknown.add(slug);
            }
            if (!unknown.isEmpty()) {
                throw new UnknownPermissionException(unknown);
            }
            // MODERATOR? allow edit; ADMIN/CUSTOMER blocked above
            if (!(name.equals("ADMIN") || name.equals("CUSTOMER"))) {
                role.getPermissions().clear();
                role.getPermissions().addAll(permissions);
                // increment authVersion for affected users
                incrementAuthVersionForRole(name);
            }
        }
        RoleEntity saved = roleRepository.save(role);
        AuditActor auditActor = AuditActor.of(actor);
        auditLogService.logSuccess(AuditAction.ROLE_PERMISSIONS_UPDATED, auditActor.userId(), auditActor.userEmail(),
                "Role", null, saved.getName(), null, null, null, null, null);
        return saved;
    }

    @Transactional
    public void delete(String name, UserPrincipal actor) {
        RoleEntity role = roleRepository.findById(name)
                .orElseThrow(() -> new RoleNotFoundException());
        if (role.isSystem()) {
            throw new RoleImmutableException();
        }
        long count = userRepository.countByRole(name);
        if (count > 0) {
            throw new RoleInUseException();
        }
        roleRepository.delete(role);
        AuditActor auditActor = AuditActor.of(actor);
        auditLogService.logSuccess(AuditAction.ROLE_DELETED, auditActor.userId(), auditActor.userEmail(),
                "Role", null, name, null, null, null, null, null);
    }

    @Transactional
    public void incrementAuthVersionForRole(String roleName) {
        userRepository.findByRoleAndSubscribedTrue(roleName).forEach(u -> {}); // no-op placeholder
        // increment auth_version for all users with role
        entityManager.createQuery("UPDATE User u SET u.authVersion = u.authVersion + 1 WHERE u.role = :role")
                .setParameter("role", roleName)
                .executeUpdate();
    }

    public boolean isValidPermission(String slug) {
        for (Permission p : Permission.values()) {
            if (p.getPermission().equals(slug)) return true;
        }
        return false;
    }

    public static RoleService getInstanceOrNull() {
        return instance;
    }

    // Exception inner classes? Use outer exception classes instead; define below as static nested not needed.
}
