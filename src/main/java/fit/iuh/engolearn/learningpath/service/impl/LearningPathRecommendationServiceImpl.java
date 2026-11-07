package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.service.LearningPathRecommendationService;
import fit.iuh.engolearn.assessment.contract.AssessmentResults;
import fit.iuh.engolearn.learningpath.mapper.LearningPathAssessmentMapper;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse.MicroSkill;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse.State;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.model.PeriodicTestSnapshot;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;

import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@lombok.RequiredArgsConstructor
public class LearningPathRecommendationServiceImpl implements LearningPathRecommendationService {

    private final AssessmentResults assessmentResults;
    private final LearningPathRepository paths;
    private final QuestionPoolRepository questionPool;

    public LearningPathRecommendationResponse recommendForAssessment(String userId, String assessmentAttemptId) {
        return recommend(userId, assessmentAttemptId);
    }

    public LearningPathRecommendationResponse recommend(String userId, String attemptId) {
        var resultDto = assessmentResults.findCompleted(userId, attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài kiểm tra định kì đã hoàn thành của người học"));
        PeriodicTestSnapshot test;
        try {
            test = LearningPathAssessmentMapper.from(resultDto, userId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã micro-skill của bài kiểm tra chưa được hỗ trợ");
        }
        if (test.level() == null || test.level().isBlank() || test.results().size() != 5) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dữ liệu bài kiểm tra định kì chưa đầy đủ để đề xuất lộ trình");
        }
        Set<String> uniqueSkills = new HashSet<>();
        for (PeriodicTestSnapshot.MicroSkillResult result : test.results()) {
            if (result.microSkill() == null || result.skill() == null || result.band() == null
                    || result.total() <= 0 || result.correct() < 0 || result.correct() > result.total()
                    || result.percent() != Math.round(100.0 * result.correct() / result.total())
                    || !uniqueSkills.add(result.microSkill())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Dữ liệu micro-skill của bài kiểm tra không hợp lệ");
            }
        }

        LearningPath existing = paths.findBySourceAssessmentAttemptId(test.attemptId()).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return response(State.ALREADY_CREATED, null, existing.getId(),
                    test.level(), List.of());
        }

        List<PeriodicTestSnapshot.MicroSkillResult> weak = test.results().stream()
                .filter(result -> result.valid() && result.percent() < 50)
                .toList();
        if (weak.isEmpty()) {
            return response(State.NO_WEAK_SKILLS, null, null, test.level(), List.of());
        }

        Set<String> occupied = new HashSet<>();
        for (LearningPath path : paths.findByUserIdAndActiveTrue(userId)) {
            if (path.getMicroSkills() != null) {
                path.getMicroSkills().stream()
                        .map(LearningPathMicroSkillSnapshot::getMicroSkill)
                        .forEach(occupied::add);
            }
        }
        List<MicroSkill> eligible = weak.stream()
                .filter(result -> !occupied.contains(result.microSkill()))
                .filter(result -> questionPool.countActive(result.microSkill(), test.level()) >= 30)
                .map(result -> new MicroSkill(result.microSkill(), result.skill(), result.correct(),
                        result.total(), result.percent(), result.band()))
                .toList();
        if (eligible.isEmpty()) {
            return response(State.NO_ELIGIBLE_SKILLS,
                    "Không có micro-skill nào cần luyện tập, hãy giữ vững phong độ nhé",
                    null, test.level(), eligible);
        }
        return response(State.AVAILABLE, null, null, test.level(), eligible);
    }

    private LearningPathRecommendationResponse response(State state, String message,
                                                        String existingPathId, String level,
                                                        List<MicroSkill> skills) {
        return new LearningPathRecommendationResponse(state, message, existingPathId,
                level, 3, skills.size(), 10, skills);
    }
}
