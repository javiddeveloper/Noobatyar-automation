# capture_demo_screens.py
"""
Screenshot every admin screen into docs/demo/ — run with
`python capture_demo_screens.py` while nothing else holds the dev database.

HOW IT GETS PAST THE LOGIN
Not by typing a password into a headless browser. It renders each page through
Django's own test client with `force_login()` — the same path the test suite
uses — and writes the resulting HTML to a temp file. Headless Chrome then opens
that file and photographs it. No credentials exist anywhere in this script and
no session is left behind in the database.

The saved HTML still points at `/static/...`, so the dev server has to be
running on RUNSERVER for the CSS, the font and Chart.js to load; those URLs are
rewritten to absolute below. Static files need no authentication, which is why
this split works at all.

Charts are drawn by Chart.js after load, hence --virtual-time-budget: without
it Chrome photographs two empty canvases.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
os.environ.setdefault('DEBUG', 'True')
django.setup()

from django.contrib.auth import get_user_model  # noqa: E402
from django.test import Client  # noqa: E402

from business.models import Business  # noqa: E402

BASE_DIR = Path(__file__).resolve().parent
OUT_DIR = BASE_DIR.parent / 'docs' / 'demo'
RUNSERVER = 'http://localhost:8011'

CHROME_CANDIDATES = [
    r'C:\Program Files\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
]

WIDTH = 1500

# Phone-width capture.
#
# `--window-size=390,H` does NOT give a 390px layout: headless Chrome clamps the
# window to roughly 500px wide on this platform, so the page lays out at ~500
# (measured: clientWidth 500 at DPR 1, 492 at DPR 2) while the screenshot still
# crops to 390. On an RTL page the crop takes the *start* of every line, and the
# first attempt came back with "مدیریت سامانه" cut down to "سامانه".
#
# So the page is rendered inside an iframe of exactly MOBILE_WIDTH instead. An
# iframe gets its own layout viewport at whatever width it is given, well under
# Chrome's window floor, and the host page is captured at a size the floor
# allows. Real device emulation would need CDP (Emulation.setDeviceMetricsOverride)
# and a websocket client, which is a dependency this repo does not have.
#
# The one thing this still cannot show is the `pointer: coarse` tap-target rule
# in admin.css — that needs true touch emulation, and is verified in a live
# browser instead.
MOBILE_WIDTH = 390
MOBILE_SCALE = 2


def find_chrome():
    for path in CHROME_CANDIDATES:
        if Path(path).exists():
            return path
    found = shutil.which('chrome') or shutil.which('msedge')
    if found:
        return found
    sys.exit('Chrome/Edge not found — set CHROME_CANDIDATES for this machine.')


def shots(demo_business_id, demo_user_id):
    """(filename, url, theme, page height).

    Heights are per-page because headless Chrome photographs the window, not the
    document: one shared height either crops the long reports or leaves a metre
    of empty page under the short ones.
    """
    return [
        ('01-dashboard-light.png',   '/admin/',                                    'light', 2600),
        ('02-dashboard-dark.png',    '/admin/',                                    'dark',  2600),
        ('03-financial-report.png',  '/admin/accounting/transaction/reports/',     'light', 3000),
        ('04-sms-report.png',        '/admin/visitor/smslog/reports/',             'light', 2400),
        ('05-moderation-queue.png',  '/admin/business/business/moderation-queue/', 'light', 1700),
        ('06-business-360.png',      f'/admin/core/businesses/{demo_business_id}/', 'light', 1800),
        ('07-user-360.png',          f'/admin/core/users/{demo_user_id}/',          'light', 2000),
        ('08-segment-builder.png',   '/admin/core/segments/',                      'light', 1200),
        ('09-business-list.png',     '/admin/business/business/',                  'light', 1300),
        ('10-content-reports.png',   '/admin/business/contentreport/',             'light', 1200),
        ('11-transactions.png',      '/admin/accounting/transaction/',             'light', 1600),
        ('12-sms-report-dark.png',   '/admin/visitor/smslog/reports/',             'dark',  2400),
    ]


def mobile_shots(demo_business_id, demo_user_id):
    """The same screens at phone width — the layout is genuinely different
    (single-column grids, tables scrolling inside their own box), so a desktop
    shot scaled down would not show what a phone actually renders."""
    return [
        ('m1-dashboard.png',        '/admin/',                                    'light', 3400),
        ('m2-financial-report.png', '/admin/accounting/transaction/reports/',     'light', 3200),
        ('m3-moderation-queue.png', '/admin/business/business/moderation-queue/', 'light', 2400),
        ('m4-business-360.png',     f'/admin/core/businesses/{demo_business_id}/', 'light', 2200),
        ('m5-sms-report.png',       '/admin/visitor/smslog/reports/',             'light', 2800),
        ('m6-dashboard-dark.png',   '/admin/',                                    'dark',  3400),
    ]


def prepare_html(client, url, theme, tmpdir, name):
    response = client.get(url, follow=True)
    if response.status_code != 200:
        return None, response.status_code
    html = response.content.decode('utf-8')

    # Point every asset at the running dev server. The file:// page cannot
    # resolve a root-relative /static/ path on its own.
    html = html.replace('href="/static/', f'href="{RUNSERVER}/static/')
    html = html.replace('src="/static/', f'src="{RUNSERVER}/static/')
    html = html.replace('href="/media/', f'href="{RUNSERVER}/media/')
    html = html.replace('src="/media/', f'src="{RUNSERVER}/media/')

    # Pin the theme.
    #
    # Setting data-theme on <html> is not enough on its own: Django ships
    # theme.js, which runs on load, reads localStorage (empty on a fresh file://
    # origin), resolves that to "auto" and writes the *OS* preference back onto
    # the same attribute — so both the light and the dark shot come out looking
    # like whatever this machine happens to be set to. Dropping the script is
    # what makes the attribute stick, and the toggle button it powers is not
    # something a screenshot can click anyway.
    html = re.sub(r'<script[^>]*\btheme\.js[^>]*>\s*</script>', '', html)
    html = re.sub(r'<html([^>]*?)\sdata-theme="[^"]*"', r'<html\1', html, count=1)
    html = re.sub(r'<html\b', f'<html data-theme="{theme}"', html, count=1)

    path = Path(tmpdir) / f'{name}.html'
    path.write_text(html, encoding='utf-8')
    return path, 200


def wrap_in_phone_frame(page_path, height, tmpdir, name):
    """Host page holding `page_path` in a MOBILE_WIDTH-wide iframe.

    See the MOBILE_WIDTH comment: this is what gets the page a real phone
    viewport despite Chrome's minimum window width.
    """
    host = f"""<!doctype html>
