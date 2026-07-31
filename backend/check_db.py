import paramiko
import sys

host = '93.127.223.93'
user = 'root'
password = 'wpecO6XDDgSG4DoHJ4Ap'

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    client.connect(hostname=host, username=user, password=password)
    # Check the state of the business in the DB
    cmd = """cd /root/app/backend && docker compose exec -T web python manage.py shell -c "
from business.models import Business
from accounting.models import Subscription
try:
    b = Business.objects.get(unique_code='XJTA5G99')
    print('Business:', b.id, b.title, b.is_locked, b.booking_enabled)
    s = Subscription.objects.filter(user=b.user).last()
    print('Subscription:', s.id, s.status, s.ends_at, s.plan.name)
except Exception as e:
    print('Error:', e)
"
"""
    stdin, stdout, stderr = client.exec_command(cmd)
    print(stdout.read().decode())
    print(stderr.read().decode(), file=sys.stderr)
finally:
    client.close()
