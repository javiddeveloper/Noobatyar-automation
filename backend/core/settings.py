# core/settings.py
from pathlib import Path
from datetime import timedelta

BASE_DIR = Path(__file__).resolve().parent.parent

import os

# Local-only overrides. Production sets real env vars via docker-compose (which
# reads its own .env at the repo root) and never carries this file, so this is a
# no-op there. `override=False` (the load_dotenv default) means anything already
# set in the real environment always wins over the file — see docs/ENVIRONMENTS.md.
from dotenv import load_dotenv
load_dotenv(BASE_DIR / '.env.local')

SECRET_KEY = os.getenv('SECRET_KEY', 'insecure-dev-key-change-in-production')
DEBUG = os.getenv('DEBUG', 'False') == 'True'
ALLOWED_HOSTS = os.getenv('ALLOWED_HOSTS', '127.0.0.1,10.0.2.2,localhost').split(',')

# Fail fast in production if the secret key was left at its insecure default.
if not DEBUG and SECRET_KEY == 'insecure-dev-key-change-in-production':
    raise RuntimeError('SECRET_KEY must be set via environment in production (DEBUG=False)')

INSTALLED_APPS = [
    # Not 'django.contrib.admin': the custom AdminConfig swaps in
    # NobatyarAdminSite as the *default* site, so every existing
    # @admin.register(...) decorator keeps working untouched while the panel
    # gets Persian branding and a place to hang dashboard views.
    # See core/apps.py and core/admin_site.py.
    'core.apps.NobatyarAdminConfig',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'corsheaders',
    'rest_framework',
    'rest_framework_simplejwt.token_blacklist',
    # No models — installed so Django discovers core/management/commands/.
    'core',
    'api',
    'accounting',
    'versions',
    'business',
    'visitor',
    'appointment',
    'bale',
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    # NOTE: django.middleware.locale.LocaleMiddleware is deliberately NOT here.
    # The admin picks RTL from the *active* language (get_language_bidi() →
    # dir="rtl" + admin/css/rtl.css), and with no LocaleMiddleware the active
    # language is always LANGUAGE_CODE — i.e. fa-ir, always RTL, which is what
    # this Persian-only product wants. Adding LocaleMiddleware makes the panel
    # follow the browser's Accept-Language instead: a staff laptop set to en-US
    # then gets an English left-to-right admin, verified in testing. If it is
    # ever needed for a real API i18n story, pin LANGUAGES = [('fa', ...)] at
    # the same time, and put it after SessionMiddleware / before
    # CommonMiddleware.
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'core.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [BASE_DIR / 'templates'],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

if os.getenv('POSTGRES_DB'):
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': os.getenv('POSTGRES_DB'),
            'USER': os.getenv('POSTGRES_USER'),
            'PASSWORD': os.getenv('POSTGRES_PASSWORD'),
            'HOST': os.getenv('POSTGRES_HOST', 'db'),
            'PORT': os.getenv('POSTGRES_PORT', '5432'),
            # Connection pooling is handled by PgBouncer (transaction mode), so we
            # keep Django connections short-lived. Override via DB_CONN_MAX_AGE if
            # you connect straight to Postgres without a pooler.
            'CONN_MAX_AGE': int(os.getenv('DB_CONN_MAX_AGE', '0')),
            'CONN_HEALTH_CHECKS': True,
        }
    }
else:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': BASE_DIR / 'db.sqlite3',
        }
    }

# ── Cache (Redis) ─────────────────────────────────────────────────────────────
# Shared across all Gunicorn/Uvicorn workers. The OTP service (api/services/otp.py)
# and DRF throttling both depend on this being a real shared cache — the previous
# implicit LocMemCache default was per-process and silently broke OTP under
# multi-worker deployments.
REDIS_URL = os.getenv('REDIS_URL', 'redis://redis:6379/1')
if DEBUG and not os.getenv('REDIS_URL'):
    # Local single-process runserver with no Redis around: fall back to LocMemCache.
    # Without this the django-redis client silently swallows every set/get
    # (IGNORE_EXCEPTIONS), which breaks OTP and register tokens in a way that
    # looks like "کد منقضی شده" rather than a connection error.
    CACHES = {
        'default': {
            'BACKEND': 'django.core.cache.backends.locmem.LocMemCache',
            'LOCATION': 'nobatyar-dev',
        }
    }
