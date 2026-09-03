from django.urls import path

from .sms_views import PushLogListView, PushLogSummaryView, SmsLogListView, SmsLogSummaryView
from .views import BusinessCategoriesView, BusinessView, ServiceCatalogView

urlpatterns = [
    # ... existing routes
    path('', BusinessView.as_view(), name='business-list-create'),
    # Declared before the '<int:business_id>/' detail route: Django resolves in
    # order, and while the detail pattern ends at its own trailing slash and so
    # would not swallow these anyway, keeping the more specific paths first makes
    # that independent of how the detail route is written later.
    path('<int:business_id>/sms-logs/', SmsLogListView.as_view(), name='business-sms-logs'),
    path('<int:business_id>/sms-logs/summary/', SmsLogSummaryView.as_view(), name='business-sms-logs-summary'),
    path('<int:business_id>/push-logs/', PushLogListView.as_view(), name='business-push-logs'),
    path('<int:business_id>/push-logs/summary/', PushLogSummaryView.as_view(), name='business-push-logs-summary'),
    # Category-scoped, not business-scoped — deliberately not nested under
    # '<int:business_id>/' since a catalog item is shared across every
    # business in the category, not owned by one.
    path('service-catalog/', ServiceCatalogView.as_view(), name='service-catalog'),
    # Static vocabulary, no business context — same "not nested under
    # <business_id>" reasoning as service-catalog above.
    path('categories/', BusinessCategoriesView.as_view(), name='business-categories'),
    path('<int:business_id>/', BusinessView.as_view(), name='business-detail'),
]
