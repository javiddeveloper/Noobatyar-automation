/**
 * The decorative mark on the business hero, chosen by category.
 *
 * It replaces the two anonymous blobs the hero used to draw. A blob said
 * nothing; a stethoscope or a pair of scales tells a visitor what the business
 * does before they have read a word — which matters most on the booking page,
 * where a lot of arrivals come from a shared link with no other context.
 *
 * ── Why Tabler and not hand-drawn paths ─────────────────────────────────────
 *
 * These marks were hand-authored SVG. Drawing an icon set to a professional
 * standard is a design job, and by hand the results were crude: a lash studio
 * got a clinical eyeball, a nail salon got a finger next to two stray arcs.
 * @tabler/icons-react (MIT) is a professionally drawn set of ~12,000 icons on
 * one grid with one stroke discipline, so every mark is crisp and consistent
 * with every other — the quality the hand-drawn set could not reach.
 *
 * It is a normal npm dependency, inlined into the bundle at build time, so
 * nothing is fetched at runtime and the offline PWA is unaffected. Icons are
 * imported by name and `optimizePackageImports` in next.config.ts keeps the
 * barrel import from pulling in the whole set.
 *
 * ── Why one icon per category, not per family ───────────────────────────────
 *
 * The hand-drawn set had to lump ~68 categories into 16 families, because
 * hand-drawing 68 marks was not realistic. With a set this size that
 * constraint is gone: an orthopaedist gets a bone, a cardiologist a heartbeat,
 * a barber a razor rather than all of them sharing a generic mark. Anything
 * unmapped still falls back to a neutral sparkle rather than a wrong icon, so
 * a category added on the server degrades gracefully instead of breaking.
 */

import { createElement, type ComponentType } from 'react';
import {
  IconApple,
  IconBabyCarriage,
  IconBarbell,
  IconBone,
  IconBrain,
  IconBriefcase,
  IconBrush,
  IconCalculator,
  IconCamera,
  IconCar,
  IconCarGarage,
  IconConfetti,
  IconDental,
  IconDeviceMobile,
  IconDog,
  IconDroplet,
  IconEar,
  IconEye,
  IconEyeClosed,
  IconEyeglass,
  IconFlask2,
  IconFlower,
  IconHeartbeat,
  IconHome,
  IconLanguage,
  IconMassage,
  IconMedicalCross,
  IconMessageCircle,
  IconMoodKid,
  IconMusic,
  IconNeedle,
  IconNeedleThread,
  IconNurse,
  IconPaw,
  IconPerfume,
  IconPill,
  IconPlane,
  IconRadioactive,
  IconRazor,
  IconRibbonHealth,
  IconScale,
  IconSchool,
  IconScissors,
  IconShield,
  IconSparkles,
  IconStethoscope,
  IconStretching,
  IconStretching2,
  IconTools,
  IconVaccineBottle,
  IconWash,
  IconWeight,
  IconYoga,
} from '@tabler/icons-react';

type TablerIcon = ComponentType<{
  className?: string;
  stroke?: number;
  'aria-hidden'?: boolean;
}>;

/**
 * Category → mark. Anything absent falls back to `IconSparkles`, which is
 * deliberately generic: a business whose category this build predates gets a
 * neutral decorative mark rather than a confidently wrong one.
 */
