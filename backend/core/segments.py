# core/segments.py
"""
Query layer behind the audience segment builder
(NobatyarAdminSite.segment_builder_view / segment_count_view /
segment_export_view in core/admin_site.py).

Same split as core/dashboard/metrics.py and accounting/reports.py: this module
parses filter parameters, builds querysets, counts, and yields export rows.
No request, no HTML, no permission check — the view's job is "parse → gate on
permission → render/export", and every number here is unit-testable directly
(core/tests_segments.py) against hand-built fixtures.

── Two audiences, two filter dialects ──────────────────────────────────────

``kind='visitor'`` filters ``visitor.Visitor`` — a client who has booked
somewhere. ``kind='owner'`` filters ``api.User`` restricted to users who own
at least one business. They are different models with different filter keys;
``build_queryset(kind, filters)`` dispatches to ``_visitor_queryset`` or
``_owner_queryset`` and the two never mix.

── Correctness rule ─────────────────────────────────────────────────────────

No query-per-row loop, anywhere in this module. A segment count/export is run
against ``Visitor``/``Appointment`` — potentially the two largest tables in
the platform — so every filter is expressed as `annotate`/`Count(...,
filter=Q(...))`/`Min`/`Max` (conditional aggregation reusing one JOIN per
relation, the same pattern Django's own docs use for "total vs matching
subset" — see ``_visitor_queryset``) rather than fetched and filtered in
Python. The **one** deliberate exception is the owner-side "low wallet
balance" filter — see ``_apply_low_wallet_filter`` — which cannot be
expressed in SQL at all because the balance lives only in Redis
(``accounting/usage.py`` keeps no historical/queryable table). That filter
batches one ``cache.get_many()`` over the *already DB-filtered* candidate
ids, never a Redis round trip per row.

── The live-count / drift warning ───────────────────────────────────────────

Every count and export here is evaluated against **current** data, always —
there is no "frozen at save time" mode. A saved ``core.models.AudienceSegment``
is a stored filter definition, not a stored result set; re-running it
(``segment_run_view``) re-executes these same queries. This is most visible
on the low-wallet filter (an owner topping up their SMS wallet between two
runs of the same saved segment silently leaves it) but is just as true of
every other filter — "hasn't booked in 60 days" for a visitor who books
today drops them from the very next re-run. The UI repeats this; it is not
restated per-filter here.

── Consent ───────────────────────────────────────────────────────────────────

Visitor segments default to excluding ``Visitor.marketing_opt_out=True`` rows
(``exclude_opted_out=True``), and the exclusion count is always computed and
returned alongside the final count so staff see "12 opted out, excluded" —
never a number that silently shrank with no explanation (see
``count_visitor_segment``'s return shape). There is deliberately **no**
equivalent opt-out for owner segments in this phase — see this module's
report / core/admin_site.py's segment_builder_view docstring for why that gap
is flagged rather than resolved here.
"""

from django.core.cache import cache
from django.db.models import Count, F, Max, Min, Q
from django.utils import timezone

from accounting.models import Plan
from accounting.reports import ReportRangeError, local_midnight, parse_jalali_date
from accounting.usage import _wallet_key  # noqa: SLF001 — see _apply_low_wallet_filter
from api.models import User
from appointment.models import Appointment
from business.models import Business
from visitor.models import Visitor

# Rows shown in a live-count preview / an export's own sanity cap. Not a hard
# ceiling on the export itself (staff may legitimately need every row) — see
# segment_export_view, which streams the full filtered set.
DEFAULT_NOT_BOOKED_DAYS = 60


class SegmentFilterError(ValueError):
    """A filter value from the builder form could not be parsed."""


# ── Visitor-side filters ──────────────────────────────────────────────────────

