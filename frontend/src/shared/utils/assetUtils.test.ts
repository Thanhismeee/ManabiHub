import { describe, expect, it } from "vitest";

import { resolvePublicAssetUrl } from "./assetUtils";

/**
 * The avatar URL returned by the backend is a server-relative path
 * (`/uploads/user-avatars/<uuid>.png`) served by Spring's static resource
 * handler on the API origin — not by the Vite dev server. It must therefore be
 * resolved against the API origin with the `/api` suffix stripped, otherwise
 * the <img> 404s and the profile screen keeps showing the old avatar.
 */
describe("resolvePublicAssetUrl", () => {
  it("resolves a stored avatar path against the API origin, without the /api suffix", () => {
    expect(resolvePublicAssetUrl("/uploads/user-avatars/abc.png")).toBe(
      "http://localhost:8081/uploads/user-avatars/abc.png",
    );
  });

  it("tolerates a path returned without a leading slash", () => {
    expect(resolvePublicAssetUrl("uploads/user-avatars/abc.png")).toBe(
      "http://localhost:8081/uploads/user-avatars/abc.png",
    );
  });

  it("leaves absolute, blob and data URLs untouched", () => {
    expect(resolvePublicAssetUrl("https://cdn.example.com/a.png")).toBe(
      "https://cdn.example.com/a.png",
    );
    expect(resolvePublicAssetUrl("blob:http://localhost:5173/xyz")).toBe(
      "blob:http://localhost:5173/xyz",
    );
    expect(resolvePublicAssetUrl("data:image/png;base64,AAAA")).toBe(
      "data:image/png;base64,AAAA",
    );
  });

  it("returns undefined for empty input so the Avatar falls back to initials", () => {
    expect(resolvePublicAssetUrl(null)).toBeUndefined();
    expect(resolvePublicAssetUrl(undefined)).toBeUndefined();
    expect(resolvePublicAssetUrl("")).toBeUndefined();
  });
});
