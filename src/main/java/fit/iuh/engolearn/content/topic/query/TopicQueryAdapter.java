package fit.iuh.engolearn.content.topic.query;

import fit.iuh.engolearn.content.api.DialogueTurnView;
import fit.iuh.engolearn.content.api.ExpressionExampleView;
import fit.iuh.engolearn.content.api.ExpressionPurposeView;
import fit.iuh.engolearn.content.api.ExpressionView;
import fit.iuh.engolearn.content.api.ItemContextView;
import fit.iuh.engolearn.content.api.ItemWordView;
import fit.iuh.engolearn.content.api.QuizModuleView;
import fit.iuh.engolearn.content.api.QuizOptionView;
import fit.iuh.engolearn.content.api.QuizQuestionView;
import fit.iuh.engolearn.content.api.QuizSentenceView;
import fit.iuh.engolearn.content.api.TopicCardView;
import fit.iuh.engolearn.content.api.TopicDetailView;
import fit.iuh.engolearn.content.api.TopicFilter;
import fit.iuh.engolearn.content.api.TopicItemDetailView;
import fit.iuh.engolearn.content.api.TopicQuery;
import fit.iuh.engolearn.content.topic.model.Topic;
import fit.iuh.engolearn.content.topic.model.TopicCategory;
import fit.iuh.engolearn.content.topic.model.TopicItem;
import fit.iuh.engolearn.content.topic.model.TopicVersion;
import fit.iuh.engolearn.content.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.content.topic.repository.TopicRepository;
import fit.iuh.engolearn.content.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.shared.model.ContentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
class TopicQueryAdapter implements TopicQuery {

    private final TopicRepository topics;
    private final TopicVersionRepository versions;
    private final TopicItemRepository items;

    TopicQueryAdapter(
            TopicRepository topics, TopicVersionRepository versions, TopicItemRepository items) {
        this.topics = topics;
        this.versions = versions;
        this.items = items;
    }

    @Override
    public Page<TopicCardView> findPublishedTopics(TopicFilter filter, Pageable pageable) {
        TopicCategory category = parseCategory(filter == null ? null : filter.category());
        Page<Topic> page;
        if (filter != null && filter.level() != null && category != null) {
            page = topics.findByCefrLevelAndCategory(filter.level(), category, pageable);
        } else if (filter != null && filter.level() != null) {
            page = topics.findByCefrLevel(filter.level(), pageable);
        } else if (category != null) {
            page = topics.findByCategory(category, pageable);
        } else {
            page = topics.findAll(pageable);
        }
        return page.map(this::toCard);
    }

    @Override
    public Optional<TopicDetailView> getPublishedTopicDetail(String topicId, Integer versionOrNull) {
        return topics
                .findById(topicId)
                .flatMap(
                        topic -> {
                            Integer version =
                                    versionOrNull != null
                                            ? versionOrNull
                                            : topic.getLatestPublishedVersion();
                            if (version == null) {
                                return Optional.empty();
                            }
                            return versions
                                    .findByTopicIdAndVersion(topic.getId(), version)
                                    .filter(v -> v.getStatus() == ContentStatus.PUBLISHED)
                                    .map(v -> toDetail(topic, v));
                        });
    }

    @Override
    public Map<String, Integer> findVersionNumbers(Collection<String> topicVersionIds) {
        if (topicVersionIds == null || topicVersionIds.isEmpty()) {
            return Map.of();
        }
        return versions.findAllById(topicVersionIds).stream()
                .filter(v -> v.getVersion() != null)
                .collect(Collectors.toMap(TopicVersion::getId, TopicVersion::getVersion, (a, b) -> a));
    }

    private TopicCardView toCard(Topic topic) {
        Integer publishedVersion = topic.getLatestPublishedVersion();
        int totalItems = 0;
        int totalWords = 0;
        if (publishedVersion != null) {
            Optional<TopicVersion> version =
                    versions.findByTopicIdAndVersion(topic.getId(), publishedVersion);
            if (version.isPresent()
                    && version.get().getStatus() == ContentStatus.PUBLISHED) {
                List<TopicItem> publishedItems =
                        items.findByTopicVersionIdAndStatusOrderByOrderAsc(
                                version.get().getId(), ContentStatus.PUBLISHED);
                totalItems = publishedItems.size();
                totalWords =
                        publishedItems.stream()
                                .mapToInt(
                                        item ->
                                                item.getWords() == null
                                                        ? 0
                                                        : item.getWords().size())
                                .sum();
            }
        }
        return new TopicCardView(
                topic.getId(),
                publishedVersion,
                topic.getTitleEn(),
                topic.getTitleVi(),
                topic.getDescription(),
                topic.getCefrLevel(),
                topic.getCategory() == null ? null : topic.getCategory().name(),
                topic.getOrderIndex(),
                topic.getDurationMinutes(),
                topic.getCoverUrl(),
                topic.getTags(),
                totalItems,
                totalWords);
    }

    private TopicDetailView toDetail(Topic topic, TopicVersion version) {
        List<TopicItemDetailView> itemViews =
                items.findByTopicVersionIdAndStatusOrderByOrderAsc(
                                version.getId(), ContentStatus.PUBLISHED)
                        .stream()
                        .map(this::toItemDetail)
                        .toList();
        return new TopicDetailView(
                topic.getId(),
                version.getVersion(),
                topic.getTitleEn(),
                topic.getTitleVi(),
                topic.getDescription(),
                topic.getCefrLevel(),
                topic.getCategory() == null ? null : topic.getCategory().name(),
                topic.getOrderIndex(),
                topic.getDurationMinutes(),
                topic.getCoverUrl(),
                topic.getTags(),
                itemViews);
    }

