package com.hecker.azaan;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android UI. No WebView, account, network service, or background location. */
@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,25,23), CARD = Color.rgb(21,43,38);
    private static final int GOLD = Color.rgb(217,187,119), TEXT = Color.rgb(241,242,230);
    private static final int MUTED = Color.rgb(158,181,168), LINE = Color.rgb(43,67,56);
    private static final int NOTIFICATION_REQUEST = 71, LOCATION_REQUEST = 72, AUDIO_REQUEST = 73;
    private static final int EXACT_REQUEST = 74, NOTIFICATION_SETTINGS = 75;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppSettings settings;
    private LinearLayout root, body;
    private ScrollView scroll;
    private TextView countdown, lastStatus;
    private int tab;
    private boolean pendingEnable;
    private boolean destroyed;
    private AzaanText.Language importLanguage;
    private TextToSpeech checker;
    private int checkGeneration;
    private Runnable checkTimeout;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private ProgressDialog locationProgress;
    private LocalDate renderedDate;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            LocalDate date = LocalDate.now(settings.zone());
            if (renderedDate != null && !date.equals(renderedDate)) render();
            updateCountdown();
            if (lastStatus != null) lastStatus.setText(settings.status());
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        if (state != null) {
            tab = state.getInt("tab", 0);
            pendingEnable = state.getBoolean("pendingEnable", false);
            String language = state.getString("importLanguage");
            if (language != null) importLanguage = AzaanText.Language.valueOf(language);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Notifications.channels(this);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab); state.putBoolean("pendingEnable", pendingEnable);
        if (importLanguage != null) state.putString("importLanguage", importLanguage.name());
    }
    @Override protected void onResume() {
        super.onResume();
        AlarmScheduler.schedule(this);
        render(); handler.removeCallbacks(ticker); handler.post(ticker);
    }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null);
        stopLocation();
        if (checker != null) { checker.shutdown(); checker = null; }
        io.shutdownNow(); super.onDestroy();
    }

    private void render() {
        if (destroyed) return;
        countdown = null; lastStatus = null;
        renderedDate = LocalDate.now(settings.zone());
        root = vertical(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = vertical(); body.setPadding(dp(22), dp(22), dp(22), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView brand = text("A Z A A N     /     H E C K E R", 11, GOLD);
        brand.setLetterSpacing(0.12f); body.addView(brand);
        if (tab == 0) today(); else if (tab == 1) reader(); else preferences();
        LinearLayout nav = horizontal(); nav.setPadding(dp(16), dp(10), dp(16), dp(10));
        nav.setBackgroundColor(CARD);
        String[] titles = {"Today", "Azaan", "Settings"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            Button b = button(titles[i], () -> { tab = index; render(); });
            b.setTextColor(tab == i ? BG : MUTED);
            b.setBackground(round(tab == i ? GOLD : CARD, 24));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1); lp.setMargins(dp(4), 0, dp(4), 0);
            nav.addView(b, lp);
        }
        root.addView(nav); setContentView(root); root.requestApplyInsets();
    }

    private void today() {
        gap(body, 22);
        body.addView(text("A moment to return.", 31, TEXT));
        body.addView(text(renderedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)), 14, MUTED));
        gap(body, 20);
        LinearLayout hero = card(body); hero.setBackground(round(Color.rgb(27,59,48), 28));
        hero.addView(new HorizonView(), new LinearLayout.LayoutParams(-1, dp(132)));
        TextView invocation = text("حَيَّ عَلَى خَيْرِ الْعَمَلِ", 29, GOLD);
        invocation.setGravity(Gravity.CENTER); invocation.setTextDirection(View.TEXT_DIRECTION_RTL);
        hero.addView(invocation);
        TextView caption = text("Hasten to the best of deeds", 13, MUTED); caption.setGravity(Gravity.CENTER); hero.addView(caption);
        gap(hero, 20);
        countdown = text("", 20, TEXT); countdown.setGravity(Gravity.CENTER); hero.addView(countdown);
        TextView method = text(settings.configured() ? settings.city() + "  ·  " + settings.method().title : "Set your location to see prayer times", 12, MUTED);
        method.setGravity(Gravity.CENTER); hero.addView(method); gap(hero, 6);
        updateCountdown();
        gap(body, 24);
        label(body, "THREE CALLS. FIVE PRAYERS.");
        gap(body, 8);
        if (!settings.configured()) {
            body.addView(button("Choose your location", this::locationChoices));
            gap(body, 12);
        }
        Map<Prayer, Instant> times = settings.configured()
            ? PrayerCalculator.calculate(renderedDate, settings.latitude(), settings.longitude(), settings.method(), settings.offsets()) : null;
        for (Prayer prayer : Prayer.values()) {
            LinearLayout row = card(body);
            LinearLayout line = horizontal(); line.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout names = vertical();
            names.addView(text(prayer.title, 20, TEXT));
            TextView urdu = text(prayer.urdu, 17, MUTED); names.addView(urdu);
            line.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            Instant instant = times == null ? null : times.get(prayer);
            TextView time = text(instant == null ? "—" : time(instant), 21, GOLD);
            time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); line.addView(time);
            row.addView(line);
            Switch enabled = toggle("Announce azaan", settings.prayerEnabled(prayer), value -> {
                settings.prayerEnabled(prayer, value); AlarmScheduler.schedule(this); updateCountdown();
            });
            row.addView(enabled);
            if (times != null && instant == null) row.addView(text("Twilight time unavailable here on this date. Check a local timetable.", 12, MUTED));
        }
        gap(body, 10);
        body.addView(toggle("Automatic azaan", settings.active(), value -> {
            if (value) enableAzaan();
            else { settings.active(false); AlarmScheduler.schedule(this); stopService(new Intent(this, PlaybackService.class)); render(); }
        }));
        TextView state = text(setupSummary(), 13, MUTED); body.addView(state);
        gap(body, 16); playbackButtons(body);
        gap(body, 18);
        lastStatus = text(settings.status(), 12, MUTED); body.addView(lastStatus);
    }

    private void reader() {
        gap(body, 22); body.addView(text("The call to prayer.", 31, TEXT));
        body.addView(text("Arabic · Urdu · English", 14, MUTED));
        gap(body, 20); playbackButtons(body);
        gap(body, 10); lastStatus = text(settings.status(), 12, GOLD); body.addView(lastStatus);
        gap(body, 12);
        body.addView(text("Playback completes the Arabic azaan, then reads each enabled translation. Translations explain its meaning.", 13, MUTED));
        gap(body, 18);
        for (AzaanText.Line line : AzaanText.LINES) {
            if (line.optional && !settings.wilayah()) continue;
            LinearLayout card = card(body);
            label(card, line.repetitions + " TIMES" + (line.optional ? "  ·  OPTIONAL TESTIMONY" : "")); gap(card, 12);
            TextView arabic = text(line.arabic, 29, TEXT);
            arabic.setGravity(Gravity.RIGHT); arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            card.addView(arabic); gap(card, 10);
            TextView urdu = text(line.urdu, 21, GOLD);
            urdu.setGravity(Gravity.RIGHT); urdu.setTextDirection(View.TEXT_DIRECTION_RTL); urdu.setLineSpacing(dp(7), 1);
            card.addView(urdu); gap(card, 12);
            card.addView(text(line.transliteration, 13, MUTED)); gap(card, 8);
            card.addView(text(line.english, 16, TEXT));
        }
        body.addView(text("The testimony to Imam Ali’s wilayah is included as an optional devotional phrase, not as an integral part of azaan.", 13, MUTED));
    }

    private void preferences() {
        gap(body, 22); body.addView(text("Make it yours.", 31, TEXT));
        body.addView(text("A quiet setup for a dependable reminder.", 14, MUTED));
        gap(body, 24);
        LinearLayout location = card(body); label(location, "LOCATION & PRAYER TIMES"); gap(location, 12);
        location.addView(text(settings.city(), 22, TEXT));
        if (settings.configured()) location.addView(text(String.format(Locale.US, "%.4f, %.4f  ·  %s", settings.latitude(), settings.longitude(), settings.zone().getId()), 12, MUTED));
        location.addView(button("Choose city or coordinates", this::locationChoices));
        location.addView(button("Use current location", this::useLocation));
        location.addView(button(settings.method().title + "  ·  Change method", this::chooseMethod));
        location.addView(button("Adjust prayer times", this::offsetDialog));
        location.addView(text("Location stays fixed until you update it. Times are calculated on your phone. Compare with your local Shia timetable.", 12, MUTED));
        gap(body, 8);
        LinearLayout playback = card(body); label(playback, "PLAYBACK ORDER"); gap(playback, 12);
        playback.addView(text("1  Arabic azaan", 18, TEXT));
        playback.addView(toggle("2  Urdu translation", settings.language(AzaanText.Language.URDU), v -> {
            settings.language(AzaanText.Language.URDU, v); settings.status("Audio options changed. Check audio setup and preview again.");
        }));
        playback.addView(toggle("3  English translation", settings.language(AzaanText.Language.ENGLISH), v -> {
            settings.language(AzaanText.Language.ENGLISH, v); settings.status("Audio options changed. Check audio setup and preview again.");
        }));
        playback.addView(text("Arabic always plays first. Each translation reads the complete azaan in the same order and repetition counts.", 12, MUTED));
        gap(playback, 16);
        playback.addView(toggle("Include testimony to Imam Ali’s wilayah", settings.wilayah(), v -> settings.wilayah(v)));
        playback.addView(text("Optional devotional testimony. This switch changes the text and synthesized speech; imported recordings play as recorded.", 12, MUTED));
        gap(playback, 16);
        TextView volumeLabel = text("Playback volume · " + Math.round(settings.volume() * 100) + "%", 15, TEXT);
        playback.addView(volumeLabel);
        SeekBar volume = new SeekBar(this); volume.setMax(100); volume.setProgress(Math.round(settings.volume() * 100));
        volume.setProgressTintList(ColorStateList.valueOf(GOLD)); volume.setThumbTintList(ColorStateList.valueOf(GOLD));
        volume.setContentDescription("Playback volume");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                volumeLabel.setText("Playback volume · " + value + "%"); if (user) settings.volume(value);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        playback.addView(volume);
        playback.addView(text("Uses your phone’s alarm volume and Do Not Disturb rules. Test with the screen locked before relying on alerts.", 12, MUTED));
        gap(body, 8);
        LinearLayout voices = card(body); label(voices, "VOICES & RECORDINGS"); gap(voices, 10);
        voices.addView(text("Use installed offline speech voices, or import your preferred reciter and translations. No reciter recording is bundled.", 13, MUTED));
        for (AzaanText.Language language : AzaanText.Language.values()) {
            gap(voices, 16);
            voices.addView(text(language.title, 17, TEXT));
            voices.addView(text(settings.recording(language).isEmpty() ? "Installed offline speech voice" : settings.recordingName(language), 12, MUTED));
            voices.addView(button("Choose audio · " + language.title, () -> recordingOptions(language)));
        }
        voices.addView(button("Check audio setup", () -> checkAudio(false)));
        voices.addView(button("Open speech voice settings", this::voiceSettings));
        gap(voices, 8); playbackButtons(voices);
        gap(body, 8);
        LinearLayout reliability = card(body); label(reliability, "AUTOMATIC AZAAN");
        gap(reliability, 8);
        reliability.addView(toggle("Enable automatic azaan", settings.active(), v -> {
            if (v) enableAzaan(); else { settings.active(false); AlarmScheduler.schedule(this); stopService(new Intent(this, PlaybackService.class)); render(); }
        }));
        reliability.addView(text(setupSummary(), 13, MUTED));
        reliability.addView(button("Alarms & reminders · " + (AlarmScheduler.exactAllowed(this) ? "Allowed" : "Allow"), this::exactSettings));
        reliability.addView(button("Notifications · " + (Notifications.allowed(this) ? "Allowed" : "Allow"), this::notificationSettings));
        reliability.addView(button("Battery & app settings", () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));
        reliability.addView(text("Alarms are restored after a restart, app update, or clock change. After force-stopping the app, reopen it. If your phone restricts background apps, allow Azaan in its battery settings.", 12, MUTED));
        gap(reliability, 12); lastStatus = text(settings.status(), 12, GOLD); reliability.addView(lastStatus);
        gap(body, 12);
        body.addView(text("Azaan 1.0 · by Hecker", 15, GOLD));
        body.addView(text("No account. No ads. No analytics. Your location and recordings stay on this device. Location is used only when you request it.", 12, MUTED));
        body.addView(button("Calculation & text sources", () -> new AlertDialog.Builder(this).setTitle("Sources")
            .setMessage("Ja‘fari (Qom): Fajr 16°, Maghrib 4°. Tehran: Fajr 17.7°, Maghrib 4.5°. Dhuhr includes a default +1 minute margin. All calculated times round upward to the next minute. No high latitude substitute times are assumed.\n\nSolar equations: praytimes.org/docs/calculation\nParameters: praytimes.org/docs/methods\nAzaan and optional testimony: sistani.org/english/book/48/2228/\n\nTranslations are provided to explain the meaning and follow the supplied reference text.")
            .setPositiveButton("Close", null).show()));
    }

    private void updateCountdown() {
        if (countdown == null) return;
        if (!settings.configured()) { countdown.setText("Your day, in prayer."); return; }
        List<AlarmPlanner.Event> events = AlarmPlanner.upcoming(Instant.now(), settings.zone(), settings.latitude(), settings.longitude(), settings.method(), settings.offsets(), new boolean[]{true, true, true});
        if (events.isEmpty()) { countdown.setText("Check a local prayer timetable"); return; }
        AlarmPlanner.Event next = events.get(0);
        long seconds = Math.max(0, Duration.between(Instant.now(), next.time).getSeconds());
        countdown.setText(String.format(Locale.ENGLISH, "%s in %02d:%02d:%02d", next.prayer.title, seconds / 3600, (seconds / 60) % 60, seconds % 60));
    }
    private String time(Instant t) {
        return t.atZone(settings.zone()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
    }
    private String setupSummary() {
        if (!settings.configured()) return "Choose a location before enabling automatic azaan.";
        if (!settings.active()) return "Automatic azaan is off. You can preview anytime.";
        if (!AlarmScheduler.exactAllowed(this)) return "Paused: allow Alarms & reminders to restore alerts.";
        if (!Notifications.allowed(this)) return "Paused: allow notifications to restore alerts.";
        List<AlarmPlanner.Event> events = AlarmPlanner.upcoming(Instant.now(), settings.zone(), settings.latitude(), settings.longitude(), settings.method(), settings.offsets(), settings.enabled());
        if (events.isEmpty()) return "No upcoming enabled alerts. Check prayer switches and your local timetable.";
        AlarmPlanner.Event e = events.get(0);
        return "Next alert: " + e.prayer.title + " · " + e.time.atZone(settings.zone()).format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.ENGLISH));
    }

    private void playbackButtons(LinearLayout parent) {
        LinearLayout row = horizontal();
        Button play = button("Preview azaan", () -> {
            settings.status("Starting preview…");
            startForegroundService(new Intent(this, PlaybackService.class).setAction(PlaybackService.PLAY).putExtra("prayer", Prayer.FAJR.name()));
        });
        play.setBackground(round(GOLD, 18)); play.setTextColor(BG);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(52), 1); left.setMargins(0, 0, dp(8), 0);
        row.addView(play, left);
        row.addView(button("Stop", () -> { stopService(new Intent(this, PlaybackService.class)); settings.status("Playback stopped."); }), new LinearLayout.LayoutParams(dp(85), dp(52)));
        parent.addView(row);
    }

    private void locationChoices() {
        String[] cities = {"Karachi", "Lahore", "Islamabad", "Rawalpindi", "Faisalabad", "Multan", "Peshawar", "Quetta", "Hyderabad", "Najaf", "Karbala", "Tehran", "Qom", "Enter coordinates…"};
        double[][] coordinates = {{24.8607,67.0011},{31.5204,74.3587},{33.6844,73.0479},{33.5651,73.0169},{31.4504,73.1350},{30.1575,71.5249},{34.0151,71.5249},{30.1798,66.9750},{25.3960,68.3578},{31.9958,44.3148},{32.6160,44.0249},{35.6892,51.3890},{34.6416,50.8746}};
        new AlertDialog.Builder(this).setTitle("Choose your location").setItems(cities, (d, which) -> {
            if (which == coordinates.length) { coordinatesDialog(); return; }
            String zone = which < 9 ? "Asia/Karachi" : which < 11 ? "Asia/Baghdad" : "Asia/Tehran";
            settings.location(cities[which], coordinates[which][0], coordinates[which][1], ZoneId.of(zone));
            AlarmScheduler.schedule(this); render();
        }).setNegativeButton("Cancel", null).show();
    }
    private void coordinatesDialog() {
        LinearLayout fields = dialogFields();
        EditText name = field(fields, "Location name", settings.configured() ? settings.city() : "", false);
        EditText lat = field(fields, "Latitude (-90 to 90)", settings.configured() ? String.valueOf(settings.latitude()) : "", true);
        EditText lon = field(fields, "Longitude (-180 to 180)", settings.configured() ? String.valueOf(settings.longitude()) : "", true);
        EditText zone = field(fields, "Time zone, e.g. Asia/Karachi", settings.zone().getId(), false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Location & time zone").setView(fields)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                settings.location(name.getText().toString(), Double.parseDouble(lat.getText().toString()), Double.parseDouble(lon.getText().toString()), ZoneId.of(zone.getText().toString().trim()));
                AlarmScheduler.schedule(this); dialog.dismiss(); render();
            } catch (Exception e) { toast("Enter valid coordinates and an IANA time zone, such as Asia/Karachi."); }
        })); dialog.show();
    }
    private void chooseMethod() {
        PrayerCalculator.Method[] methods = PrayerCalculator.Method.values();
        new AlertDialog.Builder(this).setTitle("Prayer calculation").setSingleChoiceItems(new String[]{methods[0].title, methods[1].title}, settings.method().ordinal(), (d, which) -> {
            settings.method(methods[which]); AlarmScheduler.schedule(this); d.dismiss(); render();
        }).setNegativeButton("Cancel", null).show();
    }
    private void offsetDialog() {
        LinearLayout fields = dialogFields();
        fields.addView(text("Minutes added to calculated times. Use -60 to +60 to match your local timetable.", 13, MUTED));
        EditText[] edits = new EditText[3];
        for (Prayer p : Prayer.values()) edits[p.ordinal()] = field(fields, p.title, String.valueOf(settings.offset(p)), true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Prayer time adjustments").setView(fields)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int[] values = new int[3];
                for (int i = 0; i < 3; i++) { values[i] = Integer.parseInt(edits[i].getText().toString()); if (values[i] < -60 || values[i] > 60) throw new IllegalArgumentException(); }
                settings.offsets(values); AlarmScheduler.schedule(this); dialog.dismiss(); render();
            } catch (Exception e) { toast("Each adjustment must be a whole number from -60 to 60."); }
        })); dialog.show();
    }

    private void useLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST); return;
        }
        locationManager = getSystemService(LocationManager.class);
        String provider = null;
        boolean precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (precise && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
        if (provider == null) { toast("Turn on location, or choose a city manually."); return; }
        locationProgress = ProgressDialog.show(this, "Finding your location", "Waiting for a fresh location. You can also choose a city manually.", true, true);
        locationProgress.setOnCancelListener(d -> stopLocation());
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (destroyed) return;
                settings.location("Current location", location.getLatitude(), location.getLongitude(), ZoneId.systemDefault());
                stopLocation(); AlarmScheduler.schedule(MainActivity.this); render();
                toast("Location saved using time zone " + settings.zone().getId() + ". Change it if needed.");
            }
            @Override public void onStatusChanged(String p, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        try { locationManager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper()); }
        catch (SecurityException e) { stopLocation(); toast("Location permission is unavailable. Choose a city manually."); return; }
        handler.postDelayed(() -> { if (locationListener != null) { stopLocation(); toast("No fresh location received. Try outdoors or choose a city."); } }, 30_000);
    }
    private void stopLocation() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) { }
        }
        locationListener = null;
        if (locationProgress != null && locationProgress.isShowing()) locationProgress.dismiss();
        locationProgress = null;
    }

    private void recordingOptions(AzaanText.Language language) {
        new AlertDialog.Builder(this).setTitle(language.title).setItems(new String[]{"Import a complete audio recording", "Use an installed offline speech voice"}, (d, which) -> {
            if (which == 1) { settings.recording(language, "", ""); render(); return; }
            importLanguage = language;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent, AUDIO_REQUEST); }
            catch (ActivityNotFoundException e) { toast("No file picker is available on this device."); }
        }).setNegativeButton("Cancel", null).show();
    }
    private void importAudio(Uri uri, AzaanText.Language language) {
        ProgressDialog progress = ProgressDialog.show(this, "Importing audio", "Checking and saving your recording…", true, false);
        io.execute(() -> {
            File temp = new File(getFilesDir(), "import_" + System.nanoTime() + ".tmp");
            try {
                String name = "Imported " + language.title + " recording";
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(temp)) {
                    if (input == null) throw new IllegalArgumentException("This file cannot be opened.");
                    byte[] buffer = new byte[32768]; long size = 0; int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count; if (size > 100 * 1024 * 1024L) throw new IllegalArgumentException("Choose a recording smaller than 100 MB.");
                        output.write(buffer, 0, count);
                    }
                }
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(temp.getAbsolutePath());
                    String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (duration == null || Long.parseLong(duration) <= 0 || Long.parseLong(duration) > 8 * 60_000L)
                        throw new IllegalArgumentException("Choose a playable recording under eight minutes.");
                } finally { metadata.release(); }
                File destination = new File(getFilesDir(), "recording_" + language.name() + "_" + System.nanoTime());
                if (!temp.renameTo(destination)) throw new IllegalArgumentException("Could not save this recording.");
                String old = settings.recording(language);
                settings.recording(language, Uri.fromFile(destination).toString(), name);
                if (!old.isEmpty()) { File previous = new File(Uri.parse(old).getPath()); if (previous.getParentFile().equals(getFilesDir())) previous.delete(); }
                handler.post(() -> {
                    if (!destroyed) { progress.dismiss(); settings.status("Recording imported. Preview it before enabling automatic azaan."); render(); toast("Recording imported."); }
                });
            } catch (Exception e) {
                temp.delete(); String message = e.getMessage();
                handler.post(() -> { if (!destroyed) { progress.dismiss(); toast(message == null ? "This audio could not be imported." : message); } });
            }
        });
    }

    private void enableAzaan() {
        if (!settings.configured()) { render(); locationChoices(); return; }
        if (!Notifications.allowed(this)) {
            pendingEnable = true; render();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
            else notificationSettings();
            return;
        }
        if (!AlarmScheduler.exactAllowed(this)) { pendingEnable = true; render(); exactSettings(); return; }
        pendingEnable = false; checkAudio(true);
    }
    private void checkAudio(boolean activate) {
        int token = ++checkGeneration;
        if (checkTimeout != null) handler.removeCallbacks(checkTimeout);
        if (checker != null) { checker.shutdown(); checker = null; }
        List<AzaanText.Language> languages = settings.languages();
        boolean needSpeech = false;
        for (AzaanText.Language language : languages) {
            String recording = settings.recording(language);
            if (recording.isEmpty()) { needSpeech = true; continue; }
            try (FileInputStream ignored = new FileInputStream(new File(Uri.parse(recording).getPath()))) { }
            catch (Exception e) { audioResult(activate, "Reimport the " + language.title + " recording."); return; }
        }
        if (!needSpeech) { audioResult(activate, null); return; }
        settings.status("Checking installed offline voices…");
        checker = new TextToSpeech(this, status -> handler.post(() -> {
            if (destroyed || checker == null || token != checkGeneration) return;
            if (checkTimeout != null) handler.removeCallbacks(checkTimeout);
            String error = status == TextToSpeech.SUCCESS ? null : "Install a speech engine or import recordings in Settings.";
            if (error == null) for (AzaanText.Language language : languages) {
                if (settings.recording(language).isEmpty() && VoiceSupport.offline(checker, language) == null) {
                    error = "Install an offline " + language.title + " voice, or import a recording for that language."; break;
                }
            }
            checker.shutdown(); checker = null; audioResult(activate, error);
        }));
        checkTimeout = () -> {
            if (!destroyed && checker != null && token == checkGeneration) {
                ++checkGeneration; checker.shutdown(); checker = null;
                audioResult(activate, "The speech engine did not respond. Open speech voice settings or import recordings.");
            }
        };
        handler.postDelayed(checkTimeout, 15_000);
    }
    private void audioResult(boolean activate, String error) {
        if (error != null) {
            settings.status(error); render();
            new AlertDialog.Builder(this).setTitle("Audio setup needed").setMessage(error)
                .setPositiveButton("Audio settings", (d, w) -> { tab = 2; render(); }).setNegativeButton("Close", null).show();
        } else {
            if (activate) { settings.active(true); AlarmScheduler.schedule(this); }
            settings.status(activate ? "Automatic azaan enabled. Preview once to confirm pronunciation and volume." : "Audio sources are available. Preview to confirm pronunciation and volume.");
            render(); toast(activate ? "Automatic azaan enabled." : "Audio setup checked.");
        }
    }
    private void exactSettings() {
        if (Build.VERSION.SDK_INT < 31) { toast("Exact alarms are supported without extra permission."); return; }
        try { startActivityForResult(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())), EXACT_REQUEST); }
        catch (ActivityNotFoundException e) { pendingEnable = false; toast("Open app settings and allow Alarms & reminders."); }
    }
    private void notificationSettings() {
        startActivityForResult(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()), NOTIFICATION_SETTINGS);
    }
    private void voiceSettings() {
        open(new Intent("com.android.settings.TTS_SETTINGS"));
    }
    private void open(Intent intent) {
        try { startActivity(intent); } catch (ActivityNotFoundException e) { toast("This settings page is unavailable on your phone."); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == LOCATION_REQUEST) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) useLocation();
            else toast("Choose a city manually, or allow location in app settings.");
        }
        if (request == NOTIFICATION_REQUEST) {
            if (Notifications.allowed(this) && pendingEnable) enableAzaan();
            else { pendingEnable = false; render(); toast("Allow notifications in Settings to enable automatic azaan."); }
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == AUDIO_REQUEST && result == RESULT_OK && data != null && data.getData() != null && importLanguage != null)
            importAudio(data.getData(), importLanguage);
        if (pendingEnable && (request == EXACT_REQUEST || request == NOTIFICATION_SETTINGS)) {
            boolean allowed = request == EXACT_REQUEST ? AlarmScheduler.exactAllowed(this) : Notifications.allowed(this);
            pendingEnable = false;
            if (allowed) enableAzaan(); else { render(); toast("Permission is needed for automatic azaan."); }
        }
    }

    private interface Changed { void set(boolean value); }
    private Switch toggle(String label, boolean checked, Changed change) {
        Switch view = new Switch(this); view.setText(label); view.setTextColor(TEXT); view.setTextSize(15);
        view.setPadding(0, dp(12), 0, dp(12)); view.setMinHeight(dp(48)); view.setSwitchPadding(dp(12));
        view.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{GOLD, MUTED}));
        view.setChecked(checked); view.setOnCheckedChangeListener((b, value) -> change.set(value)); return view;
    }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout v = vertical(); v.setPadding(dp(18), dp(18), dp(18), dp(18)); v.setBackground(round(CARD, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(12)); parent.addView(v, lp); return v;
    }
    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(true); v.setLineSpacing(dp(3), 1); return v;
    }
    private void label(LinearLayout parent, String value) {
        TextView v = text(value, 11, GOLD); v.setLetterSpacing(0.1f); parent.addView(v);
    }
    private Button button(String value, Runnable action) {
        Button v = new Button(this); v.setText(value); v.setAllCaps(false); v.setTextSize(14); v.setTextColor(TEXT);
        v.setBackground(round(LINE, 16)); v.setPadding(dp(12), dp(8), dp(12), dp(8)); v.setMinHeight(dp(48));
        v.setStateListAnimator(null); v.setOnClickListener(ignored -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(7), 0, dp(5)); v.setLayoutParams(lp); return v;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void gap(LinearLayout parent, int value) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(value))); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private LinearLayout dialogFields() { LinearLayout fields = vertical(); fields.setPadding(dp(24), dp(8), dp(24), dp(8)); return fields; }
    private EditText field(LinearLayout parent, String hint, String value, boolean number) {
        TextView title = text(hint, 12, MUTED); parent.addView(title);
        EditText v = new EditText(this); v.setSingleLine(true); v.setTextColor(TEXT); v.setHintTextColor(MUTED);
        v.setTextSize(16); v.setHint(hint); v.setText(value);
        if (number) v.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(52))); return v;
    }

    private final class HorizonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        HorizonView() { super(MainActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), cx = w / 2;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(Color.argb(65,217,187,119));
            canvas.drawCircle(cx, h * 0.55f, h * 0.43f, paint);
            canvas.drawCircle(cx, h * 0.55f, h * 0.54f, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(GOLD);
            canvas.drawCircle(cx, h * 0.43f, h * 0.15f, paint);
            paint.setColor(Color.rgb(27,59,48)); canvas.drawCircle(cx + h * 0.07f, h * 0.37f, h * 0.15f, paint);
            paint.setShader(new LinearGradient(0, h * 0.70f, 0, h, Color.TRANSPARENT, Color.rgb(27,59,48), Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * 0.70f, w, h, paint); paint.setShader(null);
            paint.setColor(GOLD); canvas.drawCircle(cx + h * 0.24f, h * 0.27f, dp(2), paint);
            paint.setColor(Color.argb(160,217,187,119)); canvas.drawCircle(cx - h * 0.34f, h * 0.52f, dp(1.5f), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.2f));
            Path arch = new Path(); arch.moveTo(cx - h * 0.25f, h);
            arch.lineTo(cx - h * 0.25f, h * 0.82f); arch.quadTo(cx - h * 0.25f, h * 0.65f, cx, h * 0.57f);
            arch.quadTo(cx + h * 0.25f, h * 0.65f, cx + h * 0.25f, h * 0.82f); arch.lineTo(cx + h * 0.25f, h);
            canvas.drawPath(arch, paint);
        }
    }
}