else:
    CACHES = {
        'default': {
            'BACKEND': 'django_redis.cache.RedisCache',
            'LOCATION': REDIS_URL,
            'OPTIONS': {
                'CLIENT_CLASS': 'django_redis.client.DefaultClient',
                'IGNORE_EXCEPTIONS': True,  # cache outage must not take down the API
            },
            'KEY_PREFIX': 'nobatyar',
        }
    }
DJANGO_REDIS_IGNORE_EXCEPTIONS = True

# مدل کاربر سفارشی
AUTH_USER_MODEL = 'api.User'

# Argon2 برای hash کردن پسوردها (قوی‌ترین الگوریتم)
PASSWORD_HASHERS = [
    'django.contrib.auth.hashers.Argon2PasswordHasher',
    'django.contrib.auth.hashers.PBKDF2PasswordHasher',
]

# JWT authentication
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    'EXCEPTION_HANDLER': 'api.exceptions.custom_exception_handler',
    # Throttling is backed by the Redis cache above, so counters are shared
    # across workers. Scoped rates (otp, public_slots) are enforced per-view.
    'DEFAULT_THROTTLE_CLASSES': (
        'rest_framework.throttling.AnonRateThrottle',
        'rest_framework.throttling.UserRateThrottle',
    ),
    'DEFAULT_THROTTLE_RATES': {
        'anon': os.getenv('THROTTLE_ANON', '60/min'),
        'user': os.getenv('THROTTLE_USER', '300/min'),
        'otp': os.getenv('THROTTLE_OTP', '5/min'),
        'public_slots': os.getenv('THROTTLE_PUBLIC_SLOTS', '120/min'),
        # Abuse-report submission (business/client_views.py) — an anonymous,
        # no-account endpoint with no other rate limit on it, so a tighter
        # scope than the general 'anon' bucket is needed to keep it from
        # being a spam/DoS vector (see business/client_views.py's docstring).
        'content_report': os.getenv('THROTTLE_CONTENT_REPORT', '5/hour'),
    },
}

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(hours=int(os.getenv('JWT_ACCESS_HOURS', '24'))),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=int(os.getenv('JWT_REFRESH_DAYS', '14'))),
    'ROTATE_REFRESH_TOKENS': True,
    'BLACKLIST_AFTER_ROTATION': True,
}

LANGUAGE_CODE = 'fa-ir'
TIME_ZONE = 'Asia/Tehran'
USE_I18N = True
USE_TZ = True

# ── Static files ──────────────────────────────────────────────────────────────
# Two distinct directories, deliberately not the same one:
#
#   assets/  — *source* files we author (assets/admin_custom/{css,js,fonts}).
#              Listed in STATICFILES_DIRS so collectstatic picks them up and
#              runserver serves them in DEBUG.
#   static/  — the *output* of collectstatic (STATIC_ROOT), mounted into nginx
#              as the static_volume in docker-compose.
#
# They must stay separate: if a STATICFILES_DIRS entry equals STATIC_ROOT,
# Django raises staticfiles.E002 and collectstatic would otherwise copy files
# onto themselves.
STATIC_URL = 'static/'
STATIC_ROOT = BASE_DIR / 'static'
STATICFILES_DIRS = [BASE_DIR / 'assets']
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# Where the Django admin is mounted (see core/urls.py). Moving it off the
# guessable default cuts most credential-stuffing noise.
# NOTE: nginx/nginx.conf proxies `location ~ ^/(api|admin|plans|...)` to the web
# service, so changing ADMIN_URL also requires updating that regex in both
# server blocks — otherwise the new path never reaches Django.
ADMIN_URL = os.getenv('ADMIN_URL', 'admin/').strip().strip('/')
ADMIN_URL = f'{ADMIN_URL}/' if ADMIN_URL else 'admin/'

# CORS: allow all only in DEBUG; in production use an explicit allowlist from env.
_cors_origins = os.getenv('CORS_ALLOWED_ORIGINS', '').strip()
if DEBUG:
    CORS_ALLOW_ALL_ORIGINS = True
