from django.urls import path

from . import views

app_name = 'bale'

urlpatterns = [
    # The secret is a path segment rather than a query param or header: Bale
    # only lets setWebhook register a plain URL, and a path keeps it out of the
    # places query strings end up (Referer headers, analytics).
    path('webhook/<str:secret>/', views.webhook, name='webhook'),
]
