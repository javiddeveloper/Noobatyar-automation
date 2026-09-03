import { notFound } from 'next/navigation';
import { getBusinessByCode, businessCategoryLabel, mediaUrl, type Business } from '@/lib/api';
import { absoluteBusinessUrl } from '@/lib/site';
import BusinessProfileClient from './BusinessProfileClient';

interface Props {
  params: Promise<{ slug: string }>;
}

/** Business category → the closest schema.org type Google recognises.
 *  Anything unmapped falls back to the LocalBusiness base type rather than
 *  guessing: a wrong specific type is worse than a correct general one. */
const SCHEMA_TYPE: Record<string, string> = {
  // سلامت — only categories with a real schema.org LocalBusiness subtype are
  // named; the rest (nutrition, speech therapy, midwifery, physiotherapy) have
  // no such type and fall back rather than being filed under a wrong one.
  DOCTOR: 'MedicalClinic',
  DENTIST: 'Dentist',
  PSYCHOLOGY: 'Physician',
  LABORATORY: 'MedicalClinic',
  OPTOMETRY: 'Optician',
  VETERINARY: 'VeterinaryCare',
  // زیبایی
  BEAUTY_SALON: 'BeautySalon',
  BARBERSHOP: 'HairSalon',
  NAIL_SALON: 'NailSalon',
  SKIN_LASER: 'HealthAndBeautyBusiness',
  TATTOO: 'TattooParlor',
  MASSAGE_SPA: 'DaySpa',
  // خدمات حرفه‌ای
  CONSULTANT: 'ProfessionalService',
  LAWYER: 'Attorney',
  ACCOUNTING: 'AccountingService',
  REAL_ESTATE: 'RealEstateAgent',
  INSURANCE: 'InsuranceAgency',
  IMMIGRATION: 'ProfessionalService',
  // آموزش و ورزش
  TUTORING: 'School',
  LANGUAGE_SCHOOL: 'School',
  MUSIC_SCHOOL: 'School',
  DRIVING_SCHOOL: 'School',
  GYM: 'ExerciseGym',
  YOGA_PILATES: 'ExerciseGym',
  // فنی و تعمیرات
  AUTO_SERVICE: 'AutoRepair',
  CAR_WASH: 'AutoWash',
  HOME_SERVICE: 'HomeAndConstructionBusiness',
  // سایر
  OTHER: 'LocalBusiness',
};

function businessDescription(biz: Business): string {
  // bio is the owner's own words and is the only part a search result can show
  // that another business would not also say, so it leads when it exists.
  const parts = [
    biz.bio?.trim(),
    `رزرو آنلاین نوبت در ${biz.title}`,
    businessCategoryLabel(biz),
    biz.address?.trim(),
  ].filter(Boolean);
  return parts.join('. ').slice(0, 160);
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const code = slug.replace(/^Noobatyar-/i, '');
  // Override the root manifest so "add to home screen" installs *this*
  // business under its own name, opening straight onto its booking page.
  const manifest = `/b/${slug}/manifest.webmanifest`;
  try {
    const biz = await getBusinessByCode(code);
    const title = `${biz.title} | نوبت‌یار`;
    const description = businessDescription(biz);
    // Canonical is built from the *stored* code, not from the slug in the URL.
    // The slug is matched case-insensitively and the `Noobatyar-` prefix is
    // optional in practice, so several URLs reach this same page; without this
    // they compete with each other as separate results.
    const canonical = absoluteBusinessUrl(biz.unique_code);
    const logo = mediaUrl(biz.logo);

    return {
      title,
      description,
      manifest,
      alternates: { canonical },
      openGraph: {
        title,
        description,
        url: canonical,
        siteName: 'نوبت‌یار',
        locale: 'fa_IR',
        type: 'website',
        ...(logo ? { images: [{ url: logo, alt: biz.title }] } : {}),
      },
      twitter: {
        card: logo ? 'summary_large_image' : 'summary',
        title,
        description,
        ...(logo ? { images: [logo] } : {}),
      },
    };
  } catch {
    return { title: 'نوبت‌یار', manifest };
  }
}

/** schema.org LocalBusiness markup — what feeds Google's rich results.
 *
 *  Every value comes from PublicBusinessSerializer, which masks `phone` and
 *  `address` for a business with allow_anonymous_view off. A crawler is
 *  anonymous, so those arrive as null there and are simply left out, rather
 *  than the markup publishing what the page itself is hiding.
 *
 *  There is deliberately no `openingHoursSpecification`. The model stores only
 *  work_start_hour/work_end_hour with no notion of which days a business is
 *  open, and claiming Saturday–Friday would put a wrong "Open now" on the
 *  search result. It needs weekday data on the model first.
 */
function businessJsonLd(biz: Business, canonical: string) {
  const logo = mediaUrl(biz.logo);
  return {
    '@context': 'https://schema.org',
    '@type': SCHEMA_TYPE[biz.category] ?? 'LocalBusiness',
    name: biz.title,
    url: canonical,
    description: businessDescription(biz),
    ...(logo ? { image: logo, logo } : {}),
    ...(biz.phone ? { telephone: biz.phone } : {}),
    ...(biz.address
      ? {
          address: {
            '@type': 'PostalAddress',
            streetAddress: biz.address,
            addressCountry: 'IR',
          },
        }
      : {}),
    ...(biz.booking_enabled
      ? {
          potentialAction: {
            '@type': 'ReserveAction',
            target: {
              '@type': 'EntryPoint',
              urlTemplate: `${canonical}/book`,
              actionPlatform: [
                'http://schema.org/DesktopWebPlatform',
                'http://schema.org/MobileWebPlatform',
              ],
            },
            result: { '@type': 'Reservation', name: `نوبت در ${biz.title}` },
          },
        }
      : {}),
  };
}

export default async function BusinessProfilePage({ params }: Props) {
  const { slug } = await params;
  const code = slug.replace(/^Noobatyar-/i, '');

  let business: Business;
  try {
    business = await getBusinessByCode(code);
  } catch {
    notFound();
  }

  const canonical = absoluteBusinessUrl(business.unique_code);

  return (
    <>
      <script
        type="application/ld+json"
        // The payload is JSON.stringify of our own object, never raw user
        // input: a `<` inside a business title would end the script tag early,
        // so it is escaped the way schema.org markup conventionally is.
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(businessJsonLd(business, canonical)).replace(
            /</g,
            '\\u003c'
          ),
        }}
      />
      <BusinessProfileClient business={business} slug={slug} />
    </>
  );
}
