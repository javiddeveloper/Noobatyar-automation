# business/theme_color.py
"""
Derives a business's header colour from its logo.

Done on the server, at save time, for three reasons:

* **CORS.** Media is served from ``api.noobatyar.ir`` while the booking page
  runs on ``app.noobatyar.ir``. Sampling the logo in a browser ``<canvas>``
  taints it, and ``getImageData`` then throws — so the obvious client-side
  approach cannot work here at all without opening CORS on the media host.
* **No flash.** The hero is server-rendered for SEO. Shipping the colour in
  the payload means the correct background is in the first paint instead of
  the page repainting once an image finishes downloading and decoding.
* **Cost.** A logo changes rarely; a booking page is loaded constantly. Doing
  this once per upload rather than once per visit is the obvious trade.

── Why the colour is adjusted rather than used raw ──────────────────────────

The hero prints white text and white glyphs over this colour. A raw dominant
colour is regularly unusable for that: a logo on a white card yields near-
white, a monochrome logo yields near-black, and a fluorescent brand colour
yields something that vibrates under white type. :func:`adjust_for_hero`
therefore keeps the *hue* — which is the part that actually reads as "this
business's colour" — and forces saturation and lightness into a band that is
guaranteed to carry white text.
"""

from __future__ import annotations

import colorsys
import logging

logger = logging.getLogger(__name__)

# Fallback when there is no logo, or it cannot be read. Matches the brand
# purple the hero used before this existed, so a business without a logo looks
# exactly as it always did rather than defaulting to something arbitrary.
DEFAULT_HERO_COLOR = '#8b5cf6'

# Contrast the result must reach against white text. 4.5:1 is WCAG AA for
# normal-size text, which the hero has (the 13px bio line, not just the large
# title) — so the looser 3:1 large-text allowance would not actually cover
# this header.
#
# Enforced as a *measured contrast ratio*, not as an HSL-lightness ceiling.
# Those are not interchangeable: HSL lightness is hue-blind, so a yellow and a
# blue at identical L differ enormously in perceived brightness. A pale-yellow
# logo clamped to L=0.52 produced #dddd2c — which passes any lightness rule
# and still leaves white text at 1.45:1, i.e. invisible. Darkening until the
# real ratio is met is the only version of this that holds for every hue.
MIN_CONTRAST_WITH_WHITE = 4.5
# Colours darker than needed are lifted back up until they would cross this,
# so every header lands in the same readable band regardless of whether the
# logo was pale or nearly black. The gap above MIN keeps a little margin for
# the gradient, which shades ±22% either side of this colour.
TARGET_CONTRAST = 5.2
# Bounds on how far the lightness search may travel in either direction.
MIN_LIGHTNESS = 0.16
MAX_LIGHTNESS = 0.62
# A washed-out logo would otherwise yield a grey hero that looks broken rather
# than deliberate; a neon one would fight the white text.
MIN_SATURATION = 0.25
MAX_SATURATION = 0.72

# Pixels this close to pure white/black are dropped before picking a dominant
# colour: nearly every logo is mostly background, and without this the answer
# is "white" for almost all of them.
_NEAR_WHITE = 240
_NEAR_BLACK = 18
# Sampling size. The logo only has to answer "what colour is this, roughly",
# and thumbnailing first turns a 2000px upload into a few thousand pixels.
_SAMPLE_EDGE = 64
# Colours are bucketed this coarsely before counting, so anti-aliasing and JPEG
# noise do not split one flat colour across dozens of near-identical entries.
_BUCKET = 24


def _is_meaningful(r: int, g: int, b: int) -> bool:
    """Drop background and near-neutral pixels.

    The second test matters as much as the first: a logo's drop shadow and its
    grey gradients are not "the brand colour", and counting them buries the one
    saturated accent that actually identifies the business.
    """
    if r > _NEAR_WHITE and g > _NEAR_WHITE and b > _NEAR_WHITE:
        return False
    if r < _NEAR_BLACK and g < _NEAR_BLACK and b < _NEAR_BLACK:
        return False
    return max(r, g, b) - min(r, g, b) >= 20


