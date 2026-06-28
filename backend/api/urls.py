# api/urls.py
from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView
from . import views

urlpatterns = [
    # احراز هویت
    path('auth/register/', views.register_view, name='register'),
    path('auth/login/', views.login_view, name='login'),
    path('auth/logout/', views.logout_view, name='logout'),
    path('auth/token/refresh/', TokenRefreshView.as_view(), name='token-refresh'),
    # api/urls.py
    path('auth/forgot-password/send/', views.forgot_password_send_otp),
    path('auth/forgot-password/verify/', views.forgot_password_verify_otp),
    path('auth/forgot-password/reset/', views.forgot_password_reset),

    
    # مدیریت کاربران
    path('users/', views.user_list, name='user-list'),
    path('users/<int:pk>/', views.user_detail, name='user-detail'),
    
    # محتوای VIP
    path('vip/', views.vip_content, name='vip-content'),
]
