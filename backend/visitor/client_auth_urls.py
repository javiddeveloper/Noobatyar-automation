from django.urls import path
from . import client_auth_views
from .client_views import ClientMeView

urlpatterns = [
    path('otp/send/', client_auth_views.client_send_otp_view, name='client-otp-send'),
    path('otp/verify/', client_auth_views.client_verify_otp_view, name='client-otp-verify'),
    path('register/', client_auth_views.client_register_view, name='client-register'),
    path('me/', ClientMeView.as_view(), name='client-me'),
]
