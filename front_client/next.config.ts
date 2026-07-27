/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
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
};

export default nextConfig;
