package fit.iuh.engolearn.config;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.model.vo.CollocationExampleVo;
import fit.iuh.engolearn.topic.model.vo.CollocationVo;
import fit.iuh.engolearn.topic.model.vo.DialogueTurnVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuickCheckVo;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.model.vo.QuizOptionVo;
import fit.iuh.engolearn.topic.model.vo.QuizQuestionVo;
import fit.iuh.engolearn.topic.model.vo.QuizSentenceVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSampleVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seeds sample topic data for the dev profile.
 *
 * <p>It is bound to the dev profile only and it writes only when the {@code topics}
 * collection is empty, so starting the application twice never duplicates data. All writes
 * go through the feature repositories, which keeps {@code @Field} snake_case names and id
 * generation on the same mapping path the application uses everywhere else.
 *
 * <p>The seed is deliberately shaped to make the browse rules observable on real data:
 *
 * <ul>
 *   <li>three published topics over two CEFR levels, two of them sharing {@code order_index}
 *       to exercise the deterministic {@code (order_index, id)} tie-break;
 *   <li>one unpublished topic that must never be listed and must answer a detail request as
 *       not found;
 *   <li>each topic keeps one HIDDEN item that is part of the release manifest but must not
 *       show up or contribute to any count;
 *   <li>{@code topics.duration_minutes} holds the sum of the visible item durations, the
 *       maintained aggregate the card shows and the duration sort key uses;
 *   <li>item {@code order} is a rotation of the insertion order, so the detail order proves
 *       it comes from the release item version and not from the item identity.
 * </ul>
 */
