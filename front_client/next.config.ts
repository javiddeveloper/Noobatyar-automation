/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',

  // Django's URLconf expects the trailing slash. Without this, Next answers
  // /api/client/business/ with a 308 to the slash-less path before the rewrite
  // below ever runs, and the proxied call never reaches the backend.
  skipTrailingSlashRedirect: true,
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: '127.0.0.1',
        port: '8000',
        pathname: '/media/**',
      },
      {
        protocol: 'https',
        hostname: 'api.noobatyar.ir',
        pathname: '/media/**',
      },
      {
        protocol: 'http',
        hostname: 'web',
        port: '8000',
        pathname: '/media/**',
      },
    ],
  },

  // Dev-only proxy. With NEXT_PUBLIC_API_URL pointing straight at a remote API,
  // server-rendered pages work but anything the browser fetches is blocked by
  // CORS — the remote allowlist has no reason to contain localhost:3000. Setting
  // NEXT_PUBLIC_API_URL to the dev server's own origin routes those calls back
  // through here, so they leave Node instead of the browser and CORS never
  // applies. Guarded by NODE_ENV, so production builds are untouched.
  async rewrites() {
    const target = process.env.DEV_API_PROXY_TARGET;
    if (process.env.NODE_ENV !== 'development' || !target) return [];
    return [
      { source: '/api/:path*', destination: `${target}/api/:path*` },
      { source: '/media/:path*', destination: `${target}/media/:path*` },
    ];
  },
};

export default nextConfig;
