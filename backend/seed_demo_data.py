# seed_demo_data.py
"""
Demo dataset for showing the admin panel — run with `python seed_demo_data.py`.

WHAT THIS IS FOR
Screenshots and walkthroughs. seed_test_data.py exists to give front_client a
couple of businesses to fetch; this one exists so every *panel* in the admin
has something truthful in it — the revenue chart has a shape, the moderation
queue has a flagged card, the stuck-payment alert has a stuck payment.

WHY IT IS A STORY AND NOT RANDOM ROWS
A demo built from `random.choice()` falls apart the moment somebody looks
twice: a salon with 900 appointments and no SMS, a business rejected before it
was submitted. Everything below hangs off eight businesses with a stated
situation each, and the numbers are generated *from* that situation. The set is
chosen to cover every visual state the panel can render:

    رزانا          APPROVED, healthy      the flagship — 6 months of history
    مهرگان         APPROVED, high no-show the "something is wrong here" case
    آرتا           PENDING, 6 days        the plain moderation-queue card
    نیک‌آیین        PENDING, flagged       the queue card with keyword hits
    تایتان         APPROVED, is_locked    billing lock ≠ editorial rejection
    نگین           SUSPENDED              acted on after a content report
    الماس          REJECTED               with a reason the owner was sent
    بهار           APPROVED, lapsed       expired subscription → churn/renewal

RE-RUNNABLE
Everything it creates is tagged by the DEMO_PREFIX phone range, and the first
thing it does is delete that range. Running it twice gives the same database,
and it never touches a user outside the range — including your superuser.

ONE THING IT CANNOT SEED
The quota/wallet tiles at the top of the user-360 page read live Redis
(accounting/usage.py), not the database — the page says so in as many words.
Under DEBUG the cache backend is LocMemCache, which is per-process, so anything
this script writes lives and dies inside this script and the runserver process
never sees it. Those tiles therefore read 0 in a local demo. The CreditLedger
table directly underneath them is the database-backed history and *is*
populated, which is the section worth pointing at in a walkthrough. Run against
a real Redis and the tiles fill in.

NOT FOR PRODUCTION. It writes plainly fake phone numbers and marks every
transaction as already settled.
"""
import os
import random
from datetime import timedelta

import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
os.environ.setdefault('DEBUG', 'True')
django.setup()

from django.contrib.auth import get_user_model  # noqa: E402
from django.db import transaction as db_transaction  # noqa: E402
from django.utils import timezone  # noqa: E402

from accounting.models import (  # noqa: E402
    AddOnPack, AddOnPurchase, CreditLedger, Plan, Subscription, Transaction,
)
from appointment.models import Appointment  # noqa: E402
from business.models import (  # noqa: E402
    BannedKeyword, Business, BusinessModerationLog, ContentReport,
)
from visitor.models import SmsLog, Visitor, VisitorActivity  # noqa: E402

from django.conf import settings  # noqa: E402

# Refuse to run anywhere that is not a development box.
#
# This script deletes rows and writes fake users, and its only argument-free
# entry point is `python seed_demo_data.py` — one careless run in the wrong
# shell on a server and it starts deleting live accounts that happen to fall in
# DEMO_PREFIX. DEBUG is the cheapest signal that separates the two; production
# fails fast at import, before any query.
if not settings.DEBUG:
    raise SystemExit(
        'seed_demo_data.py refuses to run with DEBUG=False. This script writes '
        'fake accounts and deletes the 09129… phone range — it is for local '
        'development only.'
    )

User = get_user_model()

# Every demo phone starts here. The teardown deletes exactly this range, which
# is what makes the script safe to re-run against a database that also holds
# your own test accounts.
DEMO_PREFIX = '09129'

# Seeded so two runs produce identical screenshots — a demo you can't reproduce
# is a demo you can't re-shoot after a style change.
rng = random.Random(20260810)

NOW = timezone.now()


def ago(days=0, hours=0, minutes=0):
    return NOW - timedelta(days=days, hours=hours, minutes=minutes)


def backdate(queryset_or_obj, field, when):
    """Set an auto_now_add field, which the ORM will not let you assign.

    `created_at = models.DateTimeField(auto_now_add=True)` ignores whatever you
    pass to create(), so a seeded row always lands at "now" and every chart
    comes out as a single spike on today. A post-hoc UPDATE is the only way to
    lay history down.
    """
    model = queryset_or_obj.__class__
    pk = queryset_or_obj.pk
    model.objects.filter(pk=pk).update(**{field: when})


