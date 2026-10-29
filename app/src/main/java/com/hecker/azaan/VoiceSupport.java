package com.hecker.azaan;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

final class VoiceSupport {
    private VoiceSupport() {}
    static Voice offline(TextToSpeech tts, AzaanText.Language language) {
        Set<Voice> voices = tts.getVoices();
        if (voices == null) return null;
        Locale wanted = Locale.forLanguageTag(language.tag);
        return voices.stream().filter(v -> v.getLocale().getLanguage().equals(wanted.getLanguage()))
            .filter(v -> !v.isNetworkConnectionRequired())
            .filter(v -> v.getFeatures() == null || !v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
            .sorted(Comparator.<Voice>comparingInt(v -> v.getLocale().getCountry().equals(wanted.getCountry()) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Voice::getQuality).reversed())
                .thenComparing(Voice::getName)).findFirst().orElse(null);
    }
}
