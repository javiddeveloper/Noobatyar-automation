from django.urls import path
from .views import CheckVersionView, ChangelogListView

urlpatterns = [
    path('check/', CheckVersionView.as_view(), name='version-check'),
    path('changelog/', ChangelogListView.as_view(), name='version-changelog'),
]
