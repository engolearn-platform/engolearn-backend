import fit.iuh.engolearn.grammar.dto.response.GrammarCheckResponse;
import fit.iuh.engolearn.grammar.dto.request.GrammarTopicRequest;
import fit.iuh.engolearn.grammar.mapper.GrammarAdminMapper;
import fit.iuh.engolearn.grammar.model.GrammarCounter;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.grammar.service.GrammarChecklistValidator;
import fit.iuh.engolearn.grammar.utils.GrammarAnswerNormalizer;
import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.bson.Document;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

// Validates the real importer bundle with BE mappings/checklists; never constructs a Mongo client.
public class ValidateGrammarSeed {
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Pass one importer JSON file path");
        var conversions = MongoCustomConversions.create(adapter -> {});
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        var collections = Document.parse(Files.readString(Path.of(args[0]))).get("collections", Document.class);
        require(collections.keySet().equals(java.util.Set.of("grammar_topics", "grammar_counters")), "Unexpected collections");
        var topics = collections.getList("grammar_topics", Document.class);
        var counters = collections.getList("grammar_counters", Document.class);
        require(topics.size() == 1 && counters.size() == 1, "Expected one topic and one counter");
        var topic = converter.read(GrammarTopic.class, topics.getFirst());
        var counter = converter.read(GrammarCounter.class, counters.getFirst());
        require(topic.getStatus() == GrammarStatus.DRAFT && topic.getFirstPublishedAt() == null, "Seed must stay draft");
        require(topic.getVersion() == 0 && topic.getOrder() == 1 && topic.getLessonSequence() == 3, "Topic defaults");
        require(counter.getId().equals("topic-order-and-code") && counter.getTopicSequence() == 1, "Counter mismatch");
        var checklist = new GrammarChecklistValidator();
        int blocks = 0, questions = 0, fills = 0;
        var identifiers = new HashSet<String>();
        var codes = new HashSet<String>();
        require(identifiers.add(topic.getId()) && codes.add(topic.getCode()), "Duplicate topic identity");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var bean = factory.getValidator();
            var topicInput = new GrammarTopicRequest(topic.getVersion(), topic.getOrder(), topic.getTitleVi(),
                    topic.getTitleEn(), topic.getDescription(), topic.getLevel().name());
            require(bean.validate(topicInput).isEmpty(), "Invalid TopicInput");
            require(topic.getLessons().size() == 3, "Three lessons required");
            int expectedOrder = 1;
            for (var lesson : topic.getLessons()) {
                require(identifiers.add(lesson.getId()) && codes.add(lesson.getCode()), "Duplicate lesson identity/code");
                require(lesson.getOrder() == expectedOrder++, "Lesson order must be contiguous");
                require(lesson.getStatus() == GrammarStatus.DRAFT && !lesson.isReady()
                        && lesson.getContentVersion() == 0 && lesson.getPublished() == null
                        && lesson.getFirstPublishedAt() == null, "Seed must not pre-publish lessons");
                var input = GrammarAdminMapper.lessonInput(lesson);
                require(bean.validate(input).isEmpty(), "Invalid LessonInput: " + lesson.getCode());
                var checks = checklist.lesson(input).checks();
                require(checks.size() == 14 && checks.stream().allMatch(GrammarCheckResponse::passed),
                        "Lesson checklist failed: " + lesson.getCode() + " " + checks);
                for (var block : input.blocks()) require(identifiers.add(block.id()), "Duplicate block ID");
                for (var question : input.questions()) {
                    require(identifiers.add(question.id()), "Duplicate question ID");
                    if (question.type().equals("MULTIPLE_CHOICE")) {
                        var distinct = new HashSet<String>();
                        for (var option : question.options()) require(distinct.add(GrammarAnswerNormalizer.normalize(option)), "Duplicate MCQ options");
                    } else {
                        fills++;
                        var accepted = new HashSet<String>();
                        require(accepted.add(GrammarAnswerNormalizer.normalize(question.correctAnswer())), "Missing answer");
                        for (var answer : question.acceptedAnswers()) {
                            require(!GrammarAnswerNormalizer.normalize(answer).isEmpty()
                                    && accepted.add(GrammarAnswerNormalizer.normalize(answer)), "Duplicate/empty FIB variants");
                        }
                    }
                }
                blocks += input.blocks().size();
                questions += input.questions().size();
                System.out.println(lesson.getCode() + ": DTO valid, checklist 14/14");
            }
        }
        require(checklist.topic(topic).checks().stream().filter(GrammarCheckResponse::passed).count() == 6,
                "Draft topic should wait only for real readiness approval");
        topic.getLessons().forEach(lesson -> lesson.setReady(true));
        require(checklist.topic(topic).checks().stream().allMatch(GrammarCheckResponse::passed), "Topic checklist failed");
        topic.getLessons().forEach(lesson -> lesson.setReady(false));
        require(blocks == 15 && questions == 15 && fills == 6, "Unexpected content counts");
        System.out.println("PASS: 1 topic, 3 lessons, 15 blocks, 15 questions; topic 6/7 as draft, 7/7 after in-memory readiness simulation. No DB access or writes.");
    }
}
