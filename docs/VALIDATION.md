# Validation — 2026-10-10

Verified in the current Linux cloud instance with Temurin JDK 21.0.12.1, Gradle 8.9, AGP 8.7.3, Kotlin/Compose compiler 2.0.21, KSP 2.0.21-1.0.28, Android SDK 35, and build tools 35.0.0.

The final clean build ran:

```sh
./gradlew --max-workers=4 clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

Result: **BUILD SUCCESSFUL**, 84 tasks executed.

| Check | Result |
| --- | --- |
| Kotlin/Java compilation and Room schema generation | Passed |
| Debug app APK | Built |
| JVM protocol tests | 4 passed, 0 failed, 0 skipped |
| JVM HTTP integration tests against MockWebServer | 3 passed, 0 failed, 0 skipped |
| Android lint | 0 errors, 0 warnings |
| Room instrumentation test APK | Compiled and packaged |
| APK signing verification | Passed, APK Signature Scheme v2 |
| Manifest inspection | Correct app ID, min SDK 26, target SDK 35 |
| Cloud setup script | Executed successfully and rerun successfully |
| Room instrumentation execution | Unrun: no connected Android device or emulator |
| Real Wi-Fi/Bluetooth/relay/fan/RFID behavior | Unrun: no ESP32 hardware or firmware supplied |

The JVM tests exercise JSON command encoding, invalid relay/fan ranges, granted and denied RFID records, device-state parsing, HTTP endpoint routing, POST contents, positive and negative acknowledgements, and HTTP server failures. The instrumentation test is ready to exercise database reopen/persistence, duplicate IDs, ordering, and local deletion on an Android device.

Generated APKs are under `app/build/outputs/apk/`. Test XML/HTML and lint reports are under `app/build/test-results/` and `app/build/reports/`; these are ignored build outputs. The final cloud command log is `/workspace/tooling/validation.log`.

Non-failing build/tool messages include deprecated `ConnectivityManager.allNetworks` (used for the API 26-compatible Wi-Fi transport), cloud analytics' read-only default home directory, an SDK command-line metadata version warning, and packaging an AndroidX native library without stripping it. TLS and artifact checksums remain enabled. No connected-device success is claimed.

Reusable `install_script` and `start_skill` configuration fields were saved as an environment draft. Publication and validation in a newly restored task have not been performed. Review and save the draft in environment settings, then publish if you want to retain this cloud setup.
