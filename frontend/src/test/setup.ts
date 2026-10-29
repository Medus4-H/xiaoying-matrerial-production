import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";
import { server } from "./server";

const originalFetch = globalThis.fetch;
globalThis.fetch = (input, init) => {
  const target =
    typeof input === "string" ? new URL(input, window.location.origin) : input;
  return originalFetch(target, init);
};

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => server.close());
