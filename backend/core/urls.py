from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import path, include
from api.exceptions import custom_404_handler


urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/', include('api.urls')),
    path('api/accounting/', include('accounting.urls')),
    path('api/version/', include('versions.urls')),
    # path('api/appointments/', include('appointments.urls')),
    path('', include('accounting.urls')),
    path('api/business/', include('business.urls')),
    path('api/visitor/', include('visitor.urls')),
    path('api/appointment/', include('appointment.urls')),
]

handler404 = custom_404_handler
if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
