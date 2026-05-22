package isel.computacaonanuvem.translate;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.util.ArrayList;
import java.util.List;

public class TranslateOperations {
    public static String translateToPT(String text) {
        Translate translate = TranslateOptions.getDefaultInstance().getService();
        Translation translation = translate.translate(
                text,
                Translate.TranslateOption.sourceLanguage("en"),
                Translate.TranslateOption.targetLanguage("pt")
        );
        return translation.getTranslatedText();
    }
    public static List<String> translateListToPT(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        Translate translate = TranslateOptions.getDefaultInstance().getService();

        // Passamos a lista 'texts' completa. A API da Google aceita isto nativamente
        List<Translation> translations = translate.translate(
                texts,
                Translate.TranslateOption.sourceLanguage("en"),
                Translate.TranslateOption.targetLanguage("pt")
        );

        List<String> translatedTexts = new ArrayList<>();
        for (Translation translation : translations) {
            translatedTexts.add(translation.getTranslatedText());
        }

        return translatedTexts;
    }
}
