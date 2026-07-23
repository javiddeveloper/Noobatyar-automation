'use client';

import { Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

// Registration is now handled in the unified OTP login flow.
// This page simply redirects to login.
function RegisterRedirect() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/appointments';

  if (typeof window !== 'undefined') {
    router.replace(`/auth/login?redirect=${encodeURIComponent(redirect)}`);
  }

  return (
    <div style={{ padding: 40, textAlign: 'center', color: 'var(--color-muted)', fontSize: 14 }}>
      در حال انتقال...
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense>
      <RegisterRedirect />
    </Suspense>
  );
}