# ─────────────────────────────────────────────────────────────────────────────
# Teardown
# ─────────────────────────────────────────────────────────────────────────────

def wipe():
    users = User.objects.filter(phone__startswith=DEMO_PREFIX)
    visitors = Visitor.objects.filter(phone_number__startswith=DEMO_PREFIX)
    n_users, n_visitors = users.count(), visitors.count()

    # Businesses cascade to appointments/sms/reports; visitors cascade to their
    # own activity. Plans and packs are PROTECTed by transactions, so they are
    # deleted only after the transactions that reference them are gone.
    Business.objects.filter(user__in=users).delete()
    Transaction.objects.filter(user__in=users).delete()
    AddOnPurchase.objects.filter(user__in=users).delete()
    Subscription.objects.filter(user__in=users).delete()
    CreditLedger.objects.filter(user__in=users).delete()
    visitors.delete()
    users.delete()
    Plan.objects.filter(name__startswith='[دمو]').delete()
    AddOnPack.objects.filter(name__startswith='[دمو]').delete()
    BannedKeyword.objects.filter(note__startswith='[دمو]').delete()
    print(f'  پاکسازی: {n_users} کاربر، {n_visitors} مراجع دمو حذف شد')


# ─────────────────────────────────────────────────────────────────────────────
# Catalogue
# ─────────────────────────────────────────────────────────────────────────────

def make_plans():
    """Three tiers. Prices are the ones a real Iranian SaaS at this size charges,
    because a demo with 1,000,000,000 تومان plans reads as unserious."""
    specs = [
        ('[دمو] پایه', 149_000, None, 1, 'month', False,
         {'sms_monthly': 200, 'appointment_monthly': 150, 'max_businesses': 1}),
        ('[دمو] حرفه‌ای', 349_000, 299_000, 1, 'month', False,
         {'sms_monthly': 1000, 'appointment_monthly': 600, 'max_businesses': 3}),
        ('[دمو] ویژه سالانه', 2_990_000, 2_490_000, 12, 'month', True,
         {'sms_monthly': -1, 'appointment_monthly': -1, 'max_businesses': 10}),
    ]
    plans = {}
    for name, price, discount, dv, du, vip, features in specs:
        plans[name.replace('[دمو] ', '')] = Plan.objects.create(
            name=name, price=price, discount_price=discount,
            duration_value=dv, duration_unit=du, is_vip=vip, is_active=True,
            description=['رزرو آنلاین نوبت', 'یادآوری پیامکی', 'پشتیبانی'],
            features=features,
        )
    print(f'  {len(plans)} پلن ساخته شد')
    return plans


def make_packs():
    specs = [
        ('[دمو] بسته ۵۰۰ پیامکی', 89_000, AddOnPack.KIND_SMS, 500, 0),
        ('[دمو] بسته ۱۰۰۰ پیامکی', 159_000, AddOnPack.KIND_SMS, 1000, 0),
        ('[دمو] بسته ۲۰۰ نوبت', 119_000, AddOnPack.KIND_APPOINTMENT, 0, 200),
    ]
    packs = {}
    for name, price, kind, sms, appt in specs:
        packs[name.replace('[دمو] ', '')] = AddOnPack.objects.create(
            name=name, price=price, kind=kind,
            sms_amount=sms, appointment_amount=appt, is_active=True,
        )
    print(f'  {len(packs)} بسته‌ی افزودنی ساخته شد')
    return packs


def make_keywords():
    """The queue highlights these inside the owner's own copy. Two severities so
    the card shows both the red and the amber treatment."""
    specs = [
        ('تضمینی', 'HIGH'), ('قطعی', 'HIGH'),
        ('ارزان‌ترین', 'LOW'), ('بهترین', 'LOW'), ('معجزه', 'HIGH'),
    ]
    for term, severity in specs:
        BannedKeyword.objects.get_or_create(
            term=term,
            defaults={'severity': severity, 'is_active': True,
                      'note': '[دمو] نمونه برای نمایش صف بررسی'},
        )
    print(f'  {len(specs)} کلیدواژه‌ی نشانه‌گذاری ساخته شد')


# ─────────────────────────────────────────────────────────────────────────────
# People and businesses
# ─────────────────────────────────────────────────────────────────────────────

