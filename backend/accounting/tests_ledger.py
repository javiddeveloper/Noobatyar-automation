# accounting/tests_ledger.py
"""
Tests for CreditLedger and its wiring into accounting/usage.py.

Covers, per the phase-5 brief:
  * A ledger row is written for every mutating usage.py call, with the right
    metric/delta/balance_after/reason.
  * consume_sms()/refund_sms() write TWO rows when a single charge/refund
    straddles both the monthly allowance and the wallet.
  * The fail-open guarantee: if CreditLedger.objects.create() raises, the
    underlying booking/SMS operation still succeeds exactly as before.
  * rebuild_wallets_from_ledger's --dry-run vs. live behaviour, and that it
    does not touch buckets with no ledger history.
  * Query-count bound for the rebuild command against a user with a large
    ledger.
"""

from io import StringIO
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.management import call_command
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext

from accounting import entitlements, usage
from accounting.models import CreditLedger, Plan, Subscription

User = get_user_model()


def make_user(phone, **kwargs):
    return User.objects.create_user(phone=phone, name=kwargs.pop('name', 'کاربر'), **kwargs)


def make_plan(monthly_appointments=2, monthly_sms=2, **feature_overrides):
    features = {
        entitlements.QUOTA_MAX_BUSINESSES: 1,
        entitlements.QUOTA_MONTHLY_APPOINTMENTS: monthly_appointments,
        entitlements.QUOTA_MONTHLY_SMS: monthly_sms,
    }
    features.update(feature_overrides)
    return Plan.objects.create(
        name='پلن تست', price=10000, description=[], duration_value=1,
        duration_unit='month', features=features,
    )


def subscribe(user, plan):
    from django.utils import timezone
    return Subscription.objects.create(
        user=user, plan=plan, status='active', ends_at=timezone.now() + __import__('datetime').timedelta(days=30),
    )


class LedgerTestCase(TestCase):
    """Base: fresh cache per test so counters/wallets from other tests never leak."""

    def setUp(self):
        cache.clear()
        self.addCleanup(cache.clear)


class WalletLedgerTests(LedgerTestCase):
    def test_add_wallet_writes_ledger_row(self):
        user = make_user('09120000001')
        usage.add_wallet(user.id, 5)

        rows = list(CreditLedger.objects.filter(user_id=user.id, metric='sms_wallet'))
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0].delta, 5)
        self.assertEqual(rows[0].balance_after, 5)
        self.assertEqual(usage.get_wallet(user.id), rows[0].balance_after)

    def test_add_wallet_reason_and_ref_are_recorded(self):
        user = make_user('09120000002')
        usage.add_wallet(user.id, 10, reason='addon_purchase', ref_type='AddOnPurchase', ref_id=42)

        row = CreditLedger.objects.get(user_id=user.id, metric='sms_wallet')
        self.assertEqual(row.reason, 'addon_purchase')
        self.assertEqual(row.ref_type, 'AddOnPurchase')
        self.assertEqual(row.ref_id, 42)

    def test_add_wallet_without_reason_falls_back_to_credit_or_debit(self):
        user = make_user('09120000003')
        usage.add_wallet(user.id, 3)
        usage.add_wallet(user.id, -1)

        rows = list(CreditLedger.objects.filter(user_id=user.id, metric='sms_wallet').order_by('created_at'))
        self.assertEqual(rows[0].reason, usage.REASON_WALLET_CREDIT)
        self.assertEqual(rows[1].reason, usage.REASON_WALLET_DEBIT)

    def test_add_appt_wallet_writes_ledger_row(self):
        user = make_user('09120000004')
        usage.add_appt_wallet(user.id, 4)

        row = CreditLedger.objects.get(user_id=user.id, metric='appointment_wallet')
        self.assertEqual(row.delta, 4)
        self.assertEqual(row.balance_after, 4)
        self.assertEqual(usage.get_appt_wallet(user.id), 4)

    def test_addon_payment_grant_path_writes_ledger(self):
        """
        accounting/payment/addon_payment.py:grant_addon_benefit calls
        usage.add_wallet / usage.add_appt_wallet directly with no reason —
        this is the one external (non-usage.py) caller of these functions
        today, and it must still get a ledger row, even with a generic
        fallback reason (see add_wallet's docstring for why the reason can't
        be more specific without a change to that out-of-scope file).
        """
        from accounting.models import AddOnPack, AddOnPurchase
        from accounting.payment.addon_payment import grant_addon_benefit

        user = make_user('09120000005')
        pack = AddOnPack.objects.create(
            name='بسته پیامک', price=5000, kind=AddOnPack.KIND_SMS, sms_amount=20,
        )
        purchase = AddOnPurchase.objects.create(
            user=user, pack=pack, amount=5000, track_id='t1', order_id='o1', status='pending',
        )

        grant_addon_benefit(purchase)

        self.assertEqual(usage.get_wallet(user.id), 20)
        row = CreditLedger.objects.get(user_id=user.id, metric='sms_wallet')
        self.assertEqual(row.delta, 20)
        self.assertEqual(row.balance_after, 20)
        self.assertEqual(row.reason, usage.REASON_WALLET_CREDIT)


