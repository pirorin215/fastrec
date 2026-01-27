#include "fastrec_alt.h"
#include <SSD1315.h>
#include "custom_font.h"

#include <cmath>

SSD1315 display;

// Font configuration (must match custom_font.h settings)
const uint8_t FONT_WIDTH = 13;    // Width of each character in pixels
const uint8_t FONT_HEIGHT = 28;   // Height of each character in rows

const int MAX_CHARS_PER_LINE = 15;
const int FONT_SIZE = 5;
const int LINE_HEIGHT = 10;  // Assuming 10 pixels per line for font size 5
const int MAX_BLE_CMD_DISPLAY_LEN = 7;


// Draw a single digit or colon using custom font (FONT_WIDTH x FONT_HEIGHT pixels)
void drawCustomChar(uint8_t x, uint8_t y, uint8_t charIndex) {
  if (charIndex > 17) return;  // Font has 18 characters (0-9, :, and 7 weekday symbols)

  const uint8_t *font = &largeFont[charIndex][0];

  // Font is FONT_WIDTH pixels wide, FONT_HEIGHT pixels tall
  // Stored as FONT_HEIGHT rows, 2 bytes per row (16 bits, using FONT_WIDTH bits)
  for (uint8_t row = 0; row < FONT_HEIGHT; row++) {
    uint8_t byte1 = pgm_read_byte(font + row * 2);
    uint8_t byte2 = pgm_read_byte(font + row * 2 + 1);
    uint16_t combined = ((uint16_t)byte1 << 8) | byte2;

    // Draw FONT_WIDTH pixels for this row
    // Font data is stored in upper bits (bits 15 down to 16-FONT_WIDTH)
    for (uint8_t col = 0; col < FONT_WIDTH; col++) {
      uint8_t bitPos = 15 - col;  // Extract from MSB
      bool pixel = (combined >> bitPos) & 0x01;
      if (pixel) {
        display.drawPixel(x + col, y + row, 1);
      }
    }
  }
}

// Draw battery level bar at top-right (20x10 pixels)
// Divided into 5 segments with 1px gaps between
void drawBatteryBar(int batteryLevel) {
  const uint8_t barWidth = 20;
  const uint8_t barHeight = 8;
  const uint8_t barX = SSD1315_WIDTH - barWidth;  // Right side of display
  const uint8_t barY = 0;

  // Each segment is 3px wide with 1px gap between (total: 3*5 + 1*4 = 19px, aligned to right)
  const uint8_t segmentWidth = 3;
  const uint8_t gapWidth = 1;
  const uint8_t numSegments = 5;

  // Calculate how many segments to fill
  // 0-9%: 0, 10-29%: 1, 30-49%: 2, 50-69%: 3, 70-89%: 4, 90-100%: 5
  uint8_t segmentsToFill = (batteryLevel + 10) / 20;
  if (segmentsToFill > numSegments) segmentsToFill = numSegments;

  // Draw segments from right to left
  for (uint8_t i = 0; i < numSegments; i++) {
    if (i < segmentsToFill) {
      // Calculate segment position (right to left)
      uint8_t segX = barX + barWidth - 1 - (i * (segmentWidth + gapWidth)) - segmentWidth + 1;

      // Draw filled segment
      display.drawRect(segX, barY, segX + segmentWidth - 1, barY + barHeight - 1, true);
    }
  }
}

void setLcdBrightness(uint8_t brightness) {
  display.setBrightness(brightness);
}

void initSSD() {
  applog("initSSD");

  Wire.begin(LCD_SDA_GPIO, LCD_SCL_GPIO, 100000L);

  display.begin();
  display.setRotation(2);       // rotate 180 degrees
  display.setBrightness(0x00);

  // Update display with initial status
  float usagePercentage = getLittleFSUsagePercentage();
  updateDisplay("");
}

void displaySleep(bool flag) {
  display.clear();
  display.sleep(flag);
}

void displayLine(uint8_t lineNumber, const char* text) {
  if(lineNumber > 3) {
    return;
  }
  char display_buffer[MAX_CHARS_PER_LINE + 1];  // +1 for null terminator
  strncpy(display_buffer, text, MAX_CHARS_PER_LINE);
  display_buffer[MAX_CHARS_PER_LINE] = '\0';  // Ensure null-termination

  display.drawString(0, lineNumber * LINE_HEIGHT, display_buffer, FONT_SIZE);
}

// Display top row: command/status, music icon, battery bar (shared by DEV and NORMAL modes)
void displayTopRow() {
  // Calculate Battery Level
  int batteryLevel = (int)(((g_currentBatteryVoltage - BAT_VOL_MIN) / 1.0) * 100);
  batteryLevel = constrain(batteryLevel, 0, 100);

  char line1[MAX_CHARS_PER_LINE + 1];
  int offset = 0;

  // Build status text
  if (isBLEConnected()) {
    std::string displayBleCommand = "BLE";
    if (!g_lastBleCommand.empty()) {
      displayBleCommand = g_lastBleCommand.substr(0, MAX_BLE_CMD_DISPLAY_LEN);
    }
    offset += snprintf(line1 + offset, sizeof(line1) - offset, "%s", displayBleCommand.c_str());
  } else {
    const char* appStateToDisplay = appStateStrings[g_currentAppState];
    offset += snprintf(line1 + offset, sizeof(line1) - offset, "%s", appStateToDisplay);
  }

  // Append '*' if audio files exist
  if (countAudioFiles() > 0 && offset < sizeof(line1) - 1) {
    snprintf(line1 + offset, sizeof(line1) - offset, "*");
  }

  displayLine(0, line1);

  // Draw battery bar at top-right (special rendering)
  drawBatteryBar(batteryLevel);
}

