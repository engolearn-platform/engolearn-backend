package fit.iuh.engolearn.learningpath.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record QuestionPoolCoverageKey(String microSkill, CefrLevel level) {
    public String id() {
        return "LP-Q-COVERAGE-" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((microSkill + "\0" + level.name()).getBytes(StandardCharsets.UTF_8));
    }
}
