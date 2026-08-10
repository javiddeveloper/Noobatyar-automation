# core/dashboard/panels.py
"""
Turns the raw payload into template-ready panels, filtered by what the viewer
is actually allowed to see.

Why the gate lives here and not in the queries: the payload is computed once and
shared through the cache (cache.py), so it always contains everything. This
module decides, per request, which of it reaches the page.

The rule is that a panel is shown when the viewer holds the ordinary Django
`view` permission on the model it summarises — the same permission that decides
whether they can open that model's changelist. Nothing bespoke: adding a role in
`setup_admin_roles` automatically gets the right dashboard with no change here.

Concretely, for the four roles that command creates:

  * Superadmin — everything.
  * Finance    — money only: it has view on all of `accounting` and nothing
                 else, so it gets the revenue cards, the revenue chart and the
                 stuck-payment alert, and no user/business panels.
  * Moderator  — no `accounting` permission at all, so **no revenue anywhere**;
                 it gets the business panels and the moderation queue.
  * Support    — users, businesses, appointments, subscriptions and add-on
                 purchases, but not `Transaction`, so no subscription-revenue
                 figures.

A missing permission removes a panel; it never 403s the index. A moderator
opening the dashboard must land on a useful page, not an error.
"""

import logging

from django.urls import NoReverseMatch, reverse

logger = logging.getLogger(__name__)

# Order matters: this is the column order of every KPI card.
WINDOW_LABELS = (
    ('today', 'امروز'),
    ('week', '۷ روز'),
    ('month', '۳۰ روز'),
    ('all', 'از ابتدا'),
)


def _fmt(value):
    """Latin digits with thousands separators.

    Persian digits are deliberately not used for figures: admin.css already
    forces `tabular-nums` and an LTR isolate on numeric cells so columns line
    up, and every other number in the panel (phone, order id, track id) is
    Latin. Mixing the two makes a column impossible to scan.
    """
    return f'{int(value or 0):,}'


def _cells(series):
    return [{'label': label, 'value': _fmt(series.get(key, 0))} for key, label in WINDOW_LABELS]


def _card(title, series, unit='', hint=''):
    return {'title': title, 'unit': unit, 'hint': hint, 'cells': _cells(series)}


def _rows(items, fields):
    """Flatten alert dicts to positional rows.

    The template renders these as a plain table, and the Django template
    language cannot do ``item[variable]`` — a dict of items plus a list of field
    names would need a custom filter for nothing. Ordering the values here keeps
    the template a two-level for loop with no lookups.
    """
    return [
        [_fmt(item[field]) if isinstance(item[field], int) else item[field] for field in fields]
        for item in items
    ]


def _link(*candidates):
    """The first admin URL that actually resolves, or ``None``.

    Holding `view` on a model does not mean it has a ModelAdmin: permission rows
    are created by migrate for every model, registration is a separate choice.
    `accounting.Transaction` is exactly that case today — it has no admin.py
    entry, so `admin:accounting_transaction_changelist` does not exist and a
    bare reverse() takes down the entire admin index with a NoReverseMatch.
    Candidates are tried in order so the stuck-payment panel can fall back to
    the add-on purchases list, which *is* registered and covers half the rows.
    A panel whose link resolves to nothing still renders — it just loses its
    "see all" link rather than the page.
    """
    for name, query in candidates:
        try:
            return reverse(name) + query
        except NoReverseMatch:
            continue
    logger.warning('dashboard: no admin URL resolved for %s',
                   ', '.join(name for name, _ in candidates))
    return None


def _alert(key, title, block, columns, fields, url, urgent=False, empty=''):
    rows = _rows(block['items'], fields)
    return {
        'key': key,
        'title': title,
        'count': _fmt(block['count']),
        'columns': columns,
        'rows': rows,
        # metrics caps each list at ALERT_ROWS but reports the true total, so
        # the page has to say how much it is not showing. Without this an
        # eight-row table reads as "eight problems" when there are ninety.
        'more': max(0, block['count'] - len(rows)),
        'url': url,
        # Red rail only when there is actually something to act on — a
        # permanently alarming panel stops being read.
        'urgent': urgent and bool(block['count']),
        'empty': empty,
    }


