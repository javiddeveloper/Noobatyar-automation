'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  getAppointment,
  payAppointment,
  payAppointmentWithReceipt,
  startOnlineDeposit,
  type Appointment,
  type PaymentMethod,
} from '@/lib/api';
import {
  digitsOnly,
  formatCardNumber,
  isValidCardChecksum,
  validatePaymentRef,
  REF_MAX_LENGTH,
} from '@/lib/validation';
import Toolbar from '@/app/components/Toolbar';
import TextField from '@/app/components/TextField';
import ReceiptUpload from '@/app/components/ReceiptUpload';
import Icon from '@/app/components/Icon';

export default function CheckoutPage({ params }: { params: Promise<{ slug: string; id: string }> }) {
  const router = useRouter();
  const [slug, setSlug] = useState('');
  const [id, setId] = useState(0);
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [paymentRef, setPaymentRef] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  // Set by a failed submit so every field stops hiding its error behind
  // "not blurred yet" (see TextField).
  const [submitAttempted, setSubmitAttempted] = useState(false);
  // Null until the user picks one: the effective method falls back to the first
  // method this business actually offers, rather than assuming CARD.
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod | null>(null);
  const [toast, setToast] = useState('');
  const [copied, setCopied] = useState(false);
  const [redirecting, setRedirecting] = useState(false);

  const biz = appointment?.business;
  const accepted = biz?.accepted_payment_methods;
  const depositMode = biz?.deposit_mode;
  // A mandatory deposit cannot be settled in person, so cash is not offered.
  // An optional deposit always allows paying at the venue instead.
  // Online is payable either through a real Zibal gateway or, for businesses
  // without a merchant account, the older static payment link. Requiring the
  // link alone would hide the option from every gateway-configured business.
  const canPayOnline =
    !!accepted?.includes('ONLINE') && (!!biz?.online_gateway_enabled || !!biz?.payment_link);
  const canPayCard = !accepted || accepted.includes('CARD');
  const canPayCash =
    depositMode !== 'MANDATORY' && (!!accepted?.includes('CASH') || depositMode === 'OPTIONAL');

  const availableMethods: PaymentMethod[] = [
    ...(canPayOnline ? (['ONLINE'] as const) : []),
    ...(canPayCard ? (['CARD'] as const) : []),
    ...(canPayCash ? (['CASH'] as const) : []),
  ];
  const method: PaymentMethod =
    selectedMethod && availableMethods.includes(selectedMethod)
      ? selectedMethod
      : availableMethods[0] ?? 'CARD';

  // Proof is required for card/online, and either half of it satisfies the
  // backend — so the reference is only mandatory when no receipt was attached.
  const refRequired = method !== 'CASH' && !file;
  const refError = validatePaymentRef(paymentRef, { required: refRequired });

  const rawCard = biz?.card_number ?? '';
  const cardDigits = digitsOnly(rawCard);
  const cardLooksWrong = cardDigits.length > 0 && !isValidCardChecksum(cardDigits);

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const copyCard = async () => {
    if (!cardDigits) return;
    try {
      await navigator.clipboard.writeText(cardDigits);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showToast('امکان کپی وجود ندارد');
    }
  };

  useEffect(() => {
    params.then((p) => {
      setSlug(p.slug);
      setId(parseInt(p.id, 10));
    });
  }, [params]);

  useEffect(() => {
    if (!id) return;
    const token = localStorage.getItem('visitor_token');
    if (!token) {
      router.push(`/auth/login?redirect=/b/${slug}/checkout/${id}`);
      return;
    }

    getAppointment(id, token)
      .then((apt) => {
        if (apt.status !== 'LOCKED') {
          // Already paid or otherwise moved on.
          router.replace('/appointments');
        } else {
          setAppointment(apt);
        }
      })
      .catch(() => setError('نوبت یافت نشد یا دسترسی ندارید'))
      .finally(() => setLoading(false));
  }, [id, slug, router]);

  /**
   * Hands the client to Zibal. The booking is settled by the gateway callback
   * on the server, so there is nothing to submit from this page afterwards —
   * the client returns straight to /appointments.
   */
  const handleGatewayRedirect = async () => {
    const token = localStorage.getItem('visitor_token');
    if (!token) return;

    setRedirecting(true);
    try {
      const { payment_url } = await startOnlineDeposit(id, token);
      window.location.href = payment_url;
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : 'خطا در اتصال به درگاه پرداخت');
      setRedirecting(false);
    }
  };

  const handleSubmit = async () => {
    setSubmitAttempted(true);

    // Card and online transfers must carry proof; paying in person carries none.
    if (method !== 'CASH' && !file && !paymentRef.trim()) {
      showToast('شماره پیگیری را وارد کنید یا تصویر فیش را بارگذاری نمایید');
      return;
    }
    if (method !== 'CASH' && refError) {
      showToast(refError);
      return;
    }

    const token = localStorage.getItem('visitor_token');
    if (!token) return;

    setSubmitting(true);
    try {
      if (method !== 'CASH' && file) {
        const formData = new FormData();
        formData.append('method', method);
        formData.append('payment_receipt', file);
        if (paymentRef.trim()) {
          formData.append('payment_reference', digitsOnly(paymentRef));
        }
        await payAppointmentWithReceipt(id, formData, token);
      } else {
        await payAppointment(
          id,
          method === 'CASH' ? '' : digitsOnly(paymentRef),
          token,
          method,
        );
      }
      // The animated confirmation screen replaces the old toast-then-list hop,
      // and `replace` keeps the settled checkout out of the back stack.
      router.replace(`/booking-success?id=${id}&kind=${method === 'CASH' ? 'pending' : 'paid'}`);
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : 'خطا در ثبت پرداخت');
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <Toolbar title="پرداخت و نهایی‌سازی" />
        <div style={{ padding: 24 }}>
          <div className="skeleton" style={{ height: 52, borderRadius: 14, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 180, borderRadius: 16, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 52, borderRadius: 12 }} />
        </div>
      </div>
    );
  }

  if (error || !appointment) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <Toolbar title="پرداخت و نهایی‌سازی" />
        <div style={{ padding: 24 }}>
          <div className="empty-state">
            <span className="empty-state-icon" style={{ color: 'var(--color-error)' }}>
              <Icon name="error" size={26} />
            </span>
            <h3>{error}</h3>
            <button className="btn-primary" style={{ marginTop: 18, height: 48 }} onClick={() => router.back()}>
              بازگشت
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
      <Toolbar title="پرداخت و نهایی‌سازی" />

      <div style={{ padding: '24px' }}>
        {/* ── Method tabs ── (only the methods this business actually offers) */}
        {availableMethods.length > 1 && (
          <div
            style={{
              display: 'flex',
              background: 'var(--color-surface-variant)',
              borderRadius: 14,
              padding: 4,
              marginBottom: 16,
            }}
          >
            {availableMethods.map((m) => {
              const isActive = method === m;
              const label =
                m === 'ONLINE' ? 'درگاه آنلاین' : m === 'CARD' ? 'کارت به کارت' : 'پرداخت در محل';
              const icon = m === 'ONLINE' ? 'payments' : m === 'CARD' ? 'creditCard' : 'storefront';
              return (
                <button
                  key={m}
                  onClick={() => setSelectedMethod(m)}
                  style={{
                    flex: 1,
                    padding: '11px 0',
                    border: 'none',
                    background: isActive ? 'var(--color-surface)' : 'none',
                    borderRadius: 10,
                    color: isActive ? 'var(--color-text)' : 'var(--color-muted)',
                    fontSize: 12.5,
                    fontWeight: 600,
                    fontFamily: 'inherit',
                    boxShadow: isActive ? '0 1px 3px rgba(0,0,0,0.05)' : 'none',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 5,
                  }}
                >
                  <Icon name={icon} size={15} />
                  {label}
                </button>
              );
            })}
          </div>
        )}

        {depositMode === 'OPTIONAL' && (
          <div
            style={{
              display: 'flex',
              gap: 9,
              background: 'var(--color-primary-tint)',
              color: 'var(--color-text)',
              borderRadius: 12,
              padding: '12px 14px',
              marginBottom: 16,
              fontSize: 12.5,
              textAlign: 'right',
              lineHeight: 1.8,
            }}
          >
            <Icon name="info" size={18} color="var(--color-primary)" style={{ marginTop: 2 }} />
            <span>
              پرداخت بیعانه برای این کسب‌وکار <b>اختیاری</b> است. می‌توانید بیعانه را پرداخت کنید یا
              گزینهٔ «پرداخت در محل» را انتخاب نمایید.
            </span>
          </div>
        )}

        {/* ── Card transfer ── */}
        {method === 'CARD' && (
          <>
            <div
              style={{
                background: 'var(--color-surface)',
                borderRadius: 16,
                border: '1px solid var(--color-border)',
                padding: 20,
                marginBottom: 20,
              }}
            >
              <h2
                style={{
                  fontSize: 13.5,
                  fontWeight: 700,
                  textAlign: 'right',
                  marginBottom: 16,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 7,
                }}
              >
                <Icon name="creditCard" size={17} color="var(--color-primary)" />
                انتقال به شماره کارت زیر
              </h2>

              {/* Grouped 4-by-4 so it can be read off the screen and typed into
                  a banking app without losing your place. */}
              <div className="card-number-row">
                <span className="card-number" dir="ltr">
                  {cardDigits ? formatCardNumber(cardDigits) : 'شماره کارت ثبت نشده'}
                </span>
                <button
                  onClick={copyCard}
                  disabled={!cardDigits}
                  className="card-copy"
                  style={{ color: copied ? 'var(--color-success-text)' : 'var(--color-primary)' }}
                >
                  <Icon name={copied ? 'check' : 'copy'} size={15} />
                  {copied ? 'کپی شد' : 'کپی'}
                </button>
              </div>

              {cardLooksWrong && (
                <p className="field-error" style={{ marginTop: 2, marginBottom: 10 }}>
                  <Icon name="warning" size={14} />
                  <span>شماره کارت ثبت‌شده معتبر به نظر نمی‌رسد — قبل از واریز با کسب‌وکار هماهنگ کنید.</span>
                </p>
              )}

              <div
                style={{
                  textAlign: 'right',
                  fontSize: 12.5,
                  color: 'var(--color-muted)',
                  marginBottom: 14,
                }}
              >
                به نام: {biz?.card_owner_name || 'صاحب کسب‌وکار'}
              </div>

              {biz?.deposit_amount ? (
                <div style={{ textAlign: 'right', fontSize: 15, fontWeight: 700, color: 'var(--color-text)' }}>
                  مبلغ بیعانه: {biz.deposit_amount.toLocaleString('fa-IR')} تومان
                  {depositMode === 'OPTIONAL' && (
                    <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--color-muted)' }}> (اختیاری)</span>
                  )}
                </div>
              ) : (
                <div style={{ textAlign: 'right', fontSize: 12.5, color: 'var(--color-muted)' }}>
                  مبلغ را با کسب‌وکار هماهنگ کنید.
                </div>
              )}
            </div>

            {/* ── Proof of payment ── */}
            <div className="section-head" style={{ marginBottom: 10 }}>
              <Icon name="receipt" size={17} color="var(--color-primary)" />
              <h2>رسید پرداخت</h2>
            </div>

            <TextField
              label="شماره پیگیری"
              value={paymentRef}
              onChange={setPaymentRef}
              transform={(raw) => digitsOnly(raw).slice(0, REF_MAX_LENGTH)}
              validate={(v) => validatePaymentRef(v, { required: refRequired })}
              showError={submitAttempted}
              type="tel"
              inputMode="numeric"
              icon="payments"
              dir="ltr"
              placeholder="۱۲۳۴۵۶۷۸"
              hint={
                file
                  ? 'فیش بارگذاری شد؛ وارد کردن شماره پیگیری اختیاری است.'
                  : 'شماره پیگیری تراکنش، یا در ادامه تصویر فیش را بارگذاری کنید.'
              }
              required={refRequired}
            />

            <ReceiptUpload file={file} onChange={setFile} onError={showToast} />
          </>
        )}

        {method === 'ONLINE' && (
          <div style={{ textAlign: 'center', padding: '28px 0', color: 'var(--color-muted)', fontSize: 14 }}>
            {biz?.online_gateway_enabled ? (
              /* Real Zibal gateway: the bank confirms the payment and the
                 booking is settled by the callback, so there is no tracking
                 number for the client to copy back. */
              <>
                <span className="empty-state-icon" style={{ margin: '0 auto 14px' }}>
                  <Icon name="payments" size={26} />
                </span>
                <p style={{ marginBottom: 20 }}>برای پرداخت بیعانه به درگاه بانکی منتقل می‌شوید.</p>
                <button
                  className="btn-primary"
                  onClick={handleGatewayRedirect}
                  disabled={redirecting}
                  style={{ height: 52 }}
                >
                  {redirecting ? (
                    <>
                      <span className="btn-spinner" /> در حال انتقال…
                    </>
                  ) : (
                    'پرداخت با درگاه بانکی'
                  )}
                </button>
                <p style={{ marginTop: 16, fontSize: 12, color: 'var(--color-faint)' }}>
                  پس از پرداخت موفق، نوبت شما بلافاصله قطعی می‌شود.
                </p>
              </>
            ) : biz?.payment_link ? (
              <>
                <p style={{ marginBottom: 16 }}>
                  برای پرداخت آنلاین مبلغ بیعانه، لطفاً از طریق لینک زیر اقدام کنید:
                </p>
                <a
                  href={biz.payment_link.startsWith('http') ? biz.payment_link : `https://${biz.payment_link}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                    color: 'var(--color-primary)',
                    fontWeight: 700,
                    textDecoration: 'none',
                    fontSize: 15,
                  }}
                >
                  انتقال به درگاه پرداخت
                  <Icon name="chevronLeft" size={17} />
                </a>
                <p style={{ margin: '24px 0 8px', fontSize: 12, color: 'var(--color-faint)' }}>
                  پس از پرداخت، شماره پیگیری را در همین صفحه وارد کنید.
                </p>

                <div style={{ textAlign: 'right' }}>
                  <TextField
                    value={paymentRef}
                    onChange={setPaymentRef}
                    transform={(raw) => digitsOnly(raw).slice(0, REF_MAX_LENGTH)}
                    validate={(v) => validatePaymentRef(v, { required: refRequired })}
                    showError={submitAttempted}
                    type="tel"
                    inputMode="numeric"
                    icon="payments"
                    dir="ltr"
                    placeholder="شماره پیگیری درگاه پرداخت"
                    required={refRequired}
                  />
                </div>
              </>
            ) : (
              'اتصال به درگاه پرداخت در حال حاضر غیرفعال است.'
            )}
          </div>
        )}

        {method === 'CASH' && (
          <div style={{ textAlign: 'center', padding: '28px 0' }}>
            <span className="empty-state-icon" style={{ margin: '0 auto 14px' }}>
              <Icon name="storefront" size={26} />
            </span>
            <p style={{ color: 'var(--color-muted)', fontSize: 13.5, lineHeight: 1.9 }}>
              شما پرداخت در محل را انتخاب کرده‌اید.
              <br />
              نوبت شما برای تأیید به کسب‌وکار ارسال می‌شود.
            </p>
          </div>
        )}
      </div>

      {toast && <div className="toast">{toast}</div>}

      {/* ── Fixed bottom button ──
           Hidden for the real gateway: that flow finishes at the bank and is
           settled by the server callback, so a "submit" here would have nothing
           to send and would only invite a double payment. */}
      {!(method === 'ONLINE' && biz?.online_gateway_enabled) && (
        <div className="btn-group">
          <button className="btn-primary" onClick={handleSubmit} disabled={submitting}>
            {submitting ? (
              <>
                <span className="btn-spinner" /> در حال ثبت…
              </>
            ) : method === 'CASH' ? (
              'ثبت نوبت (پرداخت در محل)'
            ) : (
              'ثبت نهایی نوبت'
            )}
          </button>
          {method === 'CARD' && biz?.deposit_amount ? (
            <p
              style={{
                textAlign: 'center',
                fontSize: 11,
                color: 'var(--color-faint)',
                marginTop: 8,
              }}
            >
              مبلغ {biz.deposit_amount.toLocaleString('fa-IR')} تومان
            </p>
          ) : null}
        </div>
      )}
    </div>
  );
}
