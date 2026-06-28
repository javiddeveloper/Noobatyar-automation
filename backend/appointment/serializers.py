# appointments/serializers.py
from rest_framework import serializers
from .models import Appointment, Business, Visitor
from rest_framework import serializers
from .models import Appointment, Visitor

class AppointmentSerializer(serializers.ModelSerializer):
    """
    Serializer for Appointment model with nested business and visitor details.
    Optimized for read operations with select_related queries.
    """
    business_name = serializers.CharField(source='business.name', read_only=True)
    visitor_name = serializers.CharField(source='visitor.name', read_only=True)
    visitor_phone = serializers.CharField(source='visitor.phone_number', read_only=True)

    # Computed field for appointment end time
    appointment_end_time = serializers.SerializerMethodField()

    class Meta:
        model = Appointment
        fields = [
            'id',
            'business',
            'business_name',
            'visitor',
            'visitor_name',
            'visitor_phone',
            'appointment_date',
            'appointment_end_time',
            'service_duration',
            'status',
            'description',
            'created_at',
            'updated_at',
        ]
        read_only_fields = ['id', 'created_at', 'updated_at']

    def get_appointment_end_time(self, obj):
        """Calculate and return appointment end time"""
        from datetime import timedelta
        if obj.appointment_date and obj.service_duration:
            end_time = obj.appointment_date + timedelta(minutes=obj.service_duration)
            return end_time.isoformat()
        return None


class AppointmentListSerializer(serializers.ModelSerializer):
    """
    Lightweight serializer for list views.
    Excludes heavy fields like description.
    """
    business_name = serializers.CharField(source='business.name', read_only=True)
    visitor_name = serializers.CharField(source='visitor.name', read_only=True)

    class Meta:
        model = Appointment
        fields = [
            'id',
            'business_name',
            'visitor_name',
            'appointment_date',
            'service_duration',
            'status',
            'created_at',
        ]
        read_only_fields = fields


class AppointmentCreateSerializer(serializers.ModelSerializer):
    """
    Serializer for appointment creation with validation.
    Used for write operations only.
    """

    class Meta:
        model = Appointment
        fields = [
            'business',
            'visitor',
            'appointment_date',
            'service_duration',
            'description',
        ]

    def validate_service_duration(self, value):
        """Ensure service duration is positive and reasonable"""
        if value <= 0:
            raise serializers.ValidationError("مدت زمان سرویس باید مثبت باشد")
        if value > 480:  # 8 hours
            raise serializers.ValidationError("مدت زمان سرویس نمی‌تواند بیش از ۸ ساعت باشد")
        return value

    def validate_appointment_date(self, value):
        """Ensure appointment is not in the past"""
        from django.utils import timezone
        if value < timezone.now():
            raise serializers.ValidationError("تاریخ نوبت نمی‌تواند در گذشته باشد")
        return value

class VisitorNestedSerializer(serializers.ModelSerializer):
    """Nested visitor serializer with limited fields"""

    class Meta:
        model = Visitor
        fields = ['id', 'full_name', 'phone_number']


class AppointmentQuerySerializer(serializers.ModelSerializer):
    """Appointment serializer with nested visitor and flattened fields"""
    visitor = VisitorNestedSerializer(read_only=True)
    appointment_date = serializers.SerializerMethodField()

    class Meta:
        model = Appointment
        fields = [
            'id',
            'visitor',
            'appointment_date',
            'service_duration',
            'status',
            'description',
            'created_at',
            'updated_at'
        ]

    def get_appointment_date(self, obj):
        """Convert appointment_date to Unix timestamp in milliseconds"""
        if obj.appointment_date:
            return int(obj.appointment_date.timestamp() * 1000)
        return None