def permissions(user):
    """What the viewer may see, as a flat dict the template can also read."""
    return {
        # Transaction is the subscription-revenue table; holding `view` on it is
        # the closest thing the permission model has to "may see platform money".
        'finance': user.has_perm('accounting.view_transaction'),
        'payments': (
            user.has_perm('accounting.view_transaction')
            or user.has_perm('accounting.view_addonpurchase')
        ),
        # Split out from 'payments' so the stuck-payments panel (which mixes
        # Transaction and AddOnPurchase rows) can filter row-by-row instead of
        # showing subscription data to a viewer who only holds view_addonpurchase.
        'addon_purchases': user.has_perm('accounting.view_addonpurchase'),
        'subscriptions': user.has_perm('accounting.view_subscription'),
        'users': user.has_perm('api.view_user'),
        'businesses': user.has_perm('business.view_business'),
        'appointments': user.has_perm('appointment.view_appointment'),
        'sms': user.has_perm('visitor.view_smslog'),
        # ContentReport is a business.view_contentreport model permission,
        # same as everything else on this dict — Moderator holds it via
        # 'business.ContentReport': 'acdv' in setup_admin_roles.py.
        'content_reports': user.has_perm('business.view_contentreport'),
    }


def build(payload, user):
    """Template context for the dashboard section of the admin index."""
    can = permissions(user)
    revenue = payload['revenue']
    counts = payload['counts']
    charts = payload['charts']
    alerts = payload['alerts']

    revenue_cards = []
    if can['finance']:
        revenue_cards = [
            _card('درآمد کل پلتفرم', revenue['total'], 'تومان',
                  'مجموع پرداخت‌های موفق اشتراک و بسته‌های افزودنی'),
            _card('اشتراک‌ها', revenue['subscription'], 'تومان'),
            _card('بسته‌ی پیامک', revenue['sms_pack'], 'تومان'),
            _card('بسته‌ی نوبت', revenue['appointment_pack'], 'تومان'),
        ]
        # The feature pack is no longer sold (accounting/models.py) — only show
        # the card when historical purchases exist, instead of a permanent zero.
        if revenue['feature_pack']['all']:
            revenue_cards.append(_card('قابلیت موقت (بایگانی)', revenue['feature_pack'], 'تومان'))

    volume_cards = []
    if can['users']:
        volume_cards.append(_card('کاربران جدید', counts['users']))
    if can['businesses']:
        volume_cards.append(_card('کسب‌وکارهای جدید', counts['businesses']))
    if can['appointments']:
        volume_cards.append(_card('نوبت‌های ثبت‌شده', counts['appointments']))
    if can['sms']:
        volume_cards.append(_card(
            'پیامک‌های ارسال‌شده', counts['sms'], '',
            'بر پایه‌ی گزارش ارسال؛ دقیقاً با سهمیه‌ی محاسبه‌شده یکی نیست',
        ))

    status_cards = []
    if can['subscriptions']:
        status_cards.append({
            'title': 'اشتراک‌های فعال',
            'value': _fmt(payload['active_subscriptions']),
            'url': _link(('admin:accounting_subscription_changelist', '?status__exact=active')),
        })
    if can['businesses']:
        status_cards.append({
            'title': 'در انتظار بررسی',
            'value': _fmt(payload['pending_moderation']),
            'url': _link(('admin:business_business_moderation_queue', '')),
            'urgent': bool(payload['pending_moderation']),
        })

    # Chart series are gated one by one so a Finance user gets the revenue chart
    # with no growth chart, and a Moderator the reverse.
    chart_data = {'labels': charts['labels'], 'revenue': None, 'growth': None}
    if can['finance']:
        chart_data['revenue'] = charts['revenue']
    growth = {}
    if can['users']:
        growth['users'] = charts['growth']['users']
    if can['businesses']:
        growth['businesses'] = charts['growth']['businesses']
    if growth:
        chart_data['growth'] = growth

    alert_panels = []
    if can['subscriptions']:
        alert_panels.append(_alert(
            'expiring',
            f"اشتراک‌های رو به انقضا ({alerts['expiring_days']} روز آینده)",
            alerts['expiring'],
            ['کاربر', 'پلن', 'انقضا', 'روز مانده'],
            ['user', 'plan', 'ends_at', 'days_left'],
            _link(('admin:accounting_subscription_changelist', '?status__exact=active')),
            empty='هیچ اشتراکی به‌زودی منقضی نمی‌شود.',
        ))
    if can['payments']:
        # Row-by-row filter, not just the 'payments' OR-gate above: the block
        # mixes Transaction ('اشتراک') and AddOnPurchase ('بسته افزودنی') rows,
        # and a viewer can hold view_addonpurchase without view_transaction (the
        # Support role does exactly this). Passing the block through unfiltered
        # showed subscription amounts and plan names to a viewer with no
        # permission on Transaction at all.
        stuck_block = alerts['stuck_payments']
        stuck_items = [
            row for row in stuck_block['items']
            if (row['kind'] == 'اشتراک' and can['finance'])
            or (row['kind'] == 'بسته افزودنی' and can['addon_purchases'])
        ]
        stuck_count = (
            (stuck_block['count_transaction'] if can['finance'] else 0)
            + (stuck_block['count_addonpurchase'] if can['addon_purchases'] else 0)
        )
        alert_panels.append(_alert(
            'stuck',
            f"پرداخت‌های معلق (بیش از {stuck_block['minutes']} دقیقه)",
            {'items': stuck_items, 'count': stuck_count},
            ['نوع', 'مورد', 'کاربر', 'مبلغ', 'زمان'],
            ['kind', 'label', 'user', 'amount', 'created_at'],
            _link(('admin:accounting_transaction_changelist', '?status__exact=pending'),
                  ('admin:accounting_addonpurchase_changelist', '?status__exact=pending')),
            urgent=True,
            empty='هیچ پرداخت معلقی وجود ندارد.',
        ))
    if can['sms']:
        sms_block = alerts['sms_failures']
        alert_panels.append(_alert(
            'sms',
            f"پیامک‌های ناموفق ({sms_block['hours']} ساعت اخیر)",
            sms_block,
            ['کسب‌وکار', 'خطا', 'زمان'],
            ['business', 'error', 'sent_at'],
            _link(('admin:visitor_smslog_report', '?status=FAILED'),
                  ('admin:visitor_smslog_changelist', '?status__exact=FAILED')),
            # Urgent only past the spike threshold (metrics.py), not on every
            # single failure — see SMS_FAILURE_SPIKE_THRESHOLD's comment there.
            urgent=sms_block.get('urgent', False),
            empty='پیامک ناموفقی ثبت نشده است.',
        ))
    if can['content_reports']:
        alert_panels.append(_alert(
            'content_reports',
            'گزارش‌های تخلف در انتظار بررسی',
            alerts['content_reports'],
            ['کسب‌وکار', 'دلیل', 'گزارش‌دهنده', 'زمان'],
            ['business', 'reason', 'reporter', 'created_at'],
            _link(('admin:business_contentreport_changelist', '?status__exact=NEW')),
            empty='گزارش تخلف جدیدی وجود ندارد.',
        ))
    if can['businesses']:
        alert_panels.append(_alert(
            'queue',
            'بیشترین انتظار در صف بررسی',
            alerts['moderation_queue'],
            ['کسب‌وکار', 'مالک', 'از تاریخ', 'روز در انتظار'],
            ['title', 'owner', 'waiting_since', 'waiting_days'],
            _link(('admin:business_business_moderation_queue', '')),
            empty='صف بررسی خالی است.',
        ))

    return {
        'dashboard_can': can,
        'dashboard_any': bool(revenue_cards or volume_cards or status_cards or alert_panels),
        'dashboard_revenue_cards': revenue_cards,
        'dashboard_volume_cards': volume_cards,
        'dashboard_status_cards': status_cards,
        'dashboard_alerts': alert_panels,
        'dashboard_charts': chart_data,
        'dashboard_chart_days': payload['chart_days'],
        'dashboard_generated_at': payload['generated_at'],
    }
