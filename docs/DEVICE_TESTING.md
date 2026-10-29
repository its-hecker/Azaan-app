# Device test checklist

Local unit tests and a successful APK build do not establish that Android will deliver audio reliably on every device. Run this checklist on a real phone before release.

## Setup and content

- Install on Android 8 and a recent Android 15/16/17 device where available. Verify readable Arabic/Urdu shaping, system-bar insets, rotation, navigation, 200% font size, and scrolling.
- Choose Karachi, Lahore, and your actual city. Compare all three displayed times against a trusted local Shia timetable, including the chosen method and offsets.
- Enter invalid coordinates and time zones; verify clear validation and no saved invalid state.
- Request approximate and precise location separately. Verify denial, disabled location, timeout, and manual-city fallback. Ensure no location indicator remains after lookup or cancellation.
- Read all phrases: four opening takbirs; two of every following phrase, including the two closing takbirs and two final tahlils. With the optional testimony disabled there are 18 sentences; enabled there are 20.

## Audio

- Preview Arabic → Urdu → English. Confirm complete language blocks and repetition counts. Check pronunciation with a knowledgeable speaker.
- Test each translation switch, all imported recordings, mixed recordings/speech, no speech engine, missing Urdu voice, and a voice advertising unavailable data.
- Import valid MP3/M4A/OGG/WAV, invalid content, an over-eight-minute recording, and a file over 100 MB. Confirm failure leaves the prior recording intact.
- Confirm imported audio remains playable after the original file is moved, the file picker is closed, the phone is offline, and the app is restarted.
- Stop playback in the app and through its notification. Start and stop again. Confirm no second audio stream or wake lock remains.
- Receive a call or give audio focus to another app; playback should stop, not speak over the call.
- Test volume zero, low system alarm volume, silent mode, Do Not Disturb, Bluetooth, wired headphones, and screen lock. The app does not override system DND rules.

## Scheduling

- Enable notifications and exact alarms via onboarding. Deny each, then restore it; ensure alerts are never shown as ready while a prerequisite is missing.
- Turn off the master switch and each individual prayer. Verify no disabled alert plays.
- For a development timing test, adjust an upcoming prayer using the offset settings. Verify one announcement at the displayed minute with the screen off.
- Exercise Doze (`adb shell dumpsys deviceidle force-idle`), then restore normal mode (`adb shell dumpsys deviceidle unforce`). Confirm delivery and Stop behavior.
- Reboot after enabling, unlock the phone once, then check the next scheduled alert. No recording should start just because the phone booted.
- Change clock, date, zone, location, method, and offsets. Check future alarms are recalculated and a previously delivered local-date prayer does not replay.
- Force-stop Azaan: Android cancels its alarms. Reopen it and verify scheduling resumes. Test app replacement without clearing app data.
- Revoke exact-alarm access while scheduled. Reopen, verify the paused message, restore permission, and verify scheduling resumes.
- Test a delivery more than five minutes late: skip stale playback and schedule the next occurrence.

## Known version-one boundaries

- No bundled reciter audio, high-latitude substitute timetable, automatic travel detection, release signing, store publication, or guarantee against manufacturer battery restrictions.
- An imported recording is not rewritten when the optional testimony switch changes. Supply a matching recording yourself.
- A recording can be up to eight minutes; total playback has a 29-minute safety timeout.
- Scheduling starts after initial setup and resumes after reboot once credential-protected app storage is available. There is no pre-unlock direct-boot playback.
