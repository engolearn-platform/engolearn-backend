package fit.iuh.engolearn.assessment.dto.request;

import fit.iuh.engolearn.assessment.model.enums.AssessmentSection;
import fit.iuh.engolearn.assessment.model.enums.AssessmentUnitKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record DraftContentRequest(
        @NotBlank @Size(max=64) String revision,
        @NotNull @Size(max=180) String name,
        @NotNull @Size(max=10) List<@NotNull @Valid Skill> skills,
        @NotNull @Size(max=30) List<@NotNull @Valid Unit> units) {
    public record Skill(@Size(max=32) String code, @Size(max=180) String name,
                        @Size(max=1000) String example, AssessmentSection section) {}
    public record Unit(AssessmentUnitKind kind, @Size(max=12000) String passage,
                       @Min(0) Integer sourceAudioUnit,
                       @NotNull @Size(max=8) List<@NotNull @Valid Question> questions,
                       @Pattern(regexp="[0-9a-f]{64}") String audioSha256) {}
    public record Question(@Pattern(regexp="A1|A2|B1|B2|") String level,
                           @Size(max=32) String skillCode, @Size(max=2000) String prompt,
                           @NotNull @Size(max=4) List<@NotNull @Size(max=600) String> options,
                           @Min(0) @Max(3) Integer correctOption,
                           @Size(max=2000) String explanation, @Size(max=4000) String transcript) {}
}
