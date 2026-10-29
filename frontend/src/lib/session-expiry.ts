/** Date.now() is passed in so expiry decisions are deterministic and testable. */
export function getExpiryState(
  expiresAt: string,
  now: number,
): { expired: boolean; delay: number | null } {
  const remaining = Date.parse(expiresAt) - now;
  if (!Number.isFinite(remaining) || remaining <= 0) {
    return { expired: true, delay: null };
  }
  return { expired: false, delay: Math.min(remaining, 2_147_483_647) };
}

/** Schedule from the current clock, even when identity loading was slow. */
export function getExpiryRefreshDelay(
  expiresAt: string,
  displayedAt: number,
  currentTime: number,
): number | null {
  if (getExpiryState(expiresAt, displayedAt).expired) return null;
  return getExpiryState(expiresAt, currentTime).delay ?? 0;
}
