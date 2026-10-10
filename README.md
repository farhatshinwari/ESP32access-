# ESP32 Smart Access

An Android app written in Kotlin and Jetpack Compose for a single ESP32 access controller. It provides Wi-Fi status and control, paired Bluetooth door unlocking, five relay switches, a 0–100% fan slider, and an offline Room access history. No sample data is presented as real device events.

## Downloads

- [Installable debug APK](https://github.com/farhatshinwari/ESP32access-/blob/main/downloads/ESP32-Smart-Access-debug.apk): open the file on GitHub and use **Download raw file**, then install on your Android phone.
- [Android Studio project ZIP](https://github.com/farhatshinwari/ESP32access-/blob/main/downloads/ESP32-Smart-Access.zip): download and extract it, then open the `ESP32-Smart-Access` folder in Android Studio.

These are the validated debug build and source package. The debug APK is for development and hardware testing. The project ZIP excludes build caches, machine-local SDK configuration, and Git metadata. For the latest source, use GitHub **Code → Download ZIP** or clone this repository. Rebuild the APK after changing source.

## Open in Android Studio

Open this folder in Android Studio Ladybug 2024.2.1 or newer. Install Android SDK platform 35 and build tools 35.0.0, choose JDK 17 or 21 as the Gradle JDK, and sync. The checked-in Gradle wrapper pins Gradle 8.9 with its official SHA-256; AGP 8.7.3 requires Gradle 8.9 and JDK 17 or newer. Kotlin and the Compose compiler plugin both use 2.0.21, with matching KSP 2.0.21-1.0.28. Minimum Android version: Android 8 (API 26).

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Install from Android Studio or with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

For Room persistence instrumentation tests, connect an Android device or start an emulator, then run `./gradlew connectedDebugAndroidTest`. JVM tests cover command boundaries, RFID parsing, HTTP commands, negative acknowledgements, and server errors. Instrumentation tests exercise Room persistence, deduplication, ordering, and deletion.

## Connect the ESP32

1. Flash firmware implementing [the protocol](docs/ESP32_PROTOCOL.md). This repository contains the Android client; no firmware was supplied.
2. Connect the phone to the ESP32 access point or the same Wi-Fi LAN. In **Connection**, enter the device base URL (default `http://192.168.4.1`), then tap **Connect & sync**. This reads device state and RFID events before enabling Wi-Fi controls.
3. Pair the ESP32 in Android Bluetooth settings. In **Connection**, tap **Load paired devices**, grant Nearby devices permission on Android 12+, and select the ESP32. In **Controls**, use **Unlock over Bluetooth**. Confirm the physical unlock action in the dialog.
4. Use the five relay switches and fan slider. State changes are acknowledged and then reread from the device. Failures appear in the status message; reconnect with **Refresh device status**.
5. In **Access logs**, sync RFID events. Events are stored locally, sorted newest first, and deduplicated by globally unique firmware event ID. Successful manual unlock acknowledgements also appear with their transport. Local deletion requires confirmation; remote logs can reappear on the next sync.

Bluetooth uses **Classic Bluetooth SPP**, not BLE. It works with original ESP32 variants supporting Classic Bluetooth. ESP32-C3/S3 BLE-only boards need a BLE firmware protocol and a corresponding client transport. The app uses already paired devices and does not scan or request location permissions. Enable and pair Bluetooth through Android settings. Wi-Fi network credentials are managed by Android, not stored by this app.

## Architecture

- `MainActivity`: Compose Controls, Access logs, and Connection screens; permission request and unlock/delete confirmations.
- `AccessViewModel`: lifecycle-aware UI state, persisted connection settings, serialized operations, Room history.
- `WifiClient`: bounded HTTP requests, explicit acknowledgements, and Wi-Fi network binding for access points without Internet.
- `BluetoothClient`: paired SPP connection with a 10-second connection/ack timeout and cancellation cleanup.
- `Protocol`: validated JSON command encoding and response parsing.
- `AccessDatabase`: Room DAO, offline Flow, generated versioned schema.

The app only records manual unlock success after device acknowledgement. It does not infer door position from command success, and it does not issue automatic unlock retries.

## Deployment boundaries

This is a working client for the documented firmware contract, not a verified integration with unspecified hardware. HTTP and Classic Bluetooth provide no application-level authentication or encryption. Use a trusted private network and paired device for development. Before deploying on a real access-controlled door, define firmware-side authorization and authenticated transport, and match the client to that contract. HTTPS is supported using Android's trusted certificates; TLS verification is never disabled. Local logs are in the app sandbox and Android backup is disabled.

## Cloud build environment

Run `scripts/setup-cloud.sh` in this cloud workspace to prepare the SDK and run build checks. It installs a checksum-verified Temurin JDK 21 and needs Python 3.12+, curl, and standard CA trust; downloads are verified against official metadata. Cloud caches are outside the checkout, and `local.properties` is ignored. Future tasks should use the existing isolated checkout without creating a worktree.

No phone, ESP32, or emulator is assumed to be attached. See `docs/VALIDATION.md` for the actual checks run in this environment.
