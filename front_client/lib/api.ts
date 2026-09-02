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
  /** Owner-posted advisory (closure, emergency, running late) is live.
   *  Independent of `booking_enabled`: a business can post a notice and still
   *  take bookings. */
  notice_enabled: boolean;
  /** The advisory text, max 300 chars. The backend sends an empty string
   *  whenever `notice_enabled` is false, so stale text never reaches us.
   *  Still nullable here to tolerate older payloads. */
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
  /** True when the business has a Zibal merchant, so checkout can redirect to a
   *  real gateway instead of the manual link + tracking-number flow. */
  online_gateway_enabled?: boolean;
  /** The services this business offers, defined by the owner in the app. Shown
   *  as chips at booking time so "what are you coming for?" is an answer the
   *  owner can plan a slot length around instead of free text. Optional here to
   *  tolerate older payloads. */
  services?: string[];
  /** Whether the client may add a service that isn't on `services`. Off unless
   *  the owner turned it on in their business settings — the backend rejects
   *  off-menu names either way, so this only decides whether we offer the box. */
  allow_client_add_service?: boolean;
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
  /** Whether this appointment's reminder was actually delivered on each
   *  channel — independent booleans, since a business can have one, both,
   *  or neither enabled (see backend/appointment/client_serializers.py). */
  reminder_sms_sent: boolean;
  reminder_push_sent: boolean;
}

/** Thrown when the API rejects the stored token (HTTP 401). */
export class UnauthorizedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UnauthorizedError';
  }
}

/**
 * A failed API call that still carries its HTTP status. Callers need the status
 * to tell "this resource is not served to the public" (404) apart from "the
 * network or the server is having a bad day", which look identical once the
 * response has been flattened to a message string.
 */
export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * True when the API answered 404. The public surface returns 404 for a business
 * it does not serve, without saying why — so treat it as "not available", never
 * as a bug to report.
 */
export function isNotFound(err: unknown): boolean {
  return err instanceof ApiError && err.status === 404;
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
  localStorage.removeItem('visitor_token');
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

  // A rejected credential means the token expired or was revoked: drop it and
  // send the user back to login. (Public endpoints — e.g. the OTP flow — never
  // carry an Authorization header, so they won't trigger this.)
  //
  // 403 counts too, not just 401. DRF returns 403 for an authentication failure
  // unless an authenticator advertises a WWW-Authenticate challenge, and every
  // endpoint on this surface is scoped to the caller's own visitor record — so
  // there is no "signed in but forbidden" state a visitor can legitimately hit.
  // Treating 403 as recoverable is what keeps a stale token from becoming a dead
  // end with no way to sign out.
  if (res.status === 401 || (res.status === 403 && hasAuthHeader(extraHeaders))) {
    if (hasAuthHeader(extraHeaders)) {
      forceReLogin();
    } else if (typeof window !== 'undefined') {
      localStorage.removeItem('visitor_token');
    }
    throw new UnauthorizedError('نشست شما منقضی شده است. لطفاً دوباره وارد شوید.');
  }

  // An error response is not guaranteed to be JSON — a 404 from the proxy or a
  // 500 from the WSGI layer arrives as HTML. Parsing that used to throw a raw
  // SyntaxError, which then surfaced to the user as an English parser message.
  let json: { status?: string; message?: string; data?: unknown } | null = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }

  if (!res.ok || json?.status === 'error') {
    // Prefer the server's own Persian message — for a refused booking that
    // message is the whole point ("این کسب‌وکار در حال حاضر نوبت نمی‌پذیرد");
    // replacing it with a generic string would leave the user with no idea
    // what happened.
    throw new ApiError(json?.message || 'خطا در ارتباط با سرور', res.status);
  }
  return json?.data as T;
}

// ── Auth: OTP Flow ──────────────────────────────────────────────────────────
// These hit the client-only auth surface (/api/client/auth/...), which only
// ever creates/returns a Visitor — never a full owner-app `User` account. See
// backend/visitor/client_auth_views.py.
export async function sendOtp(phone: string): Promise<{ expires_in: number }> {
  return apiFetch('/api/client/auth/otp/send/', {
    method: 'POST',
    body: JSON.stringify({ phone }),
  });
}

export interface Visitor {
  id: number;
  full_name: string;
  phone_number: string;
}

export interface OtpVerifyResult {
  is_registered: boolean;
  // If registered:
  visitor?: Visitor;
  token?: string;
  // If new visitor:
  register_token?: string;
  expires_in?: number;
}

