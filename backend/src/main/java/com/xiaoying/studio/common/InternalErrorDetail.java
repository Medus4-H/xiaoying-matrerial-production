package com.xiaoying.studio.common;

/** Internal-only reasons; never serialize these into ordinary API responses. */
public enum InternalErrorDetail {
  ASSET_ID_INVALID,
  ASSET_UNAVAILABLE_OR_NOT_OWNED,
  ASSET_FILE_MISSING
}