class AppointmentLedgerTests(LedgerTestCase):
    def test_record_appointment_monthly_writes_ledger(self):
        user = make_user('09120000010')
        subscribe(user, make_plan(monthly_appointments=2))

        source = usage.record_appointment(user.id)

        self.assertEqual(source, usage.SOURCE_MONTHLY)
        row = CreditLedger.objects.get(user_id=user.id, metric='appointment_monthly')
        self.assertEqual(row.delta, 1)
        self.assertEqual(row.balance_after, 1)
        self.assertEqual(row.reason, usage.REASON_BOOKING)
        self.assertEqual(usage.get_usage(user.id, usage.METRIC_APPOINTMENTS), row.balance_after)

    def test_record_appointment_falls_through_to_wallet_once_quota_exhausted(self):
        user = make_user('09120000011')
        subscribe(user, make_plan(monthly_appointments=1))
        usage.add_appt_wallet(user.id, 5)

        source1 = usage.record_appointment(user.id)  # from monthly quota (1)
        source2 = usage.record_appointment(user.id)  # quota exhausted, from wallet

        self.assertEqual(source1, usage.SOURCE_MONTHLY)
        self.assertEqual(source2, usage.SOURCE_WALLET)

        wallet_rows = list(
            CreditLedger.objects.filter(user_id=user.id, metric='appointment_wallet').order_by('created_at')
        )
        # First row is the +5 grant, second is the -1 booking spend.
        self.assertEqual(len(wallet_rows), 2)
        self.assertEqual(wallet_rows[1].delta, -1)
        self.assertEqual(wallet_rows[1].balance_after, 4)
        self.assertEqual(wallet_rows[1].reason, usage.REASON_BOOKING)
        self.assertEqual(usage.get_appt_wallet(user.id), 4)

    def test_release_appointment_refunds_monthly_and_ledgers_it(self):
        user = make_user('09120000012')
        subscribe(user, make_plan(monthly_appointments=2))
        from django.utils import timezone
        booked_at = timezone.now()

        source = usage.record_appointment(user.id)
        self.assertEqual(usage.get_usage(user.id, usage.METRIC_APPOINTMENTS), 1)

        refunded = usage.release_appointment(user.id, source, booked_at)

        self.assertTrue(refunded)
        self.assertEqual(usage.get_usage(user.id, usage.METRIC_APPOINTMENTS), 0)
        rows = list(
            CreditLedger.objects.filter(user_id=user.id, metric='appointment_monthly').order_by('created_at')
        )
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[1].delta, -1)
        self.assertEqual(rows[1].balance_after, 0)
        self.assertEqual(rows[1].reason, usage.REASON_CANCELLATION_REFUND)

    def test_release_appointment_from_wallet_ledgers_a_credit(self):
        user = make_user('09120000013')
        subscribe(user, make_plan(monthly_appointments=0))
        usage.add_appt_wallet(user.id, 1)

        source = usage.record_appointment(user.id)
        self.assertEqual(source, usage.SOURCE_WALLET)
        self.assertEqual(usage.get_appt_wallet(user.id), 0)

        usage.release_appointment(user.id, source)

        self.assertEqual(usage.get_appt_wallet(user.id), 1)
        rows = list(
            CreditLedger.objects.filter(user_id=user.id, metric='appointment_wallet').order_by('created_at')
        )
        self.assertEqual(len(rows), 3)  # +1 grant, -1 booking, +1 refund
        self.assertEqual(rows[-1].delta, 1)
        self.assertEqual(rows[-1].reason, usage.REASON_CANCELLATION_REFUND)


