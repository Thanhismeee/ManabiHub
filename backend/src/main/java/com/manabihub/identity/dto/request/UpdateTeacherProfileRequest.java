package com.manabihub.identity.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTeacherProfileRequest
        extends UpdateStudentProfileRequest {

    @Size(max = 2000)
    private String bio;
}