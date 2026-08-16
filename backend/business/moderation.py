"""
business/moderation.py

The moderation *decision logic*, deliberately kept free of any admin or DRF
imports. The staff queue (business/admin.py) and the owner-facing API both need
the same three operations, and the one thing that must never happen is a status
change that reaches the database without a matching BusinessModerationLog row —
so both callers go through :func:`apply_decision` rather than assigning
``business.moderation_status`` themselves.

Side effects are exactly the DB writes described on each function. In
particular nothing here sends SMS or touches the notification layer: whether an
owner is told about a rejection is a product decision that belongs to the
caller, and burying it here would fire a text every time a test or a data
migration flipped a status.
"""

import unicodedata

from django.db import transaction
from django.utils import timezone

from .models import BannedKeyword, Business, BusinessModerationLog


# ── Persian text normalisation ────────────────────────────────────────────────
# Iranian users type Persian on Arabic keyboards constantly, so the same word
# arrives spelled several ways and a naive `in` test misses most of them:
#
#   ی (U+06CC Farsi yeh)  vs  ي (U+064A Arabic yeh)  vs  ى (U+0649 alef maksura)
#   ک (U+06A9 keheh)      vs  ك (U+0643 Arabic kaf)
#   ZWNJ (U+200C) inside compounds — «آرایش‌گاه» vs «آرایشگاه»
#
# We fold all of those together. We deliberately stop there: no stemming, no
# affix stripping. Persian is agglutinative enough that stemming would match
# unrelated words, and since a match only *flags for human review* a miss is far
# cheaper than a false positive that trains reviewers to ignore the flags.
#
# NFKC runs first (see `normalize`), which already collapses the Arabic
# presentation forms; this table only has to handle the pairs NFKC considers
# genuinely distinct letters. The last three entries are invisible characters —
# they carry a comment each because they are unreviewable in a diff otherwise.
_CHAR_FOLDING = str.maketrans({
    'ي': 'ی',  # Arabic yeh      → Farsi yeh
    'ى': 'ی',  # alef maksura    → Farsi yeh
    'ك': 'ک',  # Arabic kaf      → keheh
    'أ': 'ا',  # alef w/ hamza above → alef
    'إ': 'ا',  # alef w/ hamza below → alef
    'آ': 'ا',  # alef madda      → alef
    'ة': 'ه',  # teh marbuta     → heh
    '‌': ' ',       # ZWNJ → space, so «آرایش‌گاه» and «آرایش گاه» both match
    '‏': '',        # RTL mark, invisible but breaks substring tests
    '‎': '',        # LTR mark, same problem
})

# Arabic diacritics (fatha, kasra, damma, sukun, shadda, tanwin…). Almost never
# typed deliberately, but they survive copy-paste and would break a match.
_DIACRITICS = ''.join(chr(c) for c in range(0x064B, 0x0653)) + 'ـ'  # + tatweel


def _fold_char(ch: str) -> str:
    """Fold a single character. May return '' (dropped) or several characters."""
    out = unicodedata.normalize('NFKC', ch)
    out = out.translate(_CHAR_FOLDING)
    out = ''.join(c for c in out if c not in _DIACRITICS)
    # casefold() rather than lower(): matters for the Latin-script terms that
    # creep into Persian business names.
    return out.casefold()


def normalize_with_map(text):
    """Return ``(normalized, offsets)`` where ``offsets[i]`` is the index in the
    *original* ``text`` that produced ``normalized[i]``.

    The map exists so the queue page can highlight a match where the reviewer
    actually reads it — in the owner's own spelling — instead of showing them a
    normalised copy. Normalisation drops and inserts characters (diacritics
    vanish, ZWNJ becomes a space, whitespace runs collapse), so the offsets
    cannot be reconstructed after the fact.

    Folding is applied per character rather than to the whole string: NFKC on a
    whole string can merge adjacent characters, which would make a 1:1 offset
    map impossible. For Persian the two are equivalent in practice — NFKC here
    only rewrites Arabic presentation forms, which are per-character mappings.
    """
    if not text:
        return '', []

    chars, offsets = [], []
    for index, ch in enumerate(str(text)):
        for folded in _fold_char(ch):
            if folded.isspace():
                # Collapse runs of whitespace to a single space, and drop
                # leading whitespace entirely, so «آرایش‌  گاه» and «آرایش گاه»
                # normalise identically.
                if not chars or chars[-1] == ' ':
                    continue
                chars.append(' ')
            else:
                chars.append(folded)
            offsets.append(index)

    while chars and chars[-1] == ' ':
        chars.pop()
        offsets.pop()

    return ''.join(chars), offsets


