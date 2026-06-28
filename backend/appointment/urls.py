# appointments/urls.py
from django.urls import path

from appointment.views.appointment_query_view import appointment_list
from appointment.views.appointment_stats_view import AppointmentStatsView
from appointment.views.views import AppointmentView

app_name = 'appointments'

urlpatterns = [
    # Create new appointment
    path('', AppointmentView.as_view(), name='appointment-create'),
    # Update existing appointment (status or details)
    path('<int:appointment_id>/', AppointmentView.as_view(), name='appointment-update'),
    path('stats/', AppointmentStatsView.as_view(), name='appointment-stats'),
    path('query', appointment_list, name='appointment-list'),
]