import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/site';

/**
 * Served at /robots.txt (Next file convention).
 *
 * Until this existed the origin answered /robots.txt with a 404 and had no
 * sitemap reference anywhere, so a crawler's only route to a business page was
 * an inbound link from somewhere else.
 *
 * The disallow list is about privacy and crawl budget, not secrecy — none of
 * these are access-controlled by robots.txt, they are simply pages that must
 * never appear in a search result: `/appointments` and `/profile` are one
 * visitor's own records, `/auth` is a login form, and every `/b/<slug>/checkout`
 * URL carries an appointment id.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/auth/', '/profile/', '/appointments/', '/b/*/checkout/'],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
