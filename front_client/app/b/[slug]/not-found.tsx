import BusinessUnavailable from '@/app/components/BusinessUnavailable';

/**
 * Shown when a business code does not resolve — the page a customer lands on
 * from a stale QR code, a mistyped link, or a business the public API no
 * longer serves. Next's stock 404 is a bare line of text, which for a booking
 * link someone scanned in a shop reads as "the site is broken" rather than
 * "this particular link is out of date".
 *
 * The markup lives in BusinessUnavailable so the booking page, which hits the
 * same wall client-side, shows the identical screen.
 */
export default function BusinessNotFound() {
  return <BusinessUnavailable />;
}
