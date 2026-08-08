# core/apps.py
from django.apps import AppConfig
# Imported as a module, not `from ... import AdminConfig`: Django scans this
# module's attributes for AppConfig subclasses, and a bare imported AdminConfig
# would be picked up as a second "default" candidate for the `core` app
# ("declares more than one default AppConfig").
from django.contrib.admin import apps as admin_apps


class NobatyarAdminConfig(admin_apps.AdminConfig):
    """Replaces 'django.contrib.admin' in INSTALLED_APPS.

    Django 4.2's supported way to swap the default admin site: it instantiates
    `default_site` during app loading and assigns it to `django.contrib.admin.site`
    *before* any app's admin.py is imported, so the existing `@admin.register(...)`
    decorators register against our site with no per-app changes.
    """

    # This class configures django.contrib.admin, not the `core` app, so it must
    # never be auto-picked as core's own AppConfig.
    default = False
    default_site = 'core.admin_site.NobatyarAdminSite'


class CoreConfig(AppConfig):
    """The `core` package as an installed app.

    `core` holds no models; it is in INSTALLED_APPS purely so Django discovers
    core/management/commands/ (setup_admin_roles).
    """

    default = True
    name = 'core'
    default_auto_field = 'django.db.models.BigAutoField'