def _visitor_scope_q(filters):
    """The Q object every visitor-side aggregate is conditioned on: which of a
    visitor's appointments count at all, before any status/date/count filter
    is applied. Empty (matches every appointment) when no business/category
    filter is set, so the rest of the filters default to "across every
    business" rather than silently requiring one to be picked.
    """
    q = Q()
    business_ids = filters.get('business_ids')
    if business_ids:
        q &= Q(appointments__business_id__in=business_ids)
    category = filters.get('business_category')
    if category:
        q &= Q(appointments__business__category=category)
    return q


def _visitor_queryset(filters):
    """Visitor queryset before the marketing_opt_out exclusion — kept separate
    from `visitor_queryset` so callers can compute "how many would be excluded"
    without running the filter logic twice.

    One query: every Count/Min/Max below shares the `appointments` join via
    Django's conditional-aggregation machinery (`Count('appointments',
    filter=Q(...))` — see this module's docstring), so this compiles to a
    single GROUP BY over Visitor ⋈ Appointment ⋈ Business, not one query per
    filter.
    """
    scope = _visitor_scope_q(filters)
    qs = Visitor.objects.annotate(
        _scoped_count=Count('appointments', filter=scope),
        _first_appt=Min('appointments__appointment_date', filter=scope),
        _last_appt=Max('appointments__appointment_date', filter=scope),
    )

    # Presence of *any* business/category filter still requires at least one
    # matching appointment — otherwise "business=X" would match visitors with
    # zero appointments at X, which defeats the filter's purpose.
    if filters.get('business_ids') or filters.get('business_category'):
        qs = qs.filter(_scoped_count__gt=0)

    min_count = filters.get('min_appointment_count')
    if min_count:
        qs = qs.filter(_scoped_count__gte=min_count)

    has_status = filters.get('has_status')
    if has_status:
        qs = qs.annotate(
            _has_status_count=Count('appointments', filter=scope & Q(appointments__status=has_status))
        ).filter(_has_status_count__gt=0)

    all_status = filters.get('all_status')
    if all_status:
        qs = qs.annotate(
            _all_status_count=Count('appointments', filter=scope & Q(appointments__status=all_status))
        ).filter(_scoped_count__gt=0, _all_status_count=F('_scoped_count'))
        # F() compares the two annotated aggregates against each other
        # (all appointments in scope share the same status) — Django
        # compiles that into the HAVING clause, still one query.

    # Local-midnight boundaries, not a bare `__date` lookup: same Tehran
    # (+03:30) boundary rule core/dashboard/metrics.py's docstring explains —
    # extracting `.date()` from a stored UTC instant misfiles the last 3.5
    # hours of every local day onto the wrong calendar date.
    date_from = filters.get('appointment_date_from')
    date_to = filters.get('appointment_date_to')
    date_field = '_first_appt' if filters.get('appointment_date_field') == 'first' else '_last_appt'
    if date_from:
        qs = qs.filter(**{f'{date_field}__gte': local_midnight(date_from)})
    if date_to:
        qs = qs.filter(**{f'{date_field}__lt': local_midnight(date_to + timezone.timedelta(days=1))})

    not_booked_days = filters.get('not_booked_days')
    if not_booked_days:
        cutoff = timezone.now() - timezone.timedelta(days=not_booked_days)
        # Must have booked before (otherwise this is just "everyone who never
        # booked", a different question) and not since the cutoff.
        qs = qs.filter(_last_appt__isnull=False, _last_appt__lt=cutoff)

    return qs


def visitor_queryset(filters, exclude_opted_out=True):
    qs = _visitor_queryset(filters)
    if exclude_opted_out:
        qs = qs.filter(marketing_opt_out=False)
    return qs


def count_visitor_segment(filters, exclude_opted_out=True):
    """Total matching + opted-out breakdown, two queries (not a query per
    row): one count with the marketing filter applied, one without, so the
    difference is always shown rather than a number that silently shrank —
    see this module's docstring, "Consent"."""
    base = _visitor_queryset(filters)
    total_before_consent = base.count()
    opted_out = base.filter(marketing_opt_out=True).count()
    included = total_before_consent - opted_out if exclude_opted_out else total_before_consent
    return {
        'total_before_consent': total_before_consent,
        'opted_out_excluded': opted_out if exclude_opted_out else 0,
        'included': included,
        'exclude_opted_out': exclude_opted_out,
    }


