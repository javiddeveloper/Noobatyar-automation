"""
business/tests_theme_color.py

Regression cover for theme_color not being persisted when a logo edit is
approved through moderation.

Business.save() recomputes theme_color whenever the logo changed, but
moderation.apply_decision() calls business.save(update_fields=[...]) with an
explicit column list built before that recompute happens. Django's
update_fields restricts the UPDATE statement to exactly those columns, so the
recomputed value landed on the Python instance and nowhere else — an owner who
changed their logo and got re-approved kept the old header colour forever,
because the one save that promotes pending_logo onto logo is exactly the save
that dropped the corresponding colour on the floor.
"""

import io

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from PIL import Image

from .models import Business
from .moderation import apply_decision
from .theme_color import color_from_logo
from .tests_moderation_api import make_business

User = get_user_model()


def _logo(color) -> SimpleUploadedFile:
    """A small solid-colour PNG, upload-ready."""
    buf = io.BytesIO()
    Image.new('RGB', (32, 32), color).save(buf, format='PNG')
    buf.seek(0)
    return SimpleUploadedFile('logo.png', buf.read(), content_type='image/png')


class ThemeColorPersistsThroughModerationTests(TestCase):
    """theme_color must be written to the DB, not just recomputed in memory."""

    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000091', name='مالک')

    def test_first_approval_stores_a_colour_derived_from_the_logo(self):
        business = make_business(self.owner, logo=_logo((220, 30, 30)))
        apply_decision(business, Business.MODERATION_APPROVED, actor=None)

        business.refresh_from_db()
        expected = color_from_logo(_logo((220, 30, 30)))
        self.assertEqual(business.theme_color, expected)

    def test_logo_edit_after_approval_updates_the_stored_colour(self):
        """The bug this file exists for.

        Approve once with a red logo, then edit the logo (as an owner does
        post-approval: staged onto pending_logo, not the live column — see
        services.stage_pending_moderated_fields) and approve the edit. The
        colour in the database must change to match the new logo, not stay
        pinned to whatever the first approval computed.
        """
        business = make_business(self.owner, logo=_logo((220, 30, 30)))
        apply_decision(business, Business.MODERATION_APPROVED, actor=None)
        business.refresh_from_db()
        red_color = business.theme_color

        business.pending_logo = _logo((30, 90, 220))
        business.save(update_fields=['pending_logo'])
        apply_decision(business, Business.MODERATION_APPROVED, actor=None)

        business.refresh_from_db()
        blue_color = business.theme_color
        expected_blue = color_from_logo(_logo((30, 90, 220)))

        self.assertNotEqual(
            blue_color, red_color,
            'theme_color still holds the colour from the first approval — the '
            'update_fields save on the logo-approval path dropped the recompute.',
        )
        self.assertEqual(blue_color, expected_blue)

    def test_approving_a_decision_that_does_not_touch_the_logo_leaves_colour_alone(self):
        """update_fields is only widened when a recompute actually happened —
        confirms the fix does not turn every approval into a full-column save."""
        business = make_business(self.owner, logo=_logo((220, 30, 30)))
        apply_decision(business, Business.MODERATION_APPROVED, actor=None)
        business.refresh_from_db()
        color_after_first_approval = business.theme_color

        business.pending_bio = 'توضیحات جدید'
        business.save(update_fields=['pending_bio'])
        apply_decision(business, Business.MODERATION_APPROVED, actor=None)

        business.refresh_from_db()
        self.assertEqual(business.theme_color, color_after_first_approval)
        self.assertEqual(business.bio, 'توضیحات جدید')
