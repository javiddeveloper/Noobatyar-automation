# api/urls.py
from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView
from . import device_views, views

urlpatterns = [
    # احراز هویت
    path('auth/register/', views.register_view, name='register'),
    path('auth/login/', views.login_view, name='login'),
    path('auth/logout/', views.logout_view, name='logout'),
    path('auth/otp/send/', views.send_otp_view, name='send_otp'),
    path('auth/otp/verify/', views.verify_otp_view, name='verify_otp'),
    path('auth/token/refresh/', TokenRefreshView.as_view(), name='token-refresh'),
    # api/urls.py
    path('auth/forgot-password/send/', views.forgot_password_send_otp),
    path('auth/forgot-password/verify/', views.forgot_password_verify_otp),
    path('auth/forgot-password/reset/', views.forgot_password_reset),

    
    # مدیریت کاربران
    path('users/<int:pk>/', views.user_detail, name='user-detail'),

    # اعلان‌های پوش (FCM) — ثبت و حذف توکن دستگاه اپ اونر
    path('devices/register/', device_views.register_device, name='device-register'),
    path('devices/unregister/', device_views.unregister_device, name='device-unregister'),
    
]
