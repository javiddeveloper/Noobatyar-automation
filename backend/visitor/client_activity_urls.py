from django.urls import path

from .client_views import ClientActivityView

urlpatterns = [
    path('', ClientActivityView.as_view(), name='client-activity'),
]