def export_visitor_rows(filters, exclude_opted_out=True):
    """``(full_name, phone_number)`` tuples for CSV export — one query,
    streamed via `.iterator()` so a large segment does not load every row into
    memory at once before the CSV writer even starts."""
    qs = visitor_queryset(filters, exclude_opted_out=exclude_opted_out)
    return qs.order_by('id').values_list('full_name', 'phone_number').iterator()


# ── Owner-side filters ─────────────────────────────────────────────────────────

SUBSCRIPTION_ACTIVE = 'active'
SUBSCRIPTION_EXPIRED = 'expired'
SUBSCRIPTION_NEVER = 'never'


def _owner_queryset_without_wallet(filters):
    """Every owner-side filter that *can* be expressed in SQL. Wallet
    filtering happens afterward in `_apply_low_wallet_filter` because it
    cannot join against Redis."""
    now = timezone.now()
    # "Owner" = has at least one business, not User.role: role is a
    # self-reported/administratively-set field with its own documented
    # drift problem (see docs/ADMIN_PANEL.md §5, role vs is_staff) and
    # nothing keeps it in sync with actually owning a Business. Deriving
    # "owner" from the real relation is the one definition that can't drift.
    qs = User.objects.filter(businesses__isnull=False)

    category = filters.get('business_category')
    if category:
        qs = qs.filter(businesses__category=category)

    min_businesses = filters.get('min_businesses')
    if min_businesses:
        qs = qs.annotate(_biz_count=Count('businesses', distinct=True)).filter(_biz_count__gte=min_businesses)

    sub_status = filters.get('subscription_status')
    if sub_status == SUBSCRIPTION_ACTIVE:
        qs = qs.filter(subscriptions__status='active', subscriptions__ends_at__gt=now)
    elif sub_status == SUBSCRIPTION_EXPIRED:
        # Has subscribed before, but nothing currently active — matches the
        # is_valid()/metrics.standing() convention of checking ends_at, not
        # trusting a possibly-stale `status` column alone.
        qs = qs.filter(subscriptions__isnull=False).exclude(
            subscriptions__status='active', subscriptions__ends_at__gt=now
        )
    elif sub_status == SUBSCRIPTION_NEVER:
        qs = qs.filter(subscriptions__isnull=True)

    plan_id = filters.get('plan_id')
    if plan_id:
        qs = qs.filter(subscriptions__plan_id=plan_id, subscriptions__status='active', subscriptions__ends_at__gt=now)

    expiry_within_days = filters.get('expiry_within_days')
    if expiry_within_days:
        deadline = now + timezone.timedelta(days=expiry_within_days)
        qs = qs.filter(
            subscriptions__status='active', subscriptions__ends_at__gt=now, subscriptions__ends_at__lte=deadline,
        )

    return qs.distinct()


# A cache miss and a cache OUTAGE are indistinguishable through get_many()
# alone: django-redis's IGNORE_EXCEPTIONS (core/settings.py) swallows a Redis
# connection failure and returns {} — the exact same value a genuinely-empty
# batch of wallet keys produces, since most owners who never bought an SMS
# add-on simply have no key at all. For the booking/SMS-send path that
# ambiguity is fine and deliberate ("fail open" — accounting/usage.py's own
# docstring). It is NOT fine here: {} read as "balance 0 for everyone" means
# every owner in the DB matches a "low wallet" filter, so a Redis blip turns
# a targeted upsell segment into "the entire owner base" with a plausible
# row count and no warning anywhere in the exported CSV or its audit row.
# A cheap round-trip on a throwaway key tells the two cases apart before the
# filter result is trusted for a marketing export.
_WALLET_PROBE_KEY = 'segments:wallet_probe'


