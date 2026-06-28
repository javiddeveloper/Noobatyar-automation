from django.urls import path
from .views import VisitorView

app_name = 'visitor'

urlpatterns = [
    path('', VisitorView.as_view(), name='visitor-list-create'),
    path('<int:visitor_id>/', VisitorView.as_view(), name='visitor-detail'),
]