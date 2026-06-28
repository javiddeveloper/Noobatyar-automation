# api/models.py
from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models


class UserManager(BaseUserManager):
    """مدیریت ساخت کاربران عادی و سوپریوزر"""
    
    def create_user(self, phone, password=None, **extra):
        if not phone:
            raise ValueError('شماره موبایل الزامی است')
        
        user = self.model(phone=phone, **extra)
        user.set_password(password)  # Argon2 hash
        user.save(using=self._db)
        return user

    def create_superuser(self, phone, password=None, **extra):
        extra.setdefault('is_staff', True)
        extra.setdefault('is_superuser', True)
        extra.setdefault('user_type', 'vip')
        return self.create_user(phone, password, **extra)


class User(AbstractBaseUser, PermissionsMixin):
    """
    مدل کاربر سفارشی با phone به جای username
    - phone: شناسه یکتا (09XXXXXXXXX)
    - password: hash شده با Argon2
    - user_type: دسترسی به محتوای VIP
    """
    USER_TYPE = [
        ('vip', 'VIP'),
        ('normal', 'عادی'),
    ]

    phone = models.CharField(max_length=11, unique=True, db_index=True)
    name = models.CharField(max_length=100)
    user_type = models.CharField(max_length=10, choices=USER_TYPE, default='normal')
    is_employee = models.BooleanField(default=False)
    is_staff = models.BooleanField(default=False)
    is_active = models.BooleanField(default=True)
    joined_at = models.DateTimeField(auto_now_add=True)

    USERNAME_FIELD = 'phone'
    REQUIRED_FIELDS = ['name']

    objects = UserManager()

    class Meta:
        verbose_name = 'کاربر'
        verbose_name_plural = 'کاربران'

    def __str__(self):
        return f"{self.name} ({self.phone})"
