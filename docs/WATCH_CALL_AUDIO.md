# Watch call audio: hardware decision

Naari OS currently uses an ESP32-S3 and NimBLE GATT for its phone bridge. The watch can request a call, show call state, and make Android open/place the call. It cannot act as the phone's microphone and speaker for a normal cellular call with the present radio.

Android routes normal call audio to Bluetooth devices that expose a supported calling audio profile. The ESP32-S3 supports Bluetooth LE data, but it supports neither Bluetooth Classic HFP nor Bluetooth LE Audio. Sending microphone PCM through the existing custom GATT service would not make the watch an Android call-audio endpoint and would be too fragile for emergency calling.

Supported product paths:

1. **Direct A7672S calling:** route the modem's analog/PCM voice audio to the watch microphone/speaker codec and let the modem own the call. UART remains the AT-command control link. This is the best fit when the modem is installed.
2. **Bluetooth call-audio hardware revision:** use a controller with HFP or LE Audio support, such as an ESP32 for Classic HFP or a newer LE Audio-capable controller. This keeps the Android phone as the cellular endpoint.

Until one of those paths is implemented, the UI must describe calls as controlled by the watch and spoken on the phone. It must not claim that call audio is routed through the watch.

References:

- Espressif Bluetooth audio chip comparison: <https://docs.espressif.com/projects/esp-adf/en/latest/solution-center/bluetooth-audio.html>
- Android Bluetooth call audio routing: <https://developer.android.com/develop/connectivity/bluetooth/ble-audio/telecom-api-managed-calls>
