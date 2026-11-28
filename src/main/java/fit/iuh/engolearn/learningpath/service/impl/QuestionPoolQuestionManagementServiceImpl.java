package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.QuestionPoolQuestionOptionRequest;
import fit.iuh.engolearn.learningpath.dto.request.PublishQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.RetireQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.ReplaceQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.exception.QuestionCoverageConfirmationRequiredException;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolCoverageKey;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolCoverageRepository;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionReadinessSource;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionRetirementSource;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolRepository;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.learningpath.exception.QuestionPublicationRejectedException;
import fit.iuh.engolearn.learningpath.model.vo.QuestionPublicationReviewVo;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionReadinessService;
import fit.iuh.engolearn.microskill.contract.MicroSkillLookup;
import fit.iuh.engolearn.learningpath.utils.QuestionPoolTransactionTemplate;
import fit.iuh.engolearn.learningpath.dto.response.*;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolQuestionNotFoundException;
import fit.iuh.engolearn.learningpath.exception.InvalidQuestionException;
import fit.iuh.engolearn.learningpath.exception.QuestionMutationConflictException;
import fit.iuh.engolearn.learningpath.exception.QuestionWriteUnavailableException;
import fit.iuh.engolearn.learningpath.model.QuestionPoolQuestion;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.learningpath.model.vo.QuestionPoolOptionVo;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolPassageRepository;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import fit.iuh.engolearn.learningpath.repository.QuestionPoolQuestionRepository;
import fit.iuh.engolearn.learningpath.service.QuestionPoolPassageManagementService;
import fit.iuh.engolearn.learningpath.service.QuestionPoolQuestionManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.TransactionException;

