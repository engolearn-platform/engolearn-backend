package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;

import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.dto.response.CreateLearningPathResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;
import fit.iuh.engolearn.learningpath.exception.RecommendationChangedException;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathBaseline;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathBaselineTotal;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathConfig;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathInactivity;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathProgress;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathSource;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.model.PeriodicTestSnapshot;
import fit.iuh.engolearn.learningpath.repository.LearningPathRepository;
import fit.iuh.engolearn.learningpath.repository.PeriodicTestReadRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningPathCreationService {

    private static final ZoneOffset LEARNING_ZONE = ZoneOffset.ofHours(7);

    private final LearningPathRecommendationService recommendations;
    private final PeriodicTestReadRepository periodicTests;
    private final LearningPathRepository paths;
    private final QuestionPoolRepository questionPool;
    private final LearningPlanGenerator planGenerator;

    public LearningPathCreationService(LearningPathRecommendationService recommendations,
                                       PeriodicTestReadRepository periodicTests,
                                       LearningPathRepository paths,
                                       QuestionPoolRepository questionPool,
                                       LearningPlanGenerator planGenerator) {
        this.recommendations = recommendations;
        this.periodicTests = periodicTests;
        this.paths = paths;
        this.questionPool = questionPool;
        this.planGenerator = planGenerator;
    }

    public CreateLearningPathResponse create(CreateLearningPathRequest request) {
        ObjectId userId = new ObjectId(request.userId());
        ObjectId attemptId = new ObjectId(request.attemptId());
        LearningPath existing = paths.findBySourceAttemptId(attemptId).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài kiểm tra định kì của người học");
            }
            return new CreateLearningPathResponse(existing.getId(), false);
        }
        try {
            return createPath(request, userId, attemptId);
        } catch (DuplicateKeyException exception) {
            LearningPath created = paths.findBySourceAttemptId(attemptId).orElse(null);
            if (created != null) {
                if (!userId.equals(created.getUserId())) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Không tìm thấy bài kiểm tra định kì của người học");
                }
                return new CreateLearningPathResponse(created.getId(), false);
            }
            throw new RecommendationChangedException(recommendations.recommend(userId, attemptId));
        }
    }

    private CreateLearningPathResponse createPath(CreateLearningPathRequest request,
                                                  ObjectId userId, ObjectId attemptId) {
        LearningPathRecommendationResponse current = recommendations.recommend(userId, attemptId);
        if (current.state() == LearningPathRecommendationResponse.State.ALREADY_CREATED) {
            return new CreateLearningPathResponse(current.existingPathId(), false);
        }
        Set<String> expected = current.microSkills().stream()
                .map(LearningPathRecommendationResponse.MicroSkill::name)
                .collect(Collectors.toSet());
        Set<String> confirmed = new HashSet<>(request.microSkills());
        if (current.state() != LearningPathRecommendationResponse.State.AVAILABLE
                || expected.size() != request.microSkills().size()
                || !expected.equals(confirmed)) {
            throw new RecommendationChangedException(current);
        }

        PeriodicTestSnapshot test = periodicTests.findCompletedPeriodic(attemptId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài kiểm tra định kì"));
        Map<String, PeriodicTestSnapshot.MicroSkillResult> results = test.results().stream()
                .collect(Collectors.toMap(PeriodicTestSnapshot.MicroSkillResult::microSkill,
                        result -> result));
        Map<String, List<Document>> selected = new LinkedHashMap<>();
        List<Document> allQuestions = new ArrayList<>();
        for (LearningPathRecommendationResponse.MicroSkill skill : current.microSkills()) {
            List<Document> questions = questionPool.sampleActive(skill.name(), test.level(), 30);
            if (questions.size() != 30) {
                throw new RecommendationChangedException(recommendations.recommend(userId, attemptId));
            }
            selected.put(skill.name(), questions);
            allQuestions.addAll(questions);
        }
        Set<String> passageIds = allQuestions.stream()
                .map(question -> question.getString("passage_id"))
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, Document> passages = questionPool.findPassages(passageIds);
        Date createdAt = Date.from(Instant.now());
        LocalDate createdDate = createdAt.toInstant().atOffset(LEARNING_ZONE).toLocalDate();
        LearningPath path = buildPath(test, current, results, selected, createdAt, createdDate);
        questionPool.insertSnapshots(new ObjectId(path.getId()), userId, allQuestions, passages, createdAt);
        paths.save(path);
        return new CreateLearningPathResponse(path.getId(), true);
    }

    private LearningPath buildPath(PeriodicTestSnapshot test,
                                   LearningPathRecommendationResponse recommendation,
                                   Map<String, PeriodicTestSnapshot.MicroSkillResult> results,
                                   Map<String, List<Document>> selected,
                                   Date createdAt, LocalDate createdDate) {
        List<String> skills = recommendation.microSkills().stream()
                .map(LearningPathRecommendationResponse.MicroSkill::name)
                .toList();
        LearningPath path = new LearningPath();
        path.setId(new ObjectId().toHexString());
        path.setUserId(test.userId());
        LearningPathSource source = new LearningPathSource();
        source.setAttemptId(test.attemptId());
        source.setTestSetId(test.testSetId());
        source.setTakenAt(test.takenAt());
        path.setSource(source);
        path.setCreatedAt(createdAt.toInstant());
        path.setCreatedDate(createdDate.toString());
        path.setLevel(test.level());
        path.setK(skills.size());
        path.setStatus(LearningPathStatus.NOT_STARTED);
        path.setActive(true);
        path.setCurrentWeekNo(1);

        LearningPathConfig config = new LearningPathConfig();
        config.setDurationWeeks(3);
        config.setQuestionsPerDay(10);
        config.setQuestionsPerSkillPerWeek(10);
        path.setConfig(config);

        int baselineCorrect = 0;
        int baselineTotal = 0;
        List<LearningPathMicroSkillSnapshot> baselineSkills = new ArrayList<>();
        for (String skill : skills) {
            PeriodicTestSnapshot.MicroSkillResult result = results.get(skill);
            LearningPathBaseline baseline = new LearningPathBaseline();
            baseline.setCorrect(result.correct());
            baseline.setTotal(result.total());
            baseline.setPercent(result.percent());
            baseline.setBand(result.band());
            baseline.setResultId(result.resultId());
            LearningPathMicroSkillSnapshot snapshot = new LearningPathMicroSkillSnapshot();
            snapshot.setMicroSkill(skill);
            snapshot.setSkill(result.skill());
            snapshot.setBaseline(baseline);
            baselineSkills.add(snapshot);
            baselineCorrect += result.correct();
            baselineTotal += result.total();
        }
        path.setMicroSkills(baselineSkills);
        LearningPathBaselineTotal baselineTotalSnapshot = new LearningPathBaselineTotal();
        baselineTotalSnapshot.setCorrect(baselineCorrect);
        baselineTotalSnapshot.setTotal(baselineTotal);
        baselineTotalSnapshot.setPercent(baselineTotal == 0 ? null
                : 100.0 * baselineCorrect / baselineTotal);
        path.setBaselineTotal(baselineTotalSnapshot);

        LearningPathProgress progress = new LearningPathProgress();
        progress.setAssigned(skills.size() * 30);
        progress.setAnswered(0);
        progress.setCorrect(0);
        path.setProgress(progress);

        LearningPathInactivity inactivity = new LearningPathInactivity();
        inactivity.setAnchorDate(createdDate.toString());
        inactivity.setCancelAt(Date.from(createdDate.plusDays(15)
                .atStartOfDay(LEARNING_ZONE).toInstant()));
        path.setInactivity(inactivity);

        List<LearningPathWeek> weeks = planGenerator.generate(skills, selected,
                createdAt, createdDate);
        path.setWeeks(weeks);
        path.setNextRunAt(weeks.getFirst().getCloseAt());
        return path;
    }
}