OWNERS = [
    # (key, phone suffix, name, joined days ago)
    ('rozana',   '00001', 'مریم رستگاری',    195),
    ('mehregan', '00002', 'دکتر سهیل مهرگان', 150),
    ('arta',     '00003', 'بهزاد آرین',       9),
    ('nikaein',  '00004', 'دکتر لیلا نیک‌آیین', 5),
    ('titan',    '00005', 'امیر توکلی',       88),
    ('negin',    '00006', 'نگین شفیعی',       120),
    ('almas',    '00007', 'فرزانه کریمی',     40),
    ('bahar',    '00008', 'اکبر بهاری',       260),
]

BUSINESSES = [
    # key, title, category, bio, address, duration, hours
    ('rozana', 'سالن زیبایی رزانا', 'BEAUTY_SALON',
     'رنگ و مش، میکاپ عروس، پاکسازی پوست',
     'تهران، سعادت‌آباد، بلوار دریا، کوچه مطهری، پلاک ۱۴، واحد ۳', 60, (9, 21)),
    ('mehregan', 'کلینیک دندانپزشکی مهرگان', 'DOCTOR',
     'ترمیمی، ایمپلنت، ارتودنسی',
     'تهران، میدان ونک، خیابان ملاصدرا، ساختمان پزشکان مهر، طبقه ۴', 30, (8, 20)),
    ('arta', 'آرایشگاه مردانه آرتا', 'BEAUTY_SALON',
     'اصلاح، رنگ، اصلاح ریش',
     'کرج، گوهردشت، بلوار اصلی، نبش خیابان ۸', 30, (10, 22)),
    # The bio is what trips the keyword scanner — two HIGH hits and a LOW one,
    # so the queue card renders the flagged state that is otherwise invisible.
    ('nikaein', 'مشاوره تغذیه دکتر نیک‌آیین', 'CONSULTANT',
     'کاهش وزن تضمینی و قطعی با بهترین رژیم',
     'اصفهان، خیابان توحید، مجتمع پزشکی سپاهان، طبقه ۲', 45, (9, 18)),
    ('titan', 'باشگاه بدنسازی تایتان', 'OTHER',
     'بدنسازی، کراس‌فیت، مربی خصوصی',
     'مشهد، بلوار وکیل‌آباد، بین وکیل‌آباد ۲۰ و ۲۲', 90, (6, 23)),
    ('negin', 'سالن نگین', 'BEAUTY_SALON',
     'کاشت ناخن و اکستنشن مژه',
     'شیراز، معالی‌آباد، مجتمع تجاری ستاره، طبقه ۱', 75, (10, 21)),
    ('almas', 'کلینیک زیبایی الماس', 'BEAUTY_SALON',
     'تزریق ژل و بوتاکس',
     'تهران، پاسداران، خیابان بوستان، پلاک ۷', 45, (10, 20)),
    ('bahar', 'آرایشگاه سنتی بهار', 'BEAUTY_SALON',
     'اصلاح و پیرایش',
     'تبریز، خیابان امام، جنب بازار', 30, (9, 20)),
]

# key -> (moderation_status, is_locked, submitted_days_ago, note)
MODERATION = {
    'rozana':   ('APPROVED',  False, 190, ''),
    'mehregan': ('APPROVED',  False, 148, ''),
    'arta':     ('PENDING',   False, 6,   ''),
    'nikaein':  ('PENDING',   False, 2,   ''),
    'titan':    ('APPROVED',  True,  85,  ''),
    'negin':    ('SUSPENDED', False, 118,
                 'به دنبال گزارش مراجعان دربارهٔ تصاویر نامرتبط با خدمات، '
                 'صفحه موقتاً معلق شد. پس از اصلاح گالری دوباره بررسی می‌شود.'),
    'almas':    ('REJECTED',  False, 38,
                 'ادعای درمانی بدون مجوز در معرفی کوتاه. لطفاً شمارهٔ پروانهٔ '
                 'وزارت بهداشت را در بخش آدرس اضافه کنید و دوباره ارسال کنید.'),
    'bahar':    ('APPROVED',  False, 255, ''),
}

VISITOR_NAMES = [
    'زهرا احمدی', 'فاطمه موسوی', 'نرگس کاظمی', 'سمیرا رحیمی', 'الهام نوری',
    'مریم صادقی', 'پریسا جعفری', 'شیوا مرادی', 'نگار حسینی', 'آیدا سلطانی',
    'رضا محمدی', 'علی اکبری', 'حسین قربانی', 'مهدی زارع', 'سعید فتحی',
    'امید بهرامی', 'کامران یوسفی', 'بابک شریفی', 'آرش نجفی', 'پویا رستمی',
    'سارا خسروی', 'مینا عباسی', 'لیلا طاهری', 'راضیه امینی', 'سحر بیات',
    'محمد رضایی', 'یاسر داوودی', 'نوید کریمی', 'شهاب مقدم', 'فرید عطایی',
]


