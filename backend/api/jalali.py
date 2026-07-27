"""
api/jalali.py

Jalali (Shamsi) date formatting for anything a Persian-speaking user reads.

Written as a self-contained conversion rather than pulling in ``jdatetime``:
the only thing needed is "civil Gregorian date → civil Jalali date", the
algorithm is fixed and well known, and the production image is rebuilt from
``requirements_prod.txt`` — a new dependency there is a deploy step we do not
need for thirty lines of arithmetic.

Callers pass an aware datetime; :func:`format_datetime` converts it to the
business timezone first, so the date shown is the one the recipient's wall
clock agrees with.
"""

from zoneinfo import ZoneInfo

from django.conf import settings

# Days elapsed at the start of each Gregorian month in a common year.
_GREGORIAN_MONTH_OFFSETS = (0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)


def to_jalali(gy: int, gm: int, gd: int):
    """Convert a civil Gregorian date to ``(jy, jm, jd)`` in the Jalali calendar."""
    if gy > 1600:
        jy = 979
        gy -= 1600
    else:
        jy = 0
        gy -= 621

    # March starts the Jalali year, so a date after February belongs to the
    # next Gregorian leap cycle for counting purposes.
    gy2 = gy + 1 if gm > 2 else gy

    days = (
        365 * gy
        + (gy2 + 3) // 4
        - (gy2 + 99) // 100
        + (gy2 + 399) // 400
        - 80
        + gd
        + _GREGORIAN_MONTH_OFFSETS[gm - 1]
    )

    # 12053 days = one 33-year Jalali cycle; 1461 = one 4-year sub-cycle.
    jy += 33 * (days // 12053)
    days %= 12053
    jy += 4 * (days // 1461)
    days %= 1461

    if days > 365:
        jy += (days - 1) // 365
        days = (days - 1) % 365

    # First six months have 31 days, the next five have 30.
    if days < 186:
        jm = 1 + days // 31
        jd = 1 + days % 31
    else:
        jm = 7 + (days - 186) // 30
        jd = 1 + (days - 186) % 30

    return jy, jm, jd


def _localize(dt):
    """Move ``dt`` into the configured business timezone."""
    tz = ZoneInfo(settings.TIME_ZONE)
    return dt.astimezone(tz)


def format_date(dt) -> str:
    """``1405/05/05`` — the Jalali date of ``dt`` in the business timezone."""
    local = _localize(dt)
    jy, jm, jd = to_jalali(local.year, local.month, local.day)
    return f"{jy}/{jm:02d}/{jd:02d}"


def format_datetime(dt) -> str:
    """``1405/05/05 ساعت 14:30`` — the wording every outgoing SMS shares."""
    local = _localize(dt)
    jy, jm, jd = to_jalali(local.year, local.month, local.day)
    return f"{jy}/{jm:02d}/{jd:02d} ساعت {local:%H:%M}"
