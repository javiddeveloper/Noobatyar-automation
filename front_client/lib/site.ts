/**
 * The public origin this app is served from.
 *
 * Needed by anything that has to emit an *absolute* URL: sitemap.xml,
 * robots.txt, canonical links, OpenGraph images and JSON-LD. Relative URLs are
 * fine inside the app but are meaningless to a crawler that reads the sitemap
 * out of band.
 *
 * Deliberately separate from NEXT_PUBLIC_API_URL: the API lives on
 * api.noobatyar.ir while the pages a crawler indexes live on app.noobatyar.ir,
 * so reusing the API origin here would point every canonical tag at the wrong
 * host.
 */
export const SITE_URL = (
  process.env.NEXT_PUBLIC_SITE_URL || 'https://app.noobatyar.ir'
).replace(/\/$/, '');

/** Absolute URL for a business's public booking page. */
export function absoluteBusinessUrl(code: string): string {
  return `${SITE_URL}/b/Noobatyar-${code}`;
}
