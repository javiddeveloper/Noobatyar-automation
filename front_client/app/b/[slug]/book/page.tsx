'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  getBusinessByCode,
  getAvailableSlots,
  bookAppointment,
  isNotFound,
  UnauthorizedError,
  type Business,
  type TimeSlot,
} from '@/lib/api';
import BusinessUnavailable from '@/app/components/BusinessUnavailable';
import BusinessNotice from '@/app/components/BusinessNotice';
import { schedulePushPrompt } from '@/lib/push';

interface Props {
  params: Promise<{ slug: string }>;
}

const CATEGORY_EMOJI: Record<string, string> = {
  BEAUTY_SALON: '💅',
  DOCTOR: '🏥',
  CONSULTANT: '💼',
  OTHER: '🏢',
};

function toPersianNumerals(n: string): string {
  const map: Record<string, string> = {
    '0': '۰', '1': '۱', '2': '۲', '3': '۳', '4': '۴',
    '5': '۵', '6': '۶', '7': '۷', '8': '۸', '9': '۹',
  };
  return n.replace(/[0-9]/g, (d) => map[d]);
}

function toDateString(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function getNext7Days(): Date[] {
  const days: Date[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date();
    d.setDate(d.getDate() + i);
    days.push(d);
  }
  return days;
}

export default function BookingPage({ params }: Props) {
  const router = useRouter();
  const [slug, setSlug] = useState('');
  const [business, setBusiness] = useState<Business | null>(null);
  const [days] = useState<Date[]>(getNext7Days);
  const [selectedDay, setSelectedDay] = useState<Date>(days[0]);
  const [slots, setSlots] = useState<TimeSlot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<TimeSlot | null>(null);
  // What the client is coming for, picked from the owner's own service menu.
  // This is the part the owner can actually plan around; `description` stays
  // for anything that doesn't fit a chip.
  const [selectedServices, setSelectedServices] = useState<string[]>([]);
  // Off-menu service the client typed — only offered when the owner allows it.
  const [customService, setCustomService] = useState('');
  // Optional free-text note describing the requested service.
  const [description, setDescription] = useState('');
  // A slot the user picked before logging in, to be re-selected on return.
  const [pendingSlot, setPendingSlot] = useState<TimeSlot | null>(null);
  const [loading, setLoading] = useState(true);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [booking, setBooking] = useState(false);
  const [toast, setToast] = useState('');
  const [error, setError] = useState('');
  // The API stopped serving this business — either it never resolved, or it
  // dropped out from under an open tab. Either way the page becomes the same
  // neutral "not available" screen; we never learn or show the reason.
  const [unavailable, setUnavailable] = useState(false);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  useEffect(() => {
    params.then(({ slug: s }) => {
      setSlug(s);
      const code = s.replace(/^Noobatyar-/i, '');
      getBusinessByCode(code)
        .then(setBusiness)
        .catch((err: unknown) => {
          // A 404 is "we don't serve this business"; anything else (server
          // down, offline) is a genuine load failure worth retrying.
          if (isNotFound(err)) setUnavailable(true);
          else setError('خطا در بارگذاری کسب‌وکار');
        })
        .finally(() => setLoading(false));
    });
  }, [params]);

  const loadSlots = useCallback(async (day: Date) => {
    if (!business) return;
    setSlotsLoading(true);
    setSelectedSlot(null);
    try {
      const data = await getAvailableSlots(business.id, toDateString(day));
      setSlots(data.slots);
    } catch (err: unknown) {
      setSlots([]);
      // A 404 here means the business stopped being served between loading the
      // page and picking a day. "No free hours today" would be a lie — every
      // other day is empty too — so switch to the not-available screen.
      if (isNotFound(err)) setUnavailable(true);
    } finally {
      setSlotsLoading(false);
    }
  }, [business]);

  useEffect(() => {
    if (business) loadSlots(selectedDay);
  }, [business, selectedDay, loadSlots]);

  // After returning from login, restore the slot the user had picked so their
  // selection is not lost. We switch to the slot's day (if needed); the actual
  // re-selection happens once that day's slots have loaded (effect below).
  useEffect(() => {
    if (!business || !slug) return;
    const token = typeof window !== 'undefined' ? localStorage.getItem('visitor_token') : null;
    if (!token) return;
    const raw = typeof window !== 'undefined' ? localStorage.getItem('booking_intent') : null;
    if (!raw) return;

    try {
      const intent = JSON.parse(raw) as {
        slug: string; slot: TimeSlot; businessId: number; description?: string;
        selectedServices?: string[];
      };
      if (intent.slug !== slug || intent.businessId !== business.id || !intent.slot) {
        return;
      }
      if (intent.description) setDescription(intent.description);
      if (intent.selectedServices?.length) setSelectedServices(intent.selectedServices);
      const slotDay = days.find(
        (d) => toDateString(d) === toDateString(new Date(intent.slot.timestamp))
      );
      if (slotDay && toDateString(slotDay) !== toDateString(selectedDay)) {
        setSelectedDay(slotDay);
      }
      setPendingSlot(intent.slot);
    } catch {
      localStorage.removeItem('booking_intent');
    }
    // Only re-run when the business/slug becomes available, not on every day change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [business, slug]);

  // Apply the pending slot once the correct day's slots have finished loading.
  useEffect(() => {
    if (!pendingSlot || slotsLoading) return;
    if (toDateString(new Date(pendingSlot.timestamp)) !== toDateString(selectedDay)) return;

    const match = slots.find(
      (s) => s.timestamp === pendingSlot.timestamp && s.status === 'AVAILABLE'
    );
    if (match) {
      setSelectedSlot(match);
      showToast('✅ زمان انتخابی شما بازیابی شد');
    } else {
      showToast('زمان انتخابی شما دیگر در دسترس نیست، لطفاً زمان دیگری انتخاب کنید');
    }
    setPendingSlot(null);
    localStorage.removeItem('booking_intent');
  }, [pendingSlot, slotsLoading, slots, selectedDay]);

  // Park the current selection so it can be restored after signing in.
  const saveIntentAndLogin = () => {
    if (!selectedSlot || !business) return;
    localStorage.setItem('booking_intent', JSON.stringify({
      slug,
      slot: selectedSlot,
      businessId: business.id,
      description: description.trim(),
      selectedServices,
    }));
    router.push(`/auth/login?redirect=/b/${slug}/book`);
  };

  const handleBook = async () => {
    if (!selectedSlot || !business) return;

    // Check auth token
    const token = typeof window !== 'undefined' ? localStorage.getItem('visitor_token') : null;
    if (!token) {
      saveIntentAndLogin();
      return;
    }

    setBooking(true);
    try {
      const { id: appointmentId, requires_payment } = await bookAppointment(
        business.id,
        selectedSlot.timestamp,
        business.default_service_duration,
        description.trim(),
        token,
        selectedServices
      );

      // Scheduled regardless of which branch below fires — a customer who
      // still owes a deposit wants the reminder just as much as one who
      // doesn't, and the prompt itself no-ops silently if permission is
      // already decided (see PushPermissionPrompt).
      schedulePushPrompt();

      if (requires_payment) {
        showToast('✅ نوبت با موفقیت قفل شد. در حال انتقال به درگاه پرداخت...');
        setTimeout(() => router.push(`/b/${slug}/checkout/${appointmentId}`), 1500);
      } else {
        // Basic plan / no deposit — the booking is complete, no payment step.
        showToast('✅ نوبت شما ثبت شد و در انتظار تایید کسب‌وکار است.');
        setTimeout(() => router.push('/appointments'), 1500);
      }
    } catch (err: unknown) {
      if (err instanceof UnauthorizedError) {
        // Token expired between page load and booking — keep the selection
        // rather than dropping the user on the login page empty-handed.
        showToast('نشست شما منقضی شده است. لطفاً دوباره وارد شوید');
        setTimeout(saveIntentAndLogin, 1200);
        return;
      }
      if (isNotFound(err)) {
        // The business disappeared from the public surface while this tab was
        // open; there is nothing left to book against.
        setUnavailable(true);
        return;
      }
      // Everything else — including a refusal such as «این کسب‌وکار در حال
      // حاضر نوبت نمی‌پذیرد» — is the server telling the customer something in
      // Persian. Pass it through verbatim; only fall back to a generic string
      // when there genuinely is no message.
      showToast(err instanceof Error && err.message ? err.message : 'خطا در ثبت نوبت');
    } finally {
      setBooking(false);
    }
  };

  // Checked before the skeleton: a business that drops out mid-session should
  // swap to this screen straight away, not sit behind a spinner.
  if (unavailable) {
    return <BusinessUnavailable />;
  }

  if (loading) {
    return (
      <div className="page-content">
        <div className="toolbar">
          <div className="toolbar-placeholder" />
          <h1 className="toolbar-title">انتخاب زمان نوبت</h1>
          <button className="toolbar-back" onClick={() => router.back()}>›</button>
        </div>
        {/* business header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '16px 24px', borderBottom: '1px solid var(--color-border)' }}>
          <div className="skeleton" style={{ width: 48, height: 48, borderRadius: '50%', flexShrink: 0 }} />
          <div className="skeleton" style={{ height: 16, width: 140, borderRadius: 8 }} />
        </div>
        {/* date pills */}
        <div style={{ display: 'flex', gap: 8, padding: '16px 24px' }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="skeleton" style={{ minWidth: 60, height: 68, borderRadius: 12, flexShrink: 0 }} />
          ))}
        </div>
        {/* slots */}
        <div className="section">
          <div className="slots-grid">
            {Array.from({ length: 9 }).map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 46, borderRadius: 10 }} />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (error || !business) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <p style={{ color: 'var(--color-muted)', fontSize: 14 }}>{error || 'خطا در بارگذاری'}</p>
        <button className="btn-primary" style={{ marginTop: 20, width: 'auto', padding: '0 24px' }}
          onClick={() => router.back()}>
          بازگشت
        </button>
      </div>
    );
  }

  // Check if booking is enabled for this business
  const bookingEnabled = business.booking_enabled !== false;

  if (!bookingEnabled) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)' }}>
        <div className="toolbar">
          <div className="toolbar-placeholder" />
          <h1 className="toolbar-title">رزرو نوبت</h1>
          <button className="toolbar-back" onClick={() => router.back()}>›</button>
        </div>
        <div style={{ padding: 40, textAlign: 'center' }}>
          <div style={{ fontSize: 56, marginBottom: 16 }}>🚫</div>
          <h2 style={{ fontSize: 16, color: 'var(--color-text)', fontWeight: 700, marginBottom: 8 }}>
            ثبت نوبت غیرفعال است
          </h2>
          {/* The owner's own words on why, under the generic "it's off" heading. */}
          <BusinessNotice business={business} style={{ padding: '16px 0 0' }} />
          <button className="btn-primary" style={{ marginTop: 32, width: 'auto', padding: '0 32px' }}
            onClick={() => router.back()}>
            بازگشت
          </button>
        </div>
      </div>
    );
  }


  const selectedDayMonth = new Intl.DateTimeFormat('fa-IR', { day: 'numeric', month: 'long' }).format(selectedDay);
  const availableCount = slots.filter((s) => s.status === 'AVAILABLE').length;

  // The owner's menu, plus anything the client typed themselves — a custom
  // entry has to keep showing as a chip so it can be un-picked again.
  const menuServices = business.services ?? [];
  const allowCustomService = business.allow_client_add_service === true;
  const chipServices = [
    ...menuServices,
    ...selectedServices.filter((s) => !menuServices.includes(s)),
  ];

  const toggleService = (name: string) =>
    setSelectedServices((current) =>
      current.includes(name) ? current.filter((s) => s !== name) : [...current, name]
    );

  const addCustomService = () => {
    // Commas separate services on the backend, so one inside a name would come
    // back as two. Folded to a space rather than rejected.
    const name = customService.replace(/,/g, ' ').trim();
    if (!name) return;
    setSelectedServices((current) => (current.includes(name) ? current : [...current, name]));
    setCustomService('');
  };

  return (
    <div className="page-content">

      <div className="toolbar">
        <div className="toolbar-placeholder" />
        <h1 className="toolbar-title">انتخاب زمان نوبت</h1>
        <button className="toolbar-back" onClick={() => router.back()}>›</button>
      </div>

      <div style={{ padding: '16px 24px', display: 'flex', alignItems: 'center', gap: 12, borderBottom: '1px solid var(--color-border)' }}>
        <div style={{
          width: 48, height: 48, borderRadius: '50%', background: 'var(--color-primary-tint)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, flexShrink: 0
        }}>
          {business.logo ? (
            <img
              src={business.logo.startsWith('http') ? business.logo : `${process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000'}${business.logo}`}
              alt={business.title}
              style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
            />
          ) : (CATEGORY_EMOJI[business.category] || '🏢')}
        </div>
        <div>
          <h2 style={{ fontSize: 15, fontWeight: 700, margin: 0, color: 'var(--color-text)' }}>{business.title}</h2>
        </div>
      </div>

      {/* ── Owner notice ──
          Above the date picker so a deep link straight into booking still
          surfaces it before any time is picked. The muted one-liner that used
          to sit under the business title is gone: it was the same text, in a
          colour that read as a subtitle rather than a notice. */}
      <BusinessNotice business={business} style={{ paddingTop: 16, paddingBottom: 0 }} />

      {/* ── Date Picker ── */}
      <div className="section" style={{ paddingBottom: 8 }}>
        <div className="date-row" style={{ padding: 0 }}>
          {days.map((day, idx) => {
            const weekday = idx === 0
              ? 'امروز'
              : idx === 1
                ? 'فردا'
                : new Intl.DateTimeFormat('fa-IR', { weekday: 'long' }).format(day).replace('‌', ' ');
            const dayNum = new Intl.DateTimeFormat('fa-IR', { day: 'numeric' }).format(day);
            const isSelected = toDateString(day) === toDateString(selectedDay);
            return (
              <button
                key={day.toISOString()}
                className={`day-pill ${isSelected ? 'selected' : ''}`}
                onClick={() => setSelectedDay(day)}
                style={{ border: isSelected ? 'none' : '1px solid var(--color-border)', fontFamily: 'inherit' }}
              >
                <span className="day-name">{weekday}</span>
                <span className="day-num">{dayNum}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Time Slots ── */}
      <div className="section">
        <div className="section-title" style={{ textAlign: 'right', marginBottom: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>ساعات خالی — {selectedDayMonth}</span>
          {!slotsLoading && availableCount > 0 && (
            <span style={{
              background: 'var(--color-primary-tint)', color: 'var(--color-primary)',
              fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 999,
            }}>
              {toPersianNumerals(String(availableCount))} نوبت آزاد
            </span>
          )}
        </div>

        {slotsLoading ? (
          <div className="slots-grid">
            {Array.from({ length: 9 }).map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 44, borderRadius: 10 }} />
            ))}
          </div>
        ) : slots.length === 0 ? (
          <div style={{
            textAlign: 'center', padding: '40px 0',
            color: 'var(--color-muted)', fontSize: 13,
          }}>
            <div style={{ fontSize: 32, marginBottom: 8 }}>🗓</div>
            ساعت خالی در این روز وجود ندارد
          </div>
        ) : (
          <div className="slots-grid">
            {slots.map((slot) => {
              const isSelected = selectedSlot?.timestamp === slot.timestamp;
              const isDisabled = slot.status !== 'AVAILABLE';
              return (
                <button
                  key={slot.timestamp}
                  className={`time-slot ${isSelected ? 'selected' : ''} ${isDisabled ? 'disabled' : ''}`}
                  onClick={() => !isDisabled && setSelectedSlot(slot)}
                  style={{ border: isSelected ? 'none' : '1px solid var(--color-border)', fontFamily: 'inherit' }}
                  disabled={isDisabled}
                >
                  {toPersianNumerals(slot.time)}
                </button>
              );
            })}
          </div>
        )}

        {/* ── Legend ── */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 16, marginTop: 24 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--color-muted)' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--color-border)' }} />
            رزرو شده
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--color-muted)' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--color-primary)' }} />
            انتخاب شما
          </div>
        </div>
      </div>

      {/* ── Service picker ──
          The owner's own menu, as chips. Answering "رنگ مو" instead of writing
          a sentence is what lets the owner size the slot correctly and is why
          fewer of these get cancelled. Hidden entirely for a business that
          hasn't defined a menu and doesn't accept off-menu requests — there
          would be nothing to pick. */}
      {selectedSlot && (menuServices.length > 0 || allowCustomService) && (
        <div className="section" style={{ paddingTop: 0 }}>
          <label
            style={{ display: 'block', textAlign: 'right', fontSize: 13, fontWeight: 600, color: 'var(--color-text)', marginBottom: 8 }}
          >
            چه خدمتی می‌خواهید؟ (اختیاری)
          </label>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'flex-end' }}>
            {chipServices.map((name) => {
              const isSelected = selectedServices.includes(name);
              return (
                <button
                  key={name}
                  type="button"
                  onClick={() => toggleService(name)}
                  style={{
                    padding: '8px 14px',
                    borderRadius: 999,
                    fontSize: 13,
                    fontFamily: 'inherit',
                    cursor: 'pointer',
                    border: isSelected ? '1px solid var(--color-primary)' : '1px solid var(--color-border)',
                    background: isSelected ? 'var(--color-primary-tint)' : 'var(--color-surface)',
                    color: isSelected ? 'var(--color-primary)' : 'var(--color-text)',
                    fontWeight: isSelected ? 700 : 500,
                  }}
                >
                  {isSelected ? '✓ ' : ''}{name}
                </button>
              );
            })}
          </div>

          {allowCustomService && (
            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
              <button
                type="button"
                onClick={addCustomService}
                disabled={!customService.trim()}
                style={{
                  width: 44, height: 44, flexShrink: 0,
                  borderRadius: 12, border: '1px solid var(--color-border)',
                  background: 'var(--color-surface)', color: 'var(--color-primary)',
                  fontSize: 20, fontFamily: 'inherit',
                  cursor: customService.trim() ? 'pointer' : 'default',
                  opacity: customService.trim() ? 1 : 0.5,
                }}
                aria-label="افزودن خدمت"
              >
                +
              </button>
              <input
                type="text"
                value={customService}
                onChange={(e) => setCustomService(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    addCustomService();
                  }
                }}
                maxLength={100}
                placeholder="خدمت دیگری می‌خواهید؟ بنویسید"
                style={{
                  flex: 1, minWidth: 0, boxSizing: 'border-box', height: 44,
                  padding: '0 14px', borderRadius: 12, border: '1px solid var(--color-border)',
                  background: 'var(--color-surface)', color: 'var(--color-text)',
                  fontSize: 14, fontFamily: 'inherit', textAlign: 'right',
                }}
              />
            </div>
          )}
        </div>
      )}

      {/* ── Service Description (optional) ── */}
      {selectedSlot && (
        <div className="section" style={{ paddingTop: 0 }}>
          <label
            htmlFor="service-description"
            style={{ display: 'block', textAlign: 'right', fontSize: 13, fontWeight: 600, color: 'var(--color-text)', marginBottom: 8 }}
          >
            توضیحات سرویس (اختیاری)
          </label>
          <textarea
            id="service-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            maxLength={500}
            placeholder="نوع خدمت مورد نظر خود را توضیح دهید"
            style={{
              width: '100%', boxSizing: 'border-box', resize: 'vertical',
              padding: '12px 14px', borderRadius: 12, border: '1px solid var(--color-border)',
              background: 'var(--color-surface)', color: 'var(--color-text)',
              fontSize: 14, fontFamily: 'inherit', textAlign: 'right',
            }}
          />
        </div>
      )}

      {/* ── My Appointments Button ── */}
      <div className="section" style={{ paddingBottom: 0 }}>
        <button
          onClick={() => router.push('/appointments')}
          style={{
            width: '100%',
            height: 52,
            background: 'var(--color-surface)',
            color: 'var(--color-primary)',
            border: '1.5px solid var(--color-primary)',
            borderRadius: 14,
            fontSize: 16,
            fontWeight: 600,
            fontFamily: 'inherit',
            cursor: 'pointer',
          }}
        >
          نوبت های من
        </button>
      </div>

      {/* ── Toast ── */}
      {toast && <div className="toast">{toast}</div>}

      {/* ── Fixed Bottom Button ── */}
      <div className="btn-group">
        <button
          className="btn-primary"
          onClick={handleBook}
          disabled={!selectedSlot || booking}
        >
          {booking ? 'در حال ثبت...' : selectedSlot ? `ادامه - ساعت ${toPersianNumerals(selectedSlot.time)}` : 'یک ساعت انتخاب کنید'}
        </button>
      </div>

    </div>
  );
}
