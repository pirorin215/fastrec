/**
 * HID (Human Interface Device) Implementation for FastRec
 *
 * Provides BLE HID keyboard/media key functionality for GPIO switches.
 * Uses NimBLEHIDDevice for ESP32 BLE HID implementation.
 *
 * Switches:
 * - GPIO4 (HID_VOL_UP_GPIO): Volume Up (0xE9)
 * - GPIO6 (HID_VOL_DN_GPIO): Volume Down (0xEA)
 */

#include "fastrec_alt.h"

#include "NimBLEHIDDevice.h"

// --- HID Report Map for Consumer Page (Media Keys) + Keyboard Page ---
// This report map defines:
// - Consumer Control (Volume, Play/Pause, etc.) - Report ID 1
// - Keyboard (Standard 6KRO) - Report ID 2
static const uint8_t hidReportMap[] = {
    // --- Consumer Page (Media Keys) - Report ID 1 ---
    0x05, 0x0C,        // Usage Page (Consumer)
    0x09, 0x01,        // Usage (Consumer Control)
    0xA1, 0x01,        // Collection (Application)
    0x85, 0x01,        //   Report ID (1)
    0x15, 0x00,        //   Logical Minimum (0)
    0x25, 0x01,        //   Logical Maximum (1)
    0x75, 0x01,        //   Report Size (1)
    0x95, 0x10,        //   Report Count (16 bits)
    0x09, 0xE9,        //   Usage (Volume Up)
    0x09, 0xEA,        //   Usage (Volume Down)
    0x09, 0xE2,        //   Usage (Mute)
    0x09, 0xCD,        //   Usage (Play/Pause)
    0x09, 0xB6,        //   Usage (Next Track)
    0x09, 0xB5,        //   Usage (Previous Track)
    0x81, 0x02,        //   Input (Data, Var, Abs)
    0xC0,              // End Collection

    // --- Keyboard Page - Report ID 2 (Standard 6KRO Keyboard) ---
    0x05, 0x01,        // Usage Page (Generic Desktop)
    0x09, 0x06,        // Usage (Keyboard)
    0xA1, 0x01,        // Collection (Application)
    0x85, 0x02,        //   Report ID (2)

    // Modifier byte (8 bits: Left Ctrl, Shift, Alt, GUI + Right Ctrl, Shift, Alt, GUI)
    0x05, 0x07,        //   Usage Page (Keyboard/Keypad)
    0x19, 0xE0,        //   Usage Minimum (Left Control)
    0x29, 0xE7,        //   Usage Maximum (Right GUI)
    0x15, 0x00,        //   Logical Minimum (0)
    0x25, 0x01,        //   Logical Maximum (1)
    0x75, 0x01,        //   Report Size (1 bit)
    0x95, 0x08,        //   Report Count (8 bits)
    0x81, 0x02,        //   Input (Data, Var, Abs)

    // Reserved byte (1 byte) - must be padding
    0x95, 0x01,        //   Report Count (1 byte)
    0x75, 0x08,        //   Report Size (8 bits)
    0x81, 0x03,        //   Input (Cnst, Var, Abs)

    // Key codes (6 bytes) - Array format
    0x95, 0x06,        //   Report Count (6 bytes)
    0x75, 0x08,        //   Report Size (8 bits)
    0x15, 0x00,        //   Logical Minimum (0)
    0x26, 0xFF, 0x00,  //   Logical Maximum (255)
    0x05, 0x07,        //   Usage Page (Keyboard/Keypad)
    0x19, 0x00,        //   Usage Minimum (Reserved/No event)
    0x29, 0xFF,        //   Usage Maximum (255)
    0x81, 0x00,        //   Input (Data, Array, Abs)

    0xC0               // End Collection
};

// --- HID Global Variables ---
NimBLEHIDDevice* g_hidDevice = nullptr;
NimBLECharacteristic* g_hidInputReport = nullptr;      // Report ID 1 (Consumer)
NimBLECharacteristic* g_hidKeyboardInputReport = nullptr;  // Report ID 2 (Keyboard)
NimBLECharacteristic* g_hidOutputReport = nullptr;

bool g_hidInitialized = false;

#ifdef USE_HID_FOR_AI_BUTTON
#define HID_SWITCH_COUNT 3
#else
#define HID_SWITCH_COUNT 2
#endif

// HID Switch configuration
// GPIOピンとHIDキーコードの対応付けを定義
HidSwitch hidSwitches[HID_SWITCH_COUNT] = {
  {HID_VOL_UP_GPIO, LOW, 0, HID_STATE_IDLE, HID_VOLUME_UP},
  {HID_VOL_DN_GPIO, LOW, 0, HID_STATE_IDLE, HID_VOLUME_DOWN}
#ifdef USE_HID_FOR_AI_BUTTON
  ,{AI_BUTTON_GPIO, LOW, 0, HID_STATE_IDLE, HID_RIGHT_ARROW}
#endif
};

