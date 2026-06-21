package com.nocteon.nocteon_api.auth.dto.request;

 
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @Size(min = 2, max = 50, message = "{validation.firstName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF]+$", message = "{validation.firstName.pattern}")
    private String firstName;

    @Size(min = 2, max = 50, message = "{validation.lastName.size}")
    @Pattern(regexp = "^[a-zA-Z\\u0600-\\u06FF]+$", message = "{validation.lastName.pattern}")
    private String lastName;

    @Size(min = 11, max = 15, message = "{validation.phone.size}")
    @Pattern(regexp = "^[0-9]+$", message = "{validation.phone.pattern}")
    private String phone;

    @Size(max = 255, message = "{validation.bio.size}")
    private String bio;
}
