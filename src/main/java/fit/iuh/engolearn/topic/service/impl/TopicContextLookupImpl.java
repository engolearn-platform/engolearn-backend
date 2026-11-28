package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.contract.TopicContextLookup;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.contract.dto.TopicLearningContextDto.*;
import fit.iuh.engolearn.topic.contract.enums.TopicContextStage;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicAttemptContentMapper;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.repository.*;
import fit.iuh.engolearn.topic.service.TopicGenerationService;
import fit.iuh.engolearn.topic.service.TopicRetryService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TopicContextLookupImpl implements TopicContextLookup {
    private final TopicRepository topics;
    private final TopicVersionRepository releases;
    private final TopicItemVersionRepository versions;
    private final UserTopicItemAttemptRepository attempts;
    private final TopicGenerationService generations;
    private final TopicRetryService retries;

    @Override public Optional<TopicOverviewContextDto> getOverview(String learnerId, String topicId) {
        if (blank(learnerId) || blank(topicId)) return Optional.empty();
        Topic topic = topics.findById(topicId).orElse(null);
        TopicVersion release = publishedRelease(topic);
        if (release == null) return Optional.empty();
        var items = visibleMetadata(topic, release).stream()
                .sorted(Comparator.comparing(TopicItemVersion::getOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TopicItemVersion::getTopicItemId))
                .map(item -> new TopicOverviewContextDto.ItemDto(item.getTitle(), item.getDescription())).toList();
        return Optional.of(new TopicOverviewContextDto(overview(topic), items.stream().limit(50).toList(), items.size()));
    }

    @Override public Optional<TopicLearningContextDto<?>> getLearningContext(String learnerId, String attemptId, TopicLearningPositionDto position) {
        if (blank(learnerId) || blank(attemptId) || position == null || position.stage() == null || position.itemIndex() < 0)
            return Optional.empty();
        UserTopicItemAttempt attempt;
        try {
            attempt = attempts.findById(attemptId).orElseGet(() -> retries.owned(learnerId, attemptId, false));
        } catch (TopicRequestException unavailable) {
            if (unavailable.getStatus() >= 400 && unavailable.getStatus() < 500) return Optional.empty();
            throw unavailable;
        }
        if (attempt == null || !learnerId.equals(attempt.getUserId()) || attempt.getStatus() != AttemptStatus.IN_PROGRESS
                || !generations.matches(learnerId, attempt.getTopicId(), attempt.getGeneration())
                || !positionReached(attempt, position)) return Optional.empty();
        Topic topic = topics.findById(attempt.getTopicId()).orElse(null);
        TopicVersion release = publishedRelease(topic);
        if (release == null || visibleMetadata(topic, release).stream()
                .noneMatch(item -> attempt.getTopicItemId().equals(item.getTopicItemId()))) return Optional.empty();
        Optional<TopicItemVersion> selected = switch (position.stage()) {
            case CONTEXT -> versions.findScreenDialogue(attempt.getTopicItemVersionId());
            case VOCAB -> versions.findScreenVocabulary(attempt.getTopicItemVersionId());
            case EXPRESSIONS -> versions.findScreenExpressions(attempt.getTopicItemVersionId());
        };
        TopicItemVersion pinned = selected.orElse(null);
        if (pinned == null || !Objects.equals(pinned.getTopicId(), attempt.getTopicId())
                || !Objects.equals(pinned.getTopicItemId(), attempt.getTopicItemId())
                || !(pinned.getStatus() == ContentStatus.PUBLISHED || pinned.getStatus() == ContentStatus.ARCHIVED)
                || pinned.getContext() == null || pinned.getContext().getScenarioDescription() == null) return Optional.empty();
        // Use the same ordered content mapping as the learner view, never raw array indices.
        var content = TopicAttemptContentMapper.content(pinned);
        var parent = new ItemOverviewDto(pinned.getTitle(), pinned.getDescription(), pinned.getContext().getScenarioDescription());
        return switch (position.stage()) {
            case CONTEXT -> {
                if (position.itemIndex() != 0) yield Optional.empty();
                var dialogue = content.getContext();
                yield Optional.of(new TopicLearningContextDto<>(overview(topic), parent, position,
                        new DialogueDto(dialogue.getPromptingQuestion(), dialogue.getDialogue().stream()
                                .map(line -> new DialogueTurnDto(line.getSpeaker(), line.getTextEn(), line.getTextVi())).toList(), dialogue.getTips())));
            }
            case VOCAB -> {
                if (position.itemIndex() >= content.getVocabularies().size()) yield Optional.empty();
                var word = content.getVocabularies().get(position.itemIndex());
                yield Optional.of(new TopicLearningContextDto<>(overview(topic), parent, position,
                        new VocabularyDto(word.getWord(), word.getPartOfSpeech(), word.getMeaningVi(), word.getPronunciation(),
                                word.getExamples().stream().map(e -> new ExampleDto(e.getTextEn(), e.getTextVi())).toList(),
                                word.getCollocations().stream().map(c -> new CollocationDto(c.textEn(), c.meaningVi())).toList(), word.getTips())));
            }
            case EXPRESSIONS -> {
                var purposes = content.getExpressions().stream()
                        .flatMap(group -> group.getPurposes().stream().map(purpose -> new ExpressionsDto(
                                purpose.getPurposeEn(), purpose.getPurposeVi(), purpose.getExamples().stream()
                                        .map(e -> new ExpressionExampleDto(e.getTextEn(), e.getTextVi(), e.getUsageNote())).toList(), group.getTips())))
                        .toList();
                if (position.itemIndex() >= purposes.size()) yield Optional.empty();
                yield Optional.of(new TopicLearningContextDto<>(overview(topic), parent, position, purposes.get(position.itemIndex())));
            }
        };
    }

    private boolean positionReached(UserTopicItemAttempt attempt, TopicLearningPositionDto position) {
        var frontier = attempt.getFurthestReached();
        if (frontier == null || frontier.getStage() == null || frontier.getItemIndex() == null) return false;
        int stage = position.stage().ordinal();
        int reached = frontier.getStage().ordinal();
        return stage < reached || (stage == reached && position.itemIndex() <= frontier.getItemIndex());
    }

    private TopicVersion publishedRelease(Topic topic) {
        if (topic == null || blank(topic.getLatestPublishedVersion())) return null;
        return releases.findById(topic.getLatestPublishedVersion())
                .filter(release -> ContentStatus.PUBLISHED == release.getStatus() && topic.getId().equals(release.getTopicId()))
                .orElse(null);
    }

    private List<TopicItemVersion> visibleMetadata(Topic topic, TopicVersion release) {
        if (release.getItemVersionIds() == null || release.getItemVersionIds().isEmpty()) return List.of();
        return versions.findScreenMetadata(release.getItemVersionIds()).stream()
                .filter(item -> topic.getId().equals(item.getTopicId()) && item.getTopicItemId() != null
                        && ContentStatus.PUBLISHED == item.getStatus()).toList();
    }

    private TopicOverviewDto overview(Topic topic) {
        return new TopicOverviewDto(topic.getTitleEn(), topic.getTitleVi(), topic.getDescription(),
                topic.getCefrLevel() == null ? null : topic.getCefrLevel().name());
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
