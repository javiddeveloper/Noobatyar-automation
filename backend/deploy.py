import paramiko
from scp import SCPClient
import sys
import time

def create_ssh_client(server, port, user, password):
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(server, port, user, password)
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
    server = '93.127.223.93'
    port = 22
    user = 'root'
    password = 'gbJPRmA6qRWrXOpdd5IB'
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
        
        # Create .env file for production
        env_content = '''
SECRET_KEY=production-secret-key-change-me-later
DEBUG=False
ALLOWED_HOSTS=*
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
'''
        run_command(ssh, f"echo '{env_content}' > {app_dir}/.env")
        
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
