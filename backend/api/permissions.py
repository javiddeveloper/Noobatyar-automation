from rest_framework.permissions import BasePermission
from accounting.models import Subscription

class HasActiveSubscription(BasePermission):
    """
    این پرمیشن برای "سرویس‌های بیزینسی و خدماتی" نوبت‌یار است.
    سرویس‌های حیاتی (مثل لاگین، خرید اشتراک، مشاهده پلن و ...) نباید از این پرمیشن استفاده کنند.
    """
    message = 'برای استفاده از خدمات نوبت‌یار، نیاز به اشتراک فعال دارید. لطفاً نسبت به تمدید یا خرید اشتراک اقدام کنید.'

    def has_permission(self, request, view):
        # 1. اگر کاربر لاگین نکرده باشد، پرمیشن‌های IsAuthenticated قبلاً خطا داده‌اند
        # اما محض احتیاط اینجا هم چک می‌کنیم
        if not request.user or not request.user.is_authenticated:
            return False
        
        # 2. ادمین‌ها همیشه دسترسی دارند (برای مدیریت و تست)
        if request.user.is_staff:
            return True

        # 3. چک کردن وجود اشتراک فعال و معتبر در مدل Subscription
        # توجه: این شامل پلن‌های آزمایشی (Trial) هم می‌شود، 
        # چون آن‌ها هم یک رکورد Subscription با تاریخ انقضا دارند.
        active_sub = Subscription.objects.filter(
            user=request.user, 
            status='active'
        ).first()
        
        # اگر اشتراک یافت شد، اعتبار زمانی آن را چک می‌کنیم
        return active_sub is not None and active_sub.is_valid()


class IsVIPUser(BasePermission):
    """فقط کاربران VIP"""
    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.user_type == 'vip'



class IsEmployee(BasePermission):
    """فقط کارمندان دسترسی دارن"""
    message = 'این بخش فقط برای کارمندان است'

    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.is_employee
