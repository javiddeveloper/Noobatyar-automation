"""
business/tests_moderation_admin.py

Regression cover for the admin's delete permissions on the moderation audit
trail.

BusinessModerationLogAdmin refuses add/change/delete so the trail stays
evidence. A flat refusal on delete, though, also made every *business*
undeletable: Django's delete-confirmation page asks each cascaded object's own
ModelAdmin for delete permission and reports any refusal as a missing
permission — and since a business gets its first log on its first moderation
decision, that covered effectively the whole table.

These tests pin both halves of the resolution: a log may cascade away with the
business it describes, but may never be deleted while that business lives on.
"""

from django.contrib.admin.sites import site
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse

from .models import Business, BusinessModerationLog
from .tests_moderation_api import make_business

User = get_user_model()


class ModerationLogCascadeDeleteTests(TestCase):
    """A business with moderation history must still be deletable."""

    def setUp(self):
        self.superuser = User.objects.create_superuser(
            phone='09120000099', name='ادمین', password='pw-for-test',
        )
        self.owner = User.objects.create_user(phone='09120000098', name='مالک')
        self.business = make_business(self.owner)
        # The state every real business reaches: at least one decision logged.
        BusinessModerationLog.objects.create(
            business=self.business,
            from_status=Business.MODERATION_PENDING,
            to_status=Business.MODERATION_APPROVED,
            actor=self.superuser,
            note='تأیید اولیه',
        )
        self.client.force_login(self.superuser)

    def test_delete_confirmation_does_not_demand_extra_permissions(self):
        """The page must offer deletion, not the "you lack permission" notice."""
        url = reverse('admin:business_business_delete', args=[self.business.pk])
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        # perms_lacking is what renders the Persian refusal the user hit.
        self.assertFalse(response.context['perms_lacking'])

    def test_business_with_moderation_log_can_be_deleted(self):
        url = reverse('admin:business_business_delete', args=[self.business.pk])
        response = self.client.post(url, {'post': 'yes'})
        self.assertEqual(response.status_code, 302)
        self.assertFalse(Business.objects.filter(pk=self.business.pk).exists())
        # The log goes with it; an orphan log describes nothing.
        self.assertFalse(BusinessModerationLog.objects.exists())


class ModerationLogRemainsTamperProofTests(TestCase):
    """Allowing the cascade must not open a door to editing the trail."""

    def setUp(self):
        self.superuser = User.objects.create_superuser(
            phone='09120000097', name='ادمین', password='pw-for-test',
        )
        self.owner = User.objects.create_user(phone='09120000096', name='مالک')
        self.business = make_business(self.owner)
        self.log = BusinessModerationLog.objects.create(
            business=self.business,
            from_status=Business.MODERATION_PENDING,
            to_status=Business.MODERATION_APPROVED,
            actor=self.superuser,
        )
        self.client.force_login(self.superuser)

    def test_deleting_a_log_directly_is_refused(self):
        url = reverse(
            'admin:business_businessmoderationlog_delete', args=[self.log.pk],
        )
        self.assertEqual(self.client.post(url, {'post': 'yes'}).status_code, 403)
        self.assertTrue(
            BusinessModerationLog.objects.filter(pk=self.log.pk).exists()
        )

    def test_changelist_offers_no_delete_action(self):
        """No bulk delete on the log changelist.

        Django drops `action_form` entirely once no action is available, so an
        absent form is the assertion: not merely "delete_selected is hidden"
        but "this changelist offers nothing to run at all".
        """
        url = reverse('admin:business_businessmoderationlog_changelist')
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertIsNone(response.context['action_form'])
        self.assertEqual(
            site._registry[BusinessModerationLog].get_actions(
                response.wsgi_request
            ),
            {},
        )

    def test_add_and_change_stay_closed(self):
        admin_cls = site._registry[BusinessModerationLog]
        request = self.client.get(
            reverse('admin:business_businessmoderationlog_changelist')
        ).wsgi_request
        self.assertFalse(admin_cls.has_add_permission(request))
        self.assertFalse(admin_cls.has_change_permission(request, self.log))