@Component
@Profile("dev")
public class DevTopicSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevTopicSeedRunner.class);

    private final TopicRepository topicRepository;
    private final TopicVersionRepository topicVersionRepository;
    private final TopicItemRepository topicItemRepository;
    private final TopicItemVersionRepository topicItemVersionRepository;

    public DevTopicSeedRunner(TopicRepository topicRepository,
                              TopicVersionRepository topicVersionRepository,
                              TopicItemRepository topicItemRepository,
                              TopicItemVersionRepository topicItemVersionRepository) {
        this.topicRepository = topicRepository;
        this.topicVersionRepository = topicVersionRepository;
        this.topicItemRepository = topicItemRepository;
        this.topicItemVersionRepository = topicItemVersionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = topicRepository.count();
        if (existing > 0) {
            log.info("Dev topic seed skipped: collection 'topics' already holds {} document(s), "
                    + "so the seed leaves the database untouched.", existing);
            return;
        }

        SeededCounts airport = seedPublishedTopic(
                "At the Airport", "O san bay",
                "Check in, ask for directions and show your boarding pass at the airport.",
                CefrLevel.A2, 10, TopicCategory.TRAVEL,
                new int[]{8, 6, 10, 7, 9, 5}, new int[]{30}, 3, 1);
        SeededCounts restaurant = seedPublishedTopic(
                "At the Restaurant", "Tai nha hang",
                "Order food, ask about the menu and pay the bill at a restaurant.",
                CefrLevel.A2, 20, TopicCategory.DINING,
                new int[]{15, 20, 10, 12, 8}, new int[]{20}, 4, 1);
        SeededCounts interview = seedPublishedTopic(
                "Job Interview", "Phong van xin viec",
                "Introduce yourself and answer common questions in a job interview.",
                CefrLevel.B1, 20, TopicCategory.BUSINESS,
                new int[]{15, 10, 12, 5, 8}, new int[]{25}, 5, 1);
        seedUnpublishedTopic();

        int topicItems = airport.itemCount() + restaurant.itemCount() + interview.itemCount();
        int topicItemVersions = airport.itemVersionCount() + restaurant.itemVersionCount()
                + interview.itemVersionCount();
        log.info("Dev topic seed inserted 4 topics, 3 published releases, {} topic items and "
                        + "{} item versions (published release totals: {} and {}).",
                topicItems, topicItemVersions, airport.itemVersionCount(),
                restaurant.itemVersionCount() + interview.itemVersionCount());
    }

    /**
     * Writes one topic with a single published release, its visible items and at least one
     * hidden item. Visible item durations are summed into the maintained aggregate on the
     * topic; hidden items are added to the release manifest but contribute to nothing.
     */
    private SeededCounts seedPublishedTopic(String titleEn,
                                            String titleVi,
                                            String description,
                                            CefrLevel level,
                                            int orderIndex,
                                            TopicCategory category,
                                            int[] visibleDurations,
                                            int[] hiddenDurations,
                                            int vocabulariesPerItem,
                                            int quizzesPerItem) {
        String slug = slugify(titleEn);
        Instant publishedAt = Instant.now();

        Topic topic = topicRepository.save(Topic.builder()
                .titleEn(titleEn)
                .titleVi(titleVi)
                .description(description)
                .cefrLevel(level)
                .category(category)
                .orderIndex(orderIndex)
                .coverUrl("https://cdn.engolearn.local/topics/" + slug + ".jpg")
                .tags(List.of(slug, level.name().toLowerCase(Locale.ROOT)))
                .build());

        TopicVersion release = topicVersionRepository.save(TopicVersion.builder()
                .topicId(topic.getId())
                .version(1)
                .status(ContentStatus.PUBLISHED)
                .publishedAt(publishedAt)
                .itemVersionIds(new ArrayList<>())
                .build());

        List<String> manifest = new ArrayList<>();
        int totalVisibleDuration = 0;
        int visibleCount = visibleDurations.length;
        for (int i = 0; i < visibleCount; i++) {
            // The stored order is a rotation of the insertion order, never the insertion order
            // itself, so the detail order can only come from the item version.
            int order = ((i + 2) % visibleCount) + 1;
            TopicItemVersion itemVersion = saveItemVersion(topic, release, i, order,
                    ContentStatus.PUBLISHED, visibleDurations[i], vocabulariesPerItem, quizzesPerItem);
            manifest.add(itemVersion.getId());
            totalVisibleDuration += visibleDurations[i];
        }
        for (int h = 0; h < hiddenDurations.length; h++) {
            // Hidden items stay in the release manifest; the read path filters them out.
            TopicItemVersion itemVersion = saveItemVersion(topic, release, visibleCount + h,
                    visibleCount + h + 1, ContentStatus.HIDDEN, hiddenDurations[h],
                    vocabulariesPerItem, quizzesPerItem);
            manifest.add(itemVersion.getId());
        }

        release.setItemVersionIds(manifest);
        topicVersionRepository.save(release);

        topic.setLatestPublishedVersion(release.getId());
        topic.setDurationMinutes(totalVisibleDuration);
        topicRepository.save(topic);

        int itemCount = visibleCount + hiddenDurations.length;
        return new SeededCounts(itemCount, itemCount);
    }

    /** Inserts one identity document and one content version that references it. */
    private TopicItemVersion saveItemVersion(Topic topic,
                                             TopicVersion release,
                                             int index,
                                             int order,
                                             ContentStatus status,
                                             int durationMinutes,
                                             int vocabulariesPerItem,
                                             int quizzesPerItem) {
        TopicItem item = topicItemRepository.save(TopicItem.builder()
                .topicId(topic.getId())
                .build());

        String label = topic.getTitleEn() + " item " + (index + 1);
        return topicItemVersionRepository.save(TopicItemVersion.builder()
                .topicItemId(item.getId())
                .topicId(topic.getId())
                .topicVersionId(release.getId())
                .version(1)
                .changeType(ChangeType.MAJOR)
                .title(label)
                .description("Short description for " + label)
                .order(order)
                .status(status)
                .durationMinutes(durationMinutes)
                .publishedAt(release.getPublishedAt())
                .context(contextFor(label))
                .vocabularies(vocabulariesFor(label, vocabulariesPerItem))
                .expressions(expressionsFor())
                .quizzes(quizzesFor(label, quizzesPerItem))
                .build());
    }

    /**
     * One topic with no release and no published pointer. Its {@code order_index} is lower
     * than every published topic's, so the published filter is what keeps it out of the list
     * rather than the sort order.
     */
    private void seedUnpublishedTopic() {
        topicRepository.save(Topic.builder()
                .titleEn("Unpublished Draft Topic")
                .titleVi("Topic nhap chua phat hanh")
                .description("This topic has no published release and must never be listed.")
                .cefrLevel(CefrLevel.A1)
                .category(TopicCategory.DAILY)
                .orderIndex(1)
                .coverUrl("https://cdn.engolearn.local/topics/unpublished-draft-topic.jpg")
                .tags(List.of("draft"))
                .build());
    }

    private static TopicContextVo contextFor(String label) {
        return TopicContextVo.builder()
                .prompt("Prompt for " + label)
                .scenarioDescription("Scenario for " + label)
                .dialogue(List.of(
                        DialogueTurnVo.builder()
                                .speaker("A").textEn("Hello!").textVi("Xin chao!")
                                .audioUrl("https://cdn.engolearn.local/audio/hello.mp3").build(),
                        DialogueTurnVo.builder()
                                .speaker("B").textEn("Hi there!").textVi("Chao ban!")
                                .audioUrl("https://cdn.engolearn.local/audio/hi-there.mp3").build()))
                .durationSec(60)
                .tips("Listen carefully")
                .build();
    }

    private static List<ItemWordVo> vocabulariesFor(String label, int count) {
        List<ItemWordVo> words = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String word = "word" + (i + 1);
            words.add(ItemWordVo.builder()
                    .vocabId(label + "-vocab-" + (i + 1))
                    .senseId(label + "-sense-" + (i + 1))
                    .snapshot(WordSnapshotVo.builder()
                            .word(word)
                            .pos("noun")
                            .sense("Sense " + (i + 1) + " of " + word)
                            .describeVi("Meaning of " + word)
                            .phonetic("/" + word + "/")
                            .audioUrl("https://cdn.engolearn.local/audio/" + word + ".mp3")
                            .build())
                    .samples(List.of(WordSampleVo.builder()
                            .textEn("A sample sentence with " + word)
                            .textVi("Cau vi du voi " + word)
                            .audioUrl("https://cdn.engolearn.local/audio/" + word + "-sample.mp3")
                            .isManual(Boolean.FALSE)
                            .build()))
                    .collocations(List.of(CollocationVo.builder()
                            .text(word + " up")
                            .example(CollocationExampleVo.builder()
                                    .en("They " + word + " up early")
                                    .vi("Ho " + word + " som")
                                    .build())
                            .descriptionVi("Collocation with " + word)
                            .build()))
                    .quickCheck(QuickCheckVo.builder()
                            .sentence(QuizSentenceVo.builder()
                                    .textEn("Fill in the blank: " + word)
                                    .meaningVi("Dien tu " + word)
                                    .build())
                            .quizData(Map.of("answer", word))
                            .answerData(Map.of("answer", word))
                            .explanation("The answer is " + word)
                            .build())
                    .orderIndex(i + 1)
                    .tips("Remember " + word)
                    .build());
        }
        return words;
    }

    /**
     * One expression block with two purposes and two examples each, so the counted sample
     * sentences (four) are more than the number of groups.
     */
    private static List<ExpressionBlockVo> expressionsFor() {
        return List.of(ExpressionBlockVo.builder()
                .purposes(List.of(
                        ExpressionPurposeVo.builder()
                                .purposeEn("Ask for help")
                                .purposeVi("Hoi xin tro giup")
                                .orderIndex(1)
                                .examples(List.of(
                                        ExpressionExampleVo.builder()
                                                .type(ExpressionType.BASIC_SUGGESTION)
                                                .textEn("Could you help me?")
                                                .textVi("Ban giup toi duoc khong?")
                                                .usageNote("Polite request")
                                                .build(),
                                        ExpressionExampleVo.builder()
                                                .type(ExpressionType.POLITE_INQUIRY)
                                                .textEn("Would you mind helping me?")
                                                .textVi("Ban co the giup toi khong?")
                                                .usageNote("Very polite")
                                                .build()))
                                .build(),
                        ExpressionPurposeVo.builder()
                                .purposeEn("Give directions")
                                .purposeVi("Chi duong")
                                .orderIndex(2)
                                .examples(List.of(
                                        ExpressionExampleVo.builder()
                                                .type(ExpressionType.BASIC_SUGGESTION)
                                                .textEn("Go straight ahead")
                                                .textVi("Di thang")
                                                .build(),
                                        ExpressionExampleVo.builder()
                                                .type(ExpressionType.SLOT_PATTERN)
                                                .textEn("Turn left at the [place]")
                                                .textVi("Re trai o [dia diem]")
                                                .build()))
                                .build()))
                .tips("Two expression purposes")
                .build());
    }

    private static List<QuizModuleVo> quizzesFor(String label, int count) {
        List<QuizModuleVo> quizzes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            quizzes.add(QuizModuleVo.builder()
                    .quizId(label + "-quiz-" + (i + 1))
                    .quizType(i % 2 == 0 ? QuizType.FILL_BLANK : QuizType.SITUATIONAL_CHOICE)
                    .title("Quiz " + (i + 1))
                    .instructions("Answer the question")
                    .orderIndex(i + 1)
                    .questions(List.of(QuizQuestionVo.builder()
                            .prompt("What do you say?")
                            .options(List.of(
                                    QuizOptionVo.builder()
                                            .label("A").textEn("Option A").isCorrect(Boolean.TRUE)
                                            .build(),
                                    QuizOptionVo.builder()
                                            .label("B").textEn("Option B").isCorrect(Boolean.FALSE)
                                            .build()))
                            .correct("A")
                            .explanation("Option A is correct")
                            .sentence(QuizSentenceVo.builder()
                                    .textEn("Sample sentence")
                                    .meaningVi("Cau vi du")
                                    .build())
                            .build()))
                    .build());
        }
        return quizzes;
    }

    private static String slugify(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** Documents written for one topic. */
    private record SeededCounts(int itemCount, int itemVersionCount) {
    }
}
