from django.urls import path
from .client_views import ClientAppointmentListView

urlpatterns = [
    path('', ClientAppointmentListView.as_view(), name='client-appointment-list'),
]
