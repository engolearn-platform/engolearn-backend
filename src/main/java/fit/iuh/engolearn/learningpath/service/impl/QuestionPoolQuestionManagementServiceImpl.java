package fit.iuh.engolearn.learningpath.service.impl;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.QuestionPoolQuestionOptionRequest;
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
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class QuestionPoolQuestionManagementServiceImpl implements QuestionPoolQuestionManagementService {
    private static final Map<String, QuestionPoolSkill> MICRO_SKILLS = Map.of(
            "Listening for Gist", QuestionPoolSkill.LISTENING,
            "Listening for Specific Information", QuestionPoolSkill.LISTENING,
            "Listening for Detail & Relationship", QuestionPoolSkill.LISTENING,
            "Listening for Inference", QuestionPoolSkill.LISTENING,
            "Listening for Attitude / Purpose", QuestionPoolSkill.LISTENING,
            "Reading for Gist", QuestionPoolSkill.READING,
            "Reading for Specific Information", QuestionPoolSkill.READING,
            "Reading for Detail & Relationship", QuestionPoolSkill.READING,
            "Vocabulary in Context", QuestionPoolSkill.READING,
            "Inference & Author's View", QuestionPoolSkill.READING);
    private final QuestionPoolQuestionRepository questions;
    private final QuestionPoolPassageManagementService passages;
    private final QuestionPoolPassageRepository passageRepository;
    private final CurrentUserProvider users;
    private final ObjectProvider<MongoTransactionManager> transactions;

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
                || MICRO_SKILLS.get(question.getMicroSkill()) != question.getSkill()) {
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
            return new TransactionTemplate(manager).execute(status -> operation.get());
        } catch (DataAccessException | TransactionException failure) {
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
        return new QuestionPoolQuestionListResponse(items, request.page(), request.size(), page.getTotalElements(), page.getTotalPages());
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
        return new QuestionPoolQuestionDetailResponse(question.getQuestionId(), question.getSkill(), question.getMicroSkill(),
                question.getLevel(), question.getPassageId(), question.getPrompt(), options, question.getCorrectOptionId(),
                question.getExplanation(), question.getStatus(), question.getRevision(), question.getCreatedBy(), question.getUpdatedBy(),
                question.getCreatedAt(), question.getUpdatedAt(), passage);
    }
}
