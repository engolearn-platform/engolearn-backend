package fit.iuh.engolearn.assessment.dto.response;

import java.util.Map;

public record AssessmentImportPreview(AssessmentDraftResponse draft, String token,
                                      Map<Integer, String> audioFiles, String soundCheckFile) {}
