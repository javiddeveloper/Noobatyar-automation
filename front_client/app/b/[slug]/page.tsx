import { notFound } from 'next/navigation';
import { getBusinessByCode, categoryLabel, type Business } from '@/lib/api';
import BusinessProfileClient from './BusinessProfileClient';

interface Props {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const code = slug.replace(/^Noobatyar-/i, '');
  try {
    const biz = await getBusinessByCode(code);
    return {
      title: `${biz.title} | نوبت‌یار`,
      description: `رزرو آنلاین نوبت در ${biz.title}. ${categoryLabel(biz.category)}. ${biz.address || ''}`,
    };
  } catch {
    return { title: 'نوبت‌یار' };
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
