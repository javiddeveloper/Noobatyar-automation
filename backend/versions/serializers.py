from rest_framework import serializers
from .models import AppVersion, VersionChangelog

class ChangelogSerializer(serializers.ModelSerializer):
    class Meta:
        model = VersionChangelog
        fields = ['changes']


class AppVersionSerializer(serializers.ModelSerializer):
    changelog = ChangelogSerializer(read_only=True)

    class Meta:
        model = AppVersion
        fields = ['version_name', 'version_code', 'force_update', 'changelog']
