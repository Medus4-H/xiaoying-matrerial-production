import { appConfig } from "@/config/app";

type Bundle = {
  version: number;
  locale: string;
  items: Record<string, string>;
};
const cache = new Map<
  string,
  { expires: number; items: Record<string, string> }
>();
const inFlight = new Map<string, Promise<Record<string, string> | null>>();

export function clearErrorBundleCache(): void {
  cache.clear();
  inFlight.clear();
}

async function getBundle(
  locale: string,
): Promise<Record<string, string> | null> {
  const cached = cache.get(locale);
  if (cached && cached.expires > Date.now()) return cached.items;
  const pending = inFlight.get(locale);
  if (pending) return pending;
  const loading = (async () => {
    try {
      const response = await fetch(
        `${appConfig.api.errorBundle}?locale=${encodeURIComponent(locale)}`,
        {
          credentials: "same-origin",
          signal: AbortSignal.timeout(appConfig.errorBundleTimeoutMs),
        },
      );
      if (!response.ok) return null;
      const bundle = (await response.json()) as Bundle;
      if (!bundle || typeof bundle.items !== "object" || !bundle.items)
        return null;
      cache.set(locale, {
        expires: Date.now() + appConfig.errorBundleCacheMs,
        items: bundle.items,
      });
      return bundle.items;
    } catch {
      return null;
    }
  })();
  inFlight.set(locale, loading);
  try {
    return await loading;
  } finally {
    if (inFlight.get(locale) === loading) inFlight.delete(locale);
  }
}

export async function resolveErrorMessage(
  key: string,
  fallback: string,
  locale: string,
): Promise<string> {
  const entries = await getBundle(locale);
  const translated = entries?.[key];
  return typeof translated === "string" && translated.trim()
    ? translated
    : fallback;
}
