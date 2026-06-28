import random
import time
import requests
import threading
from cachetools import TTLCache
from django.conf import settings

# thread-safe in-memory cache
_lock = threading.Lock()
_otp_store = TTLCache(maxsize=1000, ttl=120)      # کد OTP — 2 دقیقه
_rate_store = TTLCache(maxsize=1000, ttl=120)      # rate limit
_attempt_store = TTLCache(maxsize=1000, ttl=120)   # تعداد تلاش


def generate_otp() -> str:
    return str(random.randint(100000, 999999))


def send_otp(phone: str) -> dict:
    with _lock:
        if phone in _rate_store:
            return {"success": False, "error": "لطفاً 2 دقیقه صبر کنید"}

        code = generate_otp()
        _otp_store[phone] = code
        _rate_store[phone] = True
        _attempt_store[phone] = 0

    return _send_sms(phone, code)


def verify_otp(phone: str, code: str) -> dict:
    with _lock:
        stored = _otp_store.get(phone)
        if not stored:
            return {"success": False, "error": "کد منقضی شده یا ارسال نشده"}

        attempts = _attempt_store.get(phone, 0)
        if attempts >= settings.OTP_MAX_ATTEMPTS:
            _otp_store.pop(phone, None)
            return {"success": False, "error": "تعداد تلاش بیش از حد"}

        if stored != code:
            _attempt_store[phone] = attempts + 1
            remaining = settings.OTP_MAX_ATTEMPTS - attempts - 1
            return {"success": False, "error": f"کد اشتباه — {remaining} تلاش باقی"}

        # موفق
        _otp_store.pop(phone, None)
        _rate_store.pop(phone, None)
        _attempt_store.pop(phone, None)
        return {"success": True}


# services/otp.py - _send_sms با ساختار صحیح
def _send_sms(phone: str, code: str) -> dict:
    try:
        if phone.startswith('98'):
            phone = '0' + phone[2:]
        
        otp_token = getattr(settings, 'MELIPAYAMAK_OTP_TOKEN', None)
        if not otp_token:
            return {"success": False, "error": "MELIPAYAMAK_OTP_TOKEN not configured"}
        
        url = f"https://console.melipayamak.com/api/send/simple/{otp_token}"
        data = {
            'from': settings.MELIPAYAMAK_FROM,
            'to': phone,
            'text': f'{code} کد ثبت نام در نوبت یار'
        }
        
        response = requests.post(url, json=data, timeout=10)
        print("DEBUG raw:", response.status_code, repr(response.text))
        
        if not response.text.strip():
            return {"success": False, "error": "empty response"}
        
        result = response.json()
        print("DEBUG melipayamak:", result)
        
        if result.get('code') == 0 or response.status_code == 200:
            return {"success": True}
        
        return {"success": False, "error": str(result)}
        
    except requests.Timeout:
        return {"success": False, "error": "timeout"}
    except Exception as e:
        return {"success": False, "error": str(e)}

