from django.urls import path
from . import views

urlpatterns = [
    path('plans/', views.plan_list),               # لیست پلن‌ها
    path('plans/buy/', views.buy_plan),            # خرید پلن
    path('my-subscription/', views.my_subscription),  # اشتراک من
    path('subscriptions/', views.all_subscriptions),  # همه - ادمین,
    path('plans/payment/', views.pay_for_plan),
    path('payment-result', views.payment_callback, name='callback'),
]
