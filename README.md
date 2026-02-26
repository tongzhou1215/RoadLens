#🚗 RoadLens – Mobile Dash Cam App

Android-Based Collision Detection & Emergency Alert System

## 📌 Project Overview

RoadLens is an Android application designed to detect potential vehicle collisions using real-time sensor data.  
Upon detecting abnormal motion patterns, the app automatically retrieves the device's GPS location and sends an emergency SMS containing a Google Maps link to a predefined contact.

The application is built using the MVVM architectural pattern to ensure clean separation of concerns, maintainability, and scalability.

---

## ⚙️ Core Features

- 📊 Real-time accelerometer and gyroscope data monitoring
- 🚨 Threshold-based collision detection algorithm
- 📍 Automatic GPS location retrieval upon crash detection
- 📩 Emergency SMS notification with Google Maps link
- 🗂 Local event logging
- 🔄 Clean MVVM architecture for UI–logic separation

---

## 🧠 Collision Detection Logic

The system continuously monitors motion sensor data and analyzes abnormal spikes in acceleration and angular velocity.

Acceleration magnitude is computed as:

|a| = sqrt(ax² + ay² + az²)

Angular velocity magnitude:

|ω| = sqrt(wx² + wy² + wz²)

A collision event is triggered when both motion magnitudes exceed predefined thresholds within a short time window.

This approach enables real-time detection without requiring external hardware.

---

## 🚨 Emergency Alert System

One of the core features of RoadLens is its automated emergency notification mechanism.

When a collision is detected:

1. The app retrieves the device’s current GPS coordinates.
2. A Google Maps link is generated:
   https://maps.google.com/?q=latitude,longitude
3. An SMS message is automatically sent to the predefined emergency contact.

Example message:

"Emergency Alert: A potential collision has been detected.
Location: https://maps.google.com/?q=43.0731,-89.4012"

The system integrates Android’s LocationManager and SmsManager APIs and includes runtime permission handling to ensure safe and reliable operation.

---

## 🏗 System Architecture

The application follows the MVVM pattern:

UI (Activity / Fragment)  
        ↓  
ViewModel (Data Processing & Collision Logic)  
        ↓  
Repository / Sensor Layer  
        ↓  
Android SensorManager & LocationManager  

- ViewModel handles sensor data processing and collision evaluation
- LiveData ensures lifecycle-aware UI updates
- Business logic is fully decoupled from UI components

---

## 🚀 Future Improvements

- Machine learning–based collision classification
- Improved false-positive filtering
- Background service support
- Cloud-based event synchronization


