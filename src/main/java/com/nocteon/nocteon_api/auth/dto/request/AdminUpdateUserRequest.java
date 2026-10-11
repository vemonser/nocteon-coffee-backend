package com.nocteon.nocteon_api.auth.dto.request;

import com.nocteon.nocteon_api.auth.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin editing another user.
 *
 * <p>Every field is optional: this is a PATCH-shaped body where a null means "leave it alone",
 * which is why nothing here is {@code @NotNull} or {@code @NotBlank}. The bounds are the point —
 * without them an admin PUT could push a 400-character username or an unparseable email straight
 * into the {@code users} and {@code user_profiles} tables, and the failure would only surface later
 * as a broken login or a mangled profile page.
 *
 * <p>The lengths mirror the schema: {@code users.username} and {@code users.email} are
 * {@code varchar(50)} / {@code varchar(255)}, and {@code user_profiles.first_name} /
 * {@code last_name} are {@code varchar(50)}. Exceeding them is a database error at flush time, not
 * a validation message.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {

    @Size(max = 50, message = "{validation.username.size}")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "{validation.username.pattern}")
    private String username;

    @Email(message = "{validation.email.invalid}")
    @Size(max = 255, message = "{validation.email.size}")
    private String email;

    private Role role;

    @Size(max = 50, message = "{validation.firstName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF]+$", message = "{validation.firstName.pattern}")
    private String firstName;

    @Size(max = 50, message = "{validation.lastName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF]+$", message = "{validation.lastName.pattern}")
    private String lastName;

    private Boolean isActive;
}