from rest_framework import serializers
from .models import Visitor
import re


class VisitorSerializer(serializers.ModelSerializer):
    class Meta:
        model = Visitor
        fields = ['id', 'full_name', 'phone_number', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']

    def validate_phone_number(self, value):
        """Validate Iranian phone number format"""
        if not re.match(r'^09\d{9}$', value):
            raise serializers.ValidationError(
                "شماره تلفن باید با 09 شروع شده و 11 رقم باشد"
            )
        return value

    def validate_full_name(self, value):
        """Ensure name is not empty or whitespace"""
        if not value or not value.strip():
            raise serializers.ValidationError("نام نمی‌تواند خالی باشد")
        return value.strip()