def make_owners():
    owners = {}
    for key, suffix, name, joined_days in OWNERS:
        u = User.objects.create(
            phone=DEMO_PREFIX + suffix, name=name,
            role='BUSINESS_OWNER', is_active=True,
        )
        u.set_password('demo1234')
        u.save()
        backdate(u, 'joined_at', ago(days=joined_days))
        owners[key] = u
    print(f'  {len(owners)} صاحب کسب‌وکار ساخته شد')
    return owners


def make_businesses(owners, reviewer):
    biz = {}
    for key, title, category, bio, address, duration, (h1, h2) in BUSINESSES:
        status, locked, submitted_days, note = MODERATION[key]
        b = Business.objects.create(
            user=owners[key], title=title, category=category, bio=bio,
            address=address, phone='02' + str(rng.randint(10_000_000, 99_999_999)),
            default_service_duration=duration,
            work_start_hour=h1, work_end_hour=h2,
            notification_enabled=True, notification_types='SMS',
            notification_minutes_before=60,
            allow_anonymous_view=True, booking_enabled=(status == 'APPROVED'),
            is_locked=locked,
            moderation_status=status, moderation_note=note,
            moderation_reviewed_by=reviewer if status in ('APPROVED', 'REJECTED', 'SUSPENDED') else None,
            moderation_reviewed_at=ago(days=max(submitted_days - 1, 0)) if status != 'PENDING' else None,
            moderation_submitted_at=ago(days=submitted_days),
        )
        backdate(b, 'created_at', ago(days=submitted_days))
        biz[key] = b

        # The decision log is the audit trail behind the status. A business that
        # shows "معلق" with an empty history looks like a bug on the 360 page,
        # so every non-pending business gets the trail it would really have.
        if status != 'PENDING':
            log = BusinessModerationLog.objects.create(
                business=b, from_status='PENDING', to_status='APPROVED',
                note='بررسی اولیه — محتوا مطابق قوانین بود.', actor=reviewer,
            )
            backdate(log, 'created_at', ago(days=max(submitted_days - 1, 0)))
        if status in ('SUSPENDED', 'REJECTED'):
            log = BusinessModerationLog.objects.create(
                business=b, from_status='APPROVED', to_status=status,
                note=note, actor=reviewer,
            )
            backdate(log, 'created_at', ago(days=max(submitted_days - 25, 1)))
    print(f'  {len(biz)} کسب‌وکار ساخته شد')
    return biz


# ─────────────────────────────────────────────────────────────────────────────
# Money
# ─────────────────────────────────────────────────────────────────────────────

def make_subscriptions(owners, plans):
    """Deliberate spread of end dates.

    Two land inside the 7-day window so the "اشتراک‌های رو به انقضا" panel is
    populated; one is lapsed so the churn figure on the financial report is not
    a dash; one never subscribed at all, which is a state the segment builder
    can filter on.
    """
    # Renewals matter as much as lapses here. Churn is
    # "expired and not renewed ÷ active at start of window", so a dataset with
    # one lapse and no renewals reports 100% churn — arithmetically right and
    # useless as a demo. The three (expired → new) pairs below are the same
    # owner resubscribing within the grace window, which is what the metric
    # counts as a renewal, and they put the figure in a believable range.
    # (owner, plan, status, started_at, ends_at offset in days — positive is
    # the past, negative the future.)
    #
    # The renewal pairs are dated so each new term starts exactly 2 days after
    # the previous one ended. That is inside SUBSCRIPTION_GRACE_DAYS (3), which
    # is the window churn() searches for a successor — start the new term
    # *before* the old one ends, or more than 3 days after, and the report
    # counts a retained customer as churned.
    specs = [
        # ── current terms ────────────────────────────────────────────────────
        ('rozana',   'حرفه‌ای',    'active',  ago(days=12),  -18),
        ('mehregan', 'ویژه سالانه', 'active',  ago(days=140), -225),
        ('titan',    'پایه',       'active',  ago(days=27),  -3),   # ⚠ expiring
        ('negin',    'حرفه‌ای',    'active',  ago(days=25),  -5),   # ⚠ expiring
        ('almas',    'پایه',       'active',  ago(days=20),  -10),
        ('arta',     'پایه',       'active',  ago(days=6),   -24),

        # ── prior terms that lapsed inside the 30-day window and were renewed
        #    (each ends 2 days before its successor above starts) ─────────────
        ('titan',    'پایه',       'expired', ago(days=58),  29),
        ('negin',    'حرفه‌ای',    'expired', ago(days=55),  27),
        ('almas',    'پایه',       'expired', ago(days=50),  22),
        ('arta',     'پایه',       'expired', ago(days=38),  8),

        # ── lapsed inside the window and never came back: the actual churn ───
        ('bahar',    'پایه',       'expired', ago(days=45),  15),

        # ── older history, outside the window. These exist so the subscription
        #    table on the user-360 page has more than one row; they are too old
        #    to affect the churn figure. ───────────────────────────────────────
        ('rozana',   'پایه',       'expired', ago(days=75),  44),
        ('mehregan', 'حرفه‌ای',    'expired', ago(days=180), 149),
        # nikaein: never subscribed — trial user still in review.
    ]
    made = 0
    for key, plan_name, status, started, ends_offset_days in specs:
        s = Subscription.objects.create(
            user=owners[key], plan=plans[plan_name], status=status,
            ends_at=NOW - timedelta(days=ends_offset_days),
        )
        backdate(s, 'started_at', started)
        made += 1
    print(f'  {made} اشتراک ساخته شد (۲ مورد در آستانهٔ انقضا، ۱ منقضی‌شده)')


