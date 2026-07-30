"""
One-shot bootstrap deploy over SSH.

Every secret this script needs comes from the environment — nothing is baked in.
It used to hardcode the server's root password and write a fixed, publicly
readable SECRET_KEY plus `POSTGRES_PASSWORD=postgres` into the production .env,
which meant anyone reading this repository could sign Django sessions and log
into the database.

Usage:
    export DEPLOY_HOST=... DEPLOY_PASSWORD=...
    export DJANGO_SECRET_KEY="$(python -c 'import secrets;print(secrets.token_urlsafe(64))')"
    export POSTGRES_PASSWORD=...
    python deploy.py

Prefer key-based auth (ssh-copy-id) and leave DEPLOY_PASSWORD unset.
"""

import os
import paramiko
from scp import SCPClient
import sys
import time


def _required_env(name: str) -> str:
    value = (os.environ.get(name) or '').strip()
    if not value:
        sys.exit(
            f"{name} is not set. This script refuses to run with built-in "
            f"credentials — see the module docstring."
        )
    return value

def create_ssh_client(server, port, user, password):
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    # RejectPolicy, not AutoAddPolicy: silently trusting an unknown host key
    # makes this deploy (password and all) vulnerable to a man-in-the-middle.
    # Run `ssh-keyscan -H <host> >> ~/.ssh/known_hosts` once for a new server.
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    client.connect(server, port, user, password or None)
    return client

def run_command(client, command):
    print(f"Running: {command}")
    stdin, stdout, stderr = client.exec_command(command)
    exit_status = stdout.channel.recv_exit_status()          
    if exit_status == 0:
        print(f"Success: {command}")
        return stdout.read().decode()
    else:
        print(f"Error: {command}\n{stderr.read().decode()}")
        # We don't exit on error so that if apt update fails partially, we can still continue.
        return None

if __name__ == "__main__":
    server = _required_env('DEPLOY_HOST')
    port = int(os.environ.get('DEPLOY_PORT', '22'))
    user = os.environ.get('DEPLOY_USER', 'root')
    # Optional: unset means key-based auth, which is what you should be using.
    password = os.environ.get('DEPLOY_PASSWORD', '')
    django_secret = _required_env('DJANGO_SECRET_KEY')
    postgres_password = _required_env('POSTGRES_PASSWORD')
    allowed_hosts = os.environ.get('ALLOWED_HOSTS', server)
    local_file = 'backend_deploy.zip'
    remote_path = '/root/backend_deploy.zip'
    app_dir = '/root/app/backend'

    try:
        print("Connecting to server...")
        ssh = create_ssh_client(server, port, user, password)

        # Update and install Docker and unzip
        run_command(ssh, "apt-get update")
        run_command(ssh, "apt-get upgrade -y")
        run_command(ssh, "apt-get install -y docker.io docker-compose-v2 unzip")

        # Upload zip file
        print(f"Uploading {local_file} to {remote_path}...")
        with SCPClient(ssh.get_transport()) as scp:
            scp.put(local_file, remote_path)
        print("Upload complete.")

        # Unzip and deploy
        run_command(ssh, "mkdir -p " + app_dir)
        run_command(ssh, f"unzip -o {remote_path} -d {app_dir}")
        
        # Create .env file for production. Values come from this machine's
        # environment; none of them are readable in the repository.
        env_content = '\n'.join([
            f"SECRET_KEY={django_secret}",
            "DEBUG=False",
            f"ALLOWED_HOSTS={allowed_hosts}",
            "POSTGRES_DB=postgres",
            "POSTGRES_USER=postgres",
            f"POSTGRES_PASSWORD={postgres_password}",
            f"MELIPAYAMAK_OTP_TOKEN={os.environ.get('MELIPAYAMAK_OTP_TOKEN', '')}",
            f"MELIPAYAMAK_FROM={os.environ.get('MELIPAYAMAK_FROM', '')}",
        ]) + '\n'
        # Written via stdin rather than `echo '<secrets>'` so the values never
        # appear in the remote shell's process list or history.
        sftp = ssh.open_sftp()
        try:
            with sftp.file(f"{app_dir}/.env", 'w') as remote_env:
                remote_env.write(env_content)
            sftp.chmod(f"{app_dir}/.env", 0o600)
        finally:
            sftp.close()
        
        # Build and start docker-compose
        print("Starting Docker Compose...")
        run_command(ssh, f"cd {app_dir} && docker compose up -d --build")
        
        print("Deployment completed successfully!")

    except Exception as e:
        print(f"An error occurred: {e}")
        sys.exit(1)
    finally:
        if 'ssh' in locals():
            ssh.close()
