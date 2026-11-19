package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.request.CreatePlacementTestSetRequest;
import fit.iuh.engolearn.placement.dto.request.UpdatePlacementTestSetRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetPageResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetResponse.QuestionResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.mapper.PlacementAdminTestSetMapper;
import fit.iuh.engolearn.placement.model.PlacementLegacySeed;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.repository.PlacementAdminTestSetRepository;
import fit.iuh.engolearn.placement.repository.PlacementQuestionRepository;
import fit.iuh.engolearn.placement.service.PlacementAdminQuestionMedia;
import fit.iuh.engolearn.placement.service.PlacementAdminTestSetService;
import fit.iuh.engolearn.placement.service.PlacementTestSetSnapshotFactory;
import fit.iuh.engolearn.placement.service.PlacementTransactionExecutor;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementAdminTestSetServiceImpl implements PlacementAdminTestSetService {
    private final PlacementAdminTestSetRepository sets;
    private final PlacementQuestionRepository questions;
    private final PlacementAdminQuestionMedia media;
    private final PlacementTransactionExecutor transactions;
    private final Clock placementClock;

    @Override
    public PlacementAdminTestSetPageResponse list(String search, PlacementTestSetStatus status, int page, int size) {
        var result = sets.list(search, status, page, size);
        var items = result.items().stream().map(set -> PlacementAdminTestSetMapper.toResponse(set, List.of())).toList();
        return new PlacementAdminTestSetPageResponse(items, result.total(), page, size);
    }

    @Override
    public PlacementAdminTestSetResponse get(String id) {
        PlacementTestSet set = requireSet(id);
        if (set.getStatus() != PlacementTestSetStatus.DRAFT || set.getQuestionIds() == null) {
            return snapshot(set);
        }
        return PlacementAdminTestSetMapper.toResponse(set, liveSnapshot(orderedQuestions(set.orderedQuestionIds())));
    }

    @Override
    public PlacementAdminTestSetResponse create(CreatePlacementTestSetRequest request) {
        String id = UUID.randomUUID().toString();
        String code = "PLT-" + id.replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return PlacementAdminTestSetMapper.toResponse(insertDraft(id, code, request.name().strip(), 1, List.of()),
                List.of());
    }

    @Override
    public PlacementAdminTestSetResponse update(String id, UpdatePlacementTestSetRequest request) {
        PlacementTestSet draft = requireEditableDraft(id, request.revision());
        List<PlacementQuestion> ordered = requireOrderedQuestions(request.questionIds());
        validateDraftSelection(ordered, Set.copyOf(draft.orderedQuestionIds()));
        List<QuestionResponse> preview = liveSnapshot(ordered);
        PlacementTestSet saved = sets.saveDraft(id, request.revision(), request.name().strip(), request.questionIds(),
                placementClock.instant()).orElseThrow(PlacementAdminTestSetServiceImpl::staleRevision);
        return PlacementAdminTestSetMapper.toResponse(saved, preview);
    }

    @Override
    public PlacementAdminTestSetResponse publish(String id, UpdatePlacementTestSetRequest request) {
        AtomicReference<PlacementTestSet> publication = new AtomicReference<>();
        return transactions.execute(() -> {
            // The transaction executor can retry after an ambiguous commit outcome.
            PlacementTestSet previousResult = publication.get();
            if (previousResult != null && sets.findById(id).filter(previousResult::equals).isPresent()) {
                return snapshot(previousResult);
            }
            PlacementTestSet draft = requireEditableDraft(id, request.revision());
            List<PlacementQuestion> ordered = requireOrderedQuestions(request.questionIds());
            for (PlacementQuestion question : ordered) {
                if (question.getStatus() != PlacementQuestionStatus.ACTIVE) {
                    throw invalid("Question " + question.getCode() + " must be ACTIVE before publication");
                }
                if (question.getSkill() == PlacementSkill.LISTENING) {
                    media.requirePlayable(question);
                    String audioUrl = media.resolve(question);
                    if (audioUrl == null || audioUrl.isBlank()) {
                        throw invalid("Question " + question.getCode() + " has no playable audio");
                    }
                    question.setAudioUrl(audioUrl);
                }
            }
            PlacementTestSet assembled = assemble(draft, request.name().strip(), ordered);
            ordered.stream().sorted(Comparator.comparing(PlacementQuestion::getId)).forEach(question -> {
                if (!sets.guardActiveQuestion(question)) {
                    throw conflict("PLACEMENT_TEST_SET_QUESTION_CHANGED", "A selected question changed during publication");
                }
            });
            PlacementTestSet published = sets.publish(id, request.revision(), assembled.getName(), request.questionIds(),
                    assembled.getItems(), placementClock.instant())
                    .orElseThrow(PlacementAdminTestSetServiceImpl::staleRevision);
            publication.set(published);
            return snapshot(published);
        });
    }

    @Override
    public PlacementAdminTestSetResponse newVersion(String id) {
        PlacementTestSet source = requireSet(id);
        if (source.getStatus() == PlacementTestSetStatus.DRAFT && !PlacementLegacySeed.matches(source)) {
            throw conflict("PLACEMENT_TEST_SET_DRAFT_EXISTS", "This draft can be edited directly");
        }
        if (sets.hasEditableDraft(source.getCode())) {
            throw conflict("PLACEMENT_TEST_SET_DRAFT_EXISTS", "An editable draft already exists for this set");
        }
        List<String> ids = source.orderedQuestionIds();
        List<QuestionResponse> preview = liveSnapshot(orderedQuestions(ids));
        PlacementTestSet draft = insertDraft(UUID.randomUUID().toString(), source.getCode(), source.getName(),
                sets.latestVersion(source.getCode()) + 1, ids);
        return PlacementAdminTestSetMapper.toResponse(draft, preview);
    }

    @Override
    public PlacementAdminTestSetResponse archive(String id, long revision) {
        PlacementTestSet set = requireSet(id);
        assertRevision(set, revision);
        if (set.getStatus() != PlacementTestSetStatus.PUBLISHED) {
            throw conflict("PLACEMENT_TEST_SET_NOT_PUBLISHED", "Only a published set can be archived");
        }
        return snapshot(sets.archive(id, revision, placementClock.instant())
                .orElseThrow(PlacementAdminTestSetServiceImpl::staleRevision));
    }

    @Override
    public void delete(String id, long revision) {
        requireEditableDraft(id, revision);
        if (!sets.deleteDraft(id, revision)) throw staleRevision();
    }

    private PlacementTestSet insertDraft(String id, String code, String name, int version, List<String> questionIds) {
        Instant now = placementClock.instant();
        try {
            return sets.insert(PlacementTestSet.builder().id(id).code(code).name(name).version(version)
                    .status(PlacementTestSetStatus.DRAFT).questionIds(questionIds).revision(0)
                    .createdAt(now).updatedAt(now).build());
        } catch (DuplicateKeyException exception) {
            throw conflict("PLACEMENT_TEST_SET_VERSION_EXISTS", "A draft or version with this code already exists");
        }
    }

    private PlacementTestSet requireSet(String id) {
        return sets.findById(id).orElseThrow(() -> new PlacementRequestException(HttpStatus.NOT_FOUND,
                "PLACEMENT_TEST_SET_NOT_FOUND", "Test set does not exist"));
    }

    private PlacementTestSet requireEditableDraft(String id, long revision) {
        PlacementTestSet set = requireSet(id);
        assertRevision(set, revision);
        if (set.getStatus() != PlacementTestSetStatus.DRAFT) {
            throw conflict("PLACEMENT_TEST_SET_NOT_DRAFT", "Only a draft can be edited, published or deleted");
        }
        if (PlacementLegacySeed.matches(set) || sets.hasAttempts(set)) {
            throw conflict("PLACEMENT_TEST_SET_SNAPSHOT_PROTECTED", "This test set snapshot is preserved for learner attempts");
        }
        return set;
    }

    private List<PlacementQuestion> requireOrderedQuestions(List<String> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalid("Each question ID must be unique");
        }
        List<PlacementQuestion> ordered = orderedQuestions(ids);
        if (ordered.size() != ids.size()) {
            throw invalid("One or more selected questions no longer exist");
        }
        return ordered;
    }

    private List<PlacementQuestion> orderedQuestions(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        var byId = questions.findAllById(ids).stream()
                .collect(Collectors.toMap(PlacementQuestion::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static void validateDraftSelection(List<PlacementQuestion> questions, Set<String> previousIds) {
        boolean listening = false;
        for (PlacementQuestion question : questions) {
            if (question.getStatus() != PlacementQuestionStatus.ACTIVE && !previousIds.contains(question.getId())) {
                throw invalid("Only ACTIVE questions can be added to a draft");
            }
            if (question.getSkill() == PlacementSkill.LISTENING) listening = true;
            else if (question.getSkill() != PlacementSkill.READING || listening) {
                throw invalid("Reading questions must precede Listening questions");
            }
        }
    }

    private static PlacementTestSet assemble(PlacementTestSet draft, String name, List<PlacementQuestion> ordered) {
        try {
            return PlacementTestSetSnapshotFactory.assemble(draft.getId(), draft.getCode(), name, draft.getVersion(),
                    PlacementTestSetStatus.PUBLISHED, ordered);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private static PlacementAdminTestSetResponse snapshot(PlacementTestSet set) {
        PlacementQuestionStatus status = set.getStatus() == PlacementTestSetStatus.DRAFT
                ? PlacementQuestionStatus.DRAFT : PlacementQuestionStatus.ACTIVE;
        return PlacementAdminTestSetMapper.toResponse(set, set.orderedItems().stream()
                .map(item -> PlacementAdminTestSetMapper.snapshotQuestion(item, status)).toList());
    }

    private List<QuestionResponse> liveSnapshot(List<PlacementQuestion> ordered) {
        return ordered.stream().map(question -> PlacementAdminTestSetMapper.liveQuestion(question, media.resolve(question)))
                .toList();
    }

    private static void assertRevision(PlacementTestSet set, long revision) {
        if (set.getRevision() != revision) throw staleRevision();
    }

    private static PlacementRequestException staleRevision() {
        return conflict("PLACEMENT_TEST_SET_STALE_REVISION", "This test set changed. Reload it before retrying.");
    }

    private static PlacementRequestException conflict(String code, String detail) {
        return new PlacementRequestException(HttpStatus.CONFLICT, code, detail);
    }

    private static PlacementRequestException invalid(String detail) {
        return new PlacementRequestException(HttpStatus.UNPROCESSABLE_ENTITY, "PLACEMENT_TEST_SET_INVALID", detail);
    }
}
