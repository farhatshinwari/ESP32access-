# ESP32 firmware contract

The firmware must implement this contract or the client transports must be adapted to its actual API. These endpoints are an explicit integration proposal because the initial repository was empty.

## Wi-Fi

The app uses the phone's current network. Base URL is user-configurable; endpoints are absolute paths on that origin. Requests use JSON and have bounded connection/read/call timeouts. Device errors must return a non-2xx HTTP status or `{"ok":false}`. Redirects and HTML login pages are not API responses.

- `GET /api/status` → `{"relays":[false,false,false,false,false],"fan":0}`. Exactly five booleans; fan is an integer from 0 to 100.
- `GET /api/logs` → `{"logs":[{"id":"device1-event-42","uid":"04A12BCD","granted":true,"timestamp":1791633600000}]}`. IDs must be globally unique across devices and reboots. Timestamp is Unix epoch **milliseconds**; synchronize the ESP32 clock. Empty history is `{"logs":[]}`. Return retained events on each request; the phone deduplicates repeated IDs.
- `POST /api/command` accepts one of the following commands and returns `{"ok":true}` only after accepting the physical action:

```json
{"action":"unlock"}
{"action":"relay","index":1,"on":true}
{"action":"fan","speed":75}
```

Relay indices are 1–5. Fan percentage is 0–100; firmware maps it to its PWM output. Door unlock must be a firmware-controlled timed pulse with a safe timeout, never a permanently latched unlock. Status reread after relay/fan commands is the source of truth. Firmware must validate ranges and enforce access policy independently of the client.

## Bluetooth

Expose Classic Bluetooth Serial Port Profile with UUID `00001101-0000-1000-8000-00805F9B34FB`. Pair with Android first. The client sends UTF-8 `{"action":"unlock"}` followed by LF (`\n`). Firmware replies with `{"ok":true}\n` or `{"ok":false}\n`. The client closes the socket after acknowledgement, failure, or timeout; the reply must arrive within 10 seconds and stay below 4096 characters. A lost acknowledgement cannot prove whether a physical action occurred; the app reports failure and never retries automatically.

RFID grant/deny decisions are made by firmware and imported via Wi-Fi. Bluetooth is only used for manual unlocking in this app. Pairing does not substitute for a production authorization protocol.
