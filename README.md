# 🚀 KotlinConf Modified - Frontend Setup Guide

Welcome! This guide provides step-by-step instructions for setting up and running the KotlinConf Modified frontend application using Android Studio.

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Setting Up the Frontend](#setting-up-the-frontend)
4. [Connecting to the Backend](#connecting-to-the-backend)
5. [Troubleshooting](#troubleshooting)

---

## Overview

The KotlinConf Modified frontend is an Android application that communicates with a backend service. This guide will help you set up the frontend project in Android Studio and configure it to connect to your backend.

## Prerequisites

* Android Studio (latest version recommended)
* JDK 17 or higher
* Android SDK with API level 33 or higher
* Basic familiarity with Android development
* Access to the backend service (previously in Kotlin, now in Rust)
* Git installed on your system

## Setting Up the Frontend

### Step 1: Clone the Project Repository

1. Open Android Studio.
2. Select **Get from Version Control**.
3. Enter the repository URL: `https://gitlab.com/saksham.6484/kotlin-conf-modified.git`.
4. Choose a directory to clone the project into.
5. Click **Clone**.

### Step 2: Open the Project in Android Studio

1. Once the project is cloned, open it in Android Studio.
2. Wait for the project to sync and build.

### Step 3: Configure the Backend Connection

1. Navigate to the file: `shared/src/mobileMain/kotlin/org/jetbrains/kotlinApp/App.kt`.
2. Locate the line that defines the backend IP address:
   ```kotlin
   val ip = "YOUR_OLD_IP_OR_PLACEHOLDER" // <-- Find this line
   ```
3. Update the IP address string to the address where your backend is running:
   ```kotlin
   val ip = "YOUR_BACKEND_IP" // <-- Update with the actual backend IP
   ```
4. Rebuild and run the Android application. It should now communicate with your backend.

## Connecting to the Backend

The frontend application requires a backend service to function. The backend, previously written in Kotlin, is now implemented in Rust in a separate repository. The functionality remains the same.

Ensure that your backend service is running and accessible at the IP address specified in the `App.kt` file.

## Troubleshooting

If you encounter issues while setting up or running the frontend application, consider the following:

* **Build Errors:** Ensure that all dependencies are correctly synced in Android Studio.
* **Connection Issues:** Verify that the backend IP address is correct and that the backend service is running.
* **Runtime Errors:** Check the Android Studio logcat for detailed error messages.

---

This completes the frontend setup guide. Happy coding! 🎉