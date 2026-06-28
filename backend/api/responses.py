# api/responses.py
from rest_framework.response import Response
from rest_framework import status as http_status


class APIResponse:

    @staticmethod
    def success(data=None, message=None, status=http_status.HTTP_200_OK):
        return Response({
            'status': 'success',
            'code': status,
            'message': message,
            'data': data
        }, status=status)

    @staticmethod
    def error(message, code=http_status.HTTP_400_BAD_REQUEST):
        return Response({
            'status': 'error',
            'code': code,
            'message': message,
            'data': None
        }, status=code)

    @staticmethod
    def unauthorized(message='احراز هویت نامعتبر'):
        return Response({
            'status': 'unauthorized',
            'code': http_status.HTTP_401_UNAUTHORIZED,
            'message': message,
            'data': None
        }, status=http_status.HTTP_401_UNAUTHORIZED)
