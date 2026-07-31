#!/usr/bin/env python
import os
import django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
django.setup()

from accounting.models import Plan

renames = [
    ('آزمایشی', '🐦 گنجشک'),
    ('یک ماهه', '🦅 باز'),
    ('سه ماهه', '🦉 عقاب'),
    ('شش ماهه', '🔥 ققنوس'),
    ('یک ساله', '💎 سیمرغ'),
]

for old, new in renames:
    count = Plan.objects.filter(name=old).update(name=new)
    if count:
        print(f"Renamed: {old} → {new}")
    else:
        print(f"Not found (skipped): {old}")

print("\nAll plans:")
for p in Plan.objects.all():
    print(f"  {p.name} — {p.price:,} تومان — active={p.is_active}")
