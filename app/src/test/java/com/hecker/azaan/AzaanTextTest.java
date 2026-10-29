package com.hecker.azaan;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AzaanTextTest {
    @Test public void canonicalAdhanHasEighteenSentences() {
        for (AzaanText.Language language : AzaanText.Language.values()) assertEquals(18,AzaanText.phrases(language,false).size());
    }
    @Test public void optionalTestimonyAddsTwoSentencesOnly() {
        for (AzaanText.Language language : AzaanText.Language.values()) assertEquals(20,AzaanText.phrases(language,true).size());
        assertEquals(1,AzaanText.LINES.stream().filter(l -> l.optional).count());
    }
    @Test public void repetitionsAndPhraseOrderAreStable() {
        List<String> phrases = AzaanText.phrases(AzaanText.Language.ARABIC,true);
        for (int i = 0; i < 4; i++) assertEquals(AzaanText.LINES.get(0).arabic,phrases.get(i));
        assertEquals(AzaanText.LINES.get(3).arabic,phrases.get(8));
        assertEquals(AzaanText.LINES.get(6).arabic,phrases.get(14));
        assertEquals(AzaanText.LINES.get(8).arabic,phrases.get(18));
        assertEquals(phrases.get(18),phrases.get(19));
    }
    @Test public void everyLineHasAllTranslationsAndTransliteration() {
        for (AzaanText.Line line : AzaanText.LINES) {
            assertFalse(line.arabic.trim().isEmpty()); assertFalse(line.urdu.trim().isEmpty());
            assertFalse(line.english.trim().isEmpty()); assertFalse(line.transliteration.trim().isEmpty());
        }
        assertTrue(AzaanText.phrases(AzaanText.Language.URDU,false).get(0).contains("اللہ"));
        assertEquals("Hasten to the best of deeds.",AzaanText.LINES.get(6).english);
    }
}
