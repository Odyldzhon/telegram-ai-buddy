#!/bin/bash

echo "--- 1. Updating system ---"
apt update && apt full-upgrade -y

echo "--- 2. Installation of UFW ---"
apt install ufw -y

echo "--- 3. Default network access configuration ---"
ufw default deny incoming
ufw default allow outgoing

# Allow 22 SSH port
ufw allow 22/tcp

echo "--- 4. Activation of UFW ---"
# force enable UFW without prompting for confirmation
ufw --force enable

echo "--- 5. Cleanup of old packages ---"
apt autoremove -y && apt autoclean

echo "--- 6. Configuration completed. Checking status ---"
ufw status verbose

echo "--- 7. Docker installation ---"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
apt update
apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "--- 8. Docker installation completed. Checking status ---"
docker run hello-world

# Check and reboot if required
if [ -f /var/run/reboot-required ]; then
    echo "--- Reboot required after update in 5 sec ... ---"
    sleep 5
    reboot
else
    echo "--- Reboot not required, all done ---"
fi