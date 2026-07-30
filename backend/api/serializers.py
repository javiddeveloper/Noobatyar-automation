# api/serializers.py
from rest_framework import serializers

from .models import User
from .phone import is_iran_mobile, normalize_phone


def _clean_mobile(value):
    """Normalise a mobile number, rejecting anything that isn't one.

    Shared by every phone field on this module's serializers, which each carried
    an identical copy of the same regex check. Normalising first means a number
    typed on a Persian keyboard is converted rather than refused — the digits
    ۰۹۱۲... are a valid number, just not ASCII.
    """
    phone = normalize_phone(value)
    if not is_iran_mobile(phone):
        raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
    return phone


class RegisterSerializer(serializers.Serializer):
    """ثبت‌نام با phone + یکی از دو روش احراز:

    - password: مسیر کلاسیک (اپ‌های موبایل)
    - register_token: توکن کوتاه‌مدتی که پس از تأیید OTP صادر می‌شود (وب)

    هر دو اختیاری‌اند اما دست‌کم یکی باید ارسال شود؛ وگرنه ثبت‌نام وب که هرگز
    رمز عبور نمی‌گیرد با خطای «این مقدار لازم است» رد می‌شد.
    """
    phone = serializers.CharField(max_length=11)
    password = serializers.CharField(write_only=True, required=False, allow_blank=True)
    register_token = serializers.CharField(write_only=True, required=False, allow_blank=True)
    name = serializers.CharField(max_length=100)

    def validate_phone(self, value):
        phone = _clean_mobile(value)
        if User.objects.filter(phone=phone).exists():
            raise serializers.ValidationError("این شماره قبلاً ثبت شده")
        return phone

    def validate(self, attrs):
        if not attrs.get('password') and not attrs.get('register_token'):
            raise serializers.ValidationError("رمز عبور یا توکن تأیید شماره الزامی است")
        return attrs

class SendOTPSerializer(serializers.Serializer):
    """ارسال OTP عمومی"""
    phone = serializers.CharField(max_length=11)

    def validate_phone(self, value):
        return _clean_mobile(value)

class VerifyOTPSerializer(serializers.Serializer):
    """تأیید OTP عمومی"""
    phone = serializers.CharField(max_length=11)
    code = serializers.CharField(min_length=4, max_length=6)

    def validate_phone(self, value):
        return _clean_mobile(value)


class LoginSerializer(serializers.Serializer):
    """لاگین با phone + password"""
    phone = serializers.CharField(max_length=11)
    password = serializers.CharField(write_only=True)

    def validate_phone(self, value):
        return _clean_mobile(value)


class UserSerializer(serializers.ModelSerializer):
    """نمایش اطلاعات کاربر"""
    class Meta:
        model = User
        fields = ['id', 'phone', 'name', 'role', 'is_employee', 'joined_at']
        read_only_fields = ['id', 'joined_at']


class UpdateUserSerializer(serializers.ModelSerializer):
    """ویرایش نام و نقش کاربر"""
    class Meta:
        model = User
        fields = ['name', 'role']

    def validate_name(self, value):
        if not value or len(value.strip()) < 2:
            raise serializers.ValidationError("نام باید حداقل ۲ کاراکتر باشد")
        return value.strip()


class ForgotPasswordSendOTPSerializer(serializers.Serializer):
    """ارسال OTP برای بازیابی رمز"""
    phone = serializers.CharField(max_length=11)

    def validate_phone(self, value):
        return _clean_mobile(value)


class ForgotPasswordVerifyOTPSerializer(serializers.Serializer):
    """تأیید OTP"""
    phone = serializers.CharField(max_length=11)
    code = serializers.CharField(min_length=4, max_length=6)

    def validate_phone(self, value):
        return _clean_mobile(value)


class ResetPasswordSerializer(serializers.Serializer):
    """تغییر رمز عبور با reset_token"""
    phone = serializers.CharField(max_length=11)
    reset_token = serializers.CharField()
    new_password = serializers.CharField(min_length=8, write_only=True)

    def validate_phone(self, value):
        return _clean_mobile(value)

    def validate_new_password(self, value):
        if len(value) < 8:
            raise serializers.ValidationError("رمز عبور باید حداقل ۸ کاراکتر باشد")
        return value


class LogoutSerializer(serializers.Serializer):
    """خروج با refresh token"""
    refresh = serializers.CharField()

