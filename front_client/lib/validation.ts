/**
 * Field validators + input normalizers shared by every form in the client app.
 *
 * Two rules hold everywhere:
 *
 *  1. **Persian/Arabic digits are accepted as input, Latin digits are stored.**
 *     Iranian keyboards emit ۰-۹ (and Arabic ٠-٩ on some Android IMEs). Every
 *     validator normalizes before testing, so a phone typed as ۰۹۱۲… passes,
 *     and the value handed to the API is always ASCII.
 *
 *  2. **Validators return `null` when valid**, or a Persian error string. That
 *     lets call sites write `const err = validatePhone(v)` and treat the result
 *     as both the boolean and the message.
 */

const PERSIAN_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const ARABIC_DIGITS = '٠١٢٣٤٥٦٧٨٩';

/** Fold Persian/Arabic digits to ASCII, leaving everything else untouched. */
export function toLatinDigits(input: string): string {
  return input.replace(/[۰-۹٠-٩]/g, (d) => {
    const p = PERSIAN_DIGITS.indexOf(d);
    if (p > -1) return String(p);
    return String(ARABIC_DIGITS.indexOf(d));
  });
}

/** ASCII digits → Persian, for display only. */
export function toPersianDigits(input: string | number): string {
  return String(input).replace(/[0-9]/g, (d) => PERSIAN_DIGITS[Number(d)]);
}

/** Keep only digits (after folding Persian/Arabic ones). */
export function digitsOnly(input: string): string {
  return toLatinDigits(input).replace(/\D/g, '');
}

/* ── Phone ─────────────────────────────────────────────────────── */

export const PHONE_MAX_LENGTH = 11;

export function normalizePhone(input: string): string {
  let d = digitsOnly(input);
  // Tolerate the shapes people actually paste: +98912…, 0098912…, 912…
  if (d.startsWith('0098')) d = d.slice(4);
  else if (d.startsWith('98') && d.length > 10) d = d.slice(2);
  if (d.length === 10 && d.startsWith('9')) d = `0${d}`;
  return d.slice(0, PHONE_MAX_LENGTH);
}

export function validatePhone(input: string): string | null {
  const d = normalizePhone(input);
  if (!d) return 'شماره موبایل را وارد کنید';
  if (!d.startsWith('09')) return 'شماره موبایل باید با ۰۹ شروع شود';
  if (d.length !== 11) return 'شماره موبایل باید ۱۱ رقم باشد';
  return null;
}

/* ── Person name ───────────────────────────────────────────────── */

export const NAME_MAX_LENGTH = 50;

export function validateName(input: string): string | null {
  const v = input.trim();
  if (!v) return 'نام را وارد کنید';
  if (v.length < 3) return 'نام باید حداقل ۳ حرف باشد';
  if (v.length > NAME_MAX_LENGTH) return `نام نباید بیشتر از ${toPersianDigits(NAME_MAX_LENGTH)} حرف باشد`;
  // Persian/Arabic letters, Latin letters, spaces, ZWNJ and hyphens. Digits and
  // punctuation are rejected: this feeds the name the business sees on a booking.
  if (!/^[؀-ۿ‌ a-zA-Z\-']+$/.test(v)) return 'نام فقط می‌تواند شامل حروف باشد';
  return null;
}

/* ── Bank card ─────────────────────────────────────────────────── */

export const CARD_DIGITS = 16;

/** "6037991234567890" → "6037 9912 3456 7890" (groups of 4, for display). */
export function formatCardNumber(input: string): string {
  const d = digitsOnly(input).slice(0, CARD_DIGITS);
  return d.replace(/(\d{4})(?=\d)/g, '$1 ').trim();
}

/**
 * Luhn checksum — the same check the bank runs. Catches a mistyped digit in a
 * card the *owner* entered, which is worth surfacing before a customer wires
 * money into the void.
 */
export function isValidCardChecksum(input: string): boolean {
  const d = digitsOnly(input);
  if (d.length !== CARD_DIGITS) return false;
  let sum = 0;
  for (let i = 0; i < CARD_DIGITS; i++) {
    let n = Number(d[i]);
    // Double every second digit counting from the left on a 16-digit PAN.
    if (i % 2 === 0) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
  }
  return sum % 10 === 0;
}

/* ── Payment tracking reference ────────────────────────────────── */

export const REF_MIN_LENGTH = 4;
export const REF_MAX_LENGTH = 30;

/**
 * Bank tracking numbers ("شماره پیگیری") are digit strings of wildly varying
 * length depending on the issuing bank, so the only safe checks are "digits"
 * and "a plausible length".
 */
export function validatePaymentRef(input: string, { required }: { required: boolean }): string | null {
  const raw = input.trim();
  if (!raw) return required ? 'شماره پیگیری را وارد کنید' : null;
  const d = digitsOnly(raw);
  if (d.length !== raw.replace(/\s/g, '').length) return 'شماره پیگیری فقط شامل رقم است';
  if (d.length < REF_MIN_LENGTH) return `شماره پیگیری باید حداقل ${toPersianDigits(REF_MIN_LENGTH)} رقم باشد`;
  if (d.length > REF_MAX_LENGTH) return `شماره پیگیری نباید بیشتر از ${toPersianDigits(REF_MAX_LENGTH)} رقم باشد`;
  return null;
}

/* ── OTP ───────────────────────────────────────────────────────── */

export const OTP_LENGTH = 6;

export function validateOtp(code: string): string | null {
  const d = digitsOnly(code);
  if (d.length !== OTP_LENGTH) return `کد ${toPersianDigits(OTP_LENGTH)} رقمی را کامل وارد کنید`;
  return null;
}

/* ── Free text ─────────────────────────────────────────────────── */

export function validateFreeText(
  input: string,
  { max, label, required = false }: { max: number; label: string; required?: boolean },
): string | null {
  const v = input.trim();
  if (!v) return required ? `${label} را وارد کنید` : null;
  if (v.length > max) return `${label} نباید بیشتر از ${toPersianDigits(max)} کاراکتر باشد`;
  return null;
}
