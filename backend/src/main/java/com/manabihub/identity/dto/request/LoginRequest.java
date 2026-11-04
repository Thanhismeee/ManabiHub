package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "MSG-COM-002") // Required field is empty
    @Email(message = "MSG-COM-002")    // Reusing the general empty/invalid message code per spec
    @Size(max = 255, message = "MSG-COM-002")
    private String email;

    @NotBlank(message = "MSG-COM-002")
    @Size(max = 72, message = "MSG-COM-002")
    private String password;

    private boolean rememberMe;
}
