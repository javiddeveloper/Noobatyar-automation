from django.urls import path

from .views import BusinessView

urlpatterns = [
    # ... existing routes
    path('', BusinessView.as_view(), name='business-list-create'),
    path('<int:business_id>/', BusinessView.as_view(), name='business-detail'),
]
