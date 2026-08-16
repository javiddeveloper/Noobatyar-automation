from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import path, include
from api.exceptions import custom_404_handler


urlpatterns = [
    # Path comes from settings.ADMIN_URL (env: ADMIN_URL), already normalised to
    # a single trailing slash there. nginx must be taught the new prefix too —
    # see the note in core/settings.py.
    path(settings.ADMIN_URL, admin.site.urls),
    path('api/', include('api.urls')),
    path('api/accounting/', include('accounting.urls')),
    path('api/version/', include('versions.urls')),
    # path('api/appointments/', include('appointments.urls')),
    path('', include('accounting.urls')),
    path('api/business/', include('business.urls')),
    path('api/visitor/', include('visitor.urls')),
    path('api/appointment/', include('appointment.urls')),
    # Bale review bot webhook. Stays under /api/ on purpose — nginx only
    # proxies ^/(api|admin|plans|...) to Django, so a new top-level prefix
    # would 404 in production while working fine locally.
    path('api/bale/', include('bale.urls')),
    # Client APIs
    path('api/client/business/', include('business.client_urls')),
    path('api/client/appointments/', include('appointment.client_urls')),
    path('api/client/auth/', include('visitor.client_auth_urls')),
    path('api/client/activity/', include('visitor.client_activity_urls')),
]

handler404 = custom_404_handler
if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
