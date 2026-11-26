package fit.iuh.engolearn.grammar.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GrammarAnswerNormalizer {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern END_PUNCTUATION = Pattern.compile("[.,;:!?]+$");

    private GrammarAnswerNormalizer() {
    }

    public static String normalize(String answer) {
        if (answer == null) {
            return "";
        }
        String normalized = Normalizer.normalize(answer, Normalizer.Form.NFKC)
                .replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'').replace('′', '\'')
                .replace('“', '"').replace('”', '"').replace('–', '-').replace('—', '-');
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
        return END_PUNCTUATION.matcher(normalized).replaceFirst("").stripTrailing();
    }
}
