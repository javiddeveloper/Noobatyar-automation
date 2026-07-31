import { getBusinessByCode } from '@/lib/api';

/**
 * Per-business web app manifest.
 *
 * Installing from a business page used to produce an icon called "نوبت‌یار"
 * that opened at "/" — so the customer got the generic app, not the salon they
 * were booking with. This gives the install the business's own name and pins
 * start_url/scope to that business, which is what someone who taps "add to
 * home screen" on a booking page is actually asking for.
 *
 * Served as a route handler rather than Next's manifest.ts convention, because
 * that convention is root-only and cannot vary per route.
 */
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ slug: string }> },
) {
  const { slug } = await params;
  const code = slug.replace(/^Noobatyar-/i, '');

  let title: string | null = null;
  try {
    const business = await getBusinessByCode(code);
    title = business.title;
  } catch {
    // Fall through to the generic name: a manifest that 404s would make the
    // page not installable at all, which is worse than a generic label.
  }

  const start = `/b/${slug}`;

  return Response.json(
    {
      name: title ? `${title} | نوبت‌یار` : 'نوبت‌یار | رزرو آنلاین نوبت',
      short_name: title ?? 'نوبت‌یار',
      description: title
        ? `رزرو آنلاین نوبت در ${title}`
        : 'سیستم نوبت‌دهی آنلاین نوبت‌یار — از هر کجا، هر زمان نوبت بگیرید',
      start_url: start,
      scope: start,
      display: 'standalone',
      dir: 'rtl',
      lang: 'fa',
      background_color: '#f9fafb',
      theme_color: '#7c3aed',
      icons: [
        { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
        { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
        {
          // Full-bleed square: Android crops installed icons to the launcher's
          // own shape and would slice into a rounded icon's artwork.
          src: '/icons/icon-maskable-512.png',
          sizes: '512x512',
          type: 'image/png',
          purpose: 'maskable',
        },
      ],
    },
    { headers: { 'Content-Type': 'application/manifest+json' } },
  );
}
