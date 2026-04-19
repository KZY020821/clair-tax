import path from "path";

const nextConfig = {
  output: "standalone",
  experimental: {
    outputFileTracingRoot: path.resolve(import.meta.dirname),
  },
};

export default nextConfig;
