from rest_framework import serializers
from .models import Plan, Subscription, AddOnPack


class PlanSerializer(serializers.ModelSerializer):
    """نمایش پلن‌ها برای کاربر"""
    price_display = serializers.SerializerMethodField()
    duration_display = serializers.SerializerMethodField()

    class Meta:
        model = Plan
        fields = ['id', 'name', 'price', 'discount_price', 'price_display', 'duration_display', 'description', 'is_vip', 'features']

    def get_price_display(self, obj):
        """قیمت رو فارسی نمایش میده (با اولویت قیمت تخفیف)"""
        price = obj.discount_price if obj.discount_price is not None else obj.price
        if price == 0:
            return 'رایگان'
        return f"{price:,} تومان"

    def get_duration_display(self, obj):
        unit = 'روز' if obj.duration_unit == 'day' else 'ماه'
        return f"{obj.duration_value} {unit}"


class SubscriptionSerializer(serializers.ModelSerializer):
    """نمایش اشتراک فعال کاربر"""
    plan = PlanSerializer(read_only=True)
    is_valid = serializers.SerializerMethodField()
    days_left = serializers.SerializerMethodField()

    class Meta:
        model = Subscription
        fields = ['id', 'plan', 'status', 'started_at', 'ends_at', 'is_valid', 'days_left']

    def get_is_valid(self, obj):
        return obj.is_valid()

    def get_days_left(self, obj):
        return obj.days_left()


class AddOnPackSerializer(serializers.ModelSerializer):
    """نمایش بسته‌های افزودنی قابل خرید"""
    price_display = serializers.SerializerMethodField()

    class Meta:
        model = AddOnPack
        fields = ['id', 'name', 'price', 'price_display', 'kind', 'sms_amount', 'feature_key', 'duration_days']

    def get_price_display(self, obj):
        return f"{obj.price:,} تومان"


class BuyPlanSerializer(serializers.Serializer):
    """درخواست خرید پلن"""
    plan_id = serializers.IntegerField()


class BuyAddOnSerializer(serializers.Serializer):
    """درخواست خرید بسته‌ی افزودنی"""
    pack_id = serializers.IntegerField()
