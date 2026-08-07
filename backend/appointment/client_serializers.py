from rest_framework import serializers
from .models import Appointment
from business.serializers import ClientBusinessSerializer

class ClientAppointmentSerializer(serializers.ModelSerializer):
    business = ClientBusinessSerializer(read_only=True)
    appointment_date = serializers.SerializerMethodField()
    queue_position = serializers.SerializerMethodField()
    estimated_turn_time = serializers.SerializerMethodField()

    class Meta:
        model = Appointment
        fields = [
            'id',
            'business',
            'appointment_date',
            'status',
            'queue_position',
            'estimated_turn_time'
        ]

    def get_appointment_date(self, obj):
        if obj.appointment_date:
            return int(obj.appointment_date.timestamp() * 1000)
        return None

    def get_queue_position(self, obj):
        """
        Calculates how many appointments are WAITING or IN_PROGRESS 
        for the same business on the same day, scheduled before this appointment.
        """
        if obj.status not in ['WAITING', 'IN_PROGRESS']:
            return 0
        
        # Count appointments on the same day with an earlier time
        start_of_day = obj.appointment_date.replace(hour=0, minute=0, second=0, microsecond=0)
        end_of_day = obj.appointment_date.replace(hour=23, minute=59, second=59, microsecond=999999)
        
        ahead = Appointment.objects.filter(
            business=obj.business,
            appointment_date__range=(start_of_day, end_of_day),
            appointment_date__lt=obj.appointment_date,
            status__in=['WAITING', 'IN_PROGRESS']
        ).count()
        return ahead

    def get_estimated_turn_time(self, obj):
        """
        Estimates the time the user's turn will arrive.
        Wait time = queue_position * 15 minutes.
        """
        queue_pos = self.get_queue_position(obj)
        if queue_pos == 0 and obj.status == 'WAITING':
            # Their turn is next or very soon
            return self.get_appointment_date(obj)
            
        from datetime import timedelta
        # Add 15 minutes for each person ahead
        estimated_time = obj.appointment_date + timedelta(minutes=15 * queue_pos)
        return int(estimated_time.timestamp() * 1000)

class ClientAppointmentCreateSerializer(serializers.ModelSerializer):
    business_id = serializers.IntegerField(write_only=True)
    appointment_date = serializers.IntegerField(write_only=True) # Unix timestamp in ms
    service_duration = serializers.IntegerField(write_only=True, required=False)
    description = serializers.CharField(write_only=True, required=False, allow_blank=True)
    # What the client says they're coming for, picked from the business's own
    # menu (business.services). Accepted as a list here and stored on the
    # appointment as the same comma-separated string the owner app writes, so
    # both booking paths produce one comparable value.
    # Whether a name that is NOT on the menu is allowed depends on the
    # business's allow_client_add_service switch — enforced in the view, which
    # is the only place that has the Business loaded.
    selected_services = serializers.ListField(
        child=serializers.CharField(allow_blank=True),
        write_only=True,
        required=False,
    )

    class Meta:
        model = Appointment
        fields = ['business_id', 'appointment_date', 'service_duration', 'description', 'selected_services']
