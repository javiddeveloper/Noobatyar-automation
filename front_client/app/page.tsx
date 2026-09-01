import HomeClient from './HomeClient';

export const metadata = {
  title: 'نوبت‌یار | رزرو آنلاین نوبت',
  description: 'با اپلیکیشن نوبت‌یار، نوبت کسب‌وکارهای مورد علاقه‌تان را آنلاین رزرو کنید',
};

/**
 * "/" is the customer's home: their own upcoming appointments, under the app
 * banner. It is not a directory — booking always starts at /b/<code>, and this
 * route must never leak an unauthenticated list of every registered business.
 *
 * The page itself stays a Server Component so the metadata above is emitted for
 * crawlers; the signed-in/signed-out split lives in HomeClient, which reads the
 * visitor token from localStorage.
 */
export default function Home() {
  return <HomeClient />;
}
