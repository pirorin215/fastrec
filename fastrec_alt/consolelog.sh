#!/bin/sh

CAT_ACTIVE=false
DEVICE_PATTERN="/dev/cu.usbmodem* /dev/tty.usbmodem*"

while true; do
  DEVICE_PRESENT=false
  for pattern in $DEVICE_PATTERN; do
    if ls $pattern 1> /dev/null 2>&1; then
      DEVICE_PRESENT=true
      break
    fi
  done

  UPLOAD_RUNNING=false
  if pgrep -f "arduino-cli upload" > /dev/null; then
    UPLOAD_RUNNING=true
  fi

  if $DEVICE_PRESENT && ! $UPLOAD_RUNNING; then
    # Should be active
    if ! $CAT_ACTIVE; then
      # デバイスを見つけて cat 開始
      DEVICE=$(ls $DEVICE_PATTERN 2>/dev/null | head -n 1)
      echo "--- cat started at $(date) ---"
      CAT_ACTIVE=true
    fi
    cat "$(ls $DEVICE_PATTERN 2>/dev/null | head -n 1)"
  else
    # Should be inactive
    if $CAT_ACTIVE; then
      echo ""
      echo "--- cat stopped at $(date) ---"
      CAT_ACTIVE=false
    fi
    sleep 0.1
  fi
done
