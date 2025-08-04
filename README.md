# WearOS DSU Motion Source

The project aims to reverse engineer **MotionSource** by sshnuke, which was developed to utilize the **Cemuhook protocol** for gyroscope and accelerometer values to be used with emulators such as **Cemu**.

This application runs a **CemuHook (DSU) server** on your **Wear OS device**, allowing you to use its high-fidelity motion sensors for gyro aiming and other motion-controlled actions in your favorite games.

---

## Features

- **High-Fidelity Motion Control**  
  Utilizes your watch's fused `GAME_ROTATION_VECTOR` sensor for stable, low-latency motion tracking.

- **Emulator Compatibility**  
  Fully compatible with any emulator that supports the CemuHook DSU client protocol, including **Cemu** and **Dolphin**.

- **Simple UI**  
  A clean, single-button interface designed for Wear OS. Just start the server and play.

- **Power Efficient**  
  Uses power and Wi-Fi locks to ensure a stable connection during gameplay without excessive battery drain and prevents any screen locks to ensure smooth experience.

- **Modern & Native**  
  A complete, native Android application built from scratch for modern Wear OS versions.

---

## Requirements

- A **Wear OS watch** (Wear OS 3 or newer recommended).
- A **PC** running an emulator or any cemuhook compatible software.
- Both your **watch** and your **PC** must be connected to the same **Wi-Fi network**.

---

## How It Works

- This application implements a **CemuHook server** that listens for **UDP packets on port 26760**.
- It uses a **subscribe-and-stream** model:
    - The server waits for a client (the emulator) to send a "data request" packet.
    - Upon receiving the first request, it registers the client’s IP address and starts a dedicated background thread.
    - This thread continuously streams motion data packets to the client at a high frequency.
- The server uses the `GAME_ROTATION_VECTOR` sensor to get a stable orientation. It calculates:
    - A **gravity vector** (sent as accelerometer data).
    - A **real-time rotation rate** (sent as gyroscope data).
- The sensor data is transformed from the watch’s **portrait** coordinate system to the **landscape** system expected by a standard gamepad before being sent.
- If the server stops receiving **heartbeat** requests from the client for more than 5 seconds, it automatically stops the stream to save battery.

---

## 🛠 Building from Source

```bash
git clone https://github.com/justushar/DSU_Server_WearOS.git
```
1. Open the project in the latest version of Android Studio.

2. Let Gradle sync the project dependencies.

3. Build the project using Build > Make Project.

4. The installable APK will be located in:
```bash
app/build/outputs/apk/debug/
```

## Installation
Check the releases tab under [Releases](https://github.com/justushar/DSU_Server_WearOS/releases).
1. Download the APK on your PC.
2. Install ADB via [chocolatey](https://chocolatey.org/install) or any other package manager.
3. Turn on Developer Options by clicking on Build Number in About Settings on the Wear OS device.
4. Turn on ADB debugging and wireless debugging and select the wifi network to pair.
5. Connect to the same wifi network with your host system.
6. Pair the watch to the host via the following commands.
   ```bash
    adb pair [IP_ADDR][PORT_NUMBER][PAIRING CODE]
   adb connect [IP_ADDR][PORT]
   ```
7. Run the following command in the directory containing the APK after connecting to the device.
```bash
adb install DSU_Server.apk
```
8. The APK will be installed via streamed install and will be available to use.

## Contribute
I would love contributions to the project, please open up an issue first and describe as per the [reference](https://github.com/orgs/community/discussions/147722).

## License
The project is licensed under Apache License.
