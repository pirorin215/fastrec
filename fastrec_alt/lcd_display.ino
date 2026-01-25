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


// Draw a single digit or colon using custom font (FONT_WIDTH x FONT_HEIGHT pixels)
void drawCustomChar(uint8_t x, uint8_t y, uint8_t charIndex) {
  if (charIndex > 10) return;  // Font has 11 characters (0-9 and :)

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

void displayStatus(const char* msg) {

  char line1[MAX_CHARS_PER_LINE+1];

  if (isBLEConnected()) {
    std::string displayBleCommand = "BLE ";
    if (!g_lastBleCommand.empty()) {
      // Ensure the command fits, leaving space for "BLE "
      int remainingChars = MAX_CHARS_PER_LINE - displayBleCommand.length();
      if (g_lastBleCommand.length() > remainingChars) {
        displayBleCommand += g_lastBleCommand.substr(0, remainingChars);
      } else {
        displayBleCommand += g_lastBleCommand;
      }
    }
    strncpy(line1, displayBleCommand.c_str(), MAX_CHARS_PER_LINE);
    line1[MAX_CHARS_PER_LINE] = '\0';
  } else {
    const char* appStateToDisplay = appStateStrings[g_currentAppState];
    snprintf(line1, sizeof(line1), "% -*s", MAX_CHARS_PER_LINE, appStateToDisplay);
  }
  displayLine(0, line1); 
 
  // 2行目: フラッシュメモリ空き容量と電池残量
  char line2[MAX_CHARS_PER_LINE+1];

  // Calculate Battery Level (BL)
  int batteryLevel = (int)(((g_currentBatteryVoltage - BAT_VOL_MIN) / 1.0) * 100);
  if (batteryLevel < 0) batteryLevel = 0;
  if (batteryLevel > 100) batteryLevel = 100;
 
  // FS display (no decimal)
  int fsUsage = (int)ceil(getLittleFSUsagePercentage()); 

  char fsUsageStr[7];
  if (countAudioFiles() == 0) {
    fsUsageStr[0] = '\0';
  } else {
    snprintf(fsUsageStr, sizeof(fsUsageStr), "FS:%3d", fsUsage);
  }
  snprintf(line2, sizeof(line2), "%6s BL:%3d", fsUsageStr, batteryLevel);
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

  // Font configuration
  const uint8_t CHAR_SPACING = 1;   // Spacing between characters in pixels
  const uint8_t STEP = FONT_WIDTH + CHAR_SPACING;  // Total step per character

  // Calculate position (centered in 72x40 display)
  // 5 chars * (13 + 2) pixels = 75 pixels total width
  uint8_t startX = 0;  // Slight offset from left edge
  uint8_t startY = 2;  // Slight offset from top to avoid clipping

  // Draw each character with automatic spacing calculation
  uint8_t charIndex = 0;
  drawCustomChar(startX + charIndex * STEP, startY, hours / 10);       // H1
  charIndex++;
  drawCustomChar(startX + charIndex * STEP, startY, hours % 10);      // H2
  charIndex++;
  drawCustomChar(startX + charIndex * STEP, startY, 10);              // :
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

void updateDisplay(const char* msg) {
  display.clear();
  switch (g_currentAppState) {

    case SETUP:
      displaySetup();
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