class SmsLedgerSplitTests(LedgerTestCase):
    """The two-bucket split — the part most likely to get an off-by-one or a
    wrong bucket label, per the phase brief. Tested explicitly."""

    def test_consume_sms_pure_monthly_writes_one_row(self):
        user = make_user('09120000020')
        subscribe(user, make_plan(monthly_sms=5))

        receipt = usage.consume_sms(user.id, amount=3)

        self.assertIsNotNone(receipt)
        self.assertEqual(receipt[usage.SOURCE_MONTHLY], 3)
        self.assertEqual(receipt[usage.SOURCE_WALLET], 0)
        rows = list(CreditLedger.objects.filter(user_id=user.id))
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0].metric, 'sms_monthly')
        self.assertEqual(rows[0].delta, 3)
        self.assertEqual(rows[0].balance_after, 3)
        self.assertEqual(rows[0].reason, usage.REASON_SMS_SEND)

    def test_consume_sms_split_across_monthly_and_wallet_writes_two_rows(self):
        """Monthly allowance = 2, charge 5: 2 from monthly, 3 from wallet —
        must produce exactly one 'sms_monthly' row (delta=2) and one
        'sms_wallet' row (delta=-3), never a single ambiguous combined row."""
        user = make_user('09120000021')
        subscribe(user, make_plan(monthly_sms=2))
        usage.add_wallet(user.id, 10)  # grant row #1 (sms_wallet, +10)

        receipt = usage.consume_sms(user.id, amount=5)

        self.assertIsNotNone(receipt)
        self.assertEqual(receipt[usage.SOURCE_MONTHLY], 2)
        self.assertEqual(receipt[usage.SOURCE_WALLET], 3)

        monthly_rows = list(CreditLedger.objects.filter(user_id=user.id, metric='sms_monthly'))
        wallet_rows = list(CreditLedger.objects.filter(user_id=user.id, metric='sms_wallet').order_by('created_at'))

        self.assertEqual(len(monthly_rows), 1)
        self.assertEqual(monthly_rows[0].delta, 2)
        self.assertEqual(monthly_rows[0].balance_after, 2)
        self.assertEqual(monthly_rows[0].reason, usage.REASON_SMS_SEND)

        self.assertEqual(len(wallet_rows), 2)  # grant, then spend
        self.assertEqual(wallet_rows[1].delta, -3)
        self.assertEqual(wallet_rows[1].balance_after, 7)
        self.assertEqual(wallet_rows[1].reason, usage.REASON_SMS_SEND)

        self.assertEqual(usage.get_usage(user.id, usage.METRIC_SMS), 2)
        self.assertEqual(usage.get_wallet(user.id), 7)

    def test_consume_sms_pure_wallet_when_monthly_already_exhausted(self):
        user = make_user('09120000022')
        subscribe(user, make_plan(monthly_sms=0))
        usage.add_wallet(user.id, 10)

        receipt = usage.consume_sms(user.id, amount=4)

        self.assertEqual(receipt[usage.SOURCE_MONTHLY], 0)
        self.assertEqual(receipt[usage.SOURCE_WALLET], 4)
        # No sms_monthly row should be written when nothing was drawn from it.
        self.assertEqual(CreditLedger.objects.filter(user_id=user.id, metric='sms_monthly').count(), 0)

    def test_refund_sms_split_writes_two_rows(self):
        user = make_user('09120000023')
        subscribe(user, make_plan(monthly_sms=2))
        usage.add_wallet(user.id, 10)

        receipt = usage.consume_sms(user.id, amount=5)  # 2 monthly + 3 wallet
        refunded = usage.refund_sms(receipt)

        self.assertTrue(refunded)
        self.assertEqual(usage.get_usage(user.id, usage.METRIC_SMS), 0)
        self.assertEqual(usage.get_wallet(user.id), 10)

        monthly_rows = list(CreditLedger.objects.filter(user_id=user.id, metric='sms_monthly').order_by('created_at'))
        wallet_rows = list(CreditLedger.objects.filter(user_id=user.id, metric='sms_wallet').order_by('created_at'))

        self.assertEqual(len(monthly_rows), 2)  # consume (+2), refund (-2)
        self.assertEqual(monthly_rows[1].delta, -2)
        self.assertEqual(monthly_rows[1].balance_after, 0)
        self.assertEqual(monthly_rows[1].reason, usage.REASON_SMS_REFUND)

        self.assertEqual(len(wallet_rows), 3)  # grant (+10), spend (-3), refund (+3)
        self.assertEqual(wallet_rows[2].delta, 3)
        self.assertEqual(wallet_rows[2].balance_after, 10)
        self.assertEqual(wallet_rows[2].reason, usage.REASON_SMS_REFUND)

    def test_consume_sms_unlimited_quota_still_ledgers(self):
        user = make_user('09120000024')
        subscribe(user, make_plan(monthly_sms=entitlements.UNLIMITED))

        receipt = usage.consume_sms(user.id, amount=7)

        self.assertEqual(receipt[usage.SOURCE_MONTHLY], 7)
        row = CreditLedger.objects.get(user_id=user.id, metric='sms_monthly')
        self.assertEqual(row.delta, 7)
        self.assertEqual(row.balance_after, 7)


