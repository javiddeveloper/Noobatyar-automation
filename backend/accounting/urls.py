from django.urls import path
from . import views

urlpatterns = [
    path('plans/', views.plan_list),               # لیست پلن‌ها
    path('plans/buy/', views.buy_plan),            # خرید پلن
    path('my-subscription/', views.my_subscription),  # اشتراک من
    path('my-entitlements/', views.my_entitlements),  # قابلیت‌ها و مصرف

    path('plans/payment/', views.pay_for_plan),
    path('payment-result', views.payment_callback, name='callback'),

    # ── Add-on packs ──────────────────────────────────────────────
    path('addons/', views.addon_list),                 # لیست بسته‌های افزودنی
    path('addons/buy/', views.buy_addon),              # خرید بسته
    path('addons/payment-result', views.addon_payment_callback, name='addon-callback'),
]
