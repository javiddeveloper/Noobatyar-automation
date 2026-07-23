"""
accounting/payment/addon_payment.py

Zibal payment + verification for one-off add-on packs (SMS credit or a temporary
feature). Mirrors the plan payment flow but writes to AddOnPurchase and, on
success, grants the purchased benefit:

  * sms_pack → credits ``sms_amount`` into the buyer's SMS wallet.
  * feature  → activates ``feature_key`` for ``duration_days`` days.
"""

import logging
from datetime import timedelta
from typing import Any, Dict, Optional

import httpx
from django.conf import settings
from django.db import transaction as db_transaction
from django.utils import timezone

from accounting.models import AddOnPack, AddOnPurchase
from accounting import usage

logger = logging.getLogger(__name__)

BASE_URL = "https://gateway.zibal.ir"
VERIFY_URL = "https://gateway.zibal.ir/v1/verify"
SUCCESS_CODE = 100
TIMEOUT = 10.0


def grant_addon_benefit(purchase: AddOnPurchase, response_data: Optional[Dict] = None) -> Dict[str, Any]:
    """
    Mark ``purchase`` as successful and apply its benefit:
      * sms_pack → credits ``sms_amount`` into the buyer's SMS wallet.
      * feature  → activates ``feature_key`` for ``duration_days`` days.

    Idempotent guard is the caller's responsibility (check ``activated_at is
    None`` before calling) — this always (re-)applies the benefit when called.
    Shared by the Zibal verification flow and the Django admin manual-grant
    flow, so a staff member can grant a pack to a user without a real payment.
    """
    pack = purchase.pack
    with db_transaction.atomic():
        purchase.status = "success"
        purchase.zibal_response = response_data if response_data is not None else purchase.zibal_response
        purchase.activated_at = timezone.now()

        if pack.kind == AddOnPack.KIND_SMS:
            usage.add_wallet(purchase.user_id, pack.sms_amount)
        elif pack.kind == AddOnPack.KIND_APPOINTMENT:
            usage.add_appt_wallet(purchase.user_id, pack.appointment_amount)
        elif pack.kind == AddOnPack.KIND_FEATURE:
            purchase.expires_at = timezone.now() + timedelta(days=pack.duration_days)

        purchase.save(update_fields=["status", "zibal_response", "activated_at", "expires_at", "updated_at"])

    return {
        "success": True,
        "message": f"بسته‌ی «{pack.name}» با موفقیت فعال شد",
        "data": {
            "pack": pack.name,
            "kind": pack.kind,
            "sms_amount": pack.sms_amount,
            "appointment_amount": pack.appointment_amount,
            "expires_at": purchase.expires_at.isoformat() if purchase.expires_at else None,
        },
    }


class AddOnPaymentService:
    """Create a Zibal payment request for an add-on pack."""

    def create_payment(self, user, pack: AddOnPack, callback_url: str) -> Dict[str, Any]:
        order_id = f"ADDON-{user.id}-{pack.id}-{int(timezone.now().timestamp())}"
        payload = {
            "merchant": settings.ZIBAL_MERCHANT_ID,
            "amount": pack.price * 10,  # تومان → ریال
            "callbackUrl": callback_url,
            "orderId": order_id,
        }
        try:
            with httpx.Client() as client:
                response = client.post(
                    f"{BASE_URL}/v1/request",
                    headers={"Content-Type": "application/json"},
                    json=payload,
                    timeout=TIMEOUT,
                )
                response.raise_for_status()
                data = response.json()

            if data.get("result") != SUCCESS_CODE:
                logger.error(f"Zibal add-on error: {data.get('message')}")
                return {"success": False, "error": data.get("message", "خطا در ایجاد پرداخت")}

            track_id = str(data["trackId"])
            AddOnPurchase.objects.create(
                user=user,
                pack=pack,
                amount=pack.price,
                track_id=track_id,
                order_id=order_id,
                status="pending",
            )
            return {
                "success": True,
                "payment_url": f"{BASE_URL}/start/{track_id}",
                "track_id": track_id,
            }
        except httpx.HTTPError as e:
            logger.error(f"Zibal add-on request failed: {e}")
            return {"success": False, "error": "خطا در اتصال به درگاه پرداخت"}


class AddOnVerificationService:
    """Verify an add-on payment and grant the benefit (idempotent)."""

    def __init__(self, track_id: str):
        self.track_id = track_id
        self.purchase: Optional[AddOnPurchase] = None

    def verify_and_grant(self) -> Dict[str, Any]:
        try:
            self.purchase = (
                AddOnPurchase.objects.select_related("user", "pack").get(track_id=self.track_id)
            )
        except AddOnPurchase.DoesNotExist:
            return {"success": False, "message": "تراکنش یافت نشد", "data": {}}

        if self.purchase.status in ("success", "failed"):
            return {"success": False, "message": "این تراکنش قبلاً پردازش شده است", "data": {}}

        verify = self._verify_with_zibal()
        if not verify["success"]:
            self._mark_failed(verify.get("data", {}))
            return {"success": False, "message": verify.get("message", "پرداخت ناموفق بود"), "data": verify.get("data", {})}

        return self._grant(verify["data"])

    def _verify_with_zibal(self) -> Dict[str, Any]:
        with httpx.Client(timeout=TIMEOUT) as client:
            try:
                response = client.post(
                    VERIFY_URL,
                    headers={"Content-Type": "application/json"},
                    json={"merchant": settings.ZIBAL_MERCHANT_ID, "trackId": int(self.track_id)},
                )
                response.raise_for_status()
                data = response.json()
                if data.get("result") == SUCCESS_CODE:
                    return {"success": True, "data": data}
                return {"success": False, "message": data.get("message", "پرداخت تایید نشد"), "data": data}
            except httpx.HTTPError as e:
                logger.error(f"Zibal add-on verify error {self.track_id}: {e}")
                return {"success": False, "message": "خطا در ارتباط با درگاه پرداخت", "data": {"error": str(e)}}

    def _grant(self, verify_data: Dict) -> Dict[str, Any]:
        return grant_addon_benefit(self.purchase, response_data=verify_data)

    def _mark_failed(self, error_data: Dict):
        if self.purchase:
            self.purchase.status = "failed"
            self.purchase.zibal_response = error_data
            self.purchase.save(update_fields=["status", "zibal_response", "updated_at"])
