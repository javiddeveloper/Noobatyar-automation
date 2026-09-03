/**
 * Thin line-art for the business hero, chosen by category.
 *
 * Replaces the two decorative blobs the hero used to draw. A blob said
 * nothing; a stethoscope or a pair of scales tells a visitor what the business
 * does before they have read a word — which matters most on the booking page,
 * where a lot of arrivals are from a shared link with no other context.
 *
 * ── Why art is keyed to a family, not to each category ──────────────────────
 *
 * There are ~68 categories. Drawing 68 distinct marks would mean 50 of them
 * being near-identical variations nobody can tell apart at 30% opacity behind
 * a logo, and every future category arriving with no art at all. Each drawing
 * here covers a family (all dental, all legal, all automotive…), which keeps
 * every mark visually distinct and means a new category inherits sensible art
 * the moment the backend adds it.
 *
 * All strokes, no fills: the mark sits *behind* the title and avatar, and a
 * filled shape at this size reads as a smudge rather than a drawing.
 */

type ArtKey =
  | 'stethoscope'
  | 'tooth'
  | 'scissors'
  | 'nails'
  | 'scales'
  | 'chart'
  | 'book'
  | 'dumbbell'
  | 'wrench'
  | 'car'
  | 'camera'
  | 'paw'
  | 'flask'
  | 'eye'
  | 'sparkle';

/**
 * Category → art family. Anything absent falls back to `sparkle`, which is
 * deliberately generic: a business whose category this build predates gets a
 * neutral decorative mark rather than a wrong one.
 */
const ART_FOR_CATEGORY: Record<string, ArtKey> = {
  // پزشکی
  DOCTOR: 'stethoscope',
  GENERAL_PRACTITIONER: 'stethoscope',
  INTERNAL_MEDICINE: 'stethoscope',
  CARDIOLOGY: 'stethoscope',
  ORTHOPEDICS: 'stethoscope',
  PEDIATRICS: 'stethoscope',
  GYNECOLOGY: 'stethoscope',
  ENT: 'stethoscope',
  NEUROLOGY: 'stethoscope',
  UROLOGY: 'stethoscope',
  ENDOCRINOLOGY: 'stethoscope',
  GASTROENTEROLOGY: 'stethoscope',
  ONCOLOGY: 'stethoscope',
  SURGERY: 'stethoscope',
  INFERTILITY: 'stethoscope',
  PSYCHIATRY: 'stethoscope',
  MIDWIFERY: 'stethoscope',
  NURSING: 'stethoscope',
  DERMATOLOGY: 'sparkle',
  OPHTHALMOLOGY: 'eye',
  OPTOMETRY: 'eye',
  // دندان‌پزشکی
  DENTIST: 'tooth',
  ORTHODONTICS: 'tooth',
  DENTAL_IMPLANT: 'tooth',
  PEDIATRIC_DENTISTRY: 'tooth',
  // آزمایشگاه و درمان
  LABORATORY: 'flask',
  RADIOLOGY: 'flask',
  PHARMACY: 'flask',
  PHYSIOTHERAPY: 'dumbbell',
  OCCUPATIONAL_THERAPY: 'dumbbell',
  NUTRITION: 'chart',
  SPEECH_THERAPY: 'book',
  PSYCHOLOGY: 'book',
  VETERINARY: 'paw',
  // زیبایی
  BEAUTY_SALON: 'scissors',
  WOMENS_SALON: 'scissors',
  BARBERSHOP: 'scissors',
  HAIR_SALON: 'scissors',
  HAIR_TRANSPLANT: 'sparkle',
  MAKEUP: 'sparkle',
  BRIDAL: 'sparkle',
  NAIL_SALON: 'nails',
  EYEBROW_LASH: 'eye',
  SKIN_LASER: 'sparkle',
  TATTOO: 'sparkle',
  MASSAGE_SPA: 'sparkle',
  SLIMMING: 'dumbbell',
  // حرفه‌ای
  CONSULTANT: 'chart',
  LAWYER: 'scales',
  ACCOUNTING: 'chart',
  REAL_ESTATE: 'chart',
  INSURANCE: 'scales',
  IMMIGRATION: 'scales',
  // آموزش و ورزش
  TUTORING: 'book',
  LANGUAGE_SCHOOL: 'book',
  MUSIC_SCHOOL: 'book',
  GYM: 'dumbbell',
  YOGA_PILATES: 'dumbbell',
  DRIVING_SCHOOL: 'car',
  // فنی
  AUTO_SERVICE: 'car',
  CAR_WASH: 'car',
  HOME_SERVICE: 'wrench',
  DEVICE_REPAIR: 'wrench',
  TAILORING: 'scissors',
  // سایر
  PHOTOGRAPHY: 'camera',
  EVENT_SERVICES: 'sparkle',
  PET_GROOMING: 'paw',
  OTHER: 'sparkle',
};

