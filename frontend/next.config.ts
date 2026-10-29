import type { NextConfig } from "next";

const backend = process.env.BACKEND_INTERNAL_URL ?? "http://backend:8080";
const config: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};
export default config;
