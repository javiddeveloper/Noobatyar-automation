# appointments/views.py
import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional, Dict, Any, Tuple

from adrf.views import APIView
from asgiref.sync import sync_to_async
from django.db import transaction
from django.db.models import Q, Prefetch
from django.core.exceptions import ValidationError
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response

from appointment.models import Appointment, Business, Visitor
from appointment.serializers import AppointmentSerializer
from api.responses import APIResponse
from api.views import _extract_error


logger = logging.getLogger(__name__)


class AppointmentView(APIView):
    permission_classes = [IsAuthenticated]

    # Valid status transitions (state machine)
    STATUS_TRANSITIONS = {
        'WAITING': ['CONFIRMED', 'CANCELLED'],
        'CONFIRMED': ['COMPLETED', 'CANCELLED', 'NO_SHOW'],
        'COMPLETED': [],  # Terminal state
        'CANCELLED': [],  # Terminal state
        'NO_SHOW': [],  # Terminal state
    }

    @staticmethod
    def _parse_timestamp(value, request_id: str) -> datetime:
        """Parse Unix epoch milliseconds to timezone-aware UTC datetime."""
        try:
            return datetime.fromtimestamp(int(value) / 1000, tz=timezone.utc)
        except (ValueError, TypeError, OSError) as e:
            raise ValueError(f"Invalid timestamp: {value}") from e

    async def get(self, request: Request, appointment_id: int) -> Response:
        """Get appointment details by ID"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()

        try:
            # Get appointment with ownership check
            appointment, error_response = await self._get_appointment_optimized(
                appointment_id, request.user, request_id
            )
            if error_response:
                return error_response

            # Serialize appointment data
            appointment_data = await self._serialize_appointment(appointment)

            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(
                f"[{request_id}] Appointment retrieved",
                extra={
                    'appointment_id': appointment_id,
                    'elapsed_ms': elapsed_ms
                }
            )

            return APIResponse.success(data=appointment_data, message='جزئیات نوبت با موفقیت دریافت شد')

        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Error retrieving appointment",
                extra={
                    'appointment_id': appointment_id,
                    'error': str(e),
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    async def post(self, request: Request) -> Response:
        """Create new appointment with conflict detection"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()
        user = request.user

        try:
            # Extract and validate required fields
            business_id = request.data.get('business_id')
            visitor_id = request.data.get('visitor_id')
            appointment_date = request.data.get('appointment_date')
            service_duration = request.data.get('service_duration', 30)
            description = request.data.get('description', '')

            if not all([business_id, visitor_id, appointment_date]):
                logger.warning(
                    f"[{request_id}] Missing required fields",
                    extra={
                        'user_id': request.user.id,
                        'provided_fields': list(request.data.keys())
                    }
                )
                return APIResponse.error('فیلدهای business_id، visitor_id و appointment_date الزامی هستند', code=status.HTTP_400_BAD_REQUEST)

            # Verify ownership and get related objects
            business, visitor, error_response = await self._verify_ownership(
                request.user, business_id, visitor_id, request_id
            )
            if error_response:
                return error_response

            # Parse appointment date from Unix epoch milliseconds
            try:
                appointment_datetime = self._parse_timestamp(appointment_date, request_id)
            except ValueError as e:
                logger.warning(
                    f"[{request_id}] Invalid timestamp: {appointment_date}",
                    extra={'error': str(e)}
                )
                return APIResponse.error('فرمت تاریخ نامعتبر است', code=status.HTTP_400_BAD_REQUEST)

            # Check for conflicts
            # has_conflict, conflict_details = await self._check_appointment_conflicts(
            #     business=business,
            #     appointment_date=appointment_datetime,
            #     service_duration=service_duration,
            #     exclude_appointment_id=None,
            #     request_id=request_id
            # )
            #
            # if has_conflict:
            #     logger.info(
            #         f"[{request_id}] Appointment conflict detected",
            #         extra={'conflict_details': conflict_details}
            #     )
            #     return Response(
            #         {
            #             'error': 'زمان انتخابی با نوبت دیگری تداخل دارد',
            #             'conflicts': conflict_details
            #         },
            #         status=status.HTTP_409_CONFLICT
            #     )

            # Create appointment within transaction
            appointment = await self._create_appointment_atomic(
                business=business,
                visitor=visitor,
                appointment_date=appointment_datetime,
                service_duration=service_duration,
                description=description,
                request_id=request_id,
                user = user
            )

            # Serialize response
            serializer = await sync_to_async(AppointmentSerializer)(appointment)
            response_data = await sync_to_async(lambda: serializer.data)()

            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(
                f"[{request_id}] Appointment created successfully",
                extra={
                    'appointment_id': appointment.id,
                    'business_id': business.id,
                    'visitor_id': visitor.id,
                    'elapsed_ms': elapsed_ms
                }
            )

            return APIResponse.success(data=response_data, message='نوبت با موفقیت ایجاد شد', status=status.HTTP_201_CREATED)

        except ValidationError as e:
            error_message = _extract_error(e.message_dict) if hasattr(e, 'message_dict') else str(e)
            return APIResponse.error(error_message, code=status.HTTP_400_BAD_REQUEST)
        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Unexpected error creating appointment",
                extra={
                    'error': str(e),
                    'error_type': type(e).__name__,
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)


    async def patch(self, request: Request, appointment_id: int) -> Response:
        """Update appointment with state machine validation"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()

        try:
            # Get appointment with optimized query
            appointment, error_response = await self._get_appointment_optimized(
                appointment_id, request.user, request_id
            )
            if error_response:
                return error_response

            # Determine update type based on payload
            update_fields = request.data.keys()
            is_status_only = 'status' in update_fields and len(update_fields) == 1
            is_details_update = any(
                field in update_fields
                for field in ['appointment_date', 'service_duration', 'description']
            )

            if is_status_only:
                return await self._update_status_with_validation(
                    appointment, request.data['status'], request_id, start_time
                )
            elif is_details_update:
                return await self._update_details_with_conflict_check(
                    appointment, request.data, request_id, start_time
                )
            else:
                logger.warning(
                    f"[{request_id}] Invalid update payload",
                    extra={'provided_fields': list(update_fields)}
                )
                return APIResponse.error('فیلدهای قابل به‌روزرسانی نامعتبر هستند', code=status.HTTP_400_BAD_REQUEST)

        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Unexpected error updating appointment",
                extra={
                    'appointment_id': appointment_id,
                    'error': str(e),
                    'error_type': type(e).__name__,
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    async def delete(self, request: Request, appointment_id: int) -> Response:
        """Delete appointment (soft delete by setting status to CANCELLED)"""
        request_id = str(uuid.uuid4())
        start_time = datetime.now()

        try:
            # Get appointment with ownership check
            appointment, error_response = await self._get_appointment_optimized(
                appointment_id, request.user, request_id
            )
            if error_response:
                return error_response

            # Soft delete by cancelling
            await self._delete_appointment(appointment, request_id)

            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.info(
                f"[{request_id}] Appointment deleted",
                extra={
                    'appointment_id': appointment_id,
                    'elapsed_ms': elapsed_ms
                }
            )

            return APIResponse.success(message='نوبت با موفقیت حذف شد')

        except Exception as e:
            elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000
            logger.error(
                f"[{request_id}] Error deleting appointment",
                extra={
                    'appointment_id': appointment_id,
                    'error': str(e),
                    'elapsed_ms': elapsed_ms
                },
                exc_info=True
            )
            return APIResponse.error('خطای سرور رخ داده است', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    # ==================== Helper Methods ====================

    @sync_to_async
    def _serialize_appointment(self, appointment: Appointment) -> dict:
        """Serialize appointment to dict"""
        return {
            'id': appointment.id,
            'business_id': appointment.business_id,
            'visitor_id': appointment.visitor_id,
            'appointment_date': appointment.appointment_date,
            'service_duration': appointment.service_duration,
            'status': appointment.status,
            'description': appointment.description,
            # 'created_at': appointment.created_at.isoformat(),
            # 'updated_at': appointment.updated_at.isoformat(),
        }

    @sync_to_async
    def _verify_ownership(
            self, user, business_id: int, visitor_id: int, request_id: str
    ) -> Tuple[Optional[Business], Optional[Visitor], Optional[Response]]:
        """Verify user owns both business and visitor with single query optimization"""
        try:
            business = Business.objects.select_related('user').get(
                id=business_id, user=user
            )
        except Business.DoesNotExist:
            logger.warning(
                f"[{request_id}] Business not found or unauthorized",
                extra={'business_id': business_id, 'user_id': user.id}
            )
            return None, None, APIResponse.error('کسب‌وکار یافت نشد', code=status.HTTP_404_NOT_FOUND)

        try:
            visitor = Visitor.objects.select_related('user').get(
                id=visitor_id, user=user
            )
        except Visitor.DoesNotExist:
            logger.warning(
                f"[{request_id}] Visitor not found or unauthorized",
                extra={'visitor_id': visitor_id, 'user_id': user.id}
            )
            return None, None, APIResponse.error('مراجعه‌کننده یافت نشد', code=status.HTTP_404_NOT_FOUND)

        return business, visitor, None

    @sync_to_async
    def _check_appointment_conflicts(
            self,
            business: Business,
            appointment_date: datetime,
            service_duration: int,
            exclude_appointment_id: Optional[int],
            request_id: str
    ) -> Tuple[bool, Optional[Dict[str, Any]]]:
        """
        Check for overlapping appointments using efficient query.
        Returns (has_conflict, conflict_details)
        """
        appointment_end = appointment_date + timedelta(minutes=service_duration)

        # Build conflict query
        conflict_query = Q(
            business=business,
            appointment_date__lt=appointment_end,
            appointment_date__gte=appointment_date - timedelta(minutes=120)  # Look back 2 hours
        ) & ~Q(status__in=['CANCELLED', 'NO_SHOW'])

        if exclude_appointment_id:
            conflict_query &= ~Q(id=exclude_appointment_id)

        # Check for overlapping appointments
        conflicting = Appointment.objects.filter(conflict_query).select_related(
            'visitor'
        ).only('id', 'appointment_date', 'service_duration', 'visitor__name')

        conflicts = []
        for appt in conflicting:
            appt_end = appt.appointment_date + timedelta(minutes=appt.service_duration)

            # Check actual overlap
            if not (appointment_end <= appt.appointment_date or appointment_date >= appt_end):
                conflicts.append({
                    'appointment_id': appt.id,
                    'visitor_name': appt.visitor.full_name,
                    'start_time': appt.appointment_date.isoformat(),
                    'end_time': appt_end.isoformat(),
                })

        if conflicts:
            logger.info(
                f"[{request_id}] Found {len(conflicts)} conflicting appointments",
                extra={'conflicts': conflicts}
            )
            return True, {'conflicting_appointments': conflicts}

        return False, None

    @sync_to_async
    @transaction.atomic
    def _create_appointment_atomic(
            self,
            business: Business,
            visitor: Visitor,
            appointment_date: datetime,
            service_duration: int,
            description: str,
            request_id: str
    , user=None) -> Appointment:
        """Create appointment within atomic transaction"""
        appointment = Appointment(
            business=business,
            visitor=visitor,
            appointment_date=appointment_date,
            service_duration=service_duration,
            description=description,
            status='WAITING',
            user = user
        )

        try:
            appointment.full_clean()
            appointment.save()
            logger.debug(
                f"[{request_id}] Appointment saved to database",
                extra={'appointment_id': appointment.id}
            )
            return appointment
        except ValidationError as e:
            logger.error(
                f"[{request_id}] Validation error creating appointment",
                extra={'errors': e.message_dict}
            )
            raise

    @sync_to_async
    def _get_appointment_optimized(
            self, appointment_id: int, user, request_id: str
    ) -> Tuple[Optional[Appointment], Optional[Response]]:
        """Get appointment with optimized query and ownership check"""
        try:
            appointment = Appointment.objects.select_related(
                'business__user', 'visitor__user'
            ).get(id=appointment_id, business__user=user)
            return appointment, None
        except Appointment.DoesNotExist:
            logger.warning(
                f"[{request_id}] Appointment not found or unauthorized",
                extra={'appointment_id': appointment_id, 'user_id': user.id}
            )
            return None, APIResponse.error('نوبت یافت نشد', code=status.HTTP_404_NOT_FOUND)

    @sync_to_async
    @transaction.atomic
    def _update_status_with_validation(
            self, appointment: Appointment, new_status: str, request_id: str, start_time: datetime
    ) -> Response:
        """Update status with state machine validation"""
        # Validate status value
        valid_statuses = dict(Appointment.STATUS_CHOICES).keys()
        if new_status not in valid_statuses:
            logger.warning(
                f"[{request_id}] Invalid status value",
                extra={'provided_status': new_status, 'valid_statuses': list(valid_statuses)}
            )
            return APIResponse.error(f'وضعیت نامعتبر است. مقادیر مجاز: {", ".join(valid_statuses)}', code=status.HTTP_400_BAD_REQUEST)

        # Validate state transition
        current_status = appointment.status
        allowed_transitions = self.STATUS_TRANSITIONS.get(current_status, [])

        if new_status not in allowed_transitions and new_status != current_status:
            logger.warning(
                f"[{request_id}] Invalid status transition",
                extra={
                    'appointment_id': appointment.id,
                    'current_status': current_status,
                    'requested_status': new_status,
                    'allowed_transitions': allowed_transitions
                }
            )
            return APIResponse.error(
                f'تغییر وضعیت از {current_status} به {new_status} مجاز نیست. وضعیت‌های مجاز: {", ".join(allowed_transitions)}',
                code=status.HTTP_400_BAD_REQUEST
            )

        # Update status
        old_status = appointment.status
        appointment.status = new_status
        appointment.save(update_fields=['status', 'updated_at'])

        serializer = AppointmentSerializer(appointment)
        elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000

        logger.info(
            f"[{request_id}] Appointment status updated",
            extra={
                'appointment_id': appointment.id,
                'old_status': old_status,
                'new_status': new_status,
                'elapsed_ms': elapsed_ms
            }
        )

        return APIResponse.success(data=serializer.data, message='وضعیت نوبت با موفقیت بروزرسانی شد')

    @sync_to_async
    @transaction.atomic
    def _update_details_with_conflict_check(
            self, appointment: Appointment, data: Dict[str, Any], request_id: str, start_time: datetime
    ) -> Response:
        """Update appointment details with conflict detection"""
        # Extract update fields
        new_status = data.get('status')
        new_date_str = data.get('appointment_date')
        new_duration = data.get('service_duration')
        new_description = data.get('description')

        # Parse new date from Unix epoch milliseconds if provided
        new_date = None
        if new_date_str:
            try:
                new_date = self._parse_timestamp(new_date_str, request_id)
            except ValueError as e:
                logger.warning(
                    f"[{request_id}] Invalid timestamp: {new_date_str}",
                    extra={'error': str(e)}
                )
                return APIResponse.error('فرمت تاریخ نامعتبر است', code=status.HTTP_400_BAD_REQUEST)

        # Determine final values for conflict check
        check_date = new_date if new_date else appointment.appointment_date
        check_duration = new_duration if new_duration else appointment.service_duration

        # Check conflicts if date or duration changed
        # if new_date or new_duration:
        #     has_conflict, conflict_details = self._check_appointment_conflicts_sync(
        #         business=appointment.business,
        #         appointment_date=check_date,
        #         service_duration=check_duration,
        #         exclude_appointment_id=appointment.id,
        #         request_id=request_id
        #     )
        #
        #     if has_conflict:
        #         logger.info(
        #             f"[{request_id}] Update blocked due to conflict",
        #             extra={'conflict_details': conflict_details}
        #         )
        #         return Response(
        #             {
        #                 'error': 'زمان جدید با نوبت دیگری تداخل دارد',
        #                 'conflicts': conflict_details
        #             },
        #             status=status.HTTP_409_CONFLICT
        #         )

        # Apply updates
        appointment.status = new_status
        updated_fields = ['updated_at', "status"]
        if new_date:
            appointment.appointment_date = new_date
            updated_fields.append('appointment_date')
        if new_duration:
            appointment.service_duration = new_duration
            updated_fields.append('service_duration')
        if new_description is not None:
            appointment.description = new_description
            updated_fields.append('description')

        # Validate and save
        try:
            appointment.full_clean()
            appointment.save(update_fields=updated_fields)
        except ValidationError as e:
            logger.error(
                f"[{request_id}] Validation error updating appointment",
                extra={'errors': e.message_dict}
            )
            error_message = _extract_error(e.message_dict) if hasattr(e, 'message_dict') else str(e)
            return APIResponse.error(error_message, code=status.HTTP_400_BAD_REQUEST)

        serializer = AppointmentSerializer(appointment)
        elapsed_ms = (datetime.now() - start_time).total_seconds() * 1000

        logger.info(
            f"[{request_id}] Appointment details updated",
            extra={
                'appointment_id': appointment.id,
                'updated_fields': updated_fields,
                'elapsed_ms': elapsed_ms
            }
        )

        return APIResponse.success(data=serializer.data, message='جزئیات نوبت با موفقیت بروزرسانی شد')

    def _check_appointment_conflicts_sync(
            self,
            business: Business,
            appointment_date: datetime,
            service_duration: int,
            exclude_appointment_id: Optional[int],
            request_id: str
    ) -> Tuple[bool, Optional[Dict[str, Any]]]:
        """Synchronous version of conflict check for use within @sync_to_async methods"""
        appointment_end = appointment_date + timedelta(minutes=service_duration)

        conflict_query = Q(
            business=business,
            appointment_date__lt=appointment_end,
            appointment_date__gte=appointment_date - timedelta(minutes=120)
        ) & ~Q(status__in=['CANCELLED', 'NO_SHOW'])

        if exclude_appointment_id:
            conflict_query &= ~Q(id=exclude_appointment_id)

        conflicting = Appointment.objects.filter(conflict_query).select_related(
            'visitor'
        ).only('id', 'appointment_date', 'service_duration', 'visitor__name')

        conflicts = []
        for appt in conflicting:
            appt_end = appt.appointment_date + timedelta(minutes=appt.service_duration)
            if not (appointment_end <= appt.appointment_date or appointment_date >= appt_end):
                conflicts.append({
                    'appointment_id': appt.id,
                    'visitor_name': appt.visitor.full_name,
                    'start_time': appt.appointment_date.isoformat(),
                    'end_time': appt_end.isoformat(),
                })

        if conflicts:
            return True, {'conflicting_appointments': conflicts}
        return False, None

    @sync_to_async
    @transaction.atomic
    def _delete_appointment(self, appointment: Appointment, request_id: str) -> None:
        """Hard delete appointment from database"""
        appointment_id = appointment.id
        appointment.delete()
        logger.debug(
            f"[{request_id}] Appointment deleted permanently",
            extra={'appointment_id': appointment_id}
        )