/** Line drawings on a 100×100 canvas, stroke-only. */
const ART: Record<ArtKey, React.ReactNode> = {
  stethoscope: (
    <>
      <path d="M28 14v20a16 16 0 0 0 32 0V14" />
      <path d="M22 14h12M54 14h12" />
      <path d="M44 50v10a16 16 0 0 0 32 0v-6" />
      <circle cx="76" cy="40" r="9" />
      <circle cx="76" cy="40" r="3" />
    </>
  ),
  tooth: (
    <>
      <path d="M26 34c0-11 8-18 17-18 5 0 8 2 11 2s6-2 11-2c9 0 17 7 17 18 0 14-6 20-9 34-2 9-9 10-11 2-2-8-2-16-8-16s-6 8-8 16c-2 8-9 7-11-2-3-14-9-20-9-34z" />
      <path d="M40 30c4-2 8-2 12 0" />
    </>
  ),
  scissors: (
    <>
      <circle cx="26" cy="74" r="10" />
      <circle cx="70" cy="74" r="10" />
      <path d="M34 67 74 18M62 67 22 18" />
      <path d="M48 44 60 30" />
    </>
  ),
  nails: (
    <>
      <path d="M32 78c-4-16-4-34 0-46 3-9 15-9 18 0 4 12 4 30 0 46z" />
      <path d="M33 46h16" />
      <path d="M62 70c8-4 14-12 14-22" />
      <path d="M62 82c14-5 22-16 22-30" />
    </>
  ),
  scales: (
    <>
      <path d="M50 16v66M32 82h36" />
      <path d="M20 34h60M50 22l-30 12M50 22l30 12" />
      <path d="M8 56a12 12 0 0 0 24 0zM20 34 8 56M20 34l12 22" />
      <path d="M68 56a12 12 0 0 0 24 0zM80 34 68 56M80 34l12 22" />
    </>
  ),
  chart: (
    <>
      <path d="M18 82V22M18 82h64" />
      <path d="M30 68v-14M46 68V38M62 68V28M76 68V46" />
      <path d="M28 34l16-10 14 8 18-16" />
    </>
  ),
  book: (
    <>
      <path d="M50 30C42 22 30 20 18 22v50c12-2 24 0 32 8 8-8 20-10 32-8V22c-12-2-24 0-32 8z" />
      <path d="M50 30v50" />
    </>
  ),
  dumbbell: (
    <>
      <path d="M18 40v20M28 32v36M72 32v36M82 40v20" />
      <path d="M28 50h44" />
    </>
  ),
  wrench: (
    <>
      <path d="M70 20a16 16 0 0 0-20 20L26 64a8 8 0 0 0 12 12l24-24a16 16 0 0 0 20-20L70 44 58 32z" />
    </>
  ),
  car: (
    <>
      <path d="M16 60v-8l8-18a6 6 0 0 1 6-4h40a6 6 0 0 1 6 4l8 18v8" />
      <path d="M16 60h68v10H16z" />
      <circle cx="32" cy="70" r="7" />
      <circle cx="68" cy="70" r="7" />
      <path d="M24 44h52" />
    </>
  ),
  camera: (
    <>
      <path d="M16 34h14l6-8h28l6 8h14v42H16z" />
      <circle cx="50" cy="52" r="14" />
      <circle cx="50" cy="52" r="6" />
    </>
  ),
  paw: (
    <>
      <ellipse cx="30" cy="38" rx="8" ry="11" />
      <ellipse cx="48" cy="30" rx="8" ry="11" />
      <ellipse cx="66" cy="38" rx="8" ry="11" />
      <ellipse cx="78" cy="56" rx="7" ry="9" />
      <path d="M36 68c0-10 8-16 16-16s16 6 16 16c0 8-7 12-16 12s-16-4-16-12z" />
    </>
  ),
  flask: (
    <>
      <path d="M42 16v26L24 74a6 6 0 0 0 5 9h42a6 6 0 0 0 5-9L58 42V16" />
      <path d="M38 16h24M33 58h34" />
    </>
  ),
  eye: (
    <>
      <path d="M10 50s16-22 40-22 40 22 40 22-16 22-40 22S10 50 10 50z" />
      <circle cx="50" cy="50" r="12" />
      <circle cx="50" cy="50" r="4" />
    </>
  ),
  sparkle: (
    <>
      <path d="M50 14l7 22 22 7-22 7-7 22-7-22-22-7 22-7z" />
      <path d="M78 62l3 9 9 3-9 3-3 9-3-9-9-3 9-3z" />
      <path d="M22 20l2 7 7 2-7 2-2 7-2-7-7-2 7-2z" />
    </>
  ),
};

export function artKeyForCategory(category: string): ArtKey {
  return ART_FOR_CATEGORY[category] ?? 'sparkle';
}

/**
 * One decorative mark. Positioned and sized by the caller via `className`;
 * `aria-hidden` because it is ornament — the category is already stated in
 * words right beside it.
 */
export default function CategoryArt({
  category,
  className,
}: {
  category: string;
  className?: string;
}) {
  return (
    <svg
      className={className}
      viewBox="0 0 100 100"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {ART[artKeyForCategory(category)]}
    </svg>
  );
}
