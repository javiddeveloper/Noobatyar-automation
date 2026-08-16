"""
bale/models.py

Singleton configuration for the Bale review bot.

Why a model and not env vars, when every other integration in this project
(Melipayamak, Zibal) reads ``os.getenv``: the bot token and chat id are things
the operator wires up once from the admin panel, on a running server, without a
redeploy. An env var would mean editing docker-compose and rebuilding the
container to point the bot at a different chat — see DEPLOY_UPDATE_BACKEND.md
for what that costs.
"""

from django.conf import settings
from django.db import models
from django.utils.crypto import get_random_string


class BaleSettings(models.Model):
    """Row ``pk=1`` and only ``pk=1`` — see :meth:`load`.

    Kept as a table rather than a cached blob because it holds a credential:
    ``IGNORE_EXCEPTIONS=True`` on the Redis cache (core/settings.py) means a
    cache read fails open and returns None, which for a token store would
    silently disable the bot instead of erroring.
    """

    SINGLETON_PK = 1

    bot_token = models.CharField(
        max_length=255,
        blank=True,
        default='',
        help_text='توکنی که BotFather بله می‌دهد',
    )
    chat_id = models.CharField(
        max_length=64,
        blank=True,
        default='',
        help_text='شناسه‌ی چتی که اعلان‌ها به آن می‌رود؛ تنها فرستنده‌ای که '
                  'اجازه‌ی تصمیم‌گیری دارد هم همین است',
    )
    webhook_secret = models.CharField(
        max_length=64,
        blank=True,
        default='',
        help_text='بخش مخفی مسیر وبهوک — خودکار ساخته می‌شود، دستی عوضش نکن',
    )
    is_enabled = models.BooleanField(
        default=False,
        help_text='تا وقتی خاموش است هیچ پیامی ارسال و هیچ تصمیمی پذیرفته نمی‌شود',
    )
    # The moderation log records who decided. Without this the bot's decisions
    # land with actor=None and become indistinguishable from an owner-initiated
    # resubmission in business_moderation_log.
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name='+',
        help_text='کاربری که تصمیم‌های گرفته‌شده از بله به نامش در لاگ ثبت می‌شود',
    )
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'bale_settings'
        verbose_name = 'تنظیمات ربات بله'
        verbose_name_plural = 'تنظیمات ربات بله'

    def __str__(self):
        return 'تنظیمات ربات بله'

    def save(self, *args, **kwargs):
        # Pin the pk so a second row can never exist, and mint the webhook
        # secret on first save — an operator filling in the token from the admin
        # form has no way to generate one, and a blank secret would make the
        # webhook path guessable.
        self.pk = self.SINGLETON_PK
        if not self.webhook_secret:
            self.webhook_secret = get_random_string(48)
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):
        """Deleting the singleton is a no-op; blank the fields instead."""
        return 0, {}

    @classmethod
    def load(cls):
        """The settings row, created on first access.

        Never raises and never returns None, so callers can treat "the bot is
        not configured" as a normal disabled state rather than an error path.
        """
        obj, _ = cls.objects.get_or_create(pk=cls.SINGLETON_PK)
        return obj

    @property
    def is_configured(self) -> bool:
        return bool(self.is_enabled and self.bot_token and self.chat_id)


class PendingReason(models.Model):
    """A decision waiting for the operator to type its reason.

    Tapping "دلیل دلخواه" cannot decide anything on its own — the note has to
    arrive in a *later* update, as a plain message. Something has to remember
    which business that message belongs to in between.

    A table rather than a cache key, deliberately: django-redis runs with
    IGNORE_EXCEPTIONS=True, so during a Redis blip every read returns None and
    the typed reason would be silently dropped — the operator would watch their
    rejection do nothing and have no idea why.
    """

    chat_id = models.CharField(max_length=64, db_index=True)
    business = models.ForeignKey(
        'business.Business', on_delete=models.CASCADE, related_name='+',
    )
    to_status = models.CharField(max_length=20)
    # The card that was tapped, so the outcome can replace it rather than
    # leaving a dead keyboard above the answer.
    message_id = models.BigIntegerField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'bale_pending_reason'
        ordering = ['-created_at']

    def __str__(self):
        return f'{self.chat_id}: {self.business_id} → {self.to_status}'

    @classmethod
    def open(cls, chat_id, business, to_status, message_id=None):
        """Start a prompt, replacing any half-finished one for this chat.

        Only one can be outstanding: the operator has a single conversation
        thread, so a second prompt means they abandoned the first, and keeping
        both would make the next message they type ambiguous.
        """
        cls.objects.filter(chat_id=str(chat_id)).delete()
        return cls.objects.create(
            chat_id=str(chat_id), business=business,
            to_status=to_status, message_id=message_id,
        )

    @classmethod
    def take(cls, chat_id):
        """Pop the outstanding prompt for ``chat_id``, or None."""
        pending = cls.objects.filter(chat_id=str(chat_id)).first()
        if pending is None:
            return None
        cls.objects.filter(pk=pending.pk).delete()
        return pending