void displayStatus(const char* msg) {

  // Display top row (shared with normal mode)
  displayTopRow();

  // 2行目: フラッシュメモリ空き容量
  char line2[MAX_CHARS_PER_LINE+1];

  // FS display (no decimal)
  int fsUsage = (int)ceil(getLittleFSUsagePercentage());

  char fsUsageStr[7];
  if (countAudioFiles() == 0) {
    fsUsageStr[0] = '\0';
  } else {
    snprintf(fsUsageStr, sizeof(fsUsageStr), "FS:%3d", fsUsage);
  }
  snprintf(line2, sizeof(line2), "%s", fsUsageStr);
  displayLine(1, line2);

  // 3行目:時刻
  char line3[MAX_CHARS_PER_LINE+1];
  getFormattedRtcTime(line3, sizeof(line3));
  displayLine(2, line3);

  // 4行目
  char line4[MAX_CHARS_PER_LINE+1];
  if(msg[0] == '\0') {
    line4[0] = '\0';
  } else {
    strncpy(line4, msg, MAX_CHARS_PER_LINE);
  }
  line4[MAX_CHARS_PER_LINE] = '\0';
  displayLine(3, line4);
}

void displayNormalMode() {
  // Display top row (shared with dev mode)
  displayTopRow();

  struct tm timeinfo;
  if (!getValidRtcTime(&timeinfo)) {
    // RTC not set - display dashes
    drawCustomChar(16, 2, 10);  // :
    drawCustomChar(30, 2, 10);  // :
    return;
  }

  // Format time as "HH:MM"
  uint8_t hours = timeinfo.tm_hour;
  uint8_t minutes = timeinfo.tm_min;

  // Get weekday (tm_wday: 0=Sunday, 1=Monday, ..., 6=Saturday)
  uint8_t weekday = timeinfo.tm_wday;

  // Weekday symbols are at indices 11-17 (0=Sun->11, 6=Sat->17)
  uint8_t weekdaySymbol = weekday + 11;

  // Font configuration
  const uint8_t CHAR_SPACING = 2;   // Spacing between characters in pixels
  const uint8_t STEP = FONT_WIDTH + CHAR_SPACING;  // Total step per character

  // Calculate position (centered in 72x40 display)
  // 5 chars * (13 + 2) pixels = 75 pixels total width
  uint8_t startX = 0;  // Slight offset from left edge
  uint8_t startY = 12;  // Slight offset from top to avoid clipping

  // Draw each character with automatic spacing calculation
  uint8_t charIndex = 0;
  drawCustomChar(startX + charIndex * STEP, startY, hours / 10);       // H1
  charIndex++;
  drawCustomChar(startX + charIndex * STEP, startY, hours % 10);      // H2
  charIndex++;
  drawCustomChar(startX + charIndex * STEP, startY, weekdaySymbol);   // 曜日記号
  charIndex++;
  drawCustomChar(startX + charIndex * STEP, startY, minutes / 10);    // M1
  charIndex++;
  drawCustomChar(startX + charIndex * STEP, startY, minutes % 10);    // M2
}

void displaySetup() {
  displayLine(0, "setting.ini");
  displayLine(1, "not found.");
  displayLine(2, "Please send");
  displayLine(3, "from BLE tool.");
}

void displayServiceMode() {
  // Font configuration
  const uint8_t CHAR_SPACING = 2;   // Spacing between characters in pixels
  const uint8_t STEP = FONT_WIDTH + CHAR_SPACING;  // Total step per character

  // Calculate starting position
  uint8_t startX = 0;
  uint8_t startY = 12;

  // Display based on current mode
  if (g_serviceDisplayMode == 0) {
    // Display: 0 1 2 3 4
    uint8_t charIndex = 0;
    drawCustomChar(startX + charIndex * STEP, startY, 0);  // 0
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 1);  // 1
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 2);  // 2
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 3);  // 3
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 4);  // 4
  } else if (g_serviceDisplayMode == 1) {
    // Display: 5 6 7 8 9
    uint8_t charIndex = 0;
    drawCustomChar(startX + charIndex * STEP, startY, 5);  // 5
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 6);  // 6
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 7);  // 7
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 8);  // 8
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 9);  // 9
  } else {
    // Modes 2-8: Display weekday symbols
    uint8_t weekdaySymbol = g_serviceDisplayMode + 9;  // 2→11(Sun), 8→17(Sat)
    uint8_t charIndex = 0;
    drawCustomChar(startX + charIndex * STEP, startY, 1);  // 1
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 2);  // 2
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, weekdaySymbol);
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 3);  // 3
    charIndex++;
    drawCustomChar(startX + charIndex * STEP, startY, 4);  // 4
  }
}

void updateDisplay(const char* msg) {
  display.clear();
  switch (g_currentAppState) {

    case SETUP:
      displaySetup();
      break;
    case SERVICE:
      displayServiceMode();
      break;
    default:
      if (DEV_MODE) {
        displayStatus(msg); // Development mode: 4-line display
      } else {
        displayNormalMode(); // Normal mode: large font time display
      }
      break;
  }
  display.display();
}
