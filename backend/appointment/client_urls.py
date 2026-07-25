from django.urls import path
from .client_views import (
    ClientAppointmentCancelView,
    ClientAppointmentListView,
    ClientAppointmentPaymentView,
)
from .available_slots_view import AvailableSlotsView

try:
    from .views.public_slots_view import PublicAvailableSlotsView
    _has_public_slots = True
except ImportError:
    _has_public_slots = False

urlpatterns = [
    path('', ClientAppointmentListView.as_view(), name='client-appointment-list'),
    path('<int:pk>/pay/', ClientAppointmentPaymentView.as_view(), name='client-appointment-pay'),
    path('<int:pk>/cancel/', ClientAppointmentCancelView.as_view(), name='client-appointment-cancel'),
    path('<int:business_id>/available-slots/', AvailableSlotsView.as_view(), name='client-available-slots'),
]

if _has_public_slots:
    urlpatterns += [
        path('slots/<int:business_id>/', PublicAvailableSlotsView.as_view(), name='client-public-slots'),
    ]
