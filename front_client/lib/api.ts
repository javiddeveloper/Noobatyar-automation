// API client for Noobatyar backend
const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

// Resolves a (usually relative) media path like "/media/business_logos/x.jpg"
// into an absolute URL against the API host. Returns null when there is no path.
export function mediaUrl(path: string | null | undefined): string | null {
  if (!path) return null;
  return path.startsWith('http') ? path : `${BASE_URL}${path}`;
}

export interface Business {
  id: number;
  title: string;
  category: string;
  unique_code: string;
  bio?: string | null;
  phone: string | null;
  address: string | null;
  logo: string | null;
  default_service_duration: number;
  work_start_hour: number;
  work_end_hour: number;
  allow_anonymous_view: boolean;
  notice_message: string | null;
  booking_enabled: boolean;
  deposit_mode?: string;
  deposit_amount?: number;
  accepted_payment_methods?: string[];
  max_appointments_per_hour?: number | null;
  payment_method?: string;
  card_number?: string;
  card_owner_name?: string;
  payment_link?: string;
}

export interface TimeSlot {
  time: string;
  timestamp: number;
  available: boolean;
  status: 'AVAILABLE' | 'BOOKED' | 'PAST';
}

export interface Appointment {
  id: number;
  business: Business;
  appointment_date: number;
  status: string;
  queue_position: number;
  estimated_turn_time: number | null;
}

/** Thrown when the API rejects the stored token (HTTP 401). */
export class UnauthorizedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UnauthorizedError';
  }
}

/** True when the outgoing request carried an Authorization header. */
function hasAuthHeader(headers: HeadersInit | undefined): boolean {
  if (!headers) return false;
  if (headers instanceof Headers) return headers.has('Authorization');
  if (Array.isArray(headers)) return headers.some(([k]) => k.toLowerCase() === 'authorization');
  return Object.keys(headers).some((k) => k.toLowerCase() === 'authorization');
}

/**
 * The stored token is gone or rejected. Clear it and bounce the user to the
 * login screen, preserving where they were so they land back after signing in.
 */
export function forceReLogin(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
  // Avoid a redirect loop if we're already on the login screen.
  if (window.location.pathname.startsWith('/auth/login')) return;
  const target = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.href = `/auth/login?redirect=${target}`;
}

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  // Destructure headers separately so we can merge them with Content-Type
  // without `...options` overwriting the headers key entirely.
  const { headers: extraHeaders, ...restOptions } = options ?? {};
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...extraHeaders,
    },
    ...restOptions,
  });

  // A 401 on an authenticated request means the token expired or was revoked:
  // drop it and send the user back to login. (Public endpoints — e.g. the OTP
  // flow — never carry an Authorization header, so they won't trigger this.)
  if (res.status === 401) {
    if (hasAuthHeader(extraHeaders)) {
      forceReLogin();
    } else if (typeof window !== 'undefined') {
      localStorage.removeItem('access_token');
    }
    throw new UnauthorizedError('نشست شما منقضی شده است. لطفاً دوباره وارد شوید.');
  }

  const json = await res.json();
  if (!res.ok || json.status === 'error') {
    throw new Error(json.message || 'خطا در ارتباط با سرور');
  }
  return json.data as T;
}

// ── Auth: OTP Flow ──────────────────────────────────────────────────────────
export async function sendOtp(phone: string): Promise<{ expires_in: number }> {
  return apiFetch('/api/auth/otp/send/', {
    method: 'POST',
    body: JSON.stringify({ phone }),
  });
}

export interface OtpVerifyResult {
  is_registered: boolean;
  // If registered:
  user?: object;
  tokens?: { access: string; refresh: string };
  // If new user:
  register_token?: string;
  expires_in?: number;
}

export async function verifyOtp(phone: string, code: string): Promise<OtpVerifyResult> {
  return apiFetch<OtpVerifyResult>('/api/auth/otp/verify/', {
    method: 'POST',
    body: JSON.stringify({ phone, code }),
  });
}

