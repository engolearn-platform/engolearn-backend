package fit.iuh.engolearn.assessment.dto.response;

import fit.iuh.engolearn.assessment.dto.request.DraftContentRequest.*;
import java.util.List;

public record AssessmentDraftResponse(String series, String name, int version, String revision,
                                      List<Skill> skills, List<Unit> units) {}
