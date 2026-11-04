import type { AxiosRequestConfig, InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { axiosClient } from "../../shared/api/axiosClient";
import { ENDPOINTS } from "../../shared/api/endpoints";
import { avatarUploadErrorMessage, uploadAvatar } from "./profileApi";

/**
 * UC-04 (3a) avatar upload — regression suite.
 *
 * These tests exercise the REAL axios pipeline (interceptors + transformRequest)
 * and only stub the adapter, because the production bug lived inside
 * `transformRequest`: the instance default `Content-Type: application/json`
 * made axios v1 silently convert the FormData body into a JSON string, so the
 * file never left the browser. A test that mocks `axiosClient.post` would have
 * happily passed while the feature stayed broken.
 */

const originalAdapter = axiosClient.defaults.adapter;

let capturedConfig: InternalAxiosRequestConfig | null = null;

function stubAdapter(response: { status: number; data: unknown }) {
  axiosClient.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
    capturedConfig = config;

    if (response.status >= 400) {
      const error = Object.assign(new Error("Request failed"), {
        isAxiosError: true,
        config,
        response: { ...response, headers: {}, config },
        toJSON: () => ({}),
      });
      throw error;
    }

    return {
      data: response.data,
      status: response.status,
      statusText: "OK",
      headers: {},
      config: config as AxiosRequestConfig,
    };
  };
}

function pngFile() {
  const magicBytes = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  return new File([magicBytes], "avatar.png", { type: "image/png" });
}

function envelope(data: unknown) {
  return {
    success: true,
    messageCode: "MSG-PRO-001",
    message: "Profile updated successfully.",
    data,
    timestamp: new Date().toISOString(),
  };
}

beforeEach(() => {
  capturedConfig = null;
});

afterEach(() => {
  axiosClient.defaults.adapter = originalAdapter;
});

describe("uploadAvatar", () => {
  it("sends the file as real multipart FormData, not a JSON-serialised object", async () => {
    stubAdapter({ status: 200, data: envelope("/uploads/user-avatars/abc.png") });

    await uploadAvatar(pngFile());

    expect(capturedConfig).not.toBeNull();

    // The body must survive transformRequest untouched.
    expect(capturedConfig!.data).toBeInstanceOf(FormData);
    expect(typeof capturedConfig!.data).not.toBe("string");

    const sentFile = (capturedConfig!.data as FormData).get("file");
    expect(sentFile).toBeInstanceOf(File);
    expect((sentFile as File).name).toBe("avatar.png");
    expect((sentFile as File).size).toBeGreaterThan(0);
  });

  it("declares a multipart content type so axios does not fall back to JSON", () => {
    return (async () => {
      stubAdapter({ status: 200, data: envelope("/uploads/user-avatars/abc.png") });

      await uploadAvatar(pngFile());

      const contentType = String(capturedConfig!.headers?.["Content-Type"] ?? "");
      expect(contentType).toContain("multipart/form-data");
      expect(contentType).not.toContain("application/json");
    })();
  });

  it("targets /v1/users/avatar without duplicating the /api base path", async () => {
    stubAdapter({ status: 200, data: envelope("/uploads/user-avatars/abc.png") });

    await uploadAvatar(pngFile());

    expect(capturedConfig!.url).toBe(ENDPOINTS.profile.avatar);
    expect(ENDPOINTS.profile.avatar).toBe("/v1/users/avatar");

    const resolved = `${capturedConfig!.baseURL ?? ""}${capturedConfig!.url}`;
    expect(resolved).not.toContain("/api/api/");
    expect(resolved).toMatch(/\/api\/v1\/users\/avatar$/);
  });

  it("returns the stable public URL from the response envelope", async () => {
    stubAdapter({ status: 200, data: envelope("/uploads/user-avatars/abc.png") });

    await expect(uploadAvatar(pngFile())).resolves.toBe("/uploads/user-avatars/abc.png");
  });
});

describe("avatarUploadErrorMessage", () => {
  it("surfaces the backend rejection reason (UC-04 exception 5b)", async () => {
    stubAdapter({
      status: 400,
      data: {
        success: false,
        messageCode: "COMMON_BAD_REQUEST",
        message: "MIME type does not match file content",
        data: null,
        timestamp: new Date().toISOString(),
      },
    });

    await expect(uploadAvatar(pngFile())).rejects.toBeTruthy();

    try {
      await uploadAvatar(pngFile());
    } catch (error) {
      expect(avatarUploadErrorMessage(error)).toBe("MIME type does not match file content");
    }
  });

  it("falls back to a generic message for non-HTTP failures", () => {
    expect(avatarUploadErrorMessage(new Error("network down"))).toBe(
      "Failed to upload avatar. Please try again.",
    );
  });
});
