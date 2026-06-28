# api/sms.py
import requests
from typing import Optional

OTP_TOKEN = 'ba64aae8cd1f46619c8439b5dba70da9'

def send_otp(phone: str) -> Optional[str]:  # به جای str | None
    url = f'https://console.melipayamak.com/api/send/otp/{OTP_TOKEN}'
    try:
        response = requests.post(url, json={'to': phone}, timeout=10)
        result = response.json()
        if result.get('status') == 'عملیات موفق':
            return result.get('code')
        return None
    except Exception:
        return None