// --- HID Initialization ---
void initHID() {
    applog("========================================");
    applog("HID Initialization (Consumer Page)");
    applog("========================================");

    // Check if BLE server is initialized
    if (pBLEServer == nullptr) {
        applog("ERROR: BLE Server not initialized. Cannot initialize HID.");
        return;
    }

    // Create HID device
    g_hidDevice = new NimBLEHIDDevice(pBLEServer);

    // Set manufacturer and device info
    g_hidDevice->setManufacturer("pirorin215");
    g_hidDevice->setPnp(0x02, 0x05AC, 0x8200, 0x0100);  // Apple VID/PID for compatibility
    g_hidDevice->setHidInfo(0x00, 0x01);  // Country=0, Flags=RemoteWake|NormallyConnectable

    // Set report map
    g_hidDevice->setReportMap((uint8_t*)hidReportMap, sizeof(hidReportMap));

    // Get input report characteristic (Report ID 1 - Consumer)
    g_hidInputReport = g_hidDevice->getInputReport(1);
    if (g_hidInputReport) {
        g_hidInputReport->setCallbacks(nullptr);
        applog("HID Consumer Input Report (ID=1) configured");
    } else {
        applog("ERROR: Failed to get HID Consumer Input Report");
        return;
    }

    // Get keyboard input report characteristic (Report ID 2 - Keyboard)
    g_hidKeyboardInputReport = g_hidDevice->getInputReport(2);
    if (g_hidKeyboardInputReport) {
        g_hidKeyboardInputReport->setCallbacks(nullptr);
        applog("HID Keyboard Input Report (ID=2) configured");
    } else {
        applog("ERROR: Failed to get HID Keyboard Input Report");
        return;
    }

    // Get output report characteristic
    g_hidOutputReport = g_hidDevice->getOutputReport(1);

    // Start HID services
    g_hidDevice->getBatteryService()->start();
    g_hidDevice->getHidService()->start();
    g_hidDevice->getDeviceInfoService()->start();

    g_hidInitialized = true;

    applog("========================================");
    applog("✅ HID Service initialized successfully!");
    applog("  GPIO%d: Volume Up (0x%04X)", HID_VOL_UP_GPIO, HID_VOLUME_UP);
    applog("  GPIO%d: Volume Down (0x%04X)", HID_VOL_DN_GPIO, HID_VOLUME_DOWN);
#ifdef USE_HID_FOR_AI_BUTTON
    applog("  GPIO%d: Right Arrow (0x%04X)", AI_BUTTON_GPIO, HID_RIGHT_ARROW);
#endif
    applog("========================================");

    // 初期ピン状態を実際のピン状態に合わせる
    // これにより、起動時の誤動作を防止
    for (int i = 0; i < HID_SWITCH_COUNT; i++) {
        hidSwitches[i].pinState = digitalRead(hidSwitches[i].gpio);
        hidSwitches[i].state = HID_STATE_IDLE;
        hidSwitches[i].lastDebounceTime = millis();
        applog("HID SW%d init: GPIO%d, pinState=%d, keyCode=0x%04X",
               i, hidSwitches[i].gpio, hidSwitches[i].pinState, hidSwitches[i].keyCode);
    }

    applog("HID Report Map Info:");
    applog("  Report ID 1: Consumer Control (16 bits)");
    applog("  Report ID 2: Keyboard (6KRO + Modifier)");
    applog("  Keyboard report format: [Modifier(1) + Reserved(1) + Keys(6)] = 8 bytes");
    applog("  Consumer report format: [Data(2)] = 2 bytes");
}

// --- HID Switch Processing ---
// bikeclockに合わせてデバウンス処理を改善
void processHidSwitches() {
    if (!g_hidInitialized) {
        return;
    }

    unsigned long currentTime = millis();

    for (int i = 0; i < HID_SWITCH_COUNT; i++) {
        HidSwitch* sw = &hidSwitches[i];
        uint8_t reading = digitalRead(sw->gpio);

        // Only apply debounce when switch is released (HIGH->LOW transition)
        if (reading == LOW && sw->pinState == HIGH) {
            // Switch released - update debounce time
            sw->lastDebounceTime = currentTime;
        }
        // Always update pinState for next comparison
        sw->pinState = reading;

        // Skip debounce check during press to ensure quick response
        bool skipDebounce = (sw->state == HID_STATE_PRESS);

        if (skipDebounce || (currentTime - sw->lastDebounceTime > HID_DEBOUNCE_DELAY_MS)) {
            // State machine
            switch (sw->state) {
                case HID_STATE_IDLE:
                    // Check if switch is pressed (HIGH for active-high)
                    if (reading == HIGH) {
                        applog("HID SW%d P: GPIO%d=%d, KeyCode=0x%04X",
                               i + 1, sw->gpio, reading, sw->keyCode);
                        sendHidKeyPress(sw->keyCode);
                        sw->state = HID_STATE_PRESS;

                        // HID操作時にアクティビティタイマーを更新してスリープを延期
                        extern unsigned long g_lastActivityTime;
                        g_lastActivityTime = millis();
                    }
                    break;

                case HID_STATE_PRESS:
                    if (reading == LOW) {
                        // Switch released
                        applog("HID SW%d R: GPIO%d=%d", i + 1, sw->gpio, reading);
                        sendHidKeyRelease(sw->keyCode);
                        sw->state = HID_STATE_IDLE;

                        // キーリリース時もアクティビティタイマーを更新
                        extern unsigned long g_lastActivityTime;
                        g_lastActivityTime = millis();
                    }
                    break;
            }
        }
    }
}