def _wallet_cache_is_reachable():
    token = str(timezone.now().timestamp())
    cache.set(_WALLET_PROBE_KEY, token, timeout=10)
    return cache.get(_WALLET_PROBE_KEY) == token


def _apply_low_wallet_filter(user_ids, threshold):
    """Which of `user_ids` currently have an SMS wallet balance below
    `threshold` — a Redis-only check (accounting/usage.py's wallet has no
    database row/history to aggregate; see that module's docstring).

    One batched `cache.get_many()` over the already DB-narrowed candidate ids
    — never a `get_wallet()` call per user. Documented as the one place in
    this module's counts/exports that reads live external state rather than
    the database the rest of the query ran against; see this module's
    docstring, "the live-count / drift warning".

    Raises :class:`SegmentFilterError` rather than silently matching everyone
    if the cache backend cannot be reached at all — see `_wallet_cache_is_reachable`.
    """
    ids = list(user_ids)
    if not ids:
        return []
    if not _wallet_cache_is_reachable():
        raise SegmentFilterError(
            'در حال حاضر امکان بررسی موجودی کیف‌پول وجود ندارد (اتصال به سرویس کش '
            'برقرار نیست). فیلتر «موجودی کم» را حذف کنید یا کمی بعد دوباره تلاش کنید.'
        )
    keys = {uid: _wallet_key(uid) for uid in ids}
    values = cache.get_many(list(keys.values()))
    return [uid for uid, key in keys.items() if int(values.get(key) or 0) < threshold]


def owner_ids_for_segment(filters):
    """Final list of matching owner ids, after the DB filters and (if set)
    the Redis-based wallet filter. Returns ids rather than a queryset because
    the wallet step, when present, cannot stay expressed in SQL."""
    qs = _owner_queryset_without_wallet(filters)
    low_wallet_below = filters.get('low_wallet_below')
    if low_wallet_below is None:
        return list(qs.values_list('id', flat=True))
    candidate_ids = qs.values_list('id', flat=True)
    return _apply_low_wallet_filter(candidate_ids, low_wallet_below)


def owner_queryset(filters):
    return User.objects.filter(id__in=owner_ids_for_segment(filters))


def count_owner_segment(filters):
    return {'included': len(owner_ids_for_segment(filters))}


def export_owner_rows(filters):
    """``(name, phone)`` tuples for CSV export.

    No email-equivalent column: `api.models.User` has no email field at all
    (phone is the only contact identifier — see USERNAME_FIELD='phone'), so
    the "email-equivalent-if-any" column this phase's brief asked for is
    always empty for every owner today. Left out of the row shape entirely
    rather than emitted as a permanently blank column — see this module's
    report for the same note.
    """
    ids = owner_ids_for_segment(filters)
    return iter(
        User.objects.filter(id__in=ids).order_by('id').values_list('name', 'phone')
    )


# ── Dispatch + shared counting/export entry points ─────────────────────────────

def count_segment(kind, filters, exclude_opted_out=True):
    if kind == 'visitor':
        return count_visitor_segment(filters, exclude_opted_out=exclude_opted_out)
    return count_owner_segment(filters)


def export_rows(kind, filters, exclude_opted_out=True):
    """Returns (header, row_iterator, row_count) — row_count is always
    computed as a real `count()`/`len()` up front (never `len(list(...))`
    after materialising the export) because segment_export_view must write
    the audit-log row before it can honestly claim to know how many rows it
    sent. See core/admin_site.py's segment_export_view.
    """
    if kind == 'visitor':
        counts = count_visitor_segment(filters, exclude_opted_out=exclude_opted_out)
        return (
            ('نام', 'شماره تلفن'),
            export_visitor_rows(filters, exclude_opted_out=exclude_opted_out),
            counts['included'],
        )
    counts = count_owner_segment(filters)
    return (('نام', 'شماره تلفن'), export_owner_rows(filters), counts['included'])


