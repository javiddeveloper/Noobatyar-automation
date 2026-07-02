from django.urls import path
from .views import VisitorView, VisitorMessageHistoryView

app_name = 'visitor'

urlpatterns = [
    path('', VisitorView.as_view(), name='visitor-list-create'),
    path('<int:visitor_id>/', VisitorView.as_view(), name='visitor-detail'),
    path('<int:visitor_id>/messages/', VisitorMessageHistoryView.as_view(), name='visitor-messages'),
]