package fit.iuh.engolearn.assessment.contract.dto;
import java.time.Instant;
import java.util.List;
public record AssessmentResultDto(String attemptId,Instant submittedAt,String level,String paperId,List<AssessmentSkillDto> skills) {
    public AssessmentResultDto {skills=List.copyOf(skills);}
}
