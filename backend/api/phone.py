"""
api/phone.py

Phone-number normalisation.

Persian and Arabic-Indic digits look like digits to a human and to a Django
CharField, but not to Melipayamak: a business phone saved as ۰۲۱۳۹۰۹۳۰۹۳ came
back from the provider as «شماره گیرنده نامعتبر است». Anything that reaches an
SMS API — or gets validated on the way in — goes through here first.
"""

import re

# Persian (U+06F0–U+06F9) and Arabic-Indic (U+0660–U+0669) digits -> ASCII.
_DIGIT_MAP = str.maketrans(
    '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',
    '01234567890123456789',
)

# Iranian numbers are 11 digits starting with 0: mobiles are 09XXXXXXXXX, and
# landlines are 0 + a 2/3-digit area code + the rest.
IRAN_ANY_RE = re.compile(r'^0\d{10}$')
IRAN_MOBILE_RE = re.compile(r'^09\d{9}$')


def normalize_digits(value: str) -> str:
    """Convert Persian/Arabic-Indic digits in a string to ASCII digits."""
    if not value:
        return value
    return str(value).translate(_DIGIT_MAP)


def normalize_phone(value: str) -> str:
    """Normalise a phone number for storage or dispatch.

    Converts non-ASCII digits, strips the separators people paste in (spaces,
    dashes, parentheses, ZWNJ), and rewrites the +98/98 country prefix to the
    local leading zero. Returns '' for a falsy input. Does not validate — see
    is_iran_mobile() / is_iran_phone() for that.
    """
    if not value:
        return ''
    s = normalize_digits(str(value)).strip()
    s = re.sub(r'[\s\-()‌‏‎.]', '', s)
    if s.startswith('+98'):
        s = '0' + s[3:]
    elif s.startswith('0098'):
        s = '0' + s[4:]
    elif s.startswith('98') and len(s) == 12:
        s = '0' + s[2:]
    return s


def is_iran_mobile(value: str) -> bool:
    """True for a normalised Iranian mobile number (09XXXXXXXXX)."""
    return bool(IRAN_MOBILE_RE.match(normalize_phone(value)))


def is_iran_phone(value: str) -> bool:
    """True for any normalised 11-digit Iranian number, mobile or landline."""
    return bool(IRAN_ANY_RE.match(normalize_phone(value)))
