from django.urls import path

from .client_views import ClientBusinessListView, ClientBusinessDetailView, ClientContentReportView

urlpatterns = [
    path('', ClientBusinessListView.as_view(), name='client-business-list'),
    path('<int:business_id>/', ClientBusinessDetailView.as_view(), name='client-business-detail'),
    path('<int:business_id>/report/', ClientContentReportView.as_view(), name='client-business-report'),
]
