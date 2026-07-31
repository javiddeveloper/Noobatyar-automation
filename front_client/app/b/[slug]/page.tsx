import { notFound } from 'next/navigation';
import { getBusinessByCode, categoryLabel, type Business } from '@/lib/api';
import BusinessProfileClient from './BusinessProfileClient';

interface Props {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const code = slug.replace(/^Noobatyar-/i, '');
  // Override the root manifest so "add to home screen" installs *this*
  // business under its own name, opening straight onto its booking page.
  const manifest = `/b/${slug}/manifest.webmanifest`;
  try {
    const biz = await getBusinessByCode(code);
    return {
      title: `${biz.title} | نوبت‌یار`,
      description: `رزرو آنلاین نوبت در ${biz.title}. ${categoryLabel(biz.category)}. ${biz.address || ''}`,
      manifest,
    };
  } catch {
    return { title: 'نوبت‌یار', manifest };
  }
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

  return <BusinessProfileClient business={business} slug={slug} />;
}
