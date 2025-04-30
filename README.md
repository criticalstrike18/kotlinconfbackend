# 🚀 KotlinConf Modified - Google Cloud Deployment Guide ☁️

Welcome! This guide provides step-by-step instructions for deploying the KotlinConf Modified backend stack on a Google Cloud Platform VM instance using Docker and Docker Compose.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Setting Up Google Cloud VM Instance](#setting-up-google-cloud-vm-instance)
4. [Deploying the Application](#deploying-the-application)
5. [Accessing the Services](#accessing-the-services)
6. [Frontend Configuration](#frontend-configuration)
7. [Understanding the Architecture](#understanding-the-architecture)
8. [Troubleshooting](#troubleshooting)
9. [Stopping the Services](#stopping-the-services)

---

## Overview

The KotlinConf Modified application is a multi-service stack running in Docker containers:

* **Backend Service:** The core Kotlin application handling API requests.
* **PostgreSQL Database:** Stores conference schedules, speaker info, podcast data, etc.
* **Import Service:** Manages data synchronization (export/import) between PostgreSQL and SQLite for mobile clients.
* **PgAdmin:** A web-based interface for managing the PostgreSQL database.

This guide will walk you through deploying these services onto your GCP VM.

## Prerequisites

* A Google Cloud Platform (GCP) account.
* A Google Cloud VM instance (Debian/Ubuntu based recommended) already created.
* Basic familiarity with the terminal/command line.
* `git` installed on your local machine (if uploading code via SCP isn't preferred).
* Android Studio installed (for frontend configuration).

## Setting Up Google Cloud VM Instance

### Step 1: Connect to Your VM Instance

1. Open the [Google Cloud Console](https://console.cloud.google.com/).
2. Navigate to **Compute Engine** > **VM instances**.
3. Find your instance in the list.
4. Click the **SSH** button next to your instance name to open a secure terminal session.

### Step 2: Install Docker and Docker Compose

Run the following commands in the VM's SSH terminal to install Docker Engine and Docker Compose:

```bash
# Update package list
sudo apt-get update

# Install prerequisites
sudo apt-get install -y apt-transport-https ca-certificates curl software-properties-common gnupg

# Add Docker's official GPG key
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Set up the stable Docker repository
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Update package list again and install Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io

# Verify Docker installation
sudo docker --version

# Install Docker Compose (v2.23.0 - check for newer versions if desired)
sudo curl -L "https://github.com/docker/compose/releases/download/v2.23.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Verify Docker Compose installation
docker-compose --version

# Add your user to the 'docker' group (to run Docker without sudo)
sudo usermod -aG docker $USER

# IMPORTANT: Log out and log back in for the group changes to take effect!
exit
```
**(Reconnect via SSH after exiting)**

## Deploying the Application

### Step 1: Clone the Project Repository

You need the application code and configuration files (`Dockerfile`, `docker-compose.yml`) on your VM. The easiest way is to clone your Git repository.

```bash
# Clone your repository
git clone https://gitlab.com/saksham.6484/kotlin-conf-modified.git
cd kotlin-conf-modified
```

### Step 2: Verify the `Dockerfile`

Your cloned repository should already contain a `Dockerfile` for the backend service. Ensure it looks something like this (adjust based on your specific project's build process):

```dockerfile
# Example Dockerfile (should be in your repo at ~/kotlinconf-backend/Dockerfile)
FROM gradle:7.6.1-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
*(No need to create this file if it was cloned correctly)*.

### Step 3: Configure Firewall Rules in Google Cloud

To access your services from the internet, you need to allow traffic on the specific ports:

1. In the [Google Cloud Console](https://console.cloud.google.com/), navigate to **VPC Network** > **Firewall**.
2. Click **CREATE FIREWALL RULE**.
3. Configure the rule:
    * **Name:** `kotlinconf-backend-ports` (or similar)
    * **Direction of traffic:** `Ingress`
    * **Action on match:** `Allow`
    * **Targets:** `All instances in the network` (or use tags if you've tagged your VM)
    * **Source IP ranges:** `0.0.0.0/0` (Allows access from anywhere. Restrict this to your IP for better security if needed).
    * **Protocols and ports:** Select `Specified protocols and ports`
        * Check **TCP** and enter the ports: `8080, 8000, 5050`
4. Click **Create**.

### Step 4: Start the Services

Now, navigate back to your project directory in the VM's SSH terminal and start all the services defined in your `docker-compose.yml` file.

```bash
# Ensure you are in the directory containing docker-compose.yml
cd ~/kotlinconf-backend

# Start all services in detached (background) mode
# This will also build the 'backend' image using your Dockerfile
# Use 'sudo' if you didn't log out/in after adding user to docker group
docker-compose up -d --build
```

This command will:

1. **Build** the `backend` service image using the `Dockerfile`.
2. **Pull** the required images (PostgreSQL, Import Service, PgAdmin) if they aren't already present locally.
3. **Create** and **start** all the containers defined in `docker-compose.yml`.
4. **Connect** them via the defined `app-network`.

## Accessing the Services

Once the `docker-compose up -d` command completes successfully, your services should be accessible.

**Find your VM's External IP:**

1. Go to **Google Cloud Console** > **Compute Engine** > **VM Instances**.
2. Locate your VM and note the IP address listed in the **External IP** column. Let's call this `YOUR_VM_EXTERNAL_IP`.

**Access Points:**

* **Backend API:** `http://YOUR_VM_EXTERNAL_IP:8080`
* **Import Service:** `http://YOUR_VM_EXTERNAL_IP:8000`
    * (e.g., `http://YOUR_VM_EXTERNAL_IP:8000/download_latest_file` for the SQLite download)
* **PgAdmin (Database GUI):** `http://YOUR_VM_EXTERNAL_IP:5050`
    * **Login:** `admin@admin.com`
    * **Password:** `admin`
    * (You'll need to configure the connection to the `db` service within PgAdmin: Host `db`, Port `5432`, DB `kotlinconfg`, User `postgres`, Password `postgres`)

## Frontend Configuration

To connect the frontend Android application to your newly deployed backend:

1. Open the frontend project in Android Studio.
2. Navigate to the file: `shared/src/mobileMain/kotlin/org/jetbrains/kotlinApp/App.kt`.
3. Locate the line that defines the backend IP address, likely similar to:
   ```kotlin
   val ip = "YOUR_OLD_IP_OR_PLACEHOLDER" // <-- Find this line
   ```
4. Update the IP address string (`YOUR_VM_EXTERNAL_IP` obtained in the previous step) where your backend is running:
   ```kotlin
   val ip = "YOUR_VM_EXTERNAL_IP" // <-- Update with the actual External IP
   ```
5. Rebuild and run the Android application. It should now communicate with your deployed backend on GCP.

## Understanding the Architecture

* **Backend Service (Port 8080):** Your Kotlin application serving the API. Interacts directly with the PostgreSQL database (`db` service).
* **PostgreSQL Database (`db`, Port 5432 internal):** The primary data storage. Only accessible *within* the Docker network by other services (like `backend`, `importService`, `pgadmin`) unless you explicitly expose its port (which the `docker-compose.yml` does for potential external tools, but internal services use the service name `db`).
* **Import Service (Port 8000):** A utility service. On startup, it can populate the PostgreSQL DB from SQLite backups. Periodically (e.g., daily), it exports the current PostgreSQL data into a new SQLite file, making it available for download via its API (`/download_latest_file`). This is vital for the mobile app's offline capabilities.
* **PgAdmin (Port 5050):** A web interface providing administrative access to the PostgreSQL database for viewing data, running queries, and monitoring.

All services run within a dedicated Docker network (`app-network`), allowing them to communicate using their service names (e.g., `backend` can reach the database at `db:5432`).

## Troubleshooting

If things aren't working as expected, here are some commands to help diagnose issues (run these in the `~/kotlinconf-backend` directory on your VM):

**Check Running Containers:**
See the status of all services managed by Docker Compose.

```bash
# Use 'sudo' if needed
docker-compose ps
```

**View Service Logs:**
Check the output logs for specific services to find errors.

```bash
# View logs for the backend service
docker-compose logs backend

# View logs for the import service
docker-compose logs importService

# View logs for the database
docker-compose logs db

# View logs for PgAdmin
docker-compose logs pgadmin

# Follow logs in real-time (press Ctrl+C to stop)
docker-compose logs -f backend
```

**Common Issues & Checks:**

* **Backend Won't Start:**
    * Is the database running (`docker-compose ps`)?
    * Check `docker-compose logs backend` for database connection errors or application startup failures.
    * Verify environment variables in `docker-compose.yml` are correct.
* **Import Service Errors:**
    * Check `docker-compose logs importService`. Common issues involve failing to connect to `db` or `backend`. Ensure those services are running.
* **Cannot Access Services via Browser:**
    * **Double-check GCP Firewall Rules:** Ensure ports `8080`, `8000`, `5050` are open for TCP traffic from your IP or `0.0.0.0/0`.
    * **Verify Services are Running:** Use `docker-compose ps`. Are the services listed as `Up`?
    * **Correct External IP:** Are you using the correct External IP address of your VM?
* **Database Connection Issues (from Backend/Import Service):**
    * Verify `DATABASE_HOST=db` (or `PG_HOST=db`) in `docker-compose.yml`. Services connect using the service name within the Docker network.
    * Check database credentials (`DATABASE_USER`, `DATABASE_PASSWORD`, etc.) match between the `db` service environment variables and the other services' environment variables.
    * Check `docker-compose logs db` for PostgreSQL errors.

**Restarting Services:**

```bash
# Restart a specific service (e.g., backend)
docker-compose restart backend

# Restart all services
docker-compose restart
```

**Rebuilding the Backend (After Code Changes):**

If you've pulled new code changes for the backend:

```bash
# Stop, rebuild the backend image, and restart all services
docker-compose up -d --build
```

## Stopping the Services

When you need to stop the running services:

1. SSH into your VM.
2. Navigate to the project directory (`~/kotlinconf-backend`).
3. Run the appropriate command:

```bash
# Stop and remove the containers
# Use 'sudo' if needed
docker-compose down
```

If you also want to **remove the persistent data volumes** (like the PostgreSQL database and PgAdmin settings - **USE WITH CAUTION, DATA WILL BE LOST**):

```bash
# Stop containers AND remove associated volumes
# Use 'sudo' if needed
docker-compose down --volumes
```

---

This completes the deployment guide. Happy deploying! 🎉