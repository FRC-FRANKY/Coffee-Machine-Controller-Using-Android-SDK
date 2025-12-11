## ESP32 Coffee Controller (Cuppa)

### Prerequisites
- Arduino IDE 2.x or PlatformIO
- ESP32 board support installed
- Libraries:
  - NTPClient
  - Firebase ESP32 Client (by mobizt)

### Configure
Edit `firmware/esp32_cuppa/esp32_cuppa.ino`:
- Set `WIFI_SSID` and `WIFI_PASSWORD`
- Set `FIREBASE_API_KEY` and `FIREBASE_DATABASE_URL`
- Adjust pins if your relays/sensors differ

### Flash
1. Open the `.ino` in Arduino IDE
2. Board: ESP32 Dev Module (or your specific board)
3. Select the correct COM port
4. Upload

### Firebase Data Model
```
/coffee/
  command/
    brewNow: false
  schedule/
    nextBrewEpoch: 0
  settings/
    brewMillis: 120000
  state/
    isBrewing: false
    lastBrewEpoch: 0
    waterLow: false
    waterLevelRaw: 0
  notifications/
    message: ""
  history/
    -Kxyz123: { event, success, detail, timestamp }
```

### Safety
Use proper isolation/relays for AC equipment. Proceed at your own risk.


