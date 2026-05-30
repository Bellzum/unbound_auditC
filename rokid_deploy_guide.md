# Rokid Glasses — Deploy & Mirror Guide

&#x20;

> For first-time users | Audit C Hackathon | May 30, 2026

***

## PART 1 — What You Need Before Starting

### Hardware

- \[ ] Rokid Glasses device
- \[ ] Rokid dev cable (NOT the charging cable — ask Rokid team for the data cable)
- \[ ] Android phone with Hi Rokid App installed
- \[ ] Mac laptop with Android Studio installed

### Software to install NOW

```
# Install Android Studio if not installed
# Download from: https://developer.android.com/studio

# Install ADB tools (comes with Android Studio, or install separately)
brew install android-platform-tools

# Install scrcpy for screen mirroring
brew install scrcpy

# Verify ADB works
adb version

```

***

## PART 2 — First Time Glasses Setup

### Step 1 — Pair glasses to your phone

1. Open **Hi Rokid App** on Android phone
2. Tap **+** to add device
3. Put on glasses — follow on-screen pairing instructions
4. Connect glasses to same WiFi as your Mac laptop
   - In Hi Rokid App → Settings → WiFi
   - Connect to venue WiFi

### Step 2 — Enable ADB on glasses

1. Open **Hi Rokid App** on Android phone
2. Go to **Settings** → **Developer Options**
3. Toggle **Enable ADB** → ON
4. Glasses will show a confirmation prompt — accept it

### Step 3 — Connect glasses to Mac via dev cable

1. Use the **dev cable** (left temple data contacts)
2. Plug into your Mac USB port
3. On glasses — accept the "Allow USB debugging" prompt if it appears

### Step 4 — Verify glasses appear in ADB

```
# Run this in Terminal
adb devices

```

Expected output:

```
List of devices attached
XXXXXXXX    device

```

If you see your device listed → you are ready to deploy! ✅ If empty → check cable, check ADB enabled, try: `adb kill-server && adb start-server`

***

## PART 3 — Deploy Audit C App to Glasses

### Step 1 — Open project in Android Studio

1. Open Android Studio
2. File → Open → select `/Users/bellz_um/Desktop/Unbound/AuditC` (the Kotlin project TRAE built)
3. Wait for Gradle sync to complete (may take 2-3 min first time)

### Step 2 — Fix common Gradle issues

If Gradle sync fails:

```
# In TRAE Terminal
cd /Users/bellz_um/Desktop/Unbound/AuditC
./gradlew clean
./gradlew build

```

Paste any errors into SOLO Coder to fix.

### Step 3 — Select Rokid glasses as target device

1. In Android Studio top bar — click the device dropdown
2. You should see **Rokid Glasses** listed
3. Select it

If not listed:

```
adb devices          # confirm glasses connected
adb kill-server
adb start-server
adb devices          # try again

```

### Step 4 — Build and install

1. Click the green ▶ **Run** button in Android Studio
2. Android Studio will:
   - Compile the Kotlin app
   - Install the APK on glasses
   - Launch the app automatically
3. First build takes 3-5 minutes — subsequent builds are faster

### Step 5 — Verify app is running

- Put on glasses
- You should see the Audit C dark interface
- Header shows "AUDIT C | LAB QC"

If app crashes:

```
# Check logs in real time
adb logcat | grep -i "auditc\|error\|fatal"

```

Paste crash log into SOLO Coder to fix.

***

## PART 4 — Mirror Glasses Display to Mac (for demo presentation)

This is how you show judges what's happening inside the glasses on your laptop screen.

### Method 1 — scrcpy (recommended, easiest)

```
# Make sure glasses connected via dev cable
adb devices   # confirm device listed

# Mirror glasses screen to Mac
scrcpy

# Optional — with title and no controls
scrcpy --window-title "Audit C — Rokid Glasses View" --no-control

# Optional — record the demo at the same time
scrcpy --record demo.mp4

```

A window will open on your Mac showing exactly what's displayed on the glasses in real time.

### Method 2 — Android Studio screen mirror

1. In Android Studio → View → Tool Windows → Running Devices
2. Select your glasses device
3. Screen appears in the IDE panel

### For presentation

- Open scrcpy window on your Mac
- Connect Mac to projector/screen via HDMI
- Judges see the glasses AR display on the big screen
- You wear the glasses and interact — they watch the mirror

**Tip:** Make scrcpy window large — fullscreen if possible. The 480×640 display will be clearly visible.

***

## PART 5 — Demo Flow on the Day

### Setup (do this 15 min before demo)

```
# Terminal 1 — start backend
cd /Users/bellz_um/Desktop/Unbound
python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload

# Terminal 2 — connect glasses and start mirror
adb devices
scrcpy --window-title "Audit C — Rokid Glasses View"

```

### During demo

1. Projector shows: scrcpy window (glasses display)
2. You wear glasses and speak — judges watch the big screen
3. Say **"step one done"** → glasses show green VERIFIED ✓
4. Say **"contamination detected"** → glasses show red WARNING ⚠
5. Say **"generate report"** (or press long-press button) → report generated
6. Show the PDF report on laptop as final slide

***

## PART 6 — Troubleshooting Quick Reference

Problem

Fix

`adb devices` shows empty

Check cable is dev cable not charging cable

ADB not found

`brew install android-platform-tools`

Gradle sync fails

Paste error into SOLO Coder

App installs but crashes

`adb logcat` → paste error into SOLO Coder

scrcpy not found

`brew install scrcpy`

scrcpy shows black screen

Unlock glasses display first

Backend unreachable from glasses

Confirm same WiFi, check `adb shell ping 192.168.1.11`

Voice not recognized

Speak clearly, short commands, English

TTS not speaking

Check glasses volume — swipe right temple to adjust

***

## PART 7 — Key Commands Cheatsheet

```
# Check glasses connected
adb devices

# Install APK manually (if Android Studio not working)
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch app manually
adb shell am start -n com.auditc.glasses/.MainActivity

# View live logs
adb logcat | grep AuditC

# Mirror screen
scrcpy

# Mirror + record
scrcpy --record demo.mp4

# Restart app on glasses
adb shell am force-stop com.auditc.glasses
adb shell am start -n com.auditc.glasses/.MainActivity

# Check glasses WiFi IP (to verify same network as Mac)
adb shell ip addr show wlan0

```

***

## PART 8 — If Glasses Don't Arrive in Time

Fallback demo plan — show judges both:

1. **iPhone web demo** (already working) — show on laptop screen via Safari
2. **Kotlin app** in Android Studio emulator — Run on AVD (Android Virtual Device)
   - In Android Studio → Device Manager → Create Virtual Device
   - Choose: Phone → Pixel 4 → API 29
   - Run app on emulator — shows same UI as glasses

Tell judges:

> "We've built and tested the full glasses app. Here's the emulator version — the identical APK deploys to Rokid glasses via ADB when the hardware is available."

Judges understand hardware delays. The code quality matters more than the physical glasses.

***

*Guide prepared for Audit C team | TRAE SOLO Hackathon Tokyo 2026*