def normalize(text) -> str:
    """Fold ``text`` into the canonical form used for keyword matching.

    Returns ``''`` for None/empty so callers never have to guard. Defined in
    terms of :func:`normalize_with_map` so a haystack and a needle can never be
    folded by two subtly different rule sets.
    """
    return normalize_with_map(text)[0]


def find_spans(text, terms):
    """Character ranges of ``terms`` inside ``text``, as ``[(start, end), …]``.

    Indices are into the *original* ``text``. Overlapping/adjacent hits are
    merged so a highlighter never emits nested markup.
    """
    haystack, offsets = normalize_with_map(text)
    if not haystack:
        return []

    raw = []
    for term in terms:
        needle = normalize(term)
        if not needle:
            continue
        start = haystack.find(needle)
        while start != -1:
            end = start + len(needle) - 1
            raw.append((offsets[start], offsets[end] + 1))
            start = haystack.find(needle, start + 1)

    if not raw:
        return []

    raw.sort()
    merged = [raw[0]]
    for start, end in raw[1:]:
        if start <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], end))
        else:
            merged.append((start, end))
    return merged


def _effective_moderated_value(business, field):
    """The value of ``field`` a moderator/log entry should reflect.

    Once a business has cleared review at least once, an edit is staged onto
    ``pending_<field>`` rather than the live column (see Business.pending_* and
    services.stage_pending_moderated_fields), so the live column still holds
    the last-*approved* copy while it's under re-review. A reviewer deciding
    on that re-review — and the audit log recording the decision — must look
    at what's actually being proposed, not the old approved copy sitting
    underneath it. Falls back to the live value when nothing is staged (a
    first-time submission, or a field the edit didn't touch).
    """
    pending = getattr(business, 'pending_' + field, None)
    if field == 'logo':
        return pending if pending else getattr(business, field, None)
    return pending if pending is not None else getattr(business, field, None)


def moderated_snapshot(business) -> dict:
    """The MODERATED_FIELDS of ``business`` as a JSON-serialisable dict.

    ``logo`` is an ImageField, so it is stored as its file name rather than the
    FieldFile — the log has to survive ``json.dumps`` and, more importantly, has
    to still mean something after the file is replaced or deleted.
    """
    snapshot = {}
    for field in Business.MODERATED_FIELDS:
        value = _effective_moderated_value(business, field)
        if hasattr(value, 'name'):        # FieldFile / ImageFieldFile
            value = value.name or ''
        snapshot[field] = '' if value is None else str(value)
    return snapshot


def moderated_texts(business) -> dict:
    """The *textual* moderated fields, keyed by field name.

    ``logo`` is excluded: its value is a generated upload path, not something
    the owner writes, so scanning it only produces noise.
    """
    return {
        field: (_effective_moderated_value(business, field) or '')
        for field in Business.MODERATED_FIELDS
        if field != 'logo'
    }


