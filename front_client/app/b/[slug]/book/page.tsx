'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  getBusinessByCode,
  getAvailableSlots,
  bookAppointment,
  type Business,
  type TimeSlot,
} from '@/lib/api';

interface Props {
  params: Promise<{ slug: string }>;
}

// Convert a Gregorian Date to Persian weekday/day label
function toPersianDate(date: Date): { weekday: string; day: string } {
  const weekdays = ['یک‌شنبه', 'دو‌شنبه', 'سه‌شنبه', 'چهارشنبه', 'پنج‌شنبه', 'جمعه', 'شنبه'];
  return {
    weekday: weekdays[date.getDay()],
    day: toPersianNumerals(String(date.getDate())),
  };
}

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
  const [loading, setLoading] = useState(true);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [booking, setBooking] = useState(false);
  const [toast, setToast] = useState('');
  const [error, setError] = useState('');

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
        .catch(() => setError('کسب‌وکار یافت نشد'))
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
    } catch {
      setSlots([]);
    } finally {
      setSlotsLoading(false);
    }
  }, [business]);

  useEffect(() => {
    if (business) loadSlots(selectedDay);
  }, [business, selectedDay, loadSlots]);

  const handleBook = async () => {
    if (!selectedSlot || !business) return;

    // Check auth token
    const token = typeof window !== 'undefined' ? localStorage.getItem('access_token') : null;
    if (!token) {
      // Save intent and redirect to login
      localStorage.setItem('booking_intent', JSON.stringify({
        slug,
        slot: selectedSlot,
        businessId: business.id,
      }));
      router.push(`/auth/login?redirect=/b/${slug}/book`);
      return;
    }

    setBooking(true);
    try {
      await bookAppointment(
        business.id,
        selectedSlot.timestamp,
        business.default_service_duration,
        '',
        token
      );
      showToast('✅ نوبت شما با موفقیت ثبت شد و در انتظار تایید است');
      setTimeout(() => router.push('/appointments'), 2000);
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : 'خطا در ثبت نوبت');
    } finally {
      setBooking(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: '60px 24px' }}>
        {[1, 2, 3].map((i) => (
          <div key={i} className="skeleton" style={{ height: 60, marginBottom: 12, borderRadius: 12 }} />
        ))}
      </div>
    );
  }

  if (error || !business) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <p style={{ color: '#6b7280', fontSize: 14 }}>{error || 'خطا در بارگذاری'}</p>
        <button className="btn-primary" style={{ marginTop: 20, width: 'auto', padding: '0 24px' }}
          onClick={() => router.back()}>
          بازگشت
        </button>
      </div>
    );
  }

  const selectedDayMonth = new Intl.DateTimeFormat('fa-IR', { day: 'numeric', month: 'long' }).format(selectedDay);

  return (
    <div className="page-content">

      {/* ── Back Header ── */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '16px 24px', background: 'white',
        position: 'sticky', top: 0, zIndex: 50,
      }}>
        <button
          onClick={() => router.back()}
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontSize: 22, color: '#111827', padding: '4px 8px',
          }}
        >
          ←
        </button>
        <h1 style={{ fontSize: 16, fontWeight: 700, color: '#111827' }}>
          انتخاب زمان نوبت
        </h1>
      </div>

      {/* ── Date Picker ── */}
      <div className="section" style={{ paddingBottom: 8 }}>
        <div className="date-row" style={{ padding: 0 }}>
          {days.map((day) => {
            const weekday = new Intl.DateTimeFormat('fa-IR', { weekday: 'long' }).format(day).replace('‌', ' ');
            const dayNum = new Intl.DateTimeFormat('fa-IR', { day: 'numeric' }).format(day);
            const isSelected = toDateString(day) === toDateString(selectedDay);
            return (
              <button
                key={day.toISOString()}
                className={`day-pill ${isSelected ? 'selected' : ''}`}
                onClick={() => setSelectedDay(day)}
                style={{ border: isSelected ? 'none' : '1px solid #e5e7eb', fontFamily: 'inherit' }}
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
        <div className="section-title" style={{ textAlign: 'right', marginBottom: 16 }}>
          ساعات خالی - {selectedDayMonth}
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
            color: '#6b7280', fontSize: 13,
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
                  style={{ border: isSelected ? 'none' : '1px solid #e5e7eb', fontFamily: 'inherit' }}
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#6b7280' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#e5e7eb' }} />
            رزرو شده
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#6b7280' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#d735a9' }} />
            انتخاب شما
          </div>
        </div>
      </div>

      {/* ── My Appointments Button ── */}
      <div className="section" style={{ paddingBottom: 0 }}>
        <button
          onClick={() => router.push('/appointments')}
          style={{
            width: '100%',
            height: 52,
            background: 'white',
            color: '#d735a9',
            border: '1.5px solid #d735a9',
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