def make_transactions(owners, plans):
    """~60 days of subscription payments.

    Weekday-weighted, with a realistic failure mix: most succeed, a handful fail
    at the gateway, a couple are abandoned. Without the failures the "نرخ موفقیت
    پرداخت" table on the financial report is a row of 100% and says nothing.
    """
    keys = list(owners)
    counter = 0
    counts = {'success': 0, 'failed': 0, 'cancelled': 0, 'pending': 0}

    for days_back in range(59, -1, -1):
        when = ago(days=days_back, hours=rng.randint(9, 21), minutes=rng.randint(0, 59))
        # Thursday/Friday are the Iranian weekend — traffic drops.
        weekend = when.weekday() in (3, 4)
        n = rng.choices([0, 1, 2, 3], weights=[35, 40, 18, 7])[0]
        if weekend:
            n = min(n, 1)
        for _ in range(n):
            plan = rng.choices(
                [plans['پایه'], plans['حرفه‌ای'], plans['ویژه سالانه']],
                weights=[50, 38, 12],
            )[0]
            status = rng.choices(
                ['success', 'failed', 'cancelled'], weights=[82, 12, 6],
            )[0]
            counter += 1
            t = Transaction.objects.create(
                user=owners[rng.choice(keys)], plan=plan,
                amount=plan.discount_price or plan.price,
                track_id=f'DEMOTRK{counter:06d}',
                order_id=f'DEMOORD{counter:06d}',
                status=status,
                zibal_response={'result': 100, 'demo': True},
            )
            backdate(t, 'created_at', when)
            counts[status] += 1

    # One payment left hanging 3 hours ago. This is the only row that puts
    # anything in the dashboard's "پرداخت‌های معلق (بیش از ۶۰ دقیقه)" panel —
    # without it that panel only ever demos its empty state.
    counter += 1
    stuck = Transaction.objects.create(
        user=owners['arta'], plan=plans['حرفه‌ای'],
        amount=plans['حرفه‌ای'].discount_price,
        track_id=f'DEMOTRK{counter:06d}', order_id=f'DEMOORD{counter:06d}',
        status='pending', zibal_response={'result': 100, 'demo': True},
    )
    backdate(stuck, 'created_at', ago(hours=3))
    counts['pending'] += 1

    print(f'  {counter} تراکنش اشتراک: ' + '، '.join(f'{k}={v}' for k, v in counts.items()))


def make_addon_purchases(owners, packs):
    counter = 0
    made = 0
    for days_back in range(59, -1, -1):
        if rng.random() > 0.28:
            continue
        pack = rng.choice(list(packs.values()))
        status = rng.choices(['success', 'failed'], weights=[88, 12])[0]
        counter += 1
        p = AddOnPurchase.objects.create(
            user=owners[rng.choice(['rozana', 'mehregan', 'titan', 'negin'])],
            pack=pack, amount=pack.price,
            track_id=f'DEMOADD{counter:06d}', order_id=f'DEMOAOR{counter:06d}',
            status=status,
            activated_at=ago(days=days_back) if status == 'success' else None,
            zibal_response={'result': 100, 'demo': True},
        )
        backdate(p, 'created_at', ago(days=days_back, hours=rng.randint(9, 21)))
        made += 1

    # A second stuck payment, on the add-on side, so the alert panel shows that
    # it spans both payment types rather than just subscriptions.
    counter += 1
    stuck = AddOnPurchase.objects.create(
        user=owners['negin'], pack=packs['بسته ۵۰۰ پیامکی'],
        amount=packs['بسته ۵۰۰ پیامکی'].price,
        track_id=f'DEMOADD{counter:06d}', order_id=f'DEMOAOR{counter:06d}',
        status='pending', zibal_response={'result': 100, 'demo': True},
    )
    backdate(stuck, 'created_at', ago(hours=5))
    print(f'  {made + 1} خرید بستهٔ افزودنی ساخته شد (۱ مورد معلق)')