class FailOpenLedgerTests(LedgerTestCase):
    """The ledger write must never be able to break the metering path it
    only records — mirrors usage.py's existing fail-open guarantee for
    Redis, applied to the new DB side."""

    def test_booking_succeeds_even_if_ledger_write_raises(self):
        user = make_user('09120000030')
        subscribe(user, make_plan(monthly_appointments=2))

        with patch('accounting.models.CreditLedger.objects.create', side_effect=RuntimeError('db down')):
            source = usage.record_appointment(user.id)

        # The actual metering operation must have gone through untouched.
        self.assertEqual(source, usage.SOURCE_MONTHLY)
        self.assertEqual(usage.get_usage(user.id, usage.METRIC_APPOINTMENTS), 1)
        # And, as promised, no ledger row exists for it.
        self.assertEqual(CreditLedger.objects.filter(user_id=user.id).count(), 0)

    def test_sms_send_succeeds_even_if_ledger_write_raises(self):
        user = make_user('09120000031')
        subscribe(user, make_plan(monthly_sms=5))

        with patch('accounting.models.CreditLedger.objects.create', side_effect=RuntimeError('db down')):
            receipt = usage.consume_sms(user.id, amount=2)

        self.assertIsNotNone(receipt)
        self.assertEqual(usage.get_usage(user.id, usage.METRIC_SMS), 2)

    def test_wallet_grant_succeeds_even_if_ledger_write_raises(self):
        user = make_user('09120000032')

        with patch('accounting.models.CreditLedger.objects.create', side_effect=RuntimeError('db down')):
            new_value = usage.add_wallet(user.id, 15)

        self.assertEqual(new_value, 15)
        self.assertEqual(usage.get_wallet(user.id), 15)


class RebuildWalletsCommandTests(LedgerTestCase):
    def _run(self, *args):
        out = StringIO()
        call_command('rebuild_wallets_from_ledger', *args, stdout=out)
        return out.getvalue()

    def test_dry_run_reports_without_writing(self):
        user = make_user('09120000040')
        usage.add_wallet(user.id, 25)

        # Corrupt Redis directly, simulating data loss.
        cache.set(usage._wallet_key(user.id), 0, timeout=None)
        self.assertEqual(usage.get_wallet(user.id), 0)

        output = self._run('--user-id', str(user.id), '--dry-run')

        self.assertIn('redis=0 -> rebuilt=25', output)
        self.assertIn('dry-run', output)
        # Still corrupted — dry-run must not write.
        self.assertEqual(usage.get_wallet(user.id), 0)

    def test_live_run_restores_correct_balance(self):
        user = make_user('09120000041')
        usage.add_wallet(user.id, 25)
        cache.set(usage._wallet_key(user.id), 0, timeout=None)

        self._run('--user-id', str(user.id))

        self.assertEqual(usage.get_wallet(user.id), 25)

    def test_bucket_with_no_ledger_history_is_left_untouched(self):
        """A wallet credited before this ledger existed (or via any path that
        isn't recorded) must not be zeroed out just because it has no rows."""
        user = make_user('09120000042')
        # Wallet has a real balance in Redis but zero CreditLedger rows.
        cache.set(usage._appt_wallet_key(user.id), 9, timeout=None)
        self.assertEqual(CreditLedger.objects.filter(user_id=user.id).count(), 0)

        output = self._run('--user-id', str(user.id))

        self.assertEqual(usage.get_appt_wallet(user.id), 9)
        self.assertNotIn('appointment_wallet', output)

    def test_monthly_bucket_from_a_past_month_rebuilds_to_zero(self):
        user = make_user('09120000043')
        CreditLedger.objects.create(
            user_id=user.id, metric='appointment_monthly', delta=3, balance_after=3,
            reason=usage.REASON_BOOKING,
        )
        # Force that row into a past month so the "auto-reset" rule applies.
        CreditLedger.objects.filter(user_id=user.id).update(created_at='2000-01-15T00:00:00Z')
        cache.set(usage._month_key(user.id, usage.METRIC_APPOINTMENTS), 3, timeout=usage._MONTHLY_TTL)

        self._run('--user-id', str(user.id))

        self.assertEqual(usage.get_usage(user.id, usage.METRIC_APPOINTMENTS), 0)

    def test_query_count_independent_of_ledger_size(self):
        """Correctness requirement: no query-per-event loop. A user with a
        few hundred ledger rows must cost the same handful of queries as a
        user with a few."""
        user = make_user('09120000044')
        rows = [
            CreditLedger(
                user_id=user.id, metric='sms_wallet', delta=1, balance_after=i + 1,
                reason=usage.REASON_WALLET_CREDIT,
            )
            for i in range(300)
        ]
        CreditLedger.objects.bulk_create(rows)
        cache.set(usage._wallet_key(user.id), 0, timeout=None)

        with CaptureQueriesContext(connection) as ctx:
            call_command('rebuild_wallets_from_ledger', '--user-id', str(user.id), stdout=StringIO())

        # 4 buckets x (1 lookup for latest row) = a small constant, nowhere
        # near the 300 ledger rows that exist for this user.
        self.assertLess(len(ctx.captured_queries), 15)
        self.assertEqual(usage.get_wallet(user.id), 300)


class LedgerReportsTests(LedgerTestCase):
    def test_monthly_usage_separates_granted_and_spent(self):
        from accounting import ledger_reports

        user = make_user('09120000050')
        usage.add_wallet(user.id, 10)     # +10
        usage.add_wallet(user.id, -4)     # -4

        report = ledger_reports.monthly_usage(user.id, months=1)
        from django.utils import timezone
        month_key = timezone.now().strftime('%Y-%m')

        self.assertIn(month_key, report)
        bucket = report[month_key]['sms_wallet']
        self.assertEqual(bucket['granted'], 10)
        self.assertEqual(bucket['spent'], 4)
        self.assertEqual(bucket['net'], 6)

    def test_recent_entries_returns_newest_first(self):
        from accounting import ledger_reports

        user = make_user('09120000051')
        usage.add_wallet(user.id, 1)
        usage.add_wallet(user.id, 2)
        usage.add_wallet(user.id, 3)

        entries = ledger_reports.recent_entries(user.id, limit=2)
        self.assertEqual(len(entries), 2)
        self.assertEqual(entries[0].delta, 3)
        self.assertEqual(entries[1].delta, 2)
