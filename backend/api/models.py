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
        extra.setdefault('role', 'ADMIN')
        return self.create_user(phone, password, **extra)


class User(AbstractBaseUser, PermissionsMixin):
    """
    مدل کاربر سفارشی با phone به جای username
    - phone: شناسه یکتا (09XXXXXXXXX)
    - password: hash شده با Argon2
    - role: نقش کاربر در سیستم
    """
    ROLE_CHOICES = [
        ('BUSINESS_OWNER', 'صاحب کسب‌وکار'),
        ('CLIENT', 'مشتری'),
        ('ADMIN', 'مدیر'),
    ]

    # verbose_name matters here beyond cosmetics: USERNAME_FIELD is 'phone', so
    # this label is what the admin login form renders. Without it the one English
    # word "Phone:" sits in the middle of an otherwise fully Persian RTL panel.
    phone = models.CharField(max_length=11, unique=True, db_index=True, verbose_name='شماره موبایل')
    name = models.CharField(max_length=100, verbose_name='نام')
    role = models.CharField(max_length=20, choices=ROLE_CHOICES, default='CLIENT')
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


class DeviceToken(models.Model):
    """
    An FCM registration token for one installation of the owner app.

    One row per device, not per user: an owner with a phone and a tablet gets a
    push on both. ``token`` is unique across the whole table rather than unique
    *per user* on purpose — FCM hands the same token to whoever installs the app
    on that device, so when a second account signs in on a phone the row has to
    move to the new user instead of leaving the previous owner receiving
    somebody else's appointments.

    Dead tokens are deactivated rather than deleted (see
    ``api.services.push.send_to_user``) so that a device that comes back — the
    app reinstalled, the same token re-registered — simply flips ``is_active``
    back on rather than losing its history.
    """

    PLATFORM_CHOICES = [
        ('ANDROID', 'اندروید'),
        ('IOS', 'iOS'),
        ('WEB', 'وب'),
    ]

    user = models.ForeignKey(
        'api.User',
        on_delete=models.CASCADE,
        related_name='device_tokens',
        help_text="Owner of this device"
    )
    token = models.CharField(
        max_length=255,
        unique=True,
        db_index=True,
        help_text="FCM registration token"
    )
    platform = models.CharField(
        max_length=10,
        choices=PLATFORM_CHOICES,
        default='ANDROID',
    )
    device_name = models.CharField(max_length=100, blank=True, default='')
    app_version = models.CharField(max_length=30, blank=True, default='')
    is_active = models.BooleanField(
        default=True,
        db_index=True,
        help_text="False once FCM reported the token as unregistered"
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'توکن دستگاه'
        verbose_name_plural = 'توکن‌های دستگاه'
        ordering = ['-updated_at']

    def __str__(self):
        return f"{self.user.phone} · {self.platform} · {self.token[:12]}…"
