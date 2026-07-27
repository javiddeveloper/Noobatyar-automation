"""
appointment/payment/zibal_deposit.py

Zibal gateway for the deposit a client pays when booking an appointment.

This is deliberately separate from ``accounting/payment/*``. Those charge the
business *owner* for a subscription, so they bill the platform's own
``settings.ZIBAL_MERCHANT_ID``. Here the *client* is charged and the money has to
land in the owner's own Zibal account, so the merchant is ``Business.merchant_id``
— the field that sat unused while "online payment" was only a static link.

Flow
----
1. request  → POST /v1/request  {merchant, amount, callbackUrl, orderId} → trackId
2. redirect → GET  /start/{trackId}                     (client pays at the bank)
3. callback → Zibal sends the browser back with ?trackId=…&success=…
4. verify   → POST /v1/verify   {merchant, trackId}     (the authoritative step)

Only step 4 is trusted. The callback's query string is attacker-controllable, so
success is decided by Zibal's verify response, never by ``success=1`` in the URL.
"""

import logging
from typing import Any, Dict

import httpx
from django.utils import timezone

logger = logging.getLogger(__name__)

BASE_URL = "https://gateway.zibal.ir"
REQUEST_URL = f"{BASE_URL}/v1/request"
VERIFY_URL = f"{BASE_URL}/v1/verify"
SUCCESS_CODE = 100
# Zibal returns 201 when a trackId was already verified. That is a success for
# our purposes: the money moved, we just processed the callback twice.
ALREADY_VERIFIED_CODE = 201
TIMEOUT = 10.0


def deposit_amount_rial(business) -> int:
    """Deposit in Rial. Amounts are stored and displayed in Toman."""
    return int(business.deposit_amount or 0) * 10


def create_deposit_payment(appointment, callback_url: str) -> Dict[str, Any]:
    """Open a Zibal payment for this appointment's deposit.

    Returns ``{'success': True, 'payment_url': ..., 'track_id': ...}`` or
    ``{'success': False, 'error': <persian message>}``.
    """
    business = appointment.business
    merchant = (business.merchant_id or "").strip()
    amount = deposit_amount_rial(business)

    if not merchant:
        return {"success": False, "error": "درگاه پرداخت این کسب‌وکار پیکربندی نشده است"}
    if amount <= 0:
        return {"success": False, "error": "مبلغ بیعانه برای این کسب‌وکار تعیین نشده است"}

    payload = {
        "merchant": merchant,
        "amount": amount,
        "callbackUrl": callback_url,
        "orderId": f"APT-{appointment.id}-{int(timezone.now().timestamp())}",
        "description": f"بیعانه نوبت {appointment.id} — {business.title}",
    }

    try:
        with httpx.Client() as client:
            response = client.post(
                REQUEST_URL,
                headers={"Content-Type": "application/json"},
                json=payload,
                timeout=TIMEOUT,
            )
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError as exc:
        logger.error("Zibal deposit request failed for appointment %s: %s", appointment.id, exc)
        return {"success": False, "error": "خطا در اتصال به درگاه پرداخت"}

    if data.get("result") != SUCCESS_CODE:
        # A wrong merchant id is the usual cause, and it is the owner's
        # misconfiguration rather than anything the client can fix.
        logger.error(
            "Zibal deposit rejected for appointment %s (business %s): %s",
            appointment.id, business.id, data,
        )
        return {"success": False, "error": "درگاه پرداخت این کسب‌وکار در دسترس نیست"}

    track_id = str(data["trackId"])
    logger.info("Zibal deposit opened for appointment %s, trackId=%s", appointment.id, track_id)
    return {
        "success": True,
        "payment_url": f"{BASE_URL}/start/{track_id}",
        "track_id": track_id,
    }


def verify_deposit_payment(appointment, track_id: str) -> Dict[str, Any]:
    """Ask Zibal whether ``track_id`` was really paid, for this appointment.

    The amount is checked against the business's current deposit, so a client who
    tampered with the request cannot settle a 500,000 booking with a 5,000 payment.
    """
    business = appointment.business
    merchant = (business.merchant_id or "").strip()
    if not merchant:
        return {"success": False, "error": "درگاه پرداخت این کسب‌وکار پیکربندی نشده است"}

    try:
        with httpx.Client() as client:
            response = client.post(
                VERIFY_URL,
                headers={"Content-Type": "application/json"},
                json={"merchant": merchant, "trackId": track_id},
                timeout=TIMEOUT,
            )
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError as exc:
        logger.error("Zibal deposit verify failed for appointment %s: %s", appointment.id, exc)
        return {"success": False, "error": "خطا در تایید پرداخت"}

    result = data.get("result")
    if result not in (SUCCESS_CODE, ALREADY_VERIFIED_CODE):
        logger.warning(
            "Zibal deposit not verified for appointment %s, trackId=%s: %s",
            appointment.id, track_id, data,
        )
        return {"success": False, "error": "پرداخت تایید نشد", "response": data}

    paid = int(data.get("amount") or 0)
    expected = deposit_amount_rial(business)
    if expected and paid < expected:
        logger.error(
            "Zibal deposit underpaid for appointment %s: paid=%s expected=%s",
            appointment.id, paid, expected,
        )
        return {"success": False, "error": "مبلغ پرداخت‌شده با مبلغ بیعانه مطابقت ندارد", "response": data}

    return {"success": True, "response": data}
