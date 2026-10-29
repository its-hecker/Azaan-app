package com.hecker.azaan;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One utterance at a time: a language switch cannot affect already queued text. */
public final class PlaybackService extends Service {
    public static final String PLAY = "com.hecker.azaan.PLAY";
    public static final String STOP = "com.hecker.azaan.STOP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<AzaanText.Language, Voice> voices = new EnumMap<>(AzaanText.Language.class);
    private TextToSpeech tts;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private AppSettings settings;
    private List<AzaanText.Language> languages;
    private List<String> phrases;
    private int languageIndex, phraseIndex, generation;
    private boolean running, ready;
    private String currentUtterance;
    private String title;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final Runnable timeout = () -> fail("Playback timed out. Check your recordings and installed voices.");

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) { stopPlayback(); return START_NOT_STICKY; }
        if (!PLAY.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        settings = new AppSettings(this);
        Prayer prayer;
        try { prayer = Prayer.valueOf(intent.getStringExtra("prayer")); }
        catch (Exception e) { prayer = Prayer.FAJR; }
        if (intent.getBooleanExtra("automatic", false) && (!settings.active() || !settings.prayerEnabled(prayer))) {
            stopSelf(); return START_NOT_STICKY;
        }
        title = (intent.getBooleanExtra("automatic", false) ? "Azaan · " : "Preview · ") + prayer.title;
        Notifications.channels(this);
        startForeground(Notifications.PLAYBACK_ID, Notifications.playback(this, title, "Preparing Arabic → Urdu → English"));
        running = true;
        languages = settings.languages();
        languageIndex = 0; phraseIndex = 0;
        audio = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopPlayback();
            }, handler).build();
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("Audio is unavailable, for example during a call. Azaan was not played."); return START_NOT_STICKY;
        }
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Azaan:playback");
        wake.acquire(30 * 60_000L);
        handler.postDelayed(timeout, 29 * 60_000L);
        if (languages.stream().allMatch(l -> !settings.recording(l).isEmpty())) {
            playLanguage();
        } else {
            int token = ++generation;
            tts = new TextToSpeech(this, status -> handler.post(() -> initializeSpeech(status, token)));
        }
        return START_NOT_STICKY;
    }

    private void initializeSpeech(int status, int token) {
        if (!running || token != generation) return;
        if (status != TextToSpeech.SUCCESS) { fail("Speech engine unavailable. Install voices or import recordings in Settings."); return; }
        tts.setAudioAttributes(attributes);
        tts.setSpeechRate(0.85f);
        for (AzaanText.Language language : languages) {
            if (!settings.recording(language).isEmpty()) continue;
            Voice voice = VoiceSupport.offline(tts, language);
            if (voice == null) { fail("No installed offline " + language.title + " voice. Import a recording or install that voice in Settings."); return; }
            voices.put(language, voice);
        }
        ready = true;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { handler.post(() -> {
                if (running && id.equals(currentUtterance)) { phraseIndex++; speakNext(); }
            }); }
            @Override public void onError(String id) { handler.post(() -> {
                if (running && id.equals(currentUtterance)) fail("The speech engine could not speak this phrase. Check the installed voice or import a recording.");
            }); }
        });
        playLanguage();
    }

    private void playLanguage() {
        if (!running) return;
        if (languageIndex >= languages.size()) {
            settings.status("Last playback completed successfully."); stopPlayback(); return;
        }
        AzaanText.Language language = languages.get(languageIndex);
        getSystemService(android.app.NotificationManager.class).notify(Notifications.PLAYBACK_ID,
            Notifications.playback(this, title, language.title + " · " + (languageIndex + 1) + " of " + languages.size()));
        String recording = settings.recording(language);
        if (recording.isEmpty()) {
            if (!ready || tts.setVoice(voices.get(language)) == TextToSpeech.ERROR) { fail("Could not select the " + language.title + " voice."); return; }
            phrases = AzaanText.phrases(language, settings.wilayah()); phraseIndex = 0;
            speakNext();
        } else {
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(attributes);
                player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
                player.setDataSource(this, Uri.parse(recording));
                player.setVolume(settings.volume(), settings.volume());
                player.setOnPreparedListener(p -> { if (running) p.start(); });
                player.setOnCompletionListener(p -> { releasePlayer(); languageIndex++; playLanguage(); });
                player.setOnErrorListener((p, what, extra) -> { fail("Could not play " + language.title + " recording. Import a playable local audio file again."); return true; });
                player.prepareAsync();
            } catch (Exception e) { fail("The " + language.title + " recording is no longer accessible. Import it again."); }
        }
    }

    private void speakNext() {
        if (!running) return;
        if (phraseIndex >= phrases.size()) { languageIndex++; playLanguage(); return; }
        currentUtterance = generation + ":" + languageIndex + ":" + phraseIndex;
        Bundle parameters = new Bundle();
        parameters.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume());
        if (tts.speak(phrases.get(phraseIndex), TextToSpeech.QUEUE_FLUSH, parameters, currentUtterance) == TextToSpeech.ERROR)
            fail("Speech playback failed. Preview your voice settings before enabling automatic azaan.");
    }
    private void fail(String detail) {
        if (settings != null) settings.status(detail);
        Notifications.message(this, "Azaan needs attention", detail);
        stopPlayback();
    }
    private void releasePlayer() {
        if (player != null) { player.setOnCompletionListener(null); player.setOnErrorListener(null); player.release(); player = null; }
    }
    private void stopPlayback() {
        running = false; generation++;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (audio != null && focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; }
        if (wake != null && wake.isHeld()) wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy() { stopPlayback(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
