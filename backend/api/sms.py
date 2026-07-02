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


def send_sms(phone: str, message: str) -> bool:
    """Send a standard text message using Melipayamak"""
    # Replace with real API url and token based on Melipayamak docs
    url = f'https://console.melipayamak.com/api/send/simple/{OTP_TOKEN}'
    try:
        # In actual melipayamak, you might need 'from' or other params, 
        # For this prototype we will assume a generic format or log it
        payload = {
            'to': phone,
            'text': message
        }
        response = requests.post(url, json=payload, timeout=10)
        result = response.json()
        if result.get('status') == 'عملیات موفق' or result.get('status') == 'success':
            return True
        return False
    except Exception as e:
        print(f"Error sending SMS: {e}")
        return False
