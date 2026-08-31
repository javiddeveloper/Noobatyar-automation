import type { MetadataRoute } from 'next';
import { listAllPublicBusinesses } from '@/lib/api';
import { SITE_URL, absoluteBusinessUrl } from '@/lib/site';

/**
 * Served at /sitemap.xml (Next file convention).
 *
 * Rebuilt at most once an hour rather than on every crawler hit: this walks the
 * whole public directory, and Googlebot will re-request the sitemap far more
 * often than the directory changes.
 */
export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const businesses = await listAllPublicBusinesses();

  // No `lastModified` on the business entries: PublicBusinessSerializer
  // deliberately withholds updated_at, and a made-up timestamp is worse than
  // none — a crawler that is told "changed today" every day learns to ignore
  // the signal. Expose updated_at publicly and it can be filled in here.
  const businessEntries: MetadataRoute.Sitemap = businesses.map((b) => ({
    url: absoluteBusinessUrl(b.unique_code),
    changeFrequency: 'weekly',
    priority: 0.8,
  }));

  return [
    {
      url: SITE_URL,
      lastModified: new Date(),
      changeFrequency: 'monthly',
      priority: 1,
    },
    ...businessEntries,
  ];
}
