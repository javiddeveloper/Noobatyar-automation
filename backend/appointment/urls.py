# appointments/urls.py
from django.urls import path

from appointment.views.appointment_query_view import appointment_list
from appointment.views.appointment_stats_view import AppointmentStatsView
from appointment.views.daily_counts_view import DailyCountsView
from appointment.views.views import AppointmentView

app_name = 'appointments'

urlpatterns = [
    # Create new appointment
    path('', AppointmentView.as_view(), name='appointment-create'),
    path('stats/', AppointmentStatsView.as_view(), name='appointment-stats'),
    path('daily-counts/', DailyCountsView.as_view(), name='appointment-daily-counts'),
    path('query', appointment_list, name='appointment-list'),
    # Update existing appointment (status or details) — keep last (greedy int match)
    path('<int:appointment_id>/', AppointmentView.as_view(), name='appointment-update'),
]