# ── Form parsing ──────────────────────────────────────────────────────────────
# Turns the builder page's GET querystring into the `filters` dict every
# function above consumes. Kept here (not in the view) for the same reason
# accounting/reports.parse_range lives in reports.py rather than
# TransactionAdmin: it is pure parsing logic, unit-testable without a request.

APPOINTMENT_STATUS_CHOICES = Appointment.STATUS_CHOICES
BUSINESS_CATEGORY_CHOICES = Business.CATEGORY_CHOICES


def _parse_int(raw):
    raw = (raw or '').strip()
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        raise SegmentFilterError(f'عدد نامعتبر: «{raw}»')


def _parse_ids(raw):
    raw = (raw or '').strip()
    if not raw:
        return []
    try:
        return [int(part) for part in raw.split(',') if part.strip()]
    except ValueError:
        raise SegmentFilterError(f'فهرست شناسه نامعتبر: «{raw}»')


def _parse_jalali_or_none(raw):
    raw = (raw or '').strip()
    if not raw:
        return None
    try:
        return parse_jalali_date(raw)
    except ReportRangeError as exc:
        raise SegmentFilterError(str(exc))


def parse_visitor_filters(params):
    return {
        'business_ids': _parse_ids(params.get('business_ids', '')),
        'business_category': params.get('business_category') or '',
        'appointment_date_field': 'first' if params.get('appointment_date_field') == 'first' else 'last',
        'appointment_date_from': _parse_jalali_or_none(params.get('appointment_date_from')),
        'appointment_date_to': _parse_jalali_or_none(params.get('appointment_date_to')),
        'min_appointment_count': _parse_int(params.get('min_appointment_count')),
        'has_status': params.get('has_status') or '',
        'all_status': params.get('all_status') or '',
        'not_booked_days': _parse_int(params.get('not_booked_days')),
    }


def parse_owner_filters(params):
    return {
        'business_category': params.get('business_category') or '',
        'min_businesses': _parse_int(params.get('min_businesses')),
        'subscription_status': params.get('subscription_status') or '',
        'plan_id': _parse_int(params.get('plan_id')),
        'expiry_within_days': _parse_int(params.get('expiry_within_days')),
        'low_wallet_below': _parse_int(params.get('low_wallet_below')),
    }


def parse_filters(kind, params):
    if kind == 'visitor':
        return parse_visitor_filters(params)
    return parse_owner_filters(params)


# Which raw querystring/POST keys belong to each kind — used by
# NobatyarAdminSite to persist a *savable* filter definition (see
# core/models.py's AudienceSegment.definition). Deliberately the raw string
# values as submitted (e.g. a Jalali date string, a comma-joined id list),
# not the parsed `filters` dict `parse_visitor_filters`/`parse_owner_filters`
# return: those contain Python `date` objects and lists that would need a
# custom JSON encoder, and — more importantly — storing the raw strings means
# `segment_run_view` can feed them straight back through `parse_filters()`
# next time, the exact same path a fresh form submission takes. No parallel
# "deserialize a saved segment" code path to keep in sync with this one.
VISITOR_PARAM_KEYS = (
    'business_ids', 'business_category', 'appointment_date_field',
    'appointment_date_from', 'appointment_date_to', 'min_appointment_count',
    'has_status', 'all_status', 'not_booked_days',
)
OWNER_PARAM_KEYS = (
    'business_category', 'min_businesses', 'subscription_status',
    'plan_id', 'expiry_within_days', 'low_wallet_below',
)


def raw_params(kind, params):
    """The subset of `params` (a request.GET/POST QueryDict or plain dict)
    relevant to `kind`, as plain strings — the JSON-serializable, round-
    trippable form saved segments and export audit rows store."""
    keys = VISITOR_PARAM_KEYS if kind == 'visitor' else OWNER_PARAM_KEYS
    return {key: params.get(key) for key in keys if (params.get(key) or '').strip()}


def plan_choices():
    return list(Plan.objects.filter(is_active=True).values('id', 'name').order_by('name'))