else:
    CORS_ALLOW_ALL_ORIGINS = False
    CORS_ALLOWED_ORIGINS = [o.strip() for o in _cors_origins.split(',') if o.strip()]

# ── Production security headers (only enforced when DEBUG is off) ──────────────
if not DEBUG:
    SECURE_PROXY_SSL_HEADER = ('HTTP_X_FORWARDED_PROTO', 'https')
    SESSION_COOKIE_SECURE = True
    CSRF_COOKIE_SECURE = True
    SECURE_CONTENT_TYPE_NOSNIFF = True

    # Django 4+ enforces Origin checking for unsafe methods (including the admin
    # login POST). A 403 "CSRF verification failed" on /admin/ almost always
    # means the request's origin was not trusted. Start from the CORS origins,
    # then auto-add scheme-qualified origins for every ALLOWED_HOST so the admin
    # host is always trusted without a separate env var to keep in sync.
    CSRF_TRUSTED_ORIGINS = [o.strip() for o in _cors_origins.split(',') if o.strip()]
    for _host in ALLOWED_HOSTS:
        _host = _host.strip()
        if not _host or _host == '*':
            continue
        for _scheme in ('https', 'http'):
            _origin = f'{_scheme}://{_host}'
            if _origin not in CSRF_TRUSTED_ORIGINS:
                CSRF_TRUSTED_ORIGINS.append(_origin)

# Zibal Payment Gateway
ZIBAL_MERCHANT_ID = os.getenv('ZIBAL_MERCHANT_ID')
if not ZIBAL_MERCHANT_ID:
    ZIBAL_MERCHANT_ID = '6a0d8775dc2e6664d8adf3fd'

# Where the deposit gateway sends the client's browser back to. Only the client
# web app has an appointments screen to land on, so this is app.noobatyar.ir
# rather than the API host.
CLIENT_WEB_URL = os.getenv('CLIENT_WEB_URL', 'https://app.noobatyar.ir')
SITE_URL = os.getenv('SITE_URL', 'http://localhost:8000')  # Your site's base URL
MEDIA_URL = '/media/'
MEDIA_ROOT = BASE_DIR / 'media'

# Melipayamak SMS Config
MELIPAYAMAK_OTP_TOKEN = os.getenv('MELIPAYAMAK_OTP_TOKEN', '')
MELIPAYAMAK_FROM = os.getenv('MELIPAYAMAK_FROM', '')

# Dev-only OTP bypass. When DEBUG is on and OTP_DEV_CODE is set, the OTP service
# skips Melipayamak (no SMS, no credit burned) and accepts this fixed code.
# Forced empty whenever DEBUG is off, so it can never be enabled in production.
OTP_DEV_CODE = os.getenv('OTP_DEV_CODE', '') if DEBUG else ''

# Dev-only SMS bypass. When on, api/sms.py logs outgoing messages instead of
# dispatching them, so local test runs never text a real phone number. Covers
# every sender (booking, approval/rejection, reminders, subscription lifecycle).
# Forced off whenever DEBUG is off.
SMS_DEV_MODE = (os.getenv('SMS_DEV_MODE', 'False') == 'True') if DEBUG else False

# ── Push notifications (Firebase Cloud Messaging, HTTP v1) ───────────────────
# The owner app learns about new bookings and upcoming appointments through FCM.
# Authentication is a *service account*, not a server key: Google retired legacy
# server keys, so `/fcm/send` no longer exists.
#
#   FCM_CREDENTIALS_FILE  absolute path to the service-account JSON from
#                         Firebase console → Project settings → Service accounts
#   FCM_PROJECT_ID        optional; read out of that JSON when left empty
#
# Left unset, api/services/push.py logs and returns False instead of raising —
# push is a convenience channel next to the SMS that carries the real message,
# so a missing key must never break a reminder run.
FCM_CREDENTIALS_FILE = os.getenv('FCM_CREDENTIALS_FILE', '')
FCM_PROJECT_ID = os.getenv('FCM_PROJECT_ID', '')
# Must match the channel the Android app creates, or Android 8+ silently drops
# every notification (see ProQueueApp / PushMessagingService in mobile_owner).
FCM_ANDROID_CHANNEL_ID = os.getenv('FCM_ANDROID_CHANNEL_ID', 'appointment_reminders')
