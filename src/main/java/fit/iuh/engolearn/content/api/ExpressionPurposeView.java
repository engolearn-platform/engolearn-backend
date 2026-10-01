package fit.iuh.engolearn.content.api;

import java.util.List;

public record ExpressionPurposeView(
        String purposeEn,
        String purposeVi,
        Integer orderIndex,
        List<ExpressionExampleView> examples) {
}
