import { appConfig } from "@/config/app";
import { request, userScopeHeaders } from "./client";
import type { AssetResponse } from "./types";

export function uploadAsset(
  file: File,
  signal: AbortSignal,
  userId: string,
): Promise<AssetResponse> {
  const body = new FormData();
  body.append("file", file);
  return request<AssetResponse>(appConfig.api.assets, {
    method: "POST",
    body,
    signal,
    headers: userScopeHeaders(userId),
  });
}