@transaction.atomic
def apply_decision(business, to_status, actor, note=''):
    """Move ``business`` to ``to_status`` and record why.

    Atomic on purpose: a status the public gate reads (``moderation_status``)
    and its audit row have to land together. If the log insert fails we would
    rather the business stay pending than go live with no record of who cleared
    it.

    ``actor`` may be None for system-driven transitions (data migrations, the
    owner's own re-submission) — the FK is SET_NULL for the same reason.

    Returns the created :class:`BusinessModerationLog`.
    """
    from_status = business.moderation_status

    business.moderation_status = to_status
    business.moderation_note = note or ''
    business.moderation_reviewed_by = actor
    business.moderation_reviewed_at = timezone.now()

    update_fields = [
        'moderation_status',
        'moderation_note',
        'moderation_reviewed_by',
        'moderation_reviewed_at',
        'updated_at',
    ]

    if to_status == Business.MODERATION_APPROVED:
        # Promote any staged edit onto the live, publicly-served columns —
        # this is the one moment a pending draft becomes the truth — and clear
        # the staging fields so a future diff doesn't re-promote a stale draft.
        for field in Business.MODERATED_FIELDS:
            pending_field = 'pending_' + field
            pending_value = getattr(business, pending_field)
            # logo is a FieldFile: "staged" means it has a name at all, there
            # is no meaningful staged-but-empty state like there is for text.
            # Text fields use `is not None` instead of truthiness so an owner
            # deliberately clearing e.g. bio to '' still gets promoted.
            is_staged = bool(pending_value) if field == 'logo' else pending_value is not None
            if not is_staged:
                continue
            setattr(business, field, pending_value)
            setattr(business, pending_field, None)
            update_fields += [field, pending_field]
        if business.first_approved_at is None:
            business.first_approved_at = timezone.now()
            update_fields.append('first_approved_at')

    business.save(update_fields=update_fields)

    return BusinessModerationLog.objects.create(
        business=business,
        from_status=from_status,
        to_status=to_status,
        note=note or '',
        actor=actor,
        snapshot=moderated_snapshot(business),
    )


@transaction.atomic
def submit_for_review(business, reason=''):
    """Put ``business`` (back) into the pending queue and stamp its wait time.

    Used for brand-new businesses and for re-review after an owner edits a
    moderated field. ``moderation_submitted_at`` is re-stamped every time rather
    than kept at the original creation date: queue order is "who has been
    waiting longest *for this decision*", and an owner who edits an approved
    listing should not jump ahead of businesses that have never been reviewed.

    ``moderation_note`` is cleared — it holds the previous rejection reason,
    which is now answered by the resubmission and would otherwise keep showing
    on the owner's dashboard as if it were still outstanding.

    Returns the created :class:`BusinessModerationLog`.
    """
    from_status = business.moderation_status
    now = timezone.now()

    business.moderation_status = Business.MODERATION_PENDING
    business.moderation_submitted_at = now
    business.moderation_note = ''
    # reviewed_by/reviewed_at are left alone: they still truthfully record the
    # last human decision, and blanking them would lose who rejected it.
    business.save(update_fields=[
        'moderation_status',
        'moderation_submitted_at',
        'moderation_note',
        'updated_at',
    ])

    return BusinessModerationLog.objects.create(
        business=business,
        from_status=from_status,
        to_status=Business.MODERATION_PENDING,
        note=reason or '',
        actor=None,          # owner-initiated, not a staff decision
        snapshot=moderated_snapshot(business),
    )


def scan_keywords(business, keywords=None):
    """Return ``[{'term', 'severity', 'field'}, …]`` for every active match.

    **Advisory only.** This never writes, never changes status, and its result
    is not allowed to gate anything — see BannedKeyword's docstring. It exists
    so the reviewer's eye lands on the reason a listing was surfaced.

    ``keywords`` lets a caller pass a pre-fetched list when scanning many
    businesses in a loop (the queue page does exactly that), avoiding one query
    per business.
    """
    if keywords is None:
        keywords = list(BannedKeyword.objects.filter(is_active=True))

    fields = {name: normalize(value) for name, value in moderated_texts(business).items()}

    matches = []
    for keyword in keywords:
        term = normalize(keyword.term)
        if not term:
            continue
        for field_name, haystack in fields.items():
            if term and term in haystack:
                matches.append({
                    'term': keyword.term,
                    'severity': keyword.severity,
                    'field': field_name,
                })

    # HIGH first so an urgent flag is never buried under a row of LOW ones.
    matches.sort(key=lambda m: (m['severity'] != BannedKeyword.SEVERITY_HIGH, m['field']))
    return matches
