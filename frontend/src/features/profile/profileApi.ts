import axios from "axios";

import {axiosClient} from "../../shared/api/axiosClient";
import {ENDPOINTS} from "../../shared/api/endpoints";

import type {
    ApiEnvelope,
    StudentProfileResponse,
    TeacherProfileResponse,
    UpdateStudentProfileRequest,
    UpdateTeacherProfileRequest,
    PhoneVerificationResponse,
} from "./profileTypes";

/* ==========================
   STUDENT
========================== */

export async function getMyStudentProfile() {

    const response =
        await axiosClient.get<ApiEnvelope<StudentProfileResponse>>(
            ENDPOINTS.profile.student
        );

    return response.data.data;

}

export async function updateMyStudentProfile(
    payload: UpdateStudentProfileRequest
) {

    const response =
        await axiosClient.put<ApiEnvelope<StudentProfileResponse>>(
            ENDPOINTS.profile.student,
            payload
        );

    return response.data.data;

}

/* ==========================
   TEACHER
========================== */

export async function getMyTeacherProfile() {

    const response =
        await axiosClient.get<ApiEnvelope<TeacherProfileResponse>>(
            ENDPOINTS.profile.teacher
        );

    return response.data.data;

}

export async function updateMyTeacherProfile(
    payload: UpdateTeacherProfileRequest
) {

    const response =
        await axiosClient.put<ApiEnvelope<TeacherProfileResponse>>(
            ENDPOINTS.profile.teacher,
            payload
        );

    return response.data.data;

}

/* ==========================
   SHARED
========================== */

/**
 * UC-04 (3a): upload an avatar file.
 *
 * Two things are mandatory here and must not be "simplified" away:
 *
 * 1. `Content-Type: multipart/form-data` MUST be passed explicitly.
 *    `axiosClient` declares `Content-Type: application/json` as an instance
 *    default. Axios v1 `transformRequest` checks that header first and, when it
 *    says JSON, converts a FormData body into a JSON string via
 *    `formDataToJSON()`. The File would be serialised to `{}` and the backend
 *    (`consumes = multipart/form-data`) would reject the request, so the file
 *    never reached the server.
 *
 * 2. The path must NOT be prefixed with `/api`. `axiosClient.baseURL` already
 *    ends with `/api`, so a hard-coded `/api/v1/users/avatar` resolved to
 *    `.../api/api/v1/users/avatar` and returned 404.
 */
export async function uploadAvatar(file: File) {
    const formData = new FormData();
    formData.append("file", file);

    const response = await axiosClient.post<ApiEnvelope<string>>(
        ENDPOINTS.profile.avatar,
        formData,
        {
            headers: {
                "Content-Type": "multipart/form-data",
            },
        }
    );

    return response.data.data;
}

/**
 * UC-04 exception 5b: the backend explains exactly why an avatar was rejected
 * (size, MIME/magic-byte mismatch, unsupported format). Surface that message
 * instead of a generic one so testers and users can act on it.
 */
export function avatarUploadErrorMessage(error: unknown): string {
    if (axios.isAxiosError<ApiEnvelope<unknown>>(error)) {
        const message = error.response?.data?.message;
        if (message) {
            return message;
        }
    }

    return "Failed to upload avatar. Please try again.";
}

export async function requestStudentPhoneVerification(phoneNumber: string) {
    const response = await axiosClient.post<ApiEnvelope<PhoneVerificationResponse>>(
        ENDPOINTS.profile.studentPhoneVerificationRequest,
        { phoneNumber }
    );
    return response.data.data;
}

export type PhoneVerificationProof =
    | { code: string }
    | { challengeId: string; firebaseIdToken: string };

export async function confirmStudentPhoneVerification(
    phoneNumber: string,
    proof: PhoneVerificationProof,
) {
    const response = await axiosClient.post<ApiEnvelope<PhoneVerificationResponse>>(
        ENDPOINTS.profile.studentPhoneVerificationConfirm,
        { phoneNumber, ...proof }
    );
    return response.data.data;
}

export async function requestTeacherPhoneVerification(phoneNumber: string) {
    const response = await axiosClient.post<ApiEnvelope<PhoneVerificationResponse>>(
        ENDPOINTS.profile.teacherPhoneVerificationRequest,
        { phoneNumber }
    );
    return response.data.data;
}

export async function confirmTeacherPhoneVerification(
    phoneNumber: string,
    proof: PhoneVerificationProof,
) {
    const response = await axiosClient.post<ApiEnvelope<PhoneVerificationResponse>>(
        ENDPOINTS.profile.teacherPhoneVerificationConfirm,
        { phoneNumber, ...proof }
    );
    return response.data.data;
}