const ART_FOR_CATEGORY: Record<string, TablerIcon> = {
  // ── پزشکی و تخصص‌ها ──
  DOCTOR: IconStethoscope,
  GENERAL_PRACTITIONER: IconStethoscope,
  INTERNAL_MEDICINE: IconStethoscope,
  CARDIOLOGY: IconHeartbeat,
  DERMATOLOGY: IconSparkles,
  ORTHOPEDICS: IconBone,
  PEDIATRICS: IconMoodKid,
  GYNECOLOGY: IconBabyCarriage,
  ENT: IconEar,
  OPHTHALMOLOGY: IconEye,
  NEUROLOGY: IconBrain,
  UROLOGY: IconDroplet,
  ENDOCRINOLOGY: IconVaccineBottle,
  GASTROENTEROLOGY: IconApple,
  ONCOLOGY: IconRibbonHealth,
  SURGERY: IconMedicalCross,
  PSYCHIATRY: IconBrain,
  INFERTILITY: IconBabyCarriage,
  MIDWIFERY: IconBabyCarriage,
  NURSING: IconNurse,
  // ── دندان‌پزشکی ──
  DENTIST: IconDental,
  ORTHODONTICS: IconDental,
  DENTAL_IMPLANT: IconDental,
  PEDIATRIC_DENTISTRY: IconDental,
  // ── سلامت و درمان ──
  PSYCHOLOGY: IconBrain,
  PHYSIOTHERAPY: IconStretching,
  OCCUPATIONAL_THERAPY: IconStretching2,
  NUTRITION: IconApple,
  LABORATORY: IconFlask2,
  RADIOLOGY: IconRadioactive,
  OPTOMETRY: IconEyeglass,
  SPEECH_THERAPY: IconMessageCircle,
  PHARMACY: IconPill,
  VETERINARY: IconPaw,
  // ── آرایش و زیبایی ──
  BEAUTY_SALON: IconScissors,
  WOMENS_SALON: IconScissors,
  BARBERSHOP: IconRazor,
  HAIR_SALON: IconScissors,
  HAIR_TRANSPLANT: IconNeedle,
  MAKEUP: IconBrush,
  BRIDAL: IconFlower,
  NAIL_SALON: IconPerfume,
  // A closed lid with lashes, not the open clinical eyeball: this is a lash
  // and brow studio, and an ophthalmologist's diagram was the wrong trade.
  EYEBROW_LASH: IconEyeClosed,
  SKIN_LASER: IconSparkles,
  TATTOO: IconNeedleThread,
  MASSAGE_SPA: IconMassage,
  SLIMMING: IconWeight,
  // ── خدمات حرفه‌ای ──
  CONSULTANT: IconBriefcase,
  LAWYER: IconScale,
  ACCOUNTING: IconCalculator,
  REAL_ESTATE: IconHome,
  INSURANCE: IconShield,
  IMMIGRATION: IconPlane,
  // ── آموزش و ورزش ──
  TUTORING: IconSchool,
  LANGUAGE_SCHOOL: IconLanguage,
  MUSIC_SCHOOL: IconMusic,
  GYM: IconBarbell,
  YOGA_PILATES: IconYoga,
  DRIVING_SCHOOL: IconCar,
  // ── خدمات فنی و تعمیرات ──
  AUTO_SERVICE: IconCarGarage,
  CAR_WASH: IconWash,
  HOME_SERVICE: IconTools,
  DEVICE_REPAIR: IconDeviceMobile,
  TAILORING: IconNeedleThread,
  // ── سایر ──
  PHOTOGRAPHY: IconCamera,
  EVENT_SERVICES: IconConfetti,
  PET_GROOMING: IconDog,
  OTHER: IconSparkles,
};

export function artIconForCategory(category: string): TablerIcon {
  return ART_FOR_CATEGORY[category] ?? IconSparkles;
}

/** Every mapped category, for previewing the set side by side. */
export const ART_CATEGORIES = Object.keys(ART_FOR_CATEGORY);

/**
 * One decorative mark. Sized and positioned by the caller via `className`;
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
  // createElement rather than <Icon />: assigning the looked-up component to a
  // capitalized local trips react-hooks' "component created during render"
  // rule, which cannot tell a table lookup from a freshly defined component.
  // The identity here is stable — it comes straight out of a module-level map —
  // so the warning is a false positive, and this form sidesteps it without
  // suppressing a rule that is worth keeping on elsewhere.
  //
  // Tabler draws at stroke-width 2 on a 24px grid. Scaled up to the hero's
  // 132px that reads as a heavy marker line, so it is thinned here — the mark
  // sits behind the title and should look drawn, not stamped.
  return createElement(artIconForCategory(category), {
    className,
    stroke: 1.25,
    'aria-hidden': true,
  });
}
