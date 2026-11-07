package fit.iuh.engolearn.assessment.dto.response;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.*;
public record AssessmentResultResponse(String id,String status,Instant submittedAt,String series,int paperVersion,Integer correct,
            Integer score,Integer readingCorrect,Integer listeningCorrect,String level,String initialLevel,
            String currentLevel,String highestLevel,List<AssessmentSkillResponse> skills,int measuredSkills,boolean fullAnalysis,boolean developmentData) {}