def make_credit_ledger(owners, packs):
    """History behind the wallet figures on the user-360 page.

    The page says in as many words that the cards above are live Redis and these
    rows are the real history; with an empty table that sentence has nothing to
    point at.
    """
    owner = owners['rozana']
    balance_sms, balance_appt = 1000, 600
    events = [
        (28, 'sms_monthly', -0, 'plan_renewal'),
        (26, 'sms_monthly', -34, 'sms_send'),
        (24, 'appointment_monthly', -41, 'booking'),
        (21, 'sms_monthly', -58, 'sms_send'),
        (19, 'appointment_monthly', -63, 'booking'),
        (16, 'sms_wallet', +500, 'addon_purchase'),
        (14, 'sms_monthly', -77, 'sms_send'),
        (11, 'appointment_monthly', -52, 'booking'),
        (7, 'sms_monthly', -66, 'sms_send'),
        (4, 'appointment_monthly', -48, 'booking'),
        (1, 'sms_monthly', -29, 'sms_send'),
    ]
    made = 0
    for days_back, metric, delta, reason in events:
        if metric.startswith('sms'):
            balance_sms += delta
            after = balance_sms
        else:
            balance_appt += delta
            after = balance_appt
        e = CreditLedger.objects.create(
            user=owner, metric=metric, delta=delta, balance_after=after,
            reason=reason,
            ref_type='addon_purchase' if reason == 'addon_purchase' else '',
        )
        backdate(e, 'created_at', ago(days=days_back, hours=rng.randint(8, 20)))
        made += 1
    print(f'  {made} رویداد تاریخچهٔ اعتبار ساخته شد')


# ─────────────────────────────────────────────────────────────────────────────
# Operations
# ─────────────────────────────────────────────────────────────────────────────

def make_visitors():
    visitors = []
    for i, name in enumerate(VISITOR_NAMES):
        v = Visitor.objects.create(
            full_name=name,
            phone_number=f'{DEMO_PREFIX}1{i:05d}',
            # A tenth opt out. The segment builder shows "excluded by consent"
            # as its own figure, and that number has to be non-zero for the
            # point of the screen — consent is enforced, not advisory — to land.
            marketing_opt_out=(i % 10 == 3),
        )
        backdate(v, 'created_at', ago(days=rng.randint(5, 180)))
        visitors.append(v)
    opted_out = sum(1 for v in visitors if v.marketing_opt_out)
    print(f'  {len(visitors)} مراجع ساخته شد ({opted_out} مورد انصراف از بازاریابی)')
    return visitors


# Per-business appointment behaviour. no_show_weight is what separates a healthy
# salon from the clinic that needs a phone call.
APPOINTMENT_PROFILE = {
    'rozana':   {'per_day': (2, 5), 'days': 120, 'no_show': 6},
    'mehregan': {'per_day': (3, 7), 'days': 120, 'no_show': 24},
    'titan':    {'per_day': (1, 3), 'days': 80,  'no_show': 11},
    'negin':    {'per_day': (1, 3), 'days': 90,  'no_show': 9},
    'bahar':    {'per_day': (0, 2), 'days': 60,  'no_show': 5},
    'almas':    {'per_day': (0, 1), 'days': 30,  'no_show': 8},
}


