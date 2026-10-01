package fit.iuh.engolearn.content.api;

import java.util.List;

public record ExpressionExampleView(
        String type,
        String textEn,
        String textVi,
        String usageNote,
        String audioUrl,
        List<String> linkedVocabIds) {
}
