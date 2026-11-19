package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionImportRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionRequest.OptionRequest;
import fit.iuh.engolearn.placement.dto.request.PlacementAdminQuestionStatusRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionMediaResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.ImportResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.OptionResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminQuestionResponse.PageResponse;
import fit.iuh.engolearn.placement.exception.PlacementQuestionValidationException;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementQuestion;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.vo.PlacementQuestionOptionVo;
import fit.iuh.engolearn.placement.repository.PlacementQuestionRepository;
import fit.iuh.engolearn.placement.service.PlacementAdminQuestionMedia;
import fit.iuh.engolearn.placement.service.PlacementAdminQuestionService;
import fit.iuh.engolearn.placement.service.PlacementTransactionExecutor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementAdminQuestionServiceImpl implements PlacementAdminQuestionService {
    private static final Pattern QUESTION_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,79}");
    private static final Set<String> OPTION_LABELS = Set.of("A", "B", "C", "D");
    private final PlacementQuestionRepository questions;
    private final PlacementAdminQuestionMedia media;
    private final PlacementTransactionExecutor transactions;

    @Override
    public PageResponse search(String search, String skill, String level, String status, int page, int size) {
        var result = questions.findAdminPage(clean(search), parseFilter(skill, "skill", PlacementSkill.class),
                parseFilter(level, "level", PlacementLevel.class),
                parseFilter(status, "status", PlacementQuestionStatus.class), page, size);
        return new PageResponse(result.items().stream().map(this::response).toList(), result.total(), page, size);
    }

    @Override
    public PlacementAdminQuestionResponse get(String id) {
        return response(load(id));
    }

    @Override
    public PlacementAdminQuestionResponse create(PlacementAdminQuestionRequest request) {
        try {
            return response(questions.insert(draft(request)));
        } catch (DuplicateKeyException conflict) {
            throw duplicateCode();
        }
    }

    @Override
    public PlacementAdminQuestionResponse update(String id, PlacementAdminQuestionRequest request) {
        PlacementQuestion question = load(id);
        long revision = requireRevision(question, request.revision());
        applyContent(question, request);
        if (question.getStatus() == PlacementQuestionStatus.ACTIVE) activate(question);
        try {
            return response(questions.updateIfCurrent(question, revision).orElseThrow(this::staleRevision));
        } catch (DuplicateKeyException conflict) {
            throw duplicateCode();
        }
    }

    @Override
    public PlacementAdminQuestionResponse changeStatus(String id, PlacementAdminQuestionStatusRequest request) {
        PlacementQuestion question = load(id);
        long revision = requireRevision(question, request.revision());
        if (request.status() == PlacementQuestionStatus.ACTIVE) {
            validateContent(question);
            activate(question);
        } else if (request.status() != PlacementQuestionStatus.INACTIVE) {
            throw invalid("status", "Choose ACTIVE or INACTIVE.");
        }
        question.setStatus(request.status());
        return response(questions.updateStatusIfCurrent(question, revision).orElseThrow(this::staleRevision));
    }

    @Override
    public ImportResponse importQuestions(PlacementAdminQuestionImportRequest request) {
        List<PlacementQuestion> drafts = new ArrayList<>(request.questions().size());
        Set<String> codes = new HashSet<>();
        for (PlacementAdminQuestionRequest row : request.questions()) {
            int index = drafts.size();
            PlacementQuestion question;
            try {
                question = draft(row);
                if (!codes.add(question.getCode())) throw invalid("code", "This code appears twice in the import.");
            } catch (PlacementQuestionValidationException failure) {
                Map<String, String> fields = new LinkedHashMap<>();
                failure.fields().forEach((field, message) -> fields.put("questions[" + index + "]." + field, message));
                throw new PlacementQuestionValidationException(fields);
            }
            drafts.add(question);
        }
        try {
            return transactions.execute(() -> {
                questions.insertAll(drafts);
                return new ImportResponse(drafts.size());
            });
        } catch (DuplicateKeyException conflict) {
            // A commit with an unknown result may be retried after these exact IDs were inserted.
            List<String> createdIds = drafts.stream().map(PlacementQuestion::getId).toList();
            if (questions.findAllById(createdIds).size() == drafts.size()) {
                return new ImportResponse(drafts.size());
            }
            throw duplicateCode();
        }
    }

    @Override
    public PlacementAdminQuestionMediaResponse mediaCatalog() {
        return media.catalog(questions.findListeningAudioTitles());
    }

    private PlacementQuestion load(String id) {
        return questions.findById(id).orElseThrow(() -> new PlacementRequestException(HttpStatus.NOT_FOUND,
                "PLACEMENT_QUESTION_NOT_FOUND", "The Placement question was not found."));
    }

    private PlacementQuestion draft(PlacementAdminQuestionRequest request) {
        PlacementQuestion question = PlacementQuestion.builder().id(UUID.randomUUID().toString())
                .skill(request.skill()).status(PlacementQuestionStatus.DRAFT).build();
        applyContent(question, request);
        return question;
    }

    private void applyContent(PlacementQuestion question, PlacementAdminQuestionRequest request) {
        if (request.skill() != question.getSkill()) throw invalid("skill", "The question skill is fixed after creation.");
        question.setCode(clean(request.code()));
        question.setLevel(request.level());
        question.setPrompt(clean(request.prompt()));
        question.setPassage(clean(request.passage()));
        question.setOptions(request.options() == null ? List.of() : request.options().stream()
                .filter(Objects::nonNull).map(this::toOption).toList());
        question.setCorrectOptionId(clean(request.correctOptionId()));
        question.setExplanation(clean(request.explanation()));
        question.setTranscript(clean(request.transcript()));
        validateContent(question);
        question.setCode(normalize(question.getCode()));
        if (question.getSkill() == PlacementSkill.LISTENING) updateAudio(question, clean(request.audioUrl()));
    }

    private PlacementQuestionOptionVo toOption(OptionRequest option) {
        return new PlacementQuestionOptionVo(clean(option.id()), normalize(option.label()), clean(option.text()));
    }

    private void updateAudio(PlacementQuestion question, String url) {
        if (url.equals(clean(media.resolve(question)))) return;
        question.setAdminAudioOverride(true);
        if (url.isEmpty()) {
            question.setAudioUrl(null);
            question.setAudioDurationSeconds(null);
        } else {
            var selection = media.requireSelection(url);
            question.setAudioUrl(selection.audioUrl());
            question.setAudioDurationSeconds(selection.durationSeconds());
        }
    }

    private void validateContent(PlacementQuestion question) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!QUESTION_CODE.matcher(clean(question.getCode())).matches()) {
            errors.put("code", "Use up to 80 letters, numbers, hyphens or underscores, starting with a letter or number.");
        }
        if (question.getSkill() == null) errors.put("skill", "Choose READING or LISTENING.");
        if (question.getLevel() == null) errors.put("level", "Choose a level from A1 to B2.");
        if (clean(question.getPrompt()).isEmpty()) errors.put("prompt", "Enter the question prompt.");

        Set<String> ids = new HashSet<>();
        Set<String> labels = new HashSet<>();
        if (question.getOptions() == null || question.getOptions().size() != 4) {
            errors.put("options", "Enter four choices labeled A through D.");
        } else {
            for (PlacementQuestionOptionVo option : question.getOptions()) {
                if (option == null || clean(option.getId()).isEmpty() || clean(option.getText()).isEmpty()
                        || !ids.add(option.getId()) || !labels.add(option.getLabel())) {
                    errors.put("options", "Each choice needs a unique ID, a unique label and text.");
                }
            }
            if (!labels.equals(OPTION_LABELS)) errors.putIfAbsent("options", "Use labels A through D.");
        }
        if (!ids.contains(question.getCorrectOptionId())) {
            errors.put("correctOptionId", "Choose the correct answer from the four choices.");
        }
        if (!errors.isEmpty()) throw new PlacementQuestionValidationException(errors);
    }

    private void activate(PlacementQuestion question) {
        if (question.getSkill() == PlacementSkill.READING && clean(question.getPassage()).isEmpty()) {
            throw invalid("passage", "A Reading question needs a passage.");
        }
        if (question.getSkill() == PlacementSkill.LISTENING) {
            media.requirePlayable(question);
            String audioUrl = media.resolve(question);
            if (audioUrl == null) throw invalid("audioUrl", "Select a verified Placement WAV.");
            question.setAudioUrl(audioUrl);
        }
    }

    private PlacementAdminQuestionResponse response(PlacementQuestion question) {
        String audioUrl = media.resolve(question);
        List<OptionResponse> options = question.getOptions() == null ? List.of() : question.getOptions().stream()
                .filter(Objects::nonNull).map(option -> new OptionResponse(clean(option.getId()), clean(option.getLabel()),
                        clean(option.getText()))).toList();
        return new PlacementAdminQuestionResponse(question.getId(), clean(question.getCode()), question.getSkill(),
                question.getLevel(), clean(question.getPrompt()), clean(question.getPassage()), options,
                clean(question.getCorrectOptionId()), clean(question.getExplanation()), clean(question.getTranscript()),
                clean(audioUrl), question.getStatus(), question.getRevision(), audioUrl != null);
    }

    private long requireRevision(PlacementQuestion question, Long revision) {
        if (revision == null || revision < 0) throw invalid("revision", "Provide the revision you edited.");
        if (question.getRevision() != revision) throw staleRevision();
        return revision;
    }

    private PlacementRequestException staleRevision() {
        return new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_QUESTION_REVISION_CONFLICT",
                "This question has changed. Reload it before saving.");
    }

    private PlacementRequestException duplicateCode() {
        return new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_QUESTION_DUPLICATE_CODE",
                "A Placement question with this code already exists.");
    }

    private PlacementQuestionValidationException invalid(String field, String message) {
        return new PlacementQuestionValidationException(Map.of(field, message));
    }

    private <T extends Enum<T>> T parseFilter(String value, String field, Class<T> type) {
        String filter = normalize(value);
        if (filter.isEmpty()) return null;
        try {
            return Enum.valueOf(type, filter);
        } catch (IllegalArgumentException unknown) {
            throw invalid(field, "Choose a supported filter value.");
        }
    }

    private String normalize(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
