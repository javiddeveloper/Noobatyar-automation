# accounting/management/commands/rebuild_wallets_from_ledger.py
"""
Recompute a user's Redis usage/wallet state from CreditLedger and write it
back — the recovery tool the ledger exists to make possible.

Redis is still the live source of truth for gating behaviour (see
``accounting/usage.py``'s module docstring); this command only exists for the
day that state is wrong or gone (a botched migration, a `FLUSHDB`, a
misconfigured replica promotion) and needs to be reconstructed from the
durable table underneath it.

For each of the four buckets CreditLedger tracks, the *most recent* row for
a user already carries ``balance_after`` — the exact value that belonged in
Redis right after that event — so rebuilding never means replaying every
delta from the beginning; it means reading one row. Per user this is at most
four indexed lookups (``user, metric, -created_at`` — see the model's
``Meta.indexes``), regardless of how many thousand events are in that user's
history.

Monthly buckets (``sms_monthly`` / ``appointment_monthly``) are treated
differently from wallet buckets (``sms_wallet`` / ``appointment_wallet``):
monthly counters auto-reset every calendar month in Redis (a new key per
``YYYY-MM``, see ``usage._month_key``), so a ledger row from a past month
does not describe what belongs in *this* month's key — the correct rebuilt
value there is 0 (nothing consumed yet this month), not that old row's
balance. Wallets never reset, so their latest row is authoritative no matter
how old it is.

A bucket with **no** ledger history at all is left untouched, not zeroed —
wallet credit granted before this ledger existed (or any bug that stops the
ledger write without stopping the underlying operation, which is explicitly
allowed — see usage.py's fail-open ledger writes) has no row to reconstruct
from, and assuming "no rows" means "should be zero" would let this tool
destroy real, unrelated balance.

Safe to run against a live system, WITH ONE CAVEAT:
  * ``--dry-run`` computes and prints the diff without writing anything.
  * Without ``--dry-run``, it still only *writes* buckets whose rebuilt value
    differs from what's currently in Redis — an already-correct key is left
    alone (matters less for the ``cache.set`` itself, more so the report only
    highlights what actually needed fixing).
  * The caveat: this is a plain DB-read-then-``cache.set``, not a
    compare-and-swap. ``usage.py``'s own writers (``cache.incr``/``decr``,
    ``cache.add``) are single atomic round trips with no such window. If a
    booking, SMS send, or refund lands on the same key between this
    command's read and its write, that concurrent change is silently
    overwritten — a real lost-update, not merely a theoretical one — and
    stays wrong until the next mutation to that key. Prefer running this
    during low-traffic windows, and treat the printed diff as something to
    read before trusting a rebuild done while the system was busy.
"""

from datetime import timezone as dt_timezone

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.management.base import BaseCommand

from accounting import usage
from accounting.models import CreditLedger

User = get_user_model()

# ledger metric -> (bucket kind, usage.py metric/key-builder to reuse)
_MONTHLY = "monthly"
_WALLET = "wallet"

_BUCKETS = {
    CreditLedger.METRIC_SMS_MONTHLY: (_MONTHLY, usage.METRIC_SMS),
    CreditLedger.METRIC_APPOINTMENT_MONTHLY: (_MONTHLY, usage.METRIC_APPOINTMENTS),
    CreditLedger.METRIC_SMS_WALLET: (_WALLET, usage._wallet_key),
    CreditLedger.METRIC_APPOINTMENT_WALLET: (_WALLET, usage._appt_wallet_key),
}


class Command(BaseCommand):
    help = (
        "بازسازی موجودی کیف‌پول/سهمیه‌ی ماهانه‌ی کاربران در Redis از روی "
        "CreditLedger (منبع حقیقتِ ماندگار). با --dry-run فقط گزارش می‌دهد."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "--user-id", type=int, default=None,
            help="فقط این کاربر را بازسازی کن (پیش‌فرض: همه‌ی کاربرانی که در CreditLedger رکورد دارند)",
        )
        parser.add_argument(
            "--dry-run", action="store_true",
            help="فقط محاسبه و گزارش کن، چیزی در Redis نوشته نشود",
        )

    def handle(self, *args, **options):
        dry_run = options["dry_run"]
        user_id = options["user_id"]

        if user_id is not None:
            user_ids = [user_id]
        else:
            user_ids = list(
                CreditLedger.objects.order_by().values_list("user_id", flat=True).distinct()
            )

        if not user_ids:
            self.stdout.write("هیچ کاربری در CreditLedger یافت نشد.")
            return

        current_period = usage._period()
        changed = 0
        unchanged = 0
        skipped = 0

        for uid in user_ids:
            for ledger_metric, (kind, spec) in _BUCKETS.items():
                # Most recent row for this (user, metric) — the indexed query
                # this whole command exists to make cheap.
                latest = (
                    CreditLedger.objects.filter(user_id=uid, metric=ledger_metric)
                    .order_by("-created_at")
                    .first()
                )
                if latest is None:
                    skipped += 1
                    continue

                if kind == _MONTHLY:
                    redis_key = usage._month_key(uid, spec, current_period)
                    latest_period = latest.created_at.astimezone(dt_timezone.utc).strftime("%Y-%m")
                    rebuilt = latest.balance_after if latest_period == current_period else 0
                else:  # wallet — never resets, latest row is always authoritative
                    redis_key = spec(uid)
                    rebuilt = latest.balance_after

                old_value = int(cache.get(redis_key) or 0)

                if old_value == rebuilt:
                    unchanged += 1
                    continue

                changed += 1
                self.stdout.write(
                    f"user={uid} metric={ledger_metric}: redis={old_value} -> "
                    f"rebuilt={rebuilt}{' (dry-run, not written)' if dry_run else ''}"
                )
                if not dry_run:
                    # Same "no TTL for wallets, ~62 day TTL for monthly
                    # counters" convention as usage.py's own writers.
                    timeout = None if kind == _WALLET else usage._MONTHLY_TTL
                    cache.set(redis_key, rebuilt, timeout=timeout)

        self.stdout.write(self.style.SUCCESS(
            f"بازسازی {'(dry-run) ' if dry_run else ''}تمام شد — "
            f"{changed} مورد تغییر یافت، {unchanged} مورد بدون تغییر، "
            f"{skipped} مورد بدون سابقه در ledger (دست‌نخورده باقی ماند)."
        ))