export async function verifyOtp(phone: string, code: string): Promise<OtpVerifyResult> {
  return apiFetch<OtpVerifyResult>('/api/client/auth/otp/verify/', {
    method: 'POST',
    body: JSON.stringify({ phone, code }),
  });
}

export async function completeRegister(
  phone: string,
  register_token: string,
  name: string
): Promise<{ visitor: Visitor; token: string }> {
  return apiFetch('/api/client/auth/register/', {
    method: 'POST',
    body: JSON.stringify({ phone, register_token, name }),
  });
}

// Business
export async function getBusinessByCode(code: string): Promise<Business> {
  const data = await apiFetch<{
    results: Business[];
    count: number;
  }>(`/api/client/business/?search=${encodeURIComponent(code)}`);
  // Matched case-insensitively, like the server-side `unique_code__iexact`
  // lookup that produced these results: codes can be set by hand in the admin
  // and may contain lowercase letters, so a URL typed in another casing must
  // still land on the business the API already found.
  const biz = data.results?.find(
    (b) => b.unique_code.toLowerCase() === code.toLowerCase()
  );
  // The public listing only contains businesses the API is willing to serve, so
  // "absent from the results" is the same condition as a 404 on the detail
  // endpoint — report it as one, so callers get the not-available screen rather
  // than a load-failure screen.
  if (!biz) throw new ApiError('کسب‌وکار یافت نشد', 404);
  return biz;
}

export async function getBusinessById(id: number): Promise<Business> {
  return apiFetch<Business>(`/api/client/business/${id}/`);
}

/**
 * Every business the public listing is willing to serve, walked page by page.
 *
 * Only sitemap.xml needs this. The API caps page_size at 100, so a crawl of the
 * whole directory is a handful of requests, and `maxPages` stops it from
 * turning into an unbounded loop if the server ever reports a total_pages it
 * cannot actually deliver. Failures are swallowed per page rather than thrown:
 * a sitemap that is short by one page is a far better outcome for a crawler
 * than a 500, which Google treats as "this site has no sitemap".
 */
export async function listAllPublicBusinesses(maxPages = 50): Promise<Business[]> {
  const out: Business[] = [];
  let page = 1;
  let totalPages = 1;

  while (page <= totalPages && page <= maxPages) {
    try {
      const data = await apiFetch<{
        results: Business[];
        total_pages: number;
      }>(`/api/client/business/?page=${page}&page_size=100`);
      out.push(...(data.results ?? []));
      totalPages = data.total_pages ?? 1;
    } catch {
      break;
    }
    page += 1;
  }
  return out;
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
  token?: string,
  selectedServices?: string[]
) {
  return apiFetch<{ id: number; requires_payment?: boolean }>('/api/client/appointments/', {
    method: 'POST',
    headers: token ? { Authorization: `Visitor ${token}` } : {},
    body: JSON.stringify({
      business_id: businessId,
      appointment_date: appointmentDate,
      ...(serviceDuration && { service_duration: serviceDuration }),
      ...(description && { description }),
      ...(selectedServices?.length && { selected_services: selectedServices }),
    }),
  });
}

/** One row of the visitor's own activity log. */
export interface ActivityEntry {
  id: number;
  action: string;
  action_label: string;
  actor_type: 'VISITOR' | 'OWNER' | 'SYSTEM';
  actor_label: string;
  business: number | null;
  business_title: string | null;
  appointment: number | null;
  detail: Record<string, unknown>;
  created_at: string;
}

/** The signed-in visitor's own identity. */
export async function getMe(token: string): Promise<Visitor> {
  return apiFetch<Visitor>('/api/client/auth/me/', {
    headers: { Authorization: `Visitor ${token}` },
  });
}

/** Register (or refresh) this browser's FCM token against the signed-in visitor. */
export async function registerDeviceToken(deviceToken: string, visitorToken: string): Promise<void> {
  await apiFetch('/api/client/auth/devices/register/', {
    method: 'POST',
    headers: { Authorization: `Visitor ${visitorToken}` },
    body: JSON.stringify({ token: deviceToken }),
  });
}

export async function getMyActivity(token: string): Promise<ActivityEntry[]> {
  // Paginated like the appointments endpoint; tolerate a bare array too.
  const data = await apiFetch<ActivityEntry[] | { results: ActivityEntry[] }>(
    '/api/client/activity/',
    { headers: { Authorization: `Visitor ${token}` } }
  );
  return Array.isArray(data) ? data : data.results ?? [];
}

