# accounting/payment/zibal_payment.py
import httpx
import logging
from django.conf import settings
from django.utils import timezone
from accounting.models import Transaction

logger = logging.getLogger(__name__)

class ZibalPaymentService:
    BASE_URL = "https://gateway.zibal.ir"
    TIMEOUT = 10.0

    def create_payment(self, user, plan, callback_url):  # ✅ sync
        """Create payment request and return payment URL"""
        order_id = self._generate_order_id(user.id, plan.id)
        
        # استفاده از قیمت تخفیف‌خورده در صورت وجود
        final_price = plan.discount_price if plan.discount_price is not None else plan.price

        payload = {
            "merchant": settings.ZIBAL_MERCHANT_ID,
            "amount": final_price * 10, # تبدیل تومان به ریال برای زیبال
            "callbackUrl": callback_url,
            "orderId": order_id
        }

        try:
            with httpx.Client() as client:  # ✅ Client به جای AsyncClient
                response = client.post(  # ✅ بدون await
                    f"{self.BASE_URL}/v1/request",
                    headers={"Content-Type": "application/json"},
                    json=payload,
                    timeout=self.TIMEOUT
                )
                response.raise_for_status()
                data = response.json()

            if data.get('result') != 100:
                logger.error(f"Zibal error: {data.get('message')}")
                return {'success': False, 'error': data.get('message')}

            track_id = data['trackId']
            self._save_transaction(str(track_id), order_id, user, plan, final_price)  # ✅ قیمت نهایی ذخیره شود

            return {
                'success': True,
                'payment_url': f"{self.BASE_URL}/start/{track_id}",
                'track_id': track_id
            }

        except httpx.HTTPError as e:
            logger.error(f"Zibal request failed: {str(e)}")
            return {'success': False, 'error': 'خطا در اتصال به درگاه پرداخت'}

    def _generate_order_id(self, user_id, plan_id):
        timestamp = int(timezone.now().timestamp())
        return f"ORD-{user_id}-{plan_id}-{timestamp}"

    def _save_transaction(self, track_id: str, order_id: str, user, plan, amount):  # ✅ مقدار مبلغ اضافه شد
        """Save transaction to database"""
        Transaction.objects.create(
            user=user,
            plan=plan,
            amount=amount,
            track_id=track_id,
            order_id=order_id,
            status='pending'
        )