def make_appointments(biz, owners, visitors):
    total = 0
    for key, profile in APPOINTMENT_PROFILE.items():
        b = biz[key]
        owner = owners[key]
        no_show_w = profile['no_show']
        for days_back in range(profile['days'], -1, -1):
            when_day = ago(days=days_back)
            if when_day.weekday() == 4:          # Friday, closed
                continue
            for _ in range(rng.randint(*profile['per_day'])):
                visitor = rng.choice(visitors)
                slot = when_day.replace(
                    hour=rng.randint(b.work_start_hour, max(b.work_end_hour - 1, b.work_start_hour)),
                    minute=rng.choice([0, 15, 30, 45]), second=0, microsecond=0,
                )
                if days_back == 0:
                    status = rng.choices(
                        ['CONFIRMED', 'IN_PROGRESS', 'WAITING'], weights=[70, 15, 15])[0]
                elif days_back < 0:
                    status = 'CONFIRMED'
                else:
                    status = rng.choices(
                        ['COMPLETED', 'NO_SHOW', 'CANCELLED'],
                        weights=[100 - no_show_w - 8, no_show_w, 8],
                    )[0]
                a = Appointment.objects.create(
                    user=owner, business=b, visitor=visitor,
                    appointment_date=slot,
                    service_duration=b.default_service_duration,
                    status=status,
                    selected_services=rng.choice(
                        ['اصلاح', 'رنگ و مش', 'پاکسازی', 'مشاوره', 'ویزیت', '']),
                )
                backdate(a, 'created_at', slot - timedelta(days=rng.randint(1, 8)))
                total += 1

    # A handful of future bookings, so "امروز"/upcoming is not empty on a demo
    # shot taken in the afternoon.
    for offset in range(1, 6):
        for key in ('rozana', 'mehregan'):
            b, owner = biz[key], owners[key]
            for _ in range(rng.randint(1, 3)):
                slot = (NOW + timedelta(days=offset)).replace(
                    hour=rng.randint(b.work_start_hour, b.work_end_hour - 1),
                    minute=rng.choice([0, 30]), second=0, microsecond=0)
                a = Appointment.objects.create(
                    user=owner, business=b, visitor=rng.choice(visitors),
                    appointment_date=slot,
                    service_duration=b.default_service_duration,
                    status='CONFIRMED',
                )
                backdate(a, 'created_at', ago(days=rng.randint(0, 3)))
                total += 1

    print(f'  {total} نوبت ساخته شد')


# Provider errors, worded the way a real SMS gateway reports them. The SMS
# report's error column is unreadable filler if every row says "error".
SMS_ERRORS = [
    'کد ۲۰: شماره مقصد در لیست سیاه اپراتور است',
    'کد ۱۱: اعتبار پنل کافی نیست',
    'کد ۳۵: شماره مقصد نامعتبر است',
    'timeout: پاسخی از سرویس‌دهنده دریافت نشد (۳۰ ثانیه)',
    'کد ۱۶: خط ارسال‌کننده غیرفعال است',
]


def make_sms_logs(biz, visitors):
    total, failed = 0, 0
    for key in ('rozana', 'mehregan', 'titan', 'negin', 'bahar'):
        b = biz[key]
        # مهرگان runs a flakier line than the rest, so "ناموفق‌ها به تفکیک
        # کسب‌وکار" actually ranks something instead of showing a flat list.
        fail_rate = 0.17 if key == 'mehregan' else 0.04
        for days_back in range(29, -1, -1):
            for _ in range(rng.randint(2, 9)):
                is_fail = rng.random() < fail_rate
                log = SmsLog.objects.create(
                    business=b, visitor=rng.choice(visitors),
                    message_text=f'یادآوری نوبت شما در {b.title}',
                    status='FAILED' if is_fail else 'SENT',
                    error_detail=rng.choice(SMS_ERRORS) if is_fail else None,
                )
                backdate(log, 'sent_at',
                         ago(days=days_back, hours=rng.randint(8, 21),
                             minutes=rng.randint(0, 59)))
                total += 1
                failed += is_fail

    # Failures inside the last 24h specifically — that is the window the
    # dashboard's "پیامک‌های ناموفق" alert looks at, and the 30-day rows above
    # all fall outside it.
    for hours_back in (2, 4, 7, 11, 19):
        log = SmsLog.objects.create(
            business=biz['mehregan'], visitor=rng.choice(visitors),
            message_text='یادآوری نوبت شما در کلینیک دندانپزشکی مهرگان',
            status='FAILED', error_detail=rng.choice(SMS_ERRORS),
        )
        backdate(log, 'sent_at', ago(hours=hours_back))
        total += 1
        failed += 1

    print(f'  {total} لاگ پیامک ساخته شد ({failed} ناموفق، ۵ مورد در ۲۴ ساعت اخیر)')