@Service
@RequiredArgsConstructor
public class QuestionPoolQuestionManagementServiceImpl implements QuestionPoolQuestionManagementService {
    private final MicroSkillLookup microSkills;
    private final QuestionPoolQuestionRepository questions;
    private final QuestionPoolPassageManagementService passages;
    private final QuestionPoolPassageRepository passageRepository;
    private final CurrentUserProvider users;
    private final ObjectProvider<MongoTransactionManager> transactions;
    private final QuestionPoolQuestionReadinessService readiness;
    private final QuestionPoolCoverageRepository coverageRepository;
    private final QuestionPoolRepository pool;

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public PublishQuestionPoolQuestionResponse publish(String questionId, PublishQuestionPoolQuestionRequest request) {
        String actor = users.requireCurrentUserId();
        ensureCoverage(List.of(questionId));
        return write(() -> {
            var prepared = preparePublication(questionId, request);
            fenceCoverage(List.of(coverageKey(prepared.source())));
            return publishPrepared(prepared, actor, Instant.now());
        });
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public RetireQuestionPoolQuestionResponse retire(String questionId, RetireQuestionPoolQuestionRequest request) {
        String actor = users.requireCurrentUserId();
        ensureCoverage(List.of(questionId));
        return write(() -> {
            var source = requireActiveRevision(questionId, request.revision());
            var key = coverageKey(source);
            fenceCoverage(key == null ? List.of() : List.of(key));
            Long before = activeCount(key);
            var retired = retirePrepared(source, actor, Instant.now());
            var coverage = coverage(source, before, activeCount(key));
            var warnings = coverageWarnings(List.of(coverage));
            if (!request.confirmCoverageWarning() && needsCoverageConfirmation(coverage)) {
                throw new QuestionCoverageConfirmationRequiredException(warnings);
            }
            return new RetireQuestionPoolQuestionResponse(retired.questionId(), QuestionPoolContentStatus.RETIRED,
                    retired.revision(), retired.retiredBy(), retired.retiredAt(), coverage, warnings);
        });
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public ReplaceQuestionPoolQuestionResponse replace(String questionId, ReplaceQuestionPoolQuestionRequest request) {
        String actor = users.requireCurrentUserId();
        ensureCoverage(List.of(questionId, request.replacementQuestionId()));
        return write(() -> {
            var old = requireActiveRevision(questionId, request.revision());
            if (questionId.equals(request.replacementQuestionId())) throw new InvalidQuestionException("Replacement must be a different question");
            var prepared = preparePublication(request.replacementQuestionId(), new PublishQuestionPoolQuestionRequest(
                    request.replacementRevision(), request.confirmedReviewChecks()));
            var oldKey = coverageKey(old);
            var newKey = coverageKey(prepared.source());
            var keys = new java.util.ArrayList<QuestionPoolCoverageKey>();
            if (oldKey != null) keys.add(oldKey);
            keys.add(newKey);
            fenceCoverage(keys);
            Long oldBefore = activeCount(oldKey);
            Long newBefore = java.util.Objects.equals(oldKey, newKey) ? oldBefore : activeCount(newKey);
            Instant now = Instant.now();
            var published = publishPrepared(prepared, actor, now);
            var retired = retirePrepared(old, actor, now);
            var coverage = new java.util.ArrayList<QuestionPoolCoverageResponse>();
            coverage.add(coverage(old, oldBefore, activeCount(oldKey)));
            if (!java.util.Objects.equals(oldKey, newKey)) coverage.add(coverage(prepared.source(), newBefore, activeCount(newKey)));
            var warnings = coverageWarnings(coverage);
            if (!request.confirmCoverageWarning() && coverage.stream().anyMatch(this::needsCoverageConfirmation)) {
                throw new QuestionCoverageConfirmationRequiredException(warnings);
            }
            return new ReplaceQuestionPoolQuestionResponse(new QuestionPoolQuestionRetirementResponse(retired.questionId(),
                    QuestionPoolContentStatus.RETIRED, retired.revision(), retired.retiredBy(), retired.retiredAt()), published, coverage, warnings);
        });
    }

    private PreparedPublication preparePublication(String questionId, PublishQuestionPoolQuestionRequest request) {
        var source = questions.findReadinessSource(questionId).orElseThrow(QuestionPoolQuestionNotFoundException::new);
        if (!"DRAFT".equals(source.status()) || !request.revision().equals(source.revision()) || request.revision() == Integer.MAX_VALUE) {
            throw new QuestionMutationConflictException("Only the current revision of a DRAFT question can be published");
        }
        var evaluation = readiness.check(questionId);
        if (!"DRAFT".equals(evaluation.status()) || !request.revision().equals(evaluation.revision())) {
            throw new QuestionMutationConflictException("Question changed during evaluation; reload before publishing");
        }
        if (!evaluation.structurallyValid()) throw new QuestionPublicationRejectedException("QUESTION_NOT_READY", "Question has publication blockers", evaluation);
        if (!request.confirmedReviewChecks().equals(Set.copyOf(evaluation.requiredReviewChecks()))) {
            throw new QuestionPublicationRejectedException("QUESTION_REVIEW_REQUIRED", "Confirm exactly the required checks for this question revision", evaluation);
        }
        return new PreparedPublication(source, evaluation);
    }

    private PublishQuestionPoolQuestionResponse publishPrepared(PreparedPublication prepared, String actor, Instant now) {
        var source = prepared.source();
        if (microSkills.retainActive(source.microSkill()).filter(value -> value.skill().equals(source.skill())).isEmpty()) {
            throw new QuestionMutationConflictException("Micro-skill is no longer active; reload before publishing");
        }
        var evaluation = prepared.evaluation();
        if (!blank(evaluation.passageId()) && !passageRepository.fenceQuestionPublication(evaluation.passageId(), evaluation.passageRevision(), source.skill(), source.level(), actor, now)) {
            throw new QuestionMutationConflictException("Passage changed while publishing; reload before retrying");
        }
        var review = QuestionPublicationReviewVo.builder().sourceRevision(source.revision())
                .passageId(evaluation.passageId()).passageRevision(evaluation.passageRevision())
                .confirmedChecks(evaluation.requiredReviewChecks()).reviewedBy(actor).reviewedAt(now).build();
        var saved = questions.publishDraft(source.questionId(), source.revision(), review).orElseThrow(() ->
                new QuestionMutationConflictException("Question changed while publishing; reload before retrying"));
        var recorded = saved.getPublicationReview();
        return new PublishQuestionPoolQuestionResponse(saved.getQuestionId(), saved.getStatus(), saved.getRevision(),
                recorded.getSourceRevision(), recorded.getPassageId(), recorded.getPassageRevision(), recorded.getReviewedBy(),
                recorded.getReviewedAt(), recorded.getConfirmedChecks(), evaluation.warnings());
    }

    private QuestionPoolQuestionReadinessSource requireActiveRevision(String questionId, int revision) {
        var source = questions.findReadinessSource(questionId).orElseThrow(QuestionPoolQuestionNotFoundException::new);
        if (!"ACTIVE".equals(source.status()) || !Integer.valueOf(revision).equals(source.revision()) || revision == Integer.MAX_VALUE) {
            throw new QuestionMutationConflictException("Only the current revision of an ACTIVE question can be retired or replaced");
        }
        if (source.malformedFields().contains("question_id")) throw new InvalidQuestionException("Question ID must be a scalar public identifier");
        return source;
    }

    private QuestionPoolQuestionRetirementSource retirePrepared(QuestionPoolQuestionReadinessSource source, String actor, Instant now) {
        return questions.retireActive(source.questionId(), source.revision(), actor, now).orElseThrow(() ->
                new QuestionMutationConflictException("Question changed while retiring; reload before retrying"));
    }

    private QuestionPoolCoverageKey coverageKey(QuestionPoolQuestionReadinessSource source) {
        if (blank(source.microSkill()) || blank(source.level())) return null;
        try { return new QuestionPoolCoverageKey(source.microSkill(), CefrLevel.valueOf(source.level())); }
        catch (IllegalArgumentException malformedLevel) { return null; }
    }

    private void fenceCoverage(List<QuestionPoolCoverageKey> keys) {
        keys.stream().distinct().sorted(java.util.Comparator.comparing(QuestionPoolCoverageKey::id)).forEach(coverageRepository::fence);
    }

    private void ensureCoverage(List<String> questionIds) {
        // Neutral fences must predate the snapshot; first-use creation inside it can defer a conflict until commit.
        questionIds.stream().distinct().map(id -> questions.findReadinessSource(id).orElseThrow(QuestionPoolQuestionNotFoundException::new))
                .map(this::coverageKey).filter(java.util.Objects::nonNull).distinct()
                .sorted(java.util.Comparator.comparing(QuestionPoolCoverageKey::id)).forEach(coverageRepository::ensure);
    }

    private Long activeCount(QuestionPoolCoverageKey key) {
        return key == null ? null : pool.countActive(key.microSkill(), key.level().name());
    }

    private QuestionPoolCoverageResponse coverage(QuestionPoolQuestionReadinessSource source, Long before, Long after) {
        return new QuestionPoolCoverageResponse(source.microSkill(), source.level(), before, after, 30, after == null ? null : after >= 30);
    }

    private List<QuestionPoolCoverageWarningResponse> coverageWarnings(List<QuestionPoolCoverageResponse> coverage) {
        var result = new java.util.ArrayList<QuestionPoolCoverageWarningResponse>();
        for (var value : coverage) {
            if (value.activeQuestionCountAfter() == null) result.add(new QuestionPoolCoverageWarningResponse("COVERAGE_UNDETERMINED",
                    "Không xác định được độ phủ vì phân loại câu hỏi đã lưu không hợp lệ.", value));
            else if (value.activeQuestionCountAfter() < 30) result.add(new QuestionPoolCoverageWarningResponse("BELOW_MINIMUM_ACTIVE_QUESTIONS",
                    "Nhóm kỹ năng và trình độ còn dưới 30 câu ACTIVE, chưa đủ điều kiện tạo lộ trình mới.", value));
        }
        return result;
    }

    private boolean needsCoverageConfirmation(QuestionPoolCoverageResponse coverage) {
        return coverage.activeQuestionCountAfter() == null || (coverage.activeQuestionCountAfter() < 30
                && coverage.activeQuestionCountBefore() != null && coverage.activeQuestionCountAfter() < coverage.activeQuestionCountBefore());
    }

    private record PreparedPublication(QuestionPoolQuestionReadinessSource source, QuestionPoolQuestionReadinessResponse evaluation) {}

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionMutationResponse create(CreateQuestionPoolQuestionRequest request) {
        String actor = users.requireCurrentUserId();
        return write(() -> {
            var question = QuestionPoolQuestion.builder().questionId("LP-Q-" + UUID.randomUUID())
                    .skill(request.skill()).microSkill(request.microSkill().strip()).level(request.level())
                    .passageId(normalizePassageId(request.passageId())).prompt(request.prompt().strip())
                    .options(options(request.options())).correctOptionId(request.correctOptionId())
                    .explanation(request.explanation().strip()).status(QuestionPoolContentStatus.DRAFT).revision(1)
                    .createdBy(actor).updatedBy(actor).createdAt(Instant.now()).updatedAt(Instant.now()).build();
            validateContent(question);
            fencePassage(question, actor);
            return mutation(questions.insert(question));
        });
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionMutationResponse update(String questionId, UpdateQuestionPoolQuestionRequest request) {
        String actor = users.requireCurrentUserId();
        return write(() -> {
            var question = requireQuestion(questionId);
            requireDraftRevision(question, request.revision());
            if (request.revision() == Integer.MAX_VALUE) {
                throw new QuestionMutationConflictException("Question revision limit reached");
            }
            if (request.skill() == null && request.microSkill() == null && request.level() == null
                    && request.passageId() == null && request.prompt() == null && request.options() == null
                    && request.correctOptionId() == null && request.explanation() == null) {
                throw new InvalidQuestionException("Supply at least one question field");
            }
            if (request.skill() != null) question.setSkill(request.skill());
            if (request.microSkill() != null) question.setMicroSkill(request.microSkill().strip());
            if (request.level() != null) question.setLevel(request.level());
            if (request.passageId() != null) question.setPassageId(normalizePassageId(request.passageId()));
            if (request.prompt() != null) question.setPrompt(request.prompt().strip());
            if (request.options() != null) question.setOptions(options(request.options()));
            if (request.correctOptionId() != null) question.setCorrectOptionId(request.correctOptionId());
            if (request.explanation() != null) question.setExplanation(request.explanation().strip());
            validateContent(question);
            fencePassage(question, actor);
            question.setUpdatedAt(Instant.now());
            question.setUpdatedBy(actor);
            return mutation(questions.updateDraft(question, request.revision()).orElseThrow(() ->
                    new QuestionMutationConflictException("Question changed while saving; reload before retrying")));
        });
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public DeleteQuestionPoolQuestionResponse delete(String questionId, int revision) {
        users.requireCurrentUserId();
        return write(() -> {
            requireDraftRevision(requireQuestion(questionId), revision);
            if (!questions.deleteDraft(questionId, revision)) {
                throw new QuestionMutationConflictException("Question changed while deleting; reload before retrying");
            }
            return new DeleteQuestionPoolQuestionResponse(questionId, true);
        });
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionMutationResponse copy(String questionId, int revision) {
        String actor = users.requireCurrentUserId();
        return write(() -> {
            var source = requireQuestion(questionId);
            if (!Integer.valueOf(revision).equals(source.getRevision())) {
                throw new QuestionMutationConflictException("Source revision changed; reload before copying");
            }
            var question = QuestionPoolQuestion.builder().questionId("LP-Q-" + UUID.randomUUID())
                    .skill(source.getSkill()).microSkill(source.getMicroSkill()).level(source.getLevel())
                    .passageId(source.getPassageId()).prompt(source.getPrompt()).correctOptionId(source.getCorrectOptionId())
                    .explanation(source.getExplanation()).options(source.getOptions() == null ? null : source.getOptions().stream()
                            .map(option -> option == null ? null : QuestionPoolOptionVo.builder()
                                    .optionId(option.getOptionId()).text(option.getText()).build()).toList())
                    .status(QuestionPoolContentStatus.DRAFT).revision(1).createdBy(actor).updatedBy(actor)
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
            validateContent(question);
            fencePassage(question, actor);
            return mutation(questions.insert(question));
        });
    }

    private QuestionPoolQuestion requireQuestion(String questionId) {
        return questions.findByQuestionId(questionId).orElseThrow(QuestionPoolQuestionNotFoundException::new);
    }

    private void requireDraftRevision(QuestionPoolQuestion question, int revision) {
        if (question.getStatus() != QuestionPoolContentStatus.DRAFT || !Integer.valueOf(revision).equals(question.getRevision())) {
            throw new QuestionMutationConflictException("Only the current revision of a DRAFT question can be changed");
        }
    }

    private void validateContent(QuestionPoolQuestion question) {
        if (question.getSkill() == null || question.getLevel() == null || question.getMicroSkill() == null
                || !microSkills.retainActive(question.getMicroSkill())
                        .filter(value -> value.skill().equals(question.getSkill().name())).isPresent()) {
            throw new InvalidQuestionException("Use a supported canonical micro-skill belonging to the selected skill");
        }
        if (blank(question.getPrompt()) || question.getPrompt().length() > 50000
                || blank(question.getExplanation()) || question.getExplanation().length() > 50000) {
            throw new InvalidQuestionException("Prompt and explanation must be nonblank and at most 50000 characters");
        }
        var options = question.getOptions();
        if (options == null || options.size() != 4 || options.stream().anyMatch(option -> option == null
                || blank(option.getOptionId()) || blank(option.getText()) || option.getText().length() > 10000)
                || !options.stream().map(QuestionPoolOptionVo::getOptionId).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("A", "B", "C", "D")) || !Set.of("A", "B", "C", "D").contains(question.getCorrectOptionId() == null ? "" : question.getCorrectOptionId())) {
            throw new InvalidQuestionException("Supply exactly four nonblank choices A-D and one correct choice A-D");
        }
        if (question.getPassageId() != null && question.getPassageId().length() > 100) {
            throw new InvalidQuestionException("Passage ID must be at most 100 characters");
        }
        if (question.getSkill() == QuestionPoolSkill.LISTENING && blank(question.getPassageId())) {
            throw new InvalidQuestionException("Listening requires a passage reference");
        }
    }

    private void fencePassage(QuestionPoolQuestion question, String actor) {
        if (blank(question.getPassageId())) return;
        var passage = passageRepository.findReadinessSource(question.getPassageId())
                .orElseThrow(() -> new InvalidQuestionException("Referenced passage was not found"));
        if (question.getSkill() == null || question.getLevel() == null || !question.getSkill().name().equals(passage.skill())
                || !question.getLevel().name().equals(passage.level())
                || !Set.of("DRAFT", "ACTIVE", "RETIRED").contains(passage.status() == null ? "" : passage.status())) {
            throw new InvalidQuestionException("Passage must have matching skill and level and a recognized status");
        }
        if (passage.revision() == null || passage.revision() < 1 || passage.revision() == Integer.MAX_VALUE) {
            throw new QuestionMutationConflictException("Passage revision is invalid or exhausted");
        }
        // The revision write makes concurrent passage edits/deletion lose their existing CAS predicate.
        if (!passageRepository.fenceQuestionReference(passage.passageId(), passage.revision(), passage.skill(), passage.level(), actor, Instant.now())) {
            throw new QuestionMutationConflictException("Passage changed while saving the question; reload before retrying");
        }
    }

    private <T> T write(Supplier<T> operation) {
        var manager = transactions.getIfAvailable();
        if (manager == null) throw new QuestionWriteUnavailableException("MongoDB transactions must be enabled for question authoring");
        try {
            return new QuestionPoolTransactionTemplate(manager).execute(status -> operation.get());
        } catch (DataAccessException | TransactionException failure) {
            if (failure instanceof org.springframework.dao.ConcurrencyFailureException) {
                throw new QuestionMutationConflictException("Concurrent content change; reload before retrying");
            }
            boolean concurrentChange = false;
            boolean uncertainCommit = false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof com.mongodb.MongoException mongo) {
                    concurrentChange |= mongo.hasErrorLabel("TransientTransactionError");
                    uncertainCommit |= mongo.hasErrorLabel("UnknownTransactionCommitResult");
                }
            }
            if (concurrentChange && !uncertainCommit) {
                throw new QuestionMutationConflictException("Concurrent content change; reload before retrying");
            }
            throw new QuestionWriteUnavailableException(failure);
        }
    }

    private List<QuestionPoolOptionVo> options(List<QuestionPoolQuestionOptionRequest> options) {
        return options.stream().map(option -> QuestionPoolOptionVo.builder().optionId(option.optionId()).text(option.text().strip()).build()).toList();
    }

    private String normalizePassageId(String value) { return blank(value) ? null : value.strip(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private QuestionPoolQuestionMutationResponse mutation(QuestionPoolQuestion question) {
        return new QuestionPoolQuestionMutationResponse(question.getQuestionId(), question.getStatus(), question.getRevision(),
                question.getCreatedBy(), question.getUpdatedBy(), question.getCreatedAt(), question.getUpdatedAt());
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionListResponse list(ListQuestionPoolQuestionsRequest request) {
        var page = questions.search(request.keyword(), request.skill(), request.microSkill(), request.level(),
                request.status(), request.passageId(), request.page(), request.size());
        var items = page.getContent().stream().map(question -> new QuestionPoolQuestionListItemResponse(
                question.getQuestionId(), question.getSkill(), question.getMicroSkill(), question.getLevel(),
                question.getPassageId(), question.getPrompt(), question.getStatus(), question.getRevision(),
                question.getCreatedAt(), question.getUpdatedAt())).toList();
        return new QuestionPoolQuestionListResponse(items, request.page(), request.size(), page.getTotalElements(), page.getTotalPages(), this.microSkills.list());
    }

    @Override
    @PreAuthorize("hasRole('CONTENT_MANAGER')")
    public QuestionPoolQuestionDetailResponse detail(String questionId) {
        var question = questions.findByQuestionId(questionId).orElseThrow(QuestionPoolQuestionNotFoundException::new);
        QuestionPoolPassageDetailResponse passage = null;
        if (question.getPassageId() != null && !question.getPassageId().isBlank()) {
            try {
                passage = passages.detail(question.getPassageId());
            } catch (QuestionPoolPassageNotFoundException missingReference) {
                // Keep the question inspectable so an editor can see its dangling passage reference.
            }
        }
        var options = question.getOptions() == null ? null : question.getOptions().stream()
                .map(option -> option == null ? null : new QuestionPoolQuestionOptionResponse(option.getOptionId(), option.getText())).toList();
        var review = question.getPublicationReview();
        var publicationReview = review == null ? null : new QuestionPoolQuestionPublicationReviewResponse(
                review.getSourceRevision(), review.getPassageId(), review.getPassageRevision(), review.getConfirmedChecks(), review.getReviewedBy(), review.getReviewedAt());
        return new QuestionPoolQuestionDetailResponse(question.getQuestionId(), question.getSkill(), question.getMicroSkill(),
                question.getLevel(), question.getPassageId(), question.getPrompt(), options, question.getCorrectOptionId(),
                question.getExplanation(), question.getStatus(), question.getRevision(), question.getCreatedBy(), question.getUpdatedBy(),
                question.getCreatedAt(), question.getUpdatedAt(), passage, publicationReview, this.microSkills.list());
    }
}
