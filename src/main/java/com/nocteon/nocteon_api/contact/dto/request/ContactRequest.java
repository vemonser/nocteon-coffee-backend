package com.nocteon.nocteon_api.contact.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactRequest {

    @NotBlank(message = "{validation.contactName.notBlank}")
    @Size(min = 2, max = 100, message = "{validation.contactName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF][a-zA-Z\\u0600-\\u06FF\\s'.-]*$", message = "{validation.contactName.pattern}")
    private String name;

    @NotBlank(message = "{validation.email.notBlank}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.subject.notBlank}")
    @Size(max = 200, message = "{validation.subject.size}")
    private String subject;

    @NotBlank(message = "{validation.message.notBlank}")
    @Size(min = 10, max = 2000, message = "{validation.message.size}")
    private String message;
}