    private TopicItemDetailView toItemDetail(TopicItem item) {
        ItemContextView context = null;
        if (item.getContext() != null) {
            List<DialogueTurnView> dialogue =
                    item.getContext().getDialogue() == null
                            ? List.of()
                            : item.getContext().getDialogue().stream()
                                    .map(
                                            d ->
                                                    new DialogueTurnView(
                                                            d.getSpeaker(),
                                                            d.getTextEn(),
                                                            d.getTextVi(),
                                                            d.getAudioUrl()))
                                    .toList();
            context =
                    new ItemContextView(
                            item.getContext().getPrompt(),
                            item.getContext().getScenarioDescription(),
                            item.getContext().getDurationSec(),
                            item.getContext().getTips(),
                            dialogue);
        }
        List<ItemWordView> words =
                item.getWords() == null
                        ? List.of()
                        : item.getWords().stream()
                                .map(
                                        w ->
                                                new ItemWordView(
                                                        w.getVocabId(),
                                                        w.getSenseId(),
                                                        w.getSnapshot() == null
                                                                ? null
                                                                : w.getSnapshot().getWord(),
                                                        w.getSnapshot() == null
                                                                ? null
                                                                : w.getSnapshot().getPos(),
                                                        w.getSnapshot() == null
                                                                ? null
                                                                : w.getSnapshot().getSense(),
                                                        w.getSnapshot() == null
                                                                ? null
                                                                : w.getSnapshot().getPhonetic(),
                                                        w.getSnapshot() == null
                                                                ? null
                                                                : w.getSnapshot().getAudioUrl(),
                                                        w.getTips(),
                                                        w.getOrderIndex()))
                                .toList();
        ExpressionView expressions = null;
        if (item.getExpressions() != null) {
            List<ExpressionPurposeView> purposes =
                    item.getExpressions().getPurposes() == null
                            ? List.of()
                            : item.getExpressions().getPurposes().stream()
                                    .map(
                                            p ->
                                                    new ExpressionPurposeView(
                                                            p.getPurposeEn(),
                                                            p.getPurposeVi(),
                                                            p.getOrderIndex(),
                                                            p.getExamples() == null
                                                                    ? List.of()
                                                                    : p.getExamples().stream()
                                                                            .map(
                                                                                    e ->
                                                                                            new ExpressionExampleView(
                                                                                                    e.getType()
                                                                                                                    == null
                                                                                                            ? null
                                                                                                            : e.getType()
                                                                                                                    .name(),
                                                                                                    e.getTextEn(),
                                                                                                    e.getTextVi(),
                                                                                                    e.getUsageNote(),
                                                                                                    e.getAudioUrl(),
                                                                                                    e.getLinkedVocabIds()))
                                                                            .toList()))
                                    .toList();
            expressions = new ExpressionView(item.getExpressions().getTips(), purposes);
        }
        List<QuizModuleView> quizzes =
                item.getQuizzes() == null
                        ? List.of()
                        : item.getQuizzes().stream()
                                .map(
                                        q ->
                                                new QuizModuleView(
                                                        q.getQuizId(),
                                                        q.getQuizType() == null
                                                                ? null
                                                                : q.getQuizType().name(),
                                                        q.getTitle(),
                                                        q.getInstructions(),
                                                        q.getOrderIndex(),
                                                        q.getQuestions() == null
                                                                ? List.of()
                                                                : q.getQuestions().stream()
                                                                        .map(
                                                                                qq ->
                                                                                        new QuizQuestionView(
                                                                                                qq.getPrompt(),
                                                                                                qq.getAudioUrl(),
                                                                                                qq.getOptions()
                                                                                                                == null
                                                                                                        ? List.of()
                                                                                                        : qq.getOptions()
                                                                                                                .stream()
                                                                                                                .map(
                                                                                                                        o ->
                                                                                                                                new QuizOptionView(
                                                                                                                                        o.getLabel(),
                                                                                                                                        o.getTextEn(),
                                                                                                                                        o.getIsCorrect()))
                                                                                                                .toList(),
                                                                                                qq.getCorrect(),
                                                                                                qq.getExplanationOk(),
                                                                                                qq.getExplanationNg(),
                                                                                                qq.getSentence()
                                                                                                                == null
                                                                                                        ? null
                                                                                                        : new QuizSentenceView(
                                                                                                                qq.getSentence()
                                                                                                                        .getTextEn(),
                                                                                                                qq.getSentence()
                                                                                                                        .getMeaningVi(),
                                                                                                                qq.getSentence()
                                                                                                                        .getAudioUrl(),
                                                                                                                qq.getSentence()
                                                                                                                        .getAudioUrlSlow(),
                                                                                                                qq.getSentence()
                                                                                                                        .getSource()),
                                                                                                qq.getQuizData(),
                                                                                                qq.getAnswerData(),
                                                                                                qq.getExplanation(),
                                                                                                qq.getTips()))
                                                                        .toList()))
                                .toList();
        return new TopicItemDetailView(
                item.getTopicItemId(),
                item.getVersion(),
                item.getTitle(),
                item.getDescription(),
                item.getOrder(),
                item.getStatus(),
                context,
                words,
                expressions,
                quizzes);
    }

    private TopicCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return TopicCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
