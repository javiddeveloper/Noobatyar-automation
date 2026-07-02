from django.urls import path

from .client_views import ClientBusinessListView, ClientBusinessDetailView

urlpatterns = [
    path('', ClientBusinessListView.as_view(), name='client-business-list'),
    path('<int:business_id>/', ClientBusinessDetailView.as_view(), name='client-business-detail'),
]
