'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Cropper, { type Area } from 'react-easy-crop';
import Icon from './Icon';

/** Longest edge of the stored receipt. Plenty to read a tracking number off. */
const MAX_EDGE = 1400;
/** Target byte size; quality steps down until the encode fits under it. */
const TARGET_BYTES = 700 * 1024;
/** Hard stop before we even try to decode — a 20MB burst photo is a mistake. */
const MAX_INPUT_BYTES = 15 * 1024 * 1024;
const QUALITY_LADDER = [0.82, 0.72, 0.62, 0.5, 0.4];

function formatBytes(bytes: number): string {
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.round(kb).toLocaleString('fa-IR')} کیلوبایت`;
  return `${(kb / 1024).toLocaleString('fa-IR', { maximumFractionDigits: 1 })} مگابایت`;
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('تصویر قابل خواندن نیست'));
    img.src = src;
  });
}

/**
 * Rasterize the selected crop, downscale it to MAX_EDGE, and JPEG-encode it,
 * stepping quality down until the result fits under TARGET_BYTES.
 *
 * Receipts are photos of paper: they re-encode to JPEG far smaller than the
 * camera original (which is usually 3–8MB), and the upload is what customers
 * on Iranian mobile data actually wait for.
 */
async function cropAndCompress(imageSrc: string, area: Area, fileName: string): Promise<File> {
  const image = await loadImage(imageSrc);

  const scale = Math.min(1, MAX_EDGE / Math.max(area.width, area.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(area.width * scale));
  canvas.height = Math.max(1, Math.round(area.height * scale));

  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('پردازش تصویر در این مرورگر پشتیبانی نمی‌شود');
  // A receipt is a document; smoothing keeps text legible after downscaling.
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  // JPEG has no alpha — without this, transparent PNG areas encode as black.
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(
    image,
    area.x, area.y, area.width, area.height,
    0, 0, canvas.width, canvas.height,
  );

  const encode = (quality: number) =>
    new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));

  let blob: Blob | null = null;
  for (const quality of QUALITY_LADDER) {
    blob = await encode(quality);
    if (blob && blob.size <= TARGET_BYTES) break;
  }
  if (!blob) throw new Error('خطا در فشرده‌سازی تصویر');

  const base = fileName.replace(/\.[^.]+$/, '') || 'receipt';
  return new File([blob], `${base}.jpg`, { type: 'image/jpeg', lastModified: Date.now() });
}

interface ReceiptUploadProps {
  file: File | null;
  onChange: (file: File | null) => void;
  onError?: (message: string) => void;
}

/**
 * Receipt picker: choose → crop → compress → preview.
 *
 * The crop step is not cosmetic. Customers photograph a receipt on a desk or
 * hand it to the camera at an angle, and the business owner then has to read a
 * tracking number out of a mostly-empty frame. Cropping at the source also cuts
 * the upload down before it leaves a phone on mobile data.
 */
export default function ReceiptUpload({ file, onChange, onError }: ReceiptUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [rawSrc, setRawSrc] = useState<string | null>(null);
  const [originalSize, setOriginalSize] = useState(0);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [areaPixels, setAreaPixels] = useState<Area | null>(null);
  const [working, setWorking] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  // Object URLs are a manual resource; leaking them keeps whole images alive.
  // Both of these only revoke — the URLs are minted in the event handlers that
  // create them, so nothing sets state from inside an effect.
  useEffect(() => {
    if (!previewUrl) return;
    return () => URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);

  useEffect(() => {
    if (!rawSrc) return;
    return () => URL.revokeObjectURL(rawSrc);
  }, [rawSrc]);

  const clearFile = () => {
    onChange(null);
    setPreviewUrl(null);
  };

  const onCropComplete = useCallback((_: Area, pixels: Area) => setAreaPixels(pixels), []);

  const handlePick = (picked: File | undefined) => {
    if (!picked) return;
    if (!picked.type.startsWith('image/')) {
      onError?.('فقط تصویر می‌توانید انتخاب کنید');
      return;
    }
    if (picked.size > MAX_INPUT_BYTES) {
      onError?.(`حجم تصویر نباید بیشتر از ${formatBytes(MAX_INPUT_BYTES)} باشد`);
      return;
    }
    setOriginalSize(picked.size);
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setRawSrc(URL.createObjectURL(picked));
  };

  const closeCropper = () => {
    // The effect above revokes the outgoing rawSrc once this clears it.
    setRawSrc(null);
    setAreaPixels(null);
    // Let the same file be re-picked; without this, choosing the identical
    // file twice in a row fires no change event.
    if (inputRef.current) inputRef.current.value = '';
  };

  const confirmCrop = async () => {
    if (!rawSrc || !areaPixels) return;
    setWorking(true);
    try {
      const result = await cropAndCompress(rawSrc, areaPixels, 'receipt');
      onChange(result);
      setPreviewUrl(URL.createObjectURL(result));
      closeCropper();
    } catch (err) {
      onError?.(err instanceof Error ? err.message : 'خطا در پردازش تصویر');
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={(e) => handlePick(e.target.files?.[0])}
      />

      {file && previewUrl ? (
        <div className="receipt-preview">
          {/* eslint-disable-next-line @next/next/no-img-element -- local blob preview */}
          <img src={previewUrl} alt="پیش‌نمایش فیش" />
          <div className="receipt-preview-meta">
            <div className="receipt-preview-title">
              <Icon name="checkCircle" size={16} color="var(--color-success)" />
              <span>فیش آماده ارسال</span>
            </div>
            <div className="receipt-preview-size">
              {formatBytes(file.size)}
              {originalSize > file.size && (
                <span className="receipt-saving">
                  {' '}(از {formatBytes(originalSize)} فشرده شد)
                </span>
              )}
            </div>
            <div className="receipt-preview-actions">
              <button type="button" onClick={() => inputRef.current?.click()}>
                <Icon name="crop" size={15} /> تغییر تصویر
              </button>
              <button type="button" className="danger" onClick={clearFile}>
                <Icon name="close" size={15} /> حذف
              </button>
            </div>
          </div>
        </div>
      ) : (
        <button type="button" className="upload-dropzone" onClick={() => inputRef.current?.click()}>
          <span className="upload-dropzone-icon">
            <Icon name="image" size={22} />
          </span>
          <span className="upload-dropzone-text">
            <strong>افزودن تصویر فیش</strong>
            <small>بعد از انتخاب می‌توانید تصویر را برش دهید</small>
          </span>
          <Icon name="add" size={20} />
        </button>
      )}

      {/* ── Crop dialog ── */}
      {rawSrc && (
        <div className="crop-sheet" role="dialog" aria-modal="true" aria-label="برش تصویر فیش">
          <div className="crop-sheet-panel">
            <div className="crop-sheet-head">
              <button type="button" onClick={closeCropper} aria-label="انصراف">
                <Icon name="close" size={20} />
              </button>
              <h2>برش تصویر فیش</h2>
              <span style={{ width: 36 }} />
            </div>

            <div className="crop-stage">
              <Cropper
                image={rawSrc}
                crop={crop}
                zoom={zoom}
                aspect={3 / 4}
                minZoom={1}
                maxZoom={4}
                restrictPosition={false}
                showGrid
                onCropChange={setCrop}
                onZoomChange={setZoom}
                onCropComplete={onCropComplete}
              />
            </div>

            <div className="crop-sheet-foot">
              <label className="crop-zoom">
                <Icon name="image" size={16} />
                <input
                  type="range"
                  min={1}
                  max={4}
                  step={0.05}
                  value={zoom}
                  onChange={(e) => setZoom(Number(e.target.value))}
                  aria-label="بزرگ‌نمایی"
                />
                <Icon name="crop" size={16} />
              </label>
              <button
                type="button"
                className="btn-primary"
                onClick={confirmCrop}
                disabled={working || !areaPixels}
              >
                {working ? (
                  <>
                    <span className="btn-spinner" /> در حال پردازش…
                  </>
                ) : (
                  'تأیید و استفاده از این برش'
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
