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

    async def post(self, request):
        from datetime import datetime, timezone
        from business.models import Business
        from visitor.models import Visitor
        from .client_serializers import ClientAppointmentCreateSerializer

        serializer = ClientAppointmentCreateSerializer(data=request.data)
        if not serializer.is_valid():
            return APIResponse.error(message="اطلاعات نامعتبر است", code=400)

        business_id = serializer.validated_data['business_id']
        try:
            business = await Business.objects.aget(id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب و کار یافت نشد", code=404)

        # Ensure visitor exists for this user under this business
        visitor, created = await Visitor.objects.aget_or_create(
            business=business,
            phone_number=request.user.phone_number,
            defaults={
                'name': f"{request.user.first_name} {request.user.last_name}".strip() or request.user.phone_number,
                'created_by': request.user
            }
        )

        app_date = datetime.fromtimestamp(serializer.validated_data['appointment_date'] / 1000.0, tz=timezone.utc)

        appointment = await Appointment.objects.acreate(
            user=request.user,
            business=business,
            visitor=visitor,
            appointment_date=app_date,
            service_duration=serializer.validated_data.get('service_duration', business.default_service_duration),
            description=serializer.validated_data.get('description', ''),
            status='PENDING_APPROVAL'
        )

        return APIResponse.success(
            data={'id': appointment.id},
            message="نوبت شما با موفقیت ثبت شد و در انتظار تایید است"
        )
