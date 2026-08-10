# core/management/commands/setup_admin_roles.py
"""
Creates the four staff roles as Django auth Groups.

Roles are Groups rather than a column on the user, because that is what
`ModelAdmin.has_*_permission()` already consults — no admin code has to know a
role exists. A staff member is granted a role by ticking `is_staff` and adding
them to the group; nothing else.

Safe to re-run: groups are get_or_create'd and their permission sets are *set*
(not appended) each run, so editing the tables below and re-running is the
supported way to change a role. A permission removed from a role here is
removed from the group on the next run.

Deliberately tolerant of missing permissions: this runs from entrypoint.sh
alongside migrate, and app permission rows only exist after that app's
migrations have been applied. A model this command does not recognise is
reported as a warning and skipped, never a crash — a half-migrated database
must not break the deploy.
"""

from django.contrib.auth.models import Group, Permission
from django.contrib.contenttypes.models import ContentType
from django.core.management.base import BaseCommand
from django.db import connection, transaction

# Shorthand: 'app_label.ModelName': 'actions', where each letter is one of
# a(dd) c(hange) d(elete) v(iew). Django's default per-model permissions.
_ACTION_CODES = {'a': 'add', 'c': 'change', 'd': 'delete', 'v': 'view'}

# Moderation: decide on businesses and own the moderation tooling end to end.
# Only view+change on Business — a moderator marks a business approved or
# rejected, but must never be able to create or delete an owner's listing.
# Nothing from accounting: moderation decisions and money stay separated.
MODERATOR = {
    'business.Business': 'vc',
    'business.BusinessModerationLog': 'acdv',
    'business.BannedKeyword': 'acdv',
    'business.ContentReport': 'acdv',
}

# Support: read everything customer-facing, change nothing — except the two
# add-only paths that *are* the support toolkit. Adding a Subscription or an
# AddOnPurchase row is how a plan or SMS/appointment credit is granted by hand
# (see accounting/admin.py); both run the same benefit-granting code as a real
# Zibal payment. No change/delete: a mistaken grant is corrected by a new row,
# which keeps the trail auditable.
SUPPORT = {
    'business.Business': 'v',
    'api.User': 'v',
    'appointment.Appointment': 'v',
    'visitor.Visitor': 'v',
    # A business owner asking "why didn't my client get the reminder SMS" is
    # exactly the kind of ticket this role exists to answer, and view_smslog
    # is also what gates the SMS operations report (visitor/reports.py) and
    # its dashboard alert panel — without it here, that whole surface was
    # reachable by Superadmin only.
    'visitor.SmsLog': 'v',
    # Gates the owner-activity panel on the user 360 page
    # (core/detail_views.py:permissions_for_user). Same oversight as the
    # view_smslog gap just above — nothing granted this, so the panel was
    # reachable by Superadmin only, silently, with no comment marking it as
    # a deliberate gap the way core.export_pii's is.
    'visitor.VisitorActivity': 'v',
    'accounting.Subscription': 'av',
    'accounting.AddOnPurchase': 'av',
    # View-only on the catalogue models: the add forms use autocomplete_fields
    # for plan/pack, and Django's autocomplete endpoint returns 403 without view
    # permission on the *target* model. Without these, the grant forms are there
    # but the plan picker silently returns nothing.
    'accounting.Plan': 'v',
    'accounting.AddOnPack': 'v',
}

# Finance: reconciliation and reporting. Read-only across accounting and
# nothing else — no user data, no businesses.
FINANCE = {
    'accounting.Plan': 'v',
    'accounting.Subscription': 'v',
    'accounting.Transaction': 'v',
    'accounting.AddOnPack': 'v',
    'accounting.AddOnPurchase': 'v',
    # The audit trail underneath the wallets AddOnPurchase grants — same
    # domain as everything else on this table, read-only same as the rest.
    'accounting.CreditLedger': 'v',
}

ROLES = {
    # Superadmin is handled separately — it gets every permission that exists.
    'Superadmin': None,
    'Moderator': MODERATOR,
    'Support': SUPPORT,
    'Finance': FINANCE,
}


class Command(BaseCommand):
    help = 'ساخت/به‌روزرسانی گروه‌های نقش پنل ادمین (idempotent)'

    def add_arguments(self, parser):
        parser.add_argument(
            '--dry-run', action='store_true',
            help='فقط گزارش بده؛ چیزی در دیتابیس تغییر نده',
        )

    def handle(self, *args, **options):
        dry_run = options['dry_run']

        # entrypoint.sh may call this on a database where migrate has not run
        # (or is running concurrently). Without auth/contenttypes there is
        # nothing to do, and failing loudly here would abort the whole deploy
        # over a first-boot ordering detail.
        missing = {'auth_group', 'auth_permission', 'django_content_type'} - set(
            connection.introspection.table_names()
        )
        if missing:
            self.stderr.write(self.style.WARNING(
                'جداول auth/contenttypes هنوز ساخته نشده‌اند '
                f"({', '.join(sorted(missing))}). ابتدا migrate را اجرا کنید."
            ))
            return

        with transaction.atomic():
            for name, spec in ROLES.items():
                if spec is None:
                    perms = list(Permission.objects.all())
                else:
                    perms = self._resolve(name, spec)
                self._apply(name, perms, dry_run)

            if dry_run:
                transaction.set_rollback(True)
                self.stdout.write(self.style.WARNING('اجرای آزمایشی — هیچ تغییری ذخیره نشد.'))

    # ── helpers ───────────────────────────────────────────────────────────────

    def _resolve(self, role, spec):
        """Turn the 'app.Model': 'acdv' shorthand into Permission rows.

        Anything that cannot be resolved (app not migrated yet, model renamed)
        is warned about and dropped rather than raising.
        """
        perms = []
        for label, actions in spec.items():
            app_label, model_name = label.split('.')
            try:
                ct = ContentType.objects.get(
                    app_label=app_label, model=model_name.lower(),
                )
            except ContentType.DoesNotExist:
                self.stderr.write(self.style.WARNING(
                    f'[{role}] مدل {label} پیدا نشد (احتمالاً migrate نشده) — رد شد.'
                ))
                continue

            for code in actions:
                action = _ACTION_CODES[code]
                codename = f'{action}_{model_name.lower()}'
                perm = Permission.objects.filter(
                    content_type=ct, codename=codename,
                ).first()
                if perm is None:
                    self.stderr.write(self.style.WARNING(
                        f'[{role}] دسترسی {app_label}.{codename} وجود ندارد — رد شد.'
                    ))
                    continue
                perms.append(perm)
        return perms

    def _apply(self, name, perms, dry_run):
        group, created = Group.objects.get_or_create(name=name)
        before = set(group.permissions.values_list('id', flat=True))
        after = {p.id for p in perms}

        if not dry_run:
            # set(), not add(): the tables above are the single source of truth,
            # so a permission dropped from a role is revoked on the next run.
            group.permissions.set(perms)

        verb = 'ساخته شد' if created else 'به‌روزرسانی شد'
        added, removed = len(after - before), len(before - after)
        detail = f'{len(after)} دسترسی'
        if not created and (added or removed):
            detail += f' (+{added} / -{removed})'
        self.stdout.write(self.style.SUCCESS(f'گروه «{name}» {verb} — {detail}'))