export async function completeRegister(
  phone: string,
  register_token: string,
  name: string
): Promise<{ user: object; tokens: { access: string; refresh: string } }> {
  return apiFetch('/api/auth/register/', {
    method: 'POST',
    body: JSON.stringify({ phone, register_token, name }),
  });
}

// Business
export async function getBusinessByCode(code: string): Promise<Business> {
  const data = await apiFetch<{
    results: Business[];
    count: number;
  }>(`/api/client/business/?search=${code}`);
  const biz = data.results?.find((b) => b.unique_code === code);
  if (!biz) throw new Error('کسب‌وکار یافت نشد');
  return biz;
}

export async function getBusinessById(id: number): Promise<Business> {
  return apiFetch<Business>(`/api/client/business/${id}/`);
}

export async function listBusinesses(search = '', category = '', page = 1) {
  const params = new URLSearchParams({ page: String(page), page_size: '10' });
  if (search) params.set('search', search);
  if (category) params.set('category', category);
  return apiFetch<{ results: Business[]; count: number; total_pages: number }>(
    `/api/client/business/?${params}`
  );
}

// Available Slots
export async function getAvailableSlots(businessId: number, date: string): Promise<{
  slots: TimeSlot[];
  duration_minutes: number;
  date: string;
}> {
  return apiFetch(`/api/client/appointments/${businessId}/available-slots/?date=${date}`);
}

// Appointments
export async function bookAppointment(
  businessId: number,
  appointmentDate: number,
  serviceDuration?: number,
  description?: string,
  token?: string
) {
  return apiFetch<{ id: number; requires_payment?: boolean }>('/api/client/appointments/', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: JSON.stringify({
      business_id: businessId,
      appointment_date: appointmentDate,
      ...(serviceDuration && { service_duration: serviceDuration }),
      ...(description && { description }),
    }),
  });
}

export async function getMyAppointments(token: string): Promise<Appointment[]> {
  // The endpoint is paginated ({ results: [...] }); tolerate a bare array too.
  const data = await apiFetch<Appointment[] | { results: Appointment[] }>(
    '/api/client/appointments/',
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return Array.isArray(data) ? data : data.results ?? [];
}

export async function getAppointment(id: number, token: string): Promise<Appointment> {
  const all = await getMyAppointments(token);
  const apt = all.find((a) => a.id === id);
  if (!apt) throw new Error('نوبت یافت نشد');
  return apt;
}

export type PaymentMethod = 'CARD' | 'ONLINE' | 'CASH';

export async function payAppointment(
  id: number,
  paymentReference: string,
  token: string,
  paymentMethod: PaymentMethod = 'CARD',
) {
  return apiFetch<{ id: number }>(`/api/client/appointments/${id}/pay/`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ payment_reference: paymentReference, method: paymentMethod }),
  });
}

export async function cancelAppointment(id: number, token: string) {
  return apiFetch<{ id: number }>(`/api/client/appointments/${id}/cancel/`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function payAppointmentWithReceipt(id: number, formData: FormData, token: string) {
  const res = await fetch(`${BASE_URL}/api/client/appointments/${id}/pay/`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
  });
  if (res.status === 401) {
    forceReLogin();
    throw new UnauthorizedError('نشست شما منقضی شده است. لطفاً دوباره وارد شوید.');
  }
  const json = await res.json();
  if (!res.ok || json.status === 'error') {
    throw new Error(json.message || 'خطا در آپلود فیش');
  }
  return json.data;
}

// Helpers
export function businessUrl(code: string) {
  return `/b/Noobatyar-${code}`;
}

export function extractCode(slug: string): string {
  return slug.replace(/^Noobatyar-/i, '');
}

export function categoryLabel(category: string): string {
  const map: Record<string, string> = {
    BEAUTY_SALON: 'آرایشگاه و سالن زیبایی',
    DOCTOR: 'پزشک و کلینیک',
    CONSULTANT: 'مشاوره',
    OTHER: 'سایر',
  };
  return map[category] || category;
}

