from rest_framework import serializers
from rest_framework.exceptions import ValidationError

from .models import Business

class BusinessSerializer(serializers.ModelSerializer):
    class Meta:
        model = Business
        fields = [
            'id', 'title', 'category', 'unique_code', 'phone', 'address', 'logo',
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            'notification_enabled', 'notification_types', 'notification_minutes_before',
            'created_at', 'updated_at', 'allow_anonymous_view'
        ]
        read_only_fields = ['id', 'unique_code', 'created_at', 'updated_at']

    def validate_work_start_hour(self, value):
        if not 0 <= value <= 23:
            raise serializers.ValidationError("Must be between 0-23")
        return value

    def validate_work_end_hour(self, value):
        if not 0 <= value <= 23:
            raise serializers.ValidationError("Must be between 0-23")
        return value

    def validate(self, data):
        if data.get('work_start_hour') and data.get('work_end_hour'):
            if data['work_start_hour'] >= data['work_end_hour']:
                raise serializers.ValidationError(
                    "work_end_hour must be greater than work_start_hour"
                )
        return data

    def validate_logo(self, value):
        if value.size > 5 * 1024 * 1024:  # 5MB
            raise ValidationError("Image size cannot exceed 5MB")
        return value

class ClientBusinessSerializer(serializers.ModelSerializer):
    class Meta:
        model = Business
        fields = [
            'id', 'title', 'category', 'unique_code', 'phone', 'address', 'logo',
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            'allow_anonymous_view'
        ]

    def to_representation(self, instance):
        data = super().to_representation(instance)
        request = self.context.get('request')
        
        # If user is anonymous and the business doesn't allow anonymous viewing, mask contact fields
        if (not request or not request.user or request.user.is_anonymous) and not instance.allow_anonymous_view:
            data['phone'] = None
            data['address'] = None
        
        return data
