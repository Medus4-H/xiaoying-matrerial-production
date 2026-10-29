"use client";

import { useEffect, useRef, useState } from "react";
import { uploadAsset } from "@/lib/api/assets";
import type { AssetResponse } from "@/lib/api/types";

export interface SlotValue {
  asset: AssetResponse | null;
  uploading: boolean;
}

interface Props {
  userId: string;
  index: number;
  value: SlotValue;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
  onChange: (index: number, value: SlotValue) => void;
  disabled?: boolean;
}

export function UploadSlot({
  userId,
  index,
  value,
  acceptedMimeTypes,
  maxFileSizeBytes,
  onChange,
  disabled = false,
}: Props) {
  const version = useRef(0);
  const controller = useRef<AbortController | null>(null);
  const objectUrl = useRef<string | null>(null);
  const [candidate, setCandidate] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const position = index + 1;
  const inputId = `upload-slot-${position}`;

  const releasePreview = () => {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    objectUrl.current = null;
    setCandidate(null);
  };

  useEffect(
    () => () => {
      version.current++;
      controller.current?.abort();
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    },
    [],
  );

  const choose = async (file: File) => {
    const current = ++version.current;
    controller.current?.abort();
    controller.current = null;
    releasePreview();
    setError(null);
    if (!acceptedMimeTypes.includes(file.type)) {
      onChange(index, { asset: value.asset, uploading: false });
      setError("不支持该图片类型，请选择符合要求的图片。");
      return;
    }
    if (file.size > maxFileSizeBytes) {
      onChange(index, { asset: value.asset, uploading: false });
      setError("图片大小超过模板限制，请选择较小的图片。");
      return;
    }
    const abort = new AbortController();
    controller.current = abort;
    objectUrl.current = URL.createObjectURL(file);
    setCandidate(objectUrl.current);
    onChange(index, { asset: value.asset, uploading: true });
    try {
      const asset = await uploadAsset(file, abort.signal, userId);
      if (current !== version.current) return;
      controller.current = null;
      releasePreview();
      onChange(index, { asset, uploading: false });
    } catch (reason) {
      if (current !== version.current || abort.signal.aborted) return;
      controller.current = null;
      releasePreview();
      onChange(index, { asset: value.asset, uploading: false });
      setError(
        reason instanceof Error
          ? `上传失败：${reason.message}`
          : "上传失败，请重试。",
      );
    }
  };

  const remove = () => {
    version.current++;
    controller.current?.abort();
    controller.current = null;
    releasePreview();
    setError(null);
    onChange(index, { asset: null, uploading: false });
  };

  return (
    <div className="upload-slot">
      <input
        id={inputId}
        className="file-input"
        type="file"
        accept={acceptedMimeTypes.join(",")}
        aria-label={`上传第 ${position} 张图片`}
        disabled={disabled}
        onChange={(event) => {
          const file = event.currentTarget.files?.[0];
          event.currentTarget.value = "";
          if (file) void choose(file);
        }}
      />
      <label htmlFor={inputId} className="upload-target">
        {candidate ? (
          /* eslint-disable-next-line @next/next/no-img-element */
          <img src={candidate} alt={`第 ${position} 张待上传图片`} />
        ) : value.asset ? (
          /* eslint-disable-next-line @next/next/no-img-element */
          <img
            src={value.asset.previewUrl}
            alt={`第 ${position} 张已上传图片`}
          />
        ) : (
          <span className="upload-placeholder">
            <span aria-hidden="true">＋</span>选择第 {position} 张图片
          </span>
        )}
      </label>
      <div className="upload-actions">
        <label htmlFor={inputId} className="upload-change">
          {value.asset || candidate ? "更换图片" : "选择图片"}
        </label>
        {(value.asset || candidate) && (
          <button
            type="button"
            onClick={remove}
            disabled={disabled}
            aria-label={`移除第 ${position} 张图片`}
          >
            移除
          </button>
        )}
      </div>
      {value.uploading && (
        <p role="status" className="upload-status">
          上传中…
        </p>
      )}
      {error && (
        <p role="alert" className="upload-error">
          {error}
        </p>
      )}
    </div>
  );
}
