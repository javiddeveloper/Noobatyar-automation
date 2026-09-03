'use client';

import { useId, useState, type InputHTMLAttributes } from 'react';
import Icon, { type IconName } from './Icon';

type NativeProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'value' | 'onChange' | 'onBlur' | 'type'
>;

interface TextFieldProps extends NativeProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  /** Returns a Persian error string, or null when the value is acceptable. */
  validate?: (value: string) => string | null;
  /**
   * Rewrites what the user typed before it reaches state — digit folding,
   * card grouping, phone normalizing. Applied on every keystroke, so it must
   * be idempotent and must not fight the caret (append-only transforms).
   */
  transform?: (raw: string) => string;
  type?: 'text' | 'tel' | 'number' | 'textarea';
  hint?: string;
  icon?: IconName;
  /**
   * Force the error into view even if the field was never blurred — set this
   * from the parent when a submit attempt fails, so the offending field
   * explains itself instead of silently rejecting the tap.
   */
  showError?: boolean;
  rows?: number;
}

/**
 * One text input with inline validation.
 *
 * Errors appear once the field has been blurred (or `showError` is set by a
 * failed submit), never while the user is still mid-word — validating on the
 * first keystroke would tell someone their phone number is too short before
 * they have finished typing it.
 */
export default function TextField({
  label,
  value,
  onChange,
  validate,
  transform,
  type = 'text',
  hint,
  icon,
  showError = false,
  rows = 3,
  ...rest
}: TextFieldProps) {
  const id = useId();
  const [touched, setTouched] = useState(false);
  const [focused, setFocused] = useState(false);

  const error = validate ? validate(value) : null;
  // Errors stay hidden while the field is focused: telling someone their phone
  // number is too short before they finish typing it is just noise.
  const settled = (touched || showError) && !focused;
  const visibleError = settled ? error : null;
  const showValidTick = settled && !error && value.trim().length > 0 && !!validate;
  // The glyph and the message are driven by the same condition, so the field
  // can never show a green tick above a red "this is invalid".
  const showStatus = settled && !!validate && value.trim() !== '';

  const borderColor = visibleError
    ? 'var(--color-error)'
    : focused
      ? 'var(--color-primary)'
      : showValidTick
        ? 'var(--color-success)'
        : 'var(--color-border)';

  const handleChange = (raw: string) => onChange(transform ? transform(raw) : raw);

  const shared = {
    id,
    value,
    onFocus: () => setFocused(true),
    onBlur: () => {
      setFocused(false);
      setTouched(true);
    },
    'aria-invalid': visibleError ? true : undefined,
    'aria-describedby': visibleError ? `${id}-err` : hint ? `${id}-hint` : undefined,
    className: 'form-input',
    style: {
      borderColor,
      // Leave room for the leading icon and the trailing status glyph.
      paddingInlineStart: icon ? 44 : 16,
      paddingInlineEnd: validate ? 40 : 16,
      // The valid-state ring reads --color-focus-ring rather than a literal so
      // it follows the business theme colour; the error ring stays red, since
      // "this field is wrong" must not be repainted into the brand palette.
      boxShadow: focused
        ? `0 0 0 3px ${visibleError ? 'rgba(220,38,38,0.15)' : 'var(--color-focus-ring)'}`
        : 'none',
    } as React.CSSProperties,
  };

  return (
    <div className="field">
      {label && (
        <label className="form-label" htmlFor={id}>
          {label}
          {rest.required && <span style={{ color: 'var(--color-error)' }}> *</span>}
        </label>
      )}

      <div className="field-shell">
        {icon && (
          <span className="field-icon" style={{ color: visibleError ? 'var(--color-error)' : undefined }}>
            <Icon name={icon} size={19} />
          </span>
        )}

        {type === 'textarea' ? (
          <textarea
            {...(rest as React.TextareaHTMLAttributes<HTMLTextAreaElement>)}
            {...shared}
            rows={rows}
            onChange={(e) => handleChange(e.target.value)}
            style={{ ...shared.style, height: 'auto', padding: '12px 14px', resize: 'vertical' }}
          />
        ) : (
          <input
            {...rest}
            {...shared}
            type={type}
            onChange={(e) => handleChange(e.target.value)}
          />
        )}

        {/* Status glyph: only meaningful once the field has settled. */}
        {showStatus && (
          <span
            className="field-status"
            style={{ color: visibleError ? 'var(--color-error)' : 'var(--color-success)' }}
          >
            <Icon name={visibleError ? 'error' : 'checkCircle'} size={18} />
          </span>
        )}
      </div>

      {visibleError ? (
        <p id={`${id}-err`} className="field-error" role="alert">
          <Icon name="error" size={14} />
          <span>{visibleError}</span>
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="field-hint">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
