from adrf.views import APIView
from rest_framework.permissions import IsAuthenticated
from api.responses import APIResponse
from .models import Appointment
from .client_serializers import ClientAppointmentSerializer
import logging

logger = logging.getLogger(__name__)

class ClientAppointmentListView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request):
        # Clients only see their own appointments
        appointments = [
            a async for a in Appointment.objects.filter(
                user=request.user
            ).select_related('business', 'visitor').order_by('-appointment_date')
        ]

        serializer = ClientAppointmentSerializer(
            appointments, 
            many=True,
            context={'request': request}
        )

        return APIResponse.success(
            data=serializer.data,
            message="لیست نوبت‌های شما با موفقیت دریافت شد"
        )
