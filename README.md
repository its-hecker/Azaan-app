# Azaan

A native Android Shia azaan app by Hecker. Three daily announcements cover **Fajr**, **Dhuhr–Asr**, and **Maghrib–Isha**. The complete Arabic azaan plays first, followed by the complete Urdu and English translations when enabled.

## Features

- Offline solar prayer calculations: Ja‘fari (Qom) and Tehran methods.
- Three independent prayer switches, a master automatic azaan switch, next-prayer countdown, and per-prayer adjustments from −60 to +60 minutes.
- Arabic azaan, Urdu translation, and English translation, with the correct repetition counts.
- Offline Android speech voices, or a separately imported full recording for each language. Arabic is always enabled; translations can be switched off independently.
- Azaan reader with Arabic, Urdu, English, and transliteration.
- Optional testimony to Imam Ali’s wilayah, enabled by default to match the supplied reference. It is identified as a devotional addition, not an integral part of azaan. The switch affects text and synthesized speech; recordings play exactly as imported.
- Exact alarms, foreground playback, a notification with Stop, audio-focus handling, and alarm restoration after reboot, app updates, and clock changes.
- Pakistan city presets, Najaf, Karbala, Tehran, Qom, manual coordinates with an IANA time zone, and one-time foreground location lookup.
- No account, ads, analytics, server, Internet permission, background location, root, or HeckerOS dependency. Android 8.0 and later.

## First use

1. Choose your city, or enter coordinates and the correct time zone in **Settings**. A GPS lookup uses your phone’s current time zone; adjust it if necessary. Update your location when travelling.
2. Choose your calculation method and compare the displayed times with a trusted local Shia timetable. Adjust each alert if needed.
3. Open **Voices & recordings**. For each enabled language, install an offline voice in your phone’s speech settings or import a complete recording using **Choose audio**. A recording is copied into private app storage and must be a playable local audio file under eight minutes and 100 MB. The app does not bundle a human reciter’s recording.
4. Use **Check audio setup**, then **Preview azaan**. Android speech is synthesized, not a traditional recitation. In particular, an offline Urdu voice may not be available in your installed engine; importing your Urdu translation audio is supported. The app reports missing voices instead of substituting another language or silently skipping it.
5. Enable **Automatic azaan**, allow notifications and **Alarms & reminders**, and confirm your phone’s alarm volume. Playback respects system alarm-volume and Do Not Disturb rules; the app does not change them.
6. Test with the screen locked. Some phones require allowing background activity in their app battery settings. Reopen the app after force-stopping it or restoring permissions.

Exact-alarm permission is required for automatic playback on Android 12+. There is deliberately no inaccurate background-playback fallback. Azaan is not played at boot: the boot receiver only restores future alarms. If a delivery is over five minutes late it is skipped, and the next event is scheduled. Each prayer is deduplicated by its local calendar date.

## Calculation details

| Method | Fajr angle | Maghrib angle |
| --- | ---: | ---: |
| Ja‘fari (Qom) | 16° | 4° |
| Tehran | 17.7° | 4.5° |

Dhuhr is calculated from solar noon with a default **+1 minute margin**. Times round upward to the next minute. Maghrib is calculated from its twilight angle, rather than using sunset. No separate Asr or Isha alarm is scheduled. All five prayers remain named; the alerts correspond to the three requested combined-prayer sessions.

The solar implementation follows the published [PrayTimes equations](https://praytimes.org/docs/calculation) and [method parameters](https://praytimes.org/docs/methods). It computes event instants in UTC and applies the selected time zone at each event, including daylight-saving transitions. When an astronomical crossing does not occur, that time is shown as unavailable and no time is fabricated. High latitude substitute methods and elevated-terrain corrections are not implemented in this version.

The phrase order and repetition counts were checked against [Sayyid Sistani’s azaan guidance](https://www.sistani.org/english/book/48/2228/) and the supplied screenshots. Urdu and English are explanatory translations, not replacements for the Arabic azaan. The English rendering of *wali* is retained as “wali of Allah” to avoid assigning a single English gloss to that term.

## Build

Use Android Studio with JDK 17 and Android SDK 35, or install the SDK and run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows:

```bat
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. The included Gradle 8.11.1 wrapper and Android Gradle Plugin 8.9.2 build a minSdk 26 / targetSdk 35 app without third-party runtime dependencies.

GitHub Actions runs tests, lint, and an APK build on every push to `main`. Download **Azaan-debug** from a successful run’s Artifacts. These are debug builds for testing; a distribution release requires a maintained release signing key.

## Validation

The unit suite covers published-method reference times, three-event scheduling, disabled alerts, midnight rollover, DST/date-line behavior, polar twilight, invalid coordinates, time adjustments, stale/duplicate deliveries, azaan repetition counts, and all translations. See [device testing](docs/DEVICE_TESTING.md) for the checks that require a real phone, including locked-screen playback, installed voices, Doze, calls, and reboot.

## Privacy

The app stores its selected coordinates, time zone, preferences, and imported recordings only in private app storage. Location permission is optional and requested only when **Use current location** is selected. No continuous location tracking or remote requests occur. Android’s selected speech engine is a separate app: Azaan selects only voices advertised as offline and installed. Imported audio avoids speech-engine dependence. App backups are disabled.