def make_content_reports(biz, visitors, reviewer):
    """One report per status, so the changelist shows all four badge colours."""
    specs = [
        ('negin', 'INAPPROPRIATE',
         'تصاویر گالری با خدمات اعلام‌شده هم‌خوانی ندارد.',
         ContentReport.STATUS_ACTIONED, 14,
         'گزارش بررسی و تأیید شد؛ صفحه تا اصلاح گالری معلق شد.'),
        ('almas', 'MISLEADING',
         'ادعای درمان قطعی بدون ذکر مجوز.',
         ContentReport.STATUS_ACTIONED, 30,
         'ادعای درمانی بدون مجوز تأیید شد؛ صفحه رد شد و دلیل برای مالک ارسال شد.'),
        ('titan', 'SPAM',
         'پیامک تبلیغاتی مکرر بدون رضایت.',
         ContentReport.STATUS_REVIEWING, 3, ''),
        ('nikaein', 'MISLEADING',
         'وعدهٔ «کاهش وزن تضمینی» در معرفی کسب‌وکار.',
         ContentReport.STATUS_NEW, 1, ''),
        ('rozana', 'OTHER',
         'ساعت کاری اعلام‌شده با واقعیت فرق دارد.',
         ContentReport.STATUS_DISMISSED, 21,
         'بررسی شد؛ ساعت کاری در همان روز توسط مالک به‌روزرسانی شده بود.'),
        ('arta', 'IMPERSONATION',
         'به نظر می‌رسد از نام و لوگوی سالن دیگری استفاده شده.',
         ContentReport.STATUS_NEW, 0, ''),
    ]
    for key, reason, detail, status, days_back, resolution in specs:
        resolved = status in (ContentReport.STATUS_ACTIONED, ContentReport.STATUS_DISMISSED)
        r = ContentReport.objects.create(
            business=biz[key], reason=reason, detail=detail, status=status,
            reporter_visitor=rng.choice(visitors),
            reporter_phone=rng.choice(visitors).phone_number,
            resolution_note=resolution,
            resolved_by=reviewer if resolved else None,
            resolved_at=ago(days=max(days_back - 1, 0)) if resolved else None,
        )
        backdate(r, 'created_at', ago(days=days_back, hours=rng.randint(1, 20)))
    print(f'  {len(specs)} گزارش تخلف ساخته شد (هر چهار وضعیت)')


def make_activity(biz, owners, visitors):
    made = 0
    for _ in range(45):
        key = rng.choice(['rozana', 'mehregan', 'titan'])
        v = rng.choice(visitors)
        act = VisitorActivity.objects.create(
            visitor=v, business=biz[key],
            action=rng.choice([
                'APPOINTMENT_BOOKED', 'APPOINTMENT_STATUS_CHANGED',
                'APPOINTMENT_CANCELLED', 'PROFILE_UPDATED']),
            actor_type=VisitorActivity.ACTOR_OWNER,
            actor_user=owners[key],
        )
        backdate(act, 'created_at', ago(days=rng.randint(0, 45), hours=rng.randint(0, 23)))
        made += 1
    print(f'  {made} رویداد فعالیت ساخته شد')


# ─────────────────────────────────────────────────────────────────────────────

@db_transaction.atomic
def run():
    print('\n── پاکسازی دادهٔ دموی قبلی ──────────────────────────────')
    wipe()

    reviewer = (User.objects.filter(is_superuser=True).first()
                or User.objects.filter(is_staff=True).first())
    if reviewer is None:
        raise SystemExit(
            'هیچ کاربر ادمینی پیدا نشد. ابتدا `python manage.py createsuperuser` '
            'را اجرا کنید — تصمیم‌های بررسی باید به یک بازبین واقعی نسبت داده شوند.'
        )

    print('\n── ساخت دادهٔ دمو ───────────────────────────────────────')
    plans = make_plans()
    packs = make_packs()
    make_keywords()
    owners = make_owners()
    biz = make_businesses(owners, reviewer)
    visitors = make_visitors()
    make_subscriptions(owners, plans)
    make_transactions(owners, plans)
    make_addon_purchases(owners, packs)
    make_credit_ledger(owners, packs)
    make_appointments(biz, owners, visitors)
    make_sms_logs(biz, visitors)
    make_content_reports(biz, visitors, reviewer)
    make_activity(biz, owners, visitors)

    print('\n── آماده ────────────────────────────────────────────────')
    print(f'  بازبین تصمیم‌های بررسی: {reviewer.name or reviewer.phone}')
    print('  رمز همهٔ صاحبان کسب‌وکار دمو: demo1234')
    print('  داشبورد نتایج را از حافظهٔ موقت می‌خواند — دکمهٔ «بروزرسانی»'
          ' را بزنید یا سرور را ری‌استارت کنید.\n')


if __name__ == '__main__':
    run()
