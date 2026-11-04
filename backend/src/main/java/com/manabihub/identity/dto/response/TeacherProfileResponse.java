package com.manabihub.identity.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeacherProfileResponse
        extends StudentProfileResponse {

    private String bio;
}