# accounting/payment/zibal_payment_verification.py
import httpx
import logging
from typing import Dict, Any, Optional
from django.conf import settings
from django.db import transaction as db_transaction
from django.utils import timezone

from accounting.models import Transaction, Subscription

logger = logging.getLogger(__name__)

class PaymentVerificationService:
    """Handles Zibal payment verification and subscription activation"""

    ZIBAL_VERIFY_URL = "https://gateway.zibal.ir/v1/verify"
    SUCCESS_CODE = 100

    def __init__(self, track_id: str):
        self.track_id = track_id
        self.transaction: Optional[Transaction] = None

    def verify_and_activate(self) -> Dict[str, Any]:  # ✅ sync
        """Main orchestration method"""
        try:
            # 1. Load transaction
            if not self._load_transaction():
                return self._error_response("تراکنش یافت نشد", 404)

            # 2. Check idempotency
            if self._is_already_processed():
                return self._error_response("این تراکنش قبلاً پردازش شده است", 400)

            # 3. Verify with Zibal
            verify_result = self._verify_with_zibal()  # ✅ بدون await
            if not verify_result['success']:
                self._mark_failed(verify_result['data'])
                return self._error_response(
                    verify_result.get('message', 'پرداخت ناموفق بود'),
                    400,
                    verify_result['data']
                )

            # 4. Activate subscription
            subscription_data = self._activate_subscription(verify_result['data'])

            return {
                'success': True,
                'message': f'پلن {subscription_data["plan_name"]} با موفقیت فعال شد',
                'data': subscription_data,
                'status_code': 201
            }

        except Exception as e:
            logger.exception(f"Unexpected error in payment verification: {self.track_id}")
            self._mark_failed({'error': str(e)})
            return self._error_response("خطای سیستمی در پردازش پرداخت", 500)

    def _load_transaction(self) -> bool:  # ✅ sync
        """Load transaction from database"""
        try:
            self.transaction = Transaction.objects.select_related('user', 'plan').get(
                track_id=self.track_id
            )
            return True
        except Transaction.DoesNotExist:
            return False

    def _is_already_processed(self) -> bool:
        """Check if transaction was already processed"""
        return self.transaction.status in ['success', 'failed']

    def _verify_with_zibal(self) -> Dict[str, Any]:  # ✅ sync
        """Call Zibal verify API"""
        with httpx.Client(timeout=10.0) as client:  # ✅ Client به جای AsyncClient
            try:
                response = client.post(  # ✅ بدون await
                    self.ZIBAL_VERIFY_URL,
                    headers={"Content-Type": "application/json"},
                    json={
                        "merchant": settings.ZIBAL_MERCHANT_ID,
                        "trackId": int(self.track_id)
                    }
                )
                response.raise_for_status()
                data = response.json()

                if data.get('result') == self.SUCCESS_CODE:
                    return {'success': True, 'data': data}
                else:
                    return {
                        'success': False,
                        'message': data.get('message', 'پرداخت تایید نشد'),
                        'data': data
                    }

            except httpx.HTTPError as e:
                logger.error(f"Zibal API error for track_id {self.track_id}: {e}")
                return {
                    'success': False,
                    'message': 'خطا در ارتباط با درگاه پرداخت',
                    'data': {'error': str(e)}
                }

    def _activate_subscription(self, verify_data: Dict) -> Dict[str, Any]:  # ✅ sync
        """Activate subscription in atomic transaction"""
        with db_transaction.atomic():
            # Mark transaction successful
            self.transaction.status = 'success'
            self.transaction.zibal_response = verify_data
            self.transaction.save(update_fields=['status', 'zibal_response', 'updated_at'])

            # بررسی اشتراک فعلی برای تمدید زمان
            active_sub = Subscription.objects.filter(
                user=self.transaction.user,
                status='active'
            ).first()

            if active_sub and active_sub.is_valid():
                start_date = active_sub.ends_at
                active_sub.status = 'expired'
                active_sub.save(update_fields=['status'])
            else:
                Subscription.objects.filter(
                    user=self.transaction.user,
                    status='active'
                ).update(status='expired')
                start_date = timezone.now()

            # Create new subscription
            subscription = Subscription.objects.create(
                user=self.transaction.user,
                plan=self.transaction.plan,
                ends_at=self.transaction.plan.get_end_date(start_date=start_date)
            )

            # Upgrade user if VIP plan
            if self.transaction.plan.is_vip and self.transaction.user.user_type != 'vip':
                self.transaction.user.user_type = 'vip'
                self.transaction.user.save(update_fields=['user_type'])

            return {
                'subscription_id': subscription.id,
                'plan_name': self.transaction.plan.name,
                'ends_at': subscription.ends_at.isoformat(),
                'ref_number': verify_data.get('refNumber'),
                'card_number': verify_data.get('cardNumber'),
                'amount': str(self.transaction.amount)
            }

    def _mark_failed(self, error_data: Dict):  # ✅ sync
        """Mark transaction as failed"""
        if self.transaction:
            self.transaction.status = 'failed'
            self.transaction.zibal_response = error_data
            self.transaction.save(update_fields=['status', 'zibal_response', 'updated_at'])

    @staticmethod
    def _error_response(message: str, status_code: int, data: Optional[Dict] = None) -> Dict:
        """Standardized error response"""
        return {
            'success': False,
            'message': message,
            'data': data or {},
            'status_code': status_code
        }