<html><head><meta charset="utf-8"><style>
  html, body {{ margin: 0; padding: 0; background: #fff; }}
  iframe {{ display: block; width: {MOBILE_WIDTH}px; height: {height}px; border: 0; }}
</style></head>
<body><iframe src="{page_path.name}" scrolling="no"></iframe></body></html>
"""
    path = Path(tmpdir) / f'{name}-host.html'
    path.write_text(host, encoding='utf-8')
    return path


def capture(chrome, html_path, out_path, height, mobile=False):
    args = [
        chrome,
        '--headless=new',
        '--disable-gpu',
        '--hide-scrollbars',
        # Chart.js needs a few animation frames before the canvases have
        # anything on them.
        '--virtual-time-budget=6000',
    ]
    if mobile:
        args += [
            f'--force-device-scale-factor={MOBILE_SCALE}',
            f'--window-size={MOBILE_WIDTH},{height}',
        ]
    else:
        args += ['--force-device-scale-factor=1', f'--window-size={WIDTH},{height}']
    args += [f'--screenshot={out_path}', html_path.as_uri()]
    subprocess.run(args, check=True, capture_output=True)


def main():
    User = get_user_model()
    admin = User.objects.filter(is_superuser=True).first()
    if admin is None:
        sys.exit('No superuser to render as — run `manage.py createsuperuser` first.')

    demo = Business.objects.filter(user__phone__startswith='09129').order_by('id').first()
    if demo is None:
        sys.exit('No demo data — run `python seed_demo_data.py` first.')

    # SERVER_NAME, because the test client defaults to Host: testserver and
    # ALLOWED_HOSTS only lists real dev hosts. Inside `manage.py test` Django
    # appends testserver for you; run standalone like this, it does not.
    client = Client(SERVER_NAME='localhost')
    client.force_login(admin)

    chrome = find_chrome()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f'Chrome: {chrome}')
    print(f'Output: {OUT_DIR}\n')

    ok = failed = 0
    with tempfile.TemporaryDirectory() as tmpdir:
        batches = [
            ('desktop', shots(demo.id, demo.user_id), OUT_DIR, WIDTH, False),
            ('mobile', mobile_shots(demo.id, demo.user_id), OUT_DIR / 'mobile',
             MOBILE_WIDTH, True),
        ]
        for label, entries, out_dir, width, is_mobile in batches:
            out_dir.mkdir(parents=True, exist_ok=True)
            print(f'{label}:')
            for name, url, theme, height in entries:
                html_path, status = prepare_html(client, url, theme, tmpdir,
                                                 f'{label}-{name}')
                if html_path is None:
                    print(f'  ✗ {name}  (HTTP {status} on {url})')
                    failed += 1
                    continue
                out = out_dir / name
                shot_path = (wrap_in_phone_frame(html_path, height, tmpdir,
                                                 f'{label}-{name}')
                             if is_mobile else html_path)
                capture(chrome, shot_path, out, height, mobile=is_mobile)
                size_kb = out.stat().st_size // 1024 if out.exists() else 0
                if size_kb:
                    print(f'  ✓ {name}  {width}x{height}  {size_kb} KB')
                    ok += 1
                else:
                    print(f'  ✗ {name}  (Chrome wrote nothing)')
                    failed += 1
            print()

    print(f'{ok} screenshots written, {failed} failed.')


if __name__ == '__main__':
    main()
