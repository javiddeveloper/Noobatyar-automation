from django.db import models

class AppVersion(models.Model):
    version_name = models.CharField(max_length=20)  # 1.0.0
    version_code = models.IntegerField(unique=True)  # 123456789
    force_update = models.BooleanField(default=False)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-version_code']

    def __str__(self):
        return f"{self.version_name} ({self.version_code})"


class VersionChangelog(models.Model):
    version = models.OneToOneField(
        AppVersion,
        on_delete=models.CASCADE,
        related_name='changelog'
    )
    changes = models.JSONField(default=list)  # ["change 1", "change 2"]

    def __str__(self):
        return f"Changelog {self.version.version_name}"
