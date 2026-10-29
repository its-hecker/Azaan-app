package com.hecker.azaan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independently transcribed text. Repetitions are for adhan, not iqamah. */
public final class AzaanText {
    private AzaanText() {}

    public enum Language {
        ARABIC("Arabic", "العربية", "ar-SA"),
        URDU("Urdu translation", "اردو ترجمہ", "ur-PK"),
        ENGLISH("English translation", "English", "en-GB");
        public final String title, nativeName, tag;
        Language(String title, String nativeName, String tag) {
            this.title = title; this.nativeName = nativeName; this.tag = tag;
        }
    }

    public static final class Line {
        public final String arabic, urdu, english, transliteration;
        public final int repetitions;
        public final boolean optional;
        Line(String arabic, String urdu, String english, String transliteration,
             int repetitions, boolean optional) {
            this.arabic = arabic; this.urdu = urdu; this.english = english;
            this.transliteration = transliteration;
            this.repetitions = repetitions; this.optional = optional;
        }
        public String text(Language language) {
            switch (language) {
                case ARABIC: return arabic;
                case URDU: return urdu;
                default: return english;
            }
        }
    }

    public static final List<Line> LINES = Collections.unmodifiableList(Arrays.asList(
        new Line("اللَّهُ أَكْبَرُ", "اللہ ہر وصف سے بڑا ہے۔", "Allah is greater than all description.", "Allahu akbar", 4, false),
        new Line("أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا اللَّهُ", "میں گواہی دیتا ہوں کہ اللہ کے سوا کوئی معبود نہیں ہے۔", "I bear witness that there is no god but Allah.", "Ashhadu an la ilaha illallah", 2, false),
        new Line("أَشْهَدُ أَنَّ مُحَمَّدًا رَسُولُ اللَّهِ", "میں گواہی دیتا ہوں کہ حضرت محمد اللہ کے رسول ہیں۔", "I bear witness that Muhammad is the Messenger of Allah.", "Ashhadu anna Muhammadan rasulullah", 2, false),
        new Line("أَشْهَدُ أَنَّ عَلِيًّا وَلِيُّ اللَّهِ", "میں گواہی دیتا ہوں کہ حضرت علی اللہ کے ولی ہیں۔", "I bear witness that Ali is the wali of Allah.", "Ashhadu anna Aliyyan waliyyullah", 2, true),
        new Line("حَيَّ عَلَى الصَّلَاةِ", "نماز کی طرف آؤ۔", "Hasten to prayer.", "Hayya alas salah", 2, false),
        new Line("حَيَّ عَلَى الْفَلَاحِ", "کامیابی کی طرف آؤ۔", "Hasten to success.", "Hayya alal falah", 2, false),
        new Line("حَيَّ عَلَى خَيْرِ الْعَمَلِ", "بہترین عمل کی طرف آؤ۔", "Hasten to the best of deeds.", "Hayya ala khayril amal", 2, false),
        new Line("اللَّهُ أَكْبَرُ", "اللہ ہر وصف سے بڑا ہے۔", "Allah is greater than all description.", "Allahu akbar", 2, false),
        new Line("لَا إِلَٰهَ إِلَّا اللَّهُ", "اللہ کے سوا کوئی معبود نہیں ہے۔", "There is no god but Allah.", "La ilaha illallah", 2, false)
    ));

    public static List<String> phrases(Language language, boolean includeWilayah) {
        List<String> phrases = new ArrayList<>();
        for (Line line : LINES) {
            if (line.optional && !includeWilayah) continue;
            for (int i = 0; i < line.repetitions; i++) phrases.add(line.text(language));
        }
        return phrases;
    }
}