// --- HID Key Send Functions ---
void sendHidKeyPress(uint16_t keyCode) {
    if (!g_hidInitialized) {
        return;
    }

    // Keyboard Page keys (0x00-0xFF) vs Consumer Page keys (0xE0-0xFF, etc.)
    if (keyCode < 0xE0 || keyCode == 0x4F) {
        // Keyboard Page (Report ID 2) - Standard 6KRO keyboard format
        // bikeclockと同じフォーマットを使用: Modifier + 6 keys
        if (g_hidKeyboardInputReport) {
            uint8_t report[8];  // Modifier (1) + Reserved (1) + Keys (6)
            report[0] = 0x00;   // Modifier byte (no Ctrl, Shift, Alt, GUI)
            report[1] = 0x00;   // Reserved byte (padding)
            report[2] = (uint8_t)keyCode;  // Key code 1
            report[3] = 0x00;   // Key code 2
            report[4] = 0x00;   // Key code 3
            report[5] = 0x00;   // Key code 4
            report[6] = 0x00;   // Key code 5
            report[7] = 0x00;   // Key code 6

            // デバッグ: 16進数ダンプ
            applog("HID Keyboard sending: KeyCode=0x%02X", (uint8_t)keyCode);
            applog("HID Keyboard report: %02X %02X %02X %02X %02X %02X %02X %02X",
                   report[0], report[1], report[2], report[3], report[4],
                   report[5], report[6], report[7]);

            g_hidKeyboardInputReport->setValue(report, sizeof(report));
            g_hidKeyboardInputReport->notify();
        }
    } else {
        // Consumer Page (Report ID 1)
        if (g_hidInputReport) {
            uint8_t report[2];  // Key data (2 bytes)

            // Consumer Page key codes (0x0C00-0x0CFF)
            // Convert to report format (little-endian 16-bit)
            report[0] = keyCode & 0xFF;         // Low byte
            report[1] = (keyCode >> 8) & 0xFF;  // High byte

            // デバッグ: 16進数ダンプ
            applog("HID Consumer sending: KeyCode=0x%04X", keyCode);
            applog("HID Consumer report: %02X %02X", report[0], report[1]);

            g_hidInputReport->setValue(report, sizeof(report));
            g_hidInputReport->notify();
        }
    }

    // Set flag to display key code on OLED (非同期表示でパフォーマンス向上)
    extern bool g_displayingKeyCode;
    extern uint16_t g_displayingKeyCodeValue;
    extern unsigned long g_keyCodeDisplayEndTime;
    g_displayingKeyCode = true;
    g_displayingKeyCodeValue = keyCode;
    g_keyCodeDisplayEndTime = millis() + 300;  // Display for 300ms (短縮してパフォーマンス向上)
}

void sendHidKeyRelease(uint16_t keyCode) {
    if (!g_hidInitialized) {
        return;
    }

    // bikeclockに合わせて、ConsumerとKeyboardの両方を常にリリースする
    // これにより「押されっぱなしになる」問題を修正

    // Release consumer keys (Report ID 1)
    if (g_hidInputReport) {
        uint8_t report[2];
        report[0] = 0x00;
        report[1] = 0x00;

        applog("HID Consumer release: %02X %02X", report[0], report[1]);

        g_hidInputReport->setValue(report, sizeof(report));
        g_hidInputReport->notify();
    }

    // Release keyboard keys (Report ID 2)
    if (g_hidKeyboardInputReport) {
        uint8_t report[8];
        report[0] = 0x00;  // Modifier byte
        report[1] = 0x00;  // Reserved byte
        report[2] = 0x00;  // No keys
        report[3] = 0x00;
        report[4] = 0x00;
        report[5] = 0x00;
        report[6] = 0x00;
        report[7] = 0x00;

        applog("HID Keyboard release: %02X %02X %02X %02X %02X %02X %02X %02X",
               report[0], report[1], report[2], report[3], report[4],
               report[5], report[6], report[7]);

        g_hidKeyboardInputReport->setValue(report, sizeof(report));
        g_hidKeyboardInputReport->notify();
    }
}
