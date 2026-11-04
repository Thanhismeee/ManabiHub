package com.manabihub.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String type = "Bearer";
    private String csrfToken;
    private boolean remembered;

    public LoginResponse(String token) {
        this.token = token;
    }

    public LoginResponse(String token, String csrfToken, boolean remembered) {
        this.token = token;
        this.csrfToken = csrfToken;
        this.remembered = remembered;
    }
}