export async function getMyAppointments(token: string): Promise<Appointment[]> {
  // The endpoint is paginated ({ results: [...] }); tolerate a bare array too.
  const data = await apiFetch<Appointment[] | { results: Appointment[] }>(
    '/api/client/appointments/',
    { headers: { Authorization: `Visitor ${token}` } }
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
    headers: { Authorization: `Visitor ${token}` },
    body: JSON.stringify({ payment_reference: paymentReference, method: paymentMethod }),
  });
}

/**
 * Opens a Zibal payment for the deposit and returns the gateway URL to send the
 * client to. Only works when the business configured a Zibal merchant id;
 * businesses that only set a static payment_link keep the manual flow instead.
 */
export async function startOnlineDeposit(id: number, token: string) {
  return apiFetch<{ payment_url: string; track_id: string }>(
    `/api/client/appointments/${id}/pay/online/`,
    {
      method: 'POST',
      headers: { Authorization: `Visitor ${token}` },
    },
  );
}

export async function cancelAppointment(id: number, token: string) {
  return apiFetch<{ id: number }>(`/api/client/appointments/${id}/cancel/`, {
    method: 'POST',
    headers: { Authorization: `Visitor ${token}` },
  });
}

export async function payAppointmentWithReceipt(id: number, formData: FormData, token: string) {
  const res = await fetch(`${BASE_URL}/api/client/appointments/${id}/pay/`, {
    method: 'POST',
    headers: {
      Authorization: `Visitor ${token}`,
    },
    body: formData,
  });
  // Same 401-or-403 rule as apiFetch(); this upload always carries a token.
  if (res.status === 401 || res.status === 403) {
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

/**
 * Persian label for a business category.
 *
 * Mirrors `Business.CATEGORY_CHOICES` in backend/business/models.py, which is
 * the source of truth. An unknown key falls through to the raw value rather
 * than "سایر": a category this build predates should still read as *something*
 * specific, and showing the code makes the drift obvious instead of quietly
 * mislabelling every new business as "other".
 */
export function categoryLabel(category: string): string {
  const map: Record<string, string> = {
    // سلامت و درمان
    DOCTOR: 'پزشک و کلینیک',
    DENTIST: 'دندان‌پزشکی',
    PSYCHOLOGY: 'روان‌شناسی و روان‌پزشکی',
    PHYSIOTHERAPY: 'فیزیوتراپی و توان‌بخشی',
    NUTRITION: 'تغذیه و رژیم‌درمانی',
    LABORATORY: 'آزمایشگاه و تصویربرداری',
    OPTOMETRY: 'بینایی‌سنجی و عینک',
    SPEECH_THERAPY: 'گفتاردرمانی',
    MIDWIFERY: 'مامایی و سلامت بانوان',
    VETERINARY: 'دامپزشکی',
    // زیبایی و آرایش
    BEAUTY_SALON: 'آرایشگاه و سالن زیبایی',
    BARBERSHOP: 'آرایشگاه مردانه',
    NAIL_SALON: 'سالن ناخن',
    SKIN_LASER: 'پوست، مو و لیزر',
    TATTOO: 'تتو و میکروپیگمنتیشن',
    MASSAGE_SPA: 'ماساژ و اسپا',
    // خدمات حرفه‌ای
    CONSULTANT: 'مشاوره',
    LAWYER: 'وکالت و مشاوره حقوقی',
    ACCOUNTING: 'حسابداری و مالیات',
    REAL_ESTATE: 'املاک و مستغلات',
    INSURANCE: 'بیمه',
    IMMIGRATION: 'مهاجرت و ویزا',
    // آموزش و ورزش
    TUTORING: 'تدریس و کلاس خصوصی',
    LANGUAGE_SCHOOL: 'آموزشگاه زبان',
    MUSIC_SCHOOL: 'آموزش موسیقی',
    GYM: 'باشگاه ورزشی و مربی شخصی',
    YOGA_PILATES: 'یوگا و پیلاتس',
    DRIVING_SCHOOL: 'آموزشگاه رانندگی',
    // خدمات فنی و تعمیرات
    AUTO_SERVICE: 'تعمیرگاه و خدمات خودرو',
    CAR_WASH: 'کارواش و دیتیلینگ',
    HOME_SERVICE: 'خدمات و تعمیرات منزل',
    DEVICE_REPAIR: 'تعمیر موبایل و لوازم برقی',
    TAILORING: 'خیاطی و طراحی لباس',
    // سایر
    PHOTOGRAPHY: 'عکاسی و آتلیه',
    EVENT_SERVICES: 'تالار و خدمات مراسم',
    PET_GROOMING: 'آرایش و نگهداری حیوانات',
    OTHER: 'سایر',
  };
  return map[category] || category;
}