def dominant_rgb(image) -> tuple[int, int, int] | None:
    """Most common non-background colour in a PIL image, or None."""
    from PIL import Image

    img = image.convert('RGBA')
    img.thumbnail((_SAMPLE_EDGE, _SAMPLE_EDGE), Image.Resampling.LANCZOS)

    counts: dict[tuple[int, int, int], int] = {}
    for r, g, b, a in img.getdata():
        # A transparent logo's padding is not part of the mark.
        if a < 128:
            continue
        if not _is_meaningful(r, g, b):
            continue
        key = (r // _BUCKET, g // _BUCKET, b // _BUCKET)
        counts[key] = counts.get(key, 0) + 1

    if not counts:
        return None

    br, bg, bb = max(counts, key=counts.get)
    # Back to the middle of the bucket rather than its floor, so the result is
    # not systematically darker than what the logo actually contains.
    half = _BUCKET // 2
    return (
        min(br * _BUCKET + half, 255),
        min(bg * _BUCKET + half, 255),
        min(bb * _BUCKET + half, 255),
    )


def _srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def relative_luminance(rgb: tuple[float, float, float]) -> float:
    """WCAG relative luminance for an sRGB triple given in 0..1."""
    r, g, b = (_srgb_to_linear(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_with_white(rgb: tuple[float, float, float]) -> float:
    """WCAG contrast ratio of ``rgb`` (0..1 triple) against pure white."""
    return 1.05 / (relative_luminance(rgb) + 0.05)


def adjust_for_hero(rgb: tuple[int, int, int]) -> str:
    """Make a colour usable as a hero background under white text.

    Keeps the hue — that is the part that reads as "this business's colour" —
    and moves only saturation and lightness. Darkens in small steps until the
    measured contrast clears :data:`MIN_CONTRAST_WITH_WHITE`, or until
    :data:`MIN_LIGHTNESS` stops it.
    """
    r, g, b = (c / 255 for c in rgb)
    h, l, s = colorsys.rgb_to_hls(r, g, b)

    s = min(max(s, MIN_SATURATION), MAX_SATURATION)

    # Converge on the target band from *either* side. Darkening alone was not
    # enough: a logo whose dominant colour is already dark (a photo, a deep
    # navy mark) sailed past the contrast test untouched and produced a muddy
    # near-black header at 11:1 — technically readable, but it threw away all
    # the headroom and looked broken next to a bright logo's header. Lifting
    # dark colours up to the same band is what makes every business's header
    # look deliberate rather than "whatever the logo happened to be".
    while contrast_with_white(colorsys.hls_to_rgb(h, l, s)) < MIN_CONTRAST_WITH_WHITE:
        if l <= MIN_LIGHTNESS:
            break
        l -= 0.02

    while l < MAX_LIGHTNESS:
        nudged = l + 0.02
        if contrast_with_white(colorsys.hls_to_rgb(h, nudged, s)) < TARGET_CONTRAST:
            break
        l = nudged

    rgb_f = colorsys.hls_to_rgb(h, l, s)
    return '#{:02x}{:02x}{:02x}'.format(*(round(c * 255) for c in rgb_f))


def color_from_logo(logo_field) -> str:
    """Hero colour for a logo, or :data:`DEFAULT_HERO_COLOR`.

    Never raises: a business must stay saveable even if its logo is a corrupt
    upload, a format Pillow was built without, or a file the storage backend
    can no longer produce. Any of those falls back to the brand colour, which
    is exactly what the page looked like before this feature existed.
    """
    if not logo_field:
        return DEFAULT_HERO_COLOR

    try:
        from PIL import Image

        # Rewind first: DRF may have already read the uploaded file while
        # validating it, leaving the pointer at EOF — Pillow would then see an
        # empty stream and every freshly-uploaded logo would silently fall back.
        if hasattr(logo_field, 'seek'):
            logo_field.seek(0)
        with Image.open(logo_field) as img:
            rgb = dominant_rgb(img)
        if hasattr(logo_field, 'seek'):
            logo_field.seek(0)
    except Exception:
        logger.warning('Could not derive hero colour from logo', exc_info=True)
        return DEFAULT_HERO_COLOR

    if rgb is None:
        return DEFAULT_HERO_COLOR
    return adjust_for_hero(rgb)
