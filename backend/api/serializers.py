# api/serializers.py
from rest_framework import serializers
from .models import User
import re


class RegisterSerializer(serializers.Serializer):
    """ثبت‌نام با phone + password"""
    phone = serializers.CharField(max_length=11)
    password = serializers.CharField(min_length=6, write_only=True)
    name = serializers.CharField(max_length=100)

    def validate_phone(self, value):
        if not re.match(r'^09[0-9]{9}$', value):
            raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
        if User.objects.filter(phone=value).exists():
            raise serializers.ValidationError("این شماره قبلاً ثبت شده")
        return value


class LoginSerializer(serializers.Serializer):
    """لاگین با phone + password"""
    phone = serializers.CharField()
    password = serializers.CharField(write_only=True)


class UserSerializer(serializers.ModelSerializer):
    """نمایش اطلاعات کاربر"""
    class Meta:
        model = User
        fields = ['id', 'phone', 'name', 'user_type', 'is_employee', 'joined_at']
        read_only_fields = ['id', 'joined_at']


class UpdateUserSerializer(serializers.ModelSerializer):
    """ویرایش نام و نوع کاربر"""
    class Meta:
        model = User
        fields = ['name', 'user_type']
        
        


class RegisterSerializer(serializers.Serializer):
    """ثبت‌نام با phone + password"""
    phone = serializers.CharField(max_length=11)
    password = serializers.CharField(min_length=8, write_only=True)
    name = serializers.CharField(max_length=100)

    def validate_phone(self, value):
        if not re.match(r'^09[0-9]{9}$', value):
            raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
        if User.objects.filter(phone=value).exists():
            raise serializers.ValidationError("این شماره قبلاً ثبت شده")
        return value

    def validate_password(self, value):
        if len(value) < 8:
            raise serializers.ValidationError("رمز عبور باید حداقل ۸ کاراکتر باشد")
        return value


class LoginSerializer(serializers.Serializer):
    """لاگین با phone + password"""
    phone = serializers.CharField(max_length=11)
    password = serializers.CharField(write_only=True)

    def validate_phone(self, value):
        if not re.match(r'^09[0-9]{9}$', value):
            raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
        return value


class UserSerializer(serializers.ModelSerializer):
    """نمایش اطلاعات کاربر"""
    class Meta:
        model = User
        fields = ['id', 'phone', 'name', 'user_type', 'is_employee', 'joined_at']
        read_only_fields = ['id', 'joined_at']


class UpdateUserSerializer(serializers.ModelSerializer):
    """ویرایش نام و نوع کاربر"""
    class Meta:
        model = User
        fields = ['name', 'user_type']

    def validate_name(self, value):
        if not value or len(value.strip()) < 2:
            raise serializers.ValidationError("نام باید حداقل ۲ کاراکتر باشد")
        return value.strip()


class ForgotPasswordSendOTPSerializer(serializers.Serializer):
    """ارسال OTP برای بازیابی رمز"""
    phone = serializers.CharField(max_length=11)

    def validate_phone(self, value):
        if not re.match(r'^09[0-9]{9}$', value):
            raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
        return value


class ForgotPasswordVerifyOTPSerializer(serializers.Serializer):
    """تأیید OTP"""
    phone = serializers.CharField(max_length=11)
    code = serializers.CharField(min_length=4, max_length=6)

    def validate_phone(self, value):
        if not re.match(r'^09[0-9]{9}$', value):
            raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
        return value


class ResetPasswordSerializer(serializers.Serializer):
    """تغییر رمز عبور با reset_token"""
    phone = serializers.CharField(max_length=11)
    reset_token = serializers.CharField()
    new_password = serializers.CharField(min_length=8, write_only=True)

    def validate_phone(self, value):
        if not re.match(r'^09[0-9]{9}$', value):
            raise serializers.ValidationError("فرمت شماره: 09XXXXXXXXX")
        return value

    def validate_new_password(self, value):
        if len(value) < 8:
            raise serializers.ValidationError("رمز عبور باید حداقل ۸ کاراکتر باشد")
        return value


class LogoutSerializer(serializers.Serializer):
    """خروج با refresh token"""
    refresh = serializers.CharField()

