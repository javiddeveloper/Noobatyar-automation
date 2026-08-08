# business/management/commands/seed_banned_keywords.py
"""
Seeds the starter BannedKeyword list.

Scope note, because the temptation to grow this list is constant: a keyword
here does **not** block, hide, or reject anything — it only highlights a match
in the moderation queue so a reviewer looks harder (see BannedKeyword's
docstring). That makes the cost of a false positive real: a list that flags
every third salon trains reviewers to click past the flags, and then the list
protects nothing. So this stays short and high-signal, aimed at three things a
booking marketplace genuinely cannot host:

  1. Services that are illegal in Iran (unlicensed drugs, forged documents).
  2. Adult / sexual services.
  3. Claiming medical credentials or procedures the platform cannot verify —
     the highest-harm category here, because a client acts on it.

Terms that merely *co-occur* with problems (تخفیف, فوری, ارزان) are excluded on
purpose: they are ordinary marketing copy for legitimate businesses.

Idempotent — safe to re-run on every deploy. Existing rows are updated in place
rather than recreated, so a moderator who deactivated a noisy term keeps that
decision (`is_active` is never touched after creation).
"""

from django.core.management.base import BaseCommand

from business.models import BannedKeyword

HIGH = BannedKeyword.SEVERITY_HIGH
LOW = BannedKeyword.SEVERITY_LOW

# (term, severity, note) — note is shown to reviewers in the admin, so it says
# *why* the term is listed, not what the term means.
KEYWORDS = [
    # ── Unverifiable medical credentials & procedures ──────────────────────
    # A booking page is where a client decides to let someone near their body.
    # We cannot verify a medical licence, so anything asserting one is reviewed
    # by a human before it goes public.
    ('تزریق بوتاکس', HIGH, 'اقدام پزشکی — نیاز به احراز مجوز پزشکی دارد'),
    ('تزریق ژل', HIGH, 'اقدام پزشکی — نیاز به احراز مجوز پزشکی دارد'),
    ('تزریق فیلر', HIGH, 'اقدام پزشکی — نیاز به احراز مجوز پزشکی دارد'),
    ('مزوتراپی', HIGH, 'اقدام پزشکی تهاجمی — نیاز به احراز مجوز'),
    ('جراحی زیبایی', HIGH, 'جراحی — فقط با مجوز پزشکی قابل ارائه است'),
    ('کاشت مو', LOW, 'اقدام پزشکی — بررسی مجوز مرکز'),
    ('سقط', HIGH, 'خدمت غیرقانونی — گزارش فوری'),
    # Deliberately NOT listed: «دکتر», «متخصص», «کلینیک». They appear in the
    # title of nearly every legitimate business in the DOCTOR category, so
    # flagging them would mark the whole category and mean nothing.

    # ── Unlicensed pharmaceuticals & narcotics ─────────────────────────────
    ('داروی قاچاق', HIGH, 'فروش داروی غیرمجاز — غیرقانونی'),
    ('قرص لاغری', HIGH, 'فروش داروی بدون مجوز — غیرقانونی و پرخطر'),
    ('آمپول لاغری', HIGH, 'فروش داروی بدون مجوز — غیرقانونی و پرخطر'),
    ('مواد مخدر', HIGH, 'فعالیت مجرمانه — گزارش فوری'),
    ('استروئید', HIGH, 'داروی کنترل‌شده — فروش بدون مجوز غیرقانونی است'),
    ('ترک اعتیاد', LOW, 'خدمت درمانی — نیاز به مجوز مرکز درمانی'),

    # ── Adult / sexual services ────────────────────────────────────────────
    ('ماساژ ویژه آقایان', HIGH, 'عبارت رایج برای پوشش خدمات جنسی'),
    ('ماساژ خصوصی', LOW, 'عبارت مبهم — بررسی شرح خدمات'),
    ('صیغه', HIGH, 'خدمت خارج از حوزه پلتفرم'),
    ('دوستیابی', HIGH, 'خدمت خارج از حوزه پلتفرم'),
    ('همراه شبانه', HIGH, 'عبارت رایج برای پوشش خدمات جنسی'),

    # ── Forgery & impersonation ────────────────────────────────────────────
    ('مدرک تضمینی', HIGH, 'فروش مدرک جعلی — غیرقانونی'),
    ('پاسپورت', LOW, 'احتمال جعل اسناد — بررسی شرح فعالیت'),
    ('ویزای تضمینی', HIGH, 'ادعای غیرقابل تضمین — کلاهبرداری رایج'),
    ('گواهی پزشکی', HIGH, 'احتمال صدور گواهی جعلی'),

    # ── Financial scams that ride on a booking page ────────────────────────
    ('سود تضمینی', HIGH, 'الگوی کلاهبرداری سرمایه‌گذاری'),
    ('استخراج ارز دیجیتال', LOW, 'خارج از حوزه پلتفرم — بررسی شود'),
    ('وام فوری', HIGH, 'الگوی کلاهبرداری مالی'),
]


class Command(BaseCommand):
    help = 'کلیدواژه‌های پایه برای نشانه‌گذاری کسب‌وکارها در صف بررسی را ایجاد می‌کند (idempotent)'

    def handle(self, *args, **options):
        created_count = 0
        updated_count = 0

        for term, severity, note in KEYWORDS:
            # `is_active` is intentionally absent from `defaults`: it only
            # applies on creation. If a moderator turned a term off because it
            # was flagging every legitimate salon, re-running this command must
            # not silently turn it back on.
            obj, created = BannedKeyword.objects.get_or_create(
                term=term,
                defaults={'severity': severity, 'note': note, 'is_active': True},
            )
            if created:
                created_count += 1
                continue

            # Severity and note are editorial copy owned by this file, so they
            # are refreshed — that is how a wording fix ships.
            if obj.severity != severity or obj.note != note:
                obj.severity = severity
                obj.note = note
                obj.save(update_fields=['severity', 'note'])
                updated_count += 1

        total = BannedKeyword.objects.count()
        self.stdout.write(self.style.SUCCESS(
            f'کلیدواژه‌ها همگام شد — {created_count} مورد جدید، '
            f'{updated_count} مورد به‌روزرسانی، مجموع {total} کلیدواژه.'
        ))
