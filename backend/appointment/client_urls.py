from django.urls import path
from .client_views import ClientAppointmentListView
from .views.public_slots_view import PublicAvailableSlotsView

urlpatterns = [
    path('', ClientAppointmentListView.as_view(), name='client-appointment-list'),
    path(
        'slots/<int:business_id>/',
        PublicAvailableSlotsView.as_view(),
        name='client-public-slots',
    ),
]
