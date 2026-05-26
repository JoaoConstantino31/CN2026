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
}
