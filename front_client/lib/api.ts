// API client for Noobatyar backend
const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

export interface Business {
  id: number;
  title: string;
  category: string;
  unique_code: string;
  phone: string | null;
  address: string | null;
  logo: string | null;
  default_service_duration: number;
  work_start_hour: number;
  work_end_hour: number;
  allow_anonymous_view: boolean;
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

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  const json = await res.json();
  if (!res.ok || json.status === 'error') {
    throw new Error(json.message || 'خطا در ارتباط با سرور');
  }
  return json.data as T;
}

// Auth
export async function registerUser(phone: string, password: string, name: string) {
  return apiFetch('/api/auth/register/', {
    method: 'POST',
    body: JSON.stringify({ phone, password, name }),
  });
}

export async function loginUser(phone: string, password: string) {
  return apiFetch<{ user: object; tokens: { access: string; refresh: string } }>(
    '/api/auth/login/',
    { method: 'POST', body: JSON.stringify({ phone, password }) }
  );
}

// Business
export async function getBusinessByCode(code: string): Promise<Business> {
  // Search by unique_code
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
  return apiFetch<{ id: number }>('/api/client/appointments/', {
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
  return apiFetch<Appointment[]>('/api/client/appointments/', {
    headers: { Authorization: `Bearer ${token}` },
  });
}

// Helpers
export function businessUrl(code: string) {
  return `/b/Noobatyar-${code}`;
}

export function extractCode(slug: string): string {
  // "Noobatyar-XVCTY" → "XVCTY"
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
