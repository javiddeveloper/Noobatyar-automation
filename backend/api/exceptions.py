# api/exceptions.py
from rest_framework.views import exception_handler
from rest_framework.exceptions import NotFound, ValidationError
from rest_framework_simplejwt.exceptions import InvalidToken, TokenError
from .responses import APIResponse
from django.http import JsonResponse


def custom_exception_handler(exc, context):
    response = exception_handler(exc, context)

    if response is None:
        return None

    # JWT
    if isinstance(exc, (InvalidToken, TokenError)):
        return APIResponse.unauthorized('توکن نامعتبر یا منقضی شده')

    # 404
    if isinstance(exc, NotFound):
        return APIResponse.error('منبع مورد نظر پیدا نشد', code=404)

    # Validation errors - پیام اول رو بگیر
    if isinstance(exc, ValidationError):
        errors = exc.detail
        # اولین پیام خطا رو پیدا کن
        message = _extract_first_message(errors)
        return APIResponse.error(message, code=400)

    # بقیه خطاها
    detail = response.data.get('detail', 'خطای نامشخص')
    if hasattr(detail, 'string'):
        detail = str(detail)

    return APIResponse.error(str(detail), code=response.status_code)


def _extract_first_message(errors):
    if isinstance(errors, list):
        return str(errors[0])
    if isinstance(errors, dict):
        first_key = next(iter(errors))
        val = errors[first_key]
        return _extract_first_message(val)
    return str(errors)

def custom_404_handler(request, exception=None):
    return JsonResponse({
        'status': 'error',
        'code': 404,
        'message': 'آدرس مورد نظر یافت نشد',
        'data': None
    }, status=404)
