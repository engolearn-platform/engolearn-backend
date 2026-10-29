package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse.MicroSkill;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse.State;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.model.PeriodicTestSnapshot;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import fit.iuh.engolearn.learningpath.repository.PeriodicTestReadRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningPathRecommendationService {

    private final PeriodicTestReadRepository periodicTests;
    private final LearningPathRepository paths;
    private final QuestionPoolRepository questionPool;

    public LearningPathRecommendationService(PeriodicTestReadRepository periodicTests,
                                             LearningPathRepository paths,
                                             QuestionPoolRepository questionPool) {
        this.periodicTests = periodicTests;
        this.paths = paths;
        this.questionPool = questionPool;
    }

    public LearningPathRecommendationResponse recommend(String userId, String attemptId) {
        return recommend(new ObjectId(userId), new ObjectId(attemptId));
    }

    public LearningPathRecommendationResponse recommend(ObjectId userId, ObjectId attemptId) {
        PeriodicTestSnapshot test = periodicTests.findCompletedPeriodic(attemptId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài kiểm tra định kì đã hoàn thành của người học"));
        if (test.level() == null || test.level().isBlank() || test.results().size() != 5) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dữ liệu bài kiểm tra định kì chưa đầy đủ để đề xuất lộ trình");
        }
        Set<String> uniqueSkills = new HashSet<>();
        for (PeriodicTestSnapshot.MicroSkillResult result : test.results()) {
            if (result.microSkill() == null || result.skill() == null
                    || !uniqueSkills.add(result.microSkill())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Dữ liệu micro-skill của bài kiểm tra không hợp lệ");
            }
        }

        LearningPath existing = paths.findBySourceAttemptId(attemptId).orElse(null);
        if (existing != null) {
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
