package fit.iuh.engolearn.topic.mapper;

import tools.jackson.databind.ObjectMapper;
import fit.iuh.engolearn.topic.dto.response.QuizFeedbackResponse;
import fit.iuh.engolearn.topic.dto.response.QuizFillBlankResponse;
import fit.iuh.engolearn.topic.dto.response.QuizOptionResponse;
import fit.iuh.engolearn.topic.dto.response.QuizSituationalChoiceResponse;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper.QuizEntry;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.model.vo.QuizOptionVo;
import fit.iuh.engolearn.topic.model.vo.QuizQuestionVo;
import fit.iuh.engolearn.topic.model.vo.QuizSentenceVo;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TopicQuizMapperTest {

    private final TopicQuizMapper mapper = new TopicQuizMapper();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sequenceIsDeterministicAndFollowsFixedTypeModuleQuestionOrder() {
        TopicItemVersion version = version(
                module(QuizType.SITUATIONAL_CHOICE, 1, "Situational",
                        situationalQuestion("What do you say at the counter?"),
                        situationalQuestion("How do you ask for help?")),
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")));

        List<String> first = ids(mapper.sequence(version));
        List<String> second = ids(mapper.sequence(version));

        assertThat(second).isEqualTo(first);
        assertThat(first).containsExactly("iv1:quiz:0:0", "iv1:quiz:1:0", "iv1:quiz:1:1");
    }

    @Test
    void sequenceAssignsOptionIdsByOriginalOptionPosition() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")));

        List<QuizEntry> entries = mapper.sequence(version);
        QuizFillBlankResponse dto = (QuizFillBlankResponse) mapper.toDto(entries.get(0));

        assertThat(dto.options()).extracting(QuizOptionResponse::optionId).containsExactly(
                "iv1:quiz:0:0:option:0", "iv1:quiz:0:0:option:1",
                "iv1:quiz:0:0:option:2", "iv1:quiz:0:0:option:3");
        assertThat(dto.options()).extracting(QuizOptionResponse::text)
                .containsExactly("buy", "reserve", "take", "make");
        assertThat(mapper.correctOptionId(entries.get(0))).isEqualTo("iv1:quiz:0:0:option:1");
    }

    @Test
    void findReturnsTheEntryWithTheMatchingQuizId() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")),
                module(QuizType.SITUATIONAL_CHOICE, 1, "Situational",
                        situationalQuestion("What do you say at the counter?")));
        List<QuizEntry> entries = mapper.sequence(version);

        assertThat(mapper.find(entries, "iv1:quiz:1:0")).hasValueSatisfying(entry -> {
            assertThat(entry.type()).isEqualTo(QuizType.SITUATIONAL_CHOICE);
            assertThat(entry.indexInType()).isZero();
            assertThat(entry.totalInType()).isEqualTo(1);
            assertThat(entry.questionIndexInModule()).isZero();
        });
        assertThat(mapper.find(entries, "iv1:quiz:9:9")).isEmpty();
    }

    @Test
    void indexesAndTotalsSpanTheWholeItemAcrossTwoModulesOfTheSameType() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Module A",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket."),
                        fillBlankQuestion("The train leaves at six o'clock.", "The train departs at six.")),
                module(QuizType.FILL_BLANK, 1, "Module B",
                        fillBlankQuestion("She is a nurse at the clinic.", "She works as a nurse."),
                        fillBlankQuestion("We travel by bus every day.", "We take the bus daily.")));

        List<QuizEntry> entries = mapper.sequence(version);

        assertThat(entries).extracting(QuizEntry::quizId).containsExactly(
                "iv1:quiz:0:0", "iv1:quiz:0:1", "iv1:quiz:1:0", "iv1:quiz:1:1");
        assertThat(entries).extracting(QuizEntry::indexInType).containsExactly(0, 1, 2, 3);
        assertThat(entries).extracting(QuizEntry::totalInType).containsOnly(4);
    }

    @Test
    void eachTypeCountsItsOwnIndexAndTotal() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket."),
                        fillBlankQuestion("The train leaves at six o'clock.", "The train departs at six.")),
                module(QuizType.SITUATIONAL_CHOICE, 1, "Situational",
                        situationalQuestion("What do you say at the counter?"),
                        situationalQuestion("How do you ask for help?")));

        List<QuizEntry> entries = mapper.sequence(version);

        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.FILL_BLANK)
                .extracting(QuizEntry::indexInType).containsExactly(0, 1);
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.FILL_BLANK)
                .extracting(QuizEntry::totalInType).containsOnly(2);
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.SITUATIONAL_CHOICE)
                .extracting(QuizEntry::indexInType).containsExactly(0, 1);
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.SITUATIONAL_CHOICE)
                .extracting(QuizEntry::totalInType).containsOnly(2);
    }

    @Test
    void moduleWithoutOrderIndexUsesItsArrayPositionAsKey() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, null, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")),
                module(QuizType.SITUATIONAL_CHOICE, null, "Situational",
                        situationalQuestion("What do you say at the counter?")));

        assertThat(ids(mapper.sequence(version))).containsExactly("iv1:quiz:0:0", "iv1:quiz:1:0");
    }

    @Test
    void nullOrderIndexModuleSortsByItsPositionAmongExplicitOrderIndexes() {
        TopicItemVersion version = version(
                module(QuizType.SITUATIONAL_CHOICE, 2, "Later",
                        situationalQuestion("What do you say at the counter?")),
                module(QuizType.FILL_BLANK, null, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")));

        assertThat(ids(mapper.sequence(version))).containsExactly("iv1:quiz:1:0", "iv1:quiz:2:0");
    }

    @Test
    void equalEffectiveModuleKeysKeepArrayOrderAndFailAsDuplicateQuizId() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 1, "Module A",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")),
                module(QuizType.FILL_BLANK, null, "Module B",
                        fillBlankQuestion("The train leaves at six o'clock.", "The train departs at six.")));

        assertContentUnavailable(() -> mapper.sequence(version), "iv1:quiz:1:0");
    }

    @Test
    void unsupportedModuleTypesAreSkippedWithoutShiftingIndexes() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket."),
                        fillBlankQuestion("The train leaves at six o'clock.", "The train departs at six.")),
                module(QuizType.MATCHING, 1, "Matching",
                        situationalQuestion("Match the pairs"), situationalQuestion("Match again"),
                        situationalQuestion("Match once more")),
                module(QuizType.SENTENCE_ORDER, 2, "Order",
                        situationalQuestion("Put the words in order")),
                module(QuizType.SITUATIONAL_CHOICE, 3, "Situational",
                        situationalQuestion("What do you say at the counter?")));

        List<QuizEntry> entries = mapper.sequence(version);

        assertThat(entries).extracting(QuizEntry::quizId).containsExactly(
                "iv1:quiz:0:0", "iv1:quiz:0:1", "iv1:quiz:3:0");
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.FILL_BLANK)
                .extracting(QuizEntry::indexInType).containsExactly(0, 1);
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.FILL_BLANK)
                .extracting(QuizEntry::totalInType).containsOnly(2);
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.SITUATIONAL_CHOICE)
                .extracting(QuizEntry::indexInType).containsExactly(0);
        assertThat(entries).filteredOn(entry -> entry.type() == QuizType.SITUATIONAL_CHOICE)
                .extracting(QuizEntry::totalInType).containsOnly(1);
    }

    @Test
    void fillBlankDtoSplitsSentenceAroundTheCorrectOptionText() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")));

        QuizFillBlankResponse dto = (QuizFillBlankResponse) mapper.toDto(mapper.sequence(version).get(0));

        assertThat(dto.quizId()).isEqualTo("iv1:quiz:0:0");
        assertThat(dto.title()).isEqualTo("Fill blank");
        assertThat(dto.index()).isZero();
        assertThat(dto.totalInType()).isEqualTo(1);
        assertThat(dto.type()).isEqualTo("FILL_BLANK");
        assertThat(dto.situationVi()).isNull();
        assertThat(dto.imageUrl()).isNull();
        assertThat(dto.tips()).isEqualTo("Read the whole sentence");
        assertThat(dto.promptAudioUrl()).isEqualTo("question.mp3");
        assertThat(dto.sentenceBefore()).isEqualTo("I would like to ");
        assertThat(dto.sentenceAfter()).isEqualTo(" a ticket today.");
        assertThat(dto.options()).hasSize(4);
    }

    @Test
    void sentenceStartingWithTheAnswerYieldsEmptyBefore() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("reserve a ticket before noon.", "Book before noon.")));

        QuizFillBlankResponse dto = (QuizFillBlankResponse) mapper.toDto(mapper.sequence(version).get(0));

        assertThat(dto.sentenceBefore()).isEmpty();
        assertThat(dto.sentenceAfter()).isEqualTo(" a ticket before noon.");
    }

    @Test
    void situationViIsReadFromQuizDataScenarioKey() {
        QuizQuestionVo question = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.");
        question.setQuizData(Map.of("scenario_vi", "Booking a ticket online"));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        QuizFillBlankResponse dto = (QuizFillBlankResponse) mapper.toDto(mapper.sequence(version).get(0));

        assertThat(dto.situationVi()).isEqualTo("Booking a ticket online");
    }

    @Test
    void nonStringScenarioValueIsNullInsteadOfFailing() {
        QuizQuestionVo question = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.");
        question.setQuizData(Map.of("scenario_vi", 42));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        QuizFillBlankResponse dto = (QuizFillBlankResponse) mapper.toDto(mapper.sequence(version).get(0));

        assertThat(dto.situationVi()).isNull();
    }

    @Test
    void situationalChoiceDtoExposesExactlyTheContractKeys() {
        TopicItemVersion version = version(
                module(QuizType.SITUATIONAL_CHOICE, 0, "Situational",
                        situationalQuestion("Which sentence is polite?")));

        QuizSituationalChoiceResponse dto = (QuizSituationalChoiceResponse)
                mapper.toDto(mapper.sequence(version).get(0));
        Map<?, ?> node = json.convertValue(dto, Map.class);
        List<?> options = (List<?>) node.get("options");

        Set<Object> keys = new HashSet<>(node.keySet());
        assertThat(keys).containsExactlyInAnyOrder(
                "quizId", "title", "index", "totalInType", "situationVi", "imageUrl",
                "tips", "promptAudioUrl", "type", "questionVi", "options");
        Set<Object> optionKeys = new HashSet<>(((Map<?, ?>) options.get(0)).keySet());
        assertThat(optionKeys).containsExactlyInAnyOrder("optionId", "text");
        assertThat(options).hasSize(4);
        assertThat(dto.quizId()).isEqualTo("iv1:quiz:0:0");
        assertThat(dto.title()).isEqualTo("Situational");
        assertThat(dto.index()).isZero();
        assertThat(dto.totalInType()).isEqualTo(1);
        assertThat(dto.type()).isEqualTo("SITUATIONAL_CHOICE");
        assertThat(dto.questionVi()).isEqualTo("Which sentence is polite?");
        assertThat(dto.situationVi()).isEqualTo("At the airport counter");
        assertThat(dto.imageUrl()).isNull();
        assertThat(dto.tips()).isNull();
        assertThat(dto.promptAudioUrl()).isNull();
    }

    @Test
    void serializedQuizDtosNeverExposeAnswers() throws Exception {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")),
                module(QuizType.SITUATIONAL_CHOICE, 1, "Situational",
                        situationalQuestion("Which sentence is polite?")));
        List<QuizEntry> entries = mapper.sequence(version);

        String fillBlankJson = json.writeValueAsString(mapper.toDto(entries.get(0)));
        String situationalJson = json.writeValueAsString(mapper.toDto(entries.get(1)));

        for (String serialized : List.of(fillBlankJson, situationalJson)) {
            assertThat(serialized)
                    .doesNotContain("isCorrect", "is_correct", "correct", "answerData", "answer_data", "explanation");
        }
        assertThat(fillBlankJson).contains("\"type\":\"FILL_BLANK\"");
        assertThat(situationalJson).contains("\"type\":\"SITUATIONAL_CHOICE\"");
    }

    @Test
    void feedbackForFillBlankMapsEveryField() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")));
        QuizEntry entry = mapper.sequence(version).get(0);

        QuizFeedbackResponse feedback = mapper.feedback(entry);

        assertThat(feedback.correctOptionId()).isEqualTo("iv1:quiz:0:0:option:1");
        assertThat(feedback.completedSentenceEn()).isEqualTo("I would like to reserve a ticket today.");
        assertThat(feedback.translationVi()).isEqualTo("I want a ticket.");
        assertThat(feedback.explanationVi()).isEqualTo("Choose the verb that fits");
        assertThat(feedback.grammarNoteVi()).isNull();
        assertThat(feedback.audioUrl()).isEqualTo("sentence.mp3");
    }

    @Test
    void feedbackAllowsNullTranslationVi() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Fill blank",
                        fillBlankQuestion("I would like to reserve a ticket today.", null)));

        QuizFeedbackResponse feedback = mapper.feedback(mapper.sequence(version).get(0));

        assertThat(feedback.translationVi()).isNull();
        assertThat(feedback.completedSentenceEn()).isEqualTo("I would like to reserve a ticket today.");
    }

    @Test
    void situationalFeedbackHasNoCompletedSentence() {
        QuizQuestionVo question = situationalQuestion("Which sentence is polite?");
        question.setSentence(QuizSentenceVo.builder()
                .textEn("Where is the departure gate?")
                .meaningVi("Cong di hanh o dau?")
                .audioUrl("sentence.mp3")
                .build());
        TopicItemVersion version = version(module(QuizType.SITUATIONAL_CHOICE, 0, "Situational", question));

        QuizFeedbackResponse feedback = mapper.feedback(mapper.sequence(version).get(0));

        assertThat(feedback.correctOptionId()).isEqualTo("iv1:quiz:0:0:option:2");
        assertThat(feedback.completedSentenceEn()).isNull();
        assertThat(feedback.translationVi()).isEqualTo("Cong di hanh o dau?");
        assertThat(feedback.audioUrl()).isEqualTo("sentence.mp3");
        assertThat(feedback.explanationVi()).isEqualTo("Choose the polite request");
    }

    @Test
    void fillBlankAnswerMissingFromSentenceFails() {
        QuizQuestionVo question = fillBlankQuestion("The train leaves at six o'clock.", "The train departs at six.",
                option("d", "buy", false), option("c", "depart", true), option("b", "take", false),
                option("a", "make", false));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        TopicRequestException error = catchThrowableOfType(
                () -> mapper.toDto(mapper.sequence(version).get(0)), TopicRequestException.class);

        assertContentUnavailable(error, "iv1:quiz:0:0");
        assertThat(error.getMessage()).doesNotContain("depart");
    }

    @Test
    void fillBlankAnswerAppearingTwiceInSentenceFails() {
        QuizQuestionVo question = fillBlankQuestion("I want to book a book today.", "I want to reserve a book.",
                option("d", "buy", false), option("c", "book", true), option("b", "take", false),
                option("a", "make", false));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        assertContentUnavailable(() -> mapper.toDto(mapper.sequence(version).get(0)), "iv1:quiz:0:0");
    }

    @Test
    void fillBlankWithoutSentenceFails() {
        QuizQuestionVo question = QuizQuestionVo.builder()
                .prompt("Fill in the blank")
                .explanation("Choose the verb that fits")
                .options(List.of(option("d", "buy", false), option("c", "reserve", true),
                        option("b", "take", false), option("a", "make", false)))
                .build();
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        assertContentUnavailable(() -> mapper.toDto(mapper.sequence(version).get(0)), "iv1:quiz:0:0");
    }

    @Test
    void fillBlankWithBlankSentenceTextFails() {
        QuizQuestionVo question = fillBlankQuestion("   ", "I want a ticket.");

        assertContentUnavailable(() -> mapper.toDto(
                mapper.sequence(version(module(QuizType.FILL_BLANK, 0, "Fill blank", question))).get(0)),
                "iv1:quiz:0:0");
    }

    @Test
    void missingExplanationFailsAtDtoAndFeedbackMapping() {
        QuizQuestionVo question = QuizQuestionVo.builder()
                .prompt("Fill in the blank")
                .options(List.of(option("d", "buy", false), option("c", "reserve", true),
                        option("b", "take", false), option("a", "make", false)))
                .sentence(QuizSentenceVo.builder().textEn("I would like to reserve a ticket today.").build())
                .build();
        QuizEntry entry = mapper.sequence(version(module(QuizType.FILL_BLANK, 0, "Fill blank", question))).get(0);

        assertContentUnavailable(() -> mapper.toDto(entry), "iv1:quiz:0:0");
        assertContentUnavailable(() -> mapper.feedback(entry), "iv1:quiz:0:0");
    }

    @Test
    void missingModuleTitleFails() {
        QuizQuestionVo question = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.");
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, null, question));

        assertContentUnavailable(() -> mapper.toDto(mapper.sequence(version).get(0)), "iv1:quiz:0:0");
    }

    @Test
    void zeroCorrectOptionsFail() {
        QuizQuestionVo question = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.",
                option("d", "buy", false), option("c", "reserve", false), option("b", "take", false),
                option("a", "make", false));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        assertContentUnavailable(() -> mapper.toDto(mapper.sequence(version).get(0)), "iv1:quiz:0:0");
    }

    @Test
    void twoCorrectOptionsFail() {
        QuizQuestionVo question = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.",
                option("d", "buy", false), option("c", "reserve", true), option("b", "take", true),
                option("a", "make", false));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        assertContentUnavailable(() -> mapper.toDto(mapper.sequence(version).get(0)), "iv1:quiz:0:0");
    }

    @Test
    void fillBlankOutsideTheFourToSixOptionRangeFails() {
        QuizQuestionVo three = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.",
                option("d", "buy", false), option("c", "reserve", true), option("b", "take", false));
        QuizQuestionVo seven = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.",
                option("d", "buy", false), option("c", "reserve", true), option("b", "take", false),
                option("a", "make", false), option("e", "get", false), option("f", "hold", false),
                option("g", "keep", false));

        assertContentUnavailable(() -> mapper.toDto(
                mapper.sequence(version(module(QuizType.FILL_BLANK, 0, "Fill blank", three))).get(0)),
                "iv1:quiz:0:0");
        assertContentUnavailable(() -> mapper.toDto(
                mapper.sequence(version(module(QuizType.FILL_BLANK, 0, "Fill blank", seven))).get(0)),
                "iv1:quiz:0:0");
    }

    @Test
    void fillBlankWithBlankOptionTextFails() {
        QuizQuestionVo question = fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.",
                option("d", "buy", false), option("c", "reserve", true), option("b", "take", false),
                option("a", " ", false));
        TopicItemVersion version = version(module(QuizType.FILL_BLANK, 0, "Fill blank", question));

        assertContentUnavailable(() -> mapper.toDto(mapper.sequence(version).get(0)), "iv1:quiz:0:0");
    }

    @Test
    void situationalChoiceMustHaveExactlyFourOptions() {
        QuizQuestionVo five = situationalQuestion("Which sentence is polite?",
                option("A", "Could I have a map", false), option("B", "How much is the ticket", false),
                option("C", "Where is the departure gate", true), option("D", "May I sit here", false),
                option("E", "Thank you very much", false));
        QuizQuestionVo three = situationalQuestion("Which sentence is polite?",
                option("A", "Could I have a map", false),
                option("C", "Where is the departure gate", true), option("D", "May I sit here", false));

        assertContentUnavailable(() -> mapper.toDto(
                mapper.sequence(version(module(QuizType.SITUATIONAL_CHOICE, 0, "Situational", five))).get(0)),
                "iv1:quiz:0:0");
        assertContentUnavailable(() -> mapper.toDto(
                mapper.sequence(version(module(QuizType.SITUATIONAL_CHOICE, 0, "Situational", three))).get(0)),
                "iv1:quiz:0:0");
    }

    @Test
    void situationalChoiceWithBlankPromptFails() {
        QuizQuestionVo question = situationalQuestion("   ");

        assertContentUnavailable(() -> mapper.toDto(
                mapper.sequence(version(module(QuizType.SITUATIONAL_CHOICE, 0, "Situational", question))).get(0)),
                "iv1:quiz:0:0");
    }

    @Test
    void duplicateQuizIdFailsTheWholeItem() {
        TopicItemVersion version = version(
                module(QuizType.FILL_BLANK, 0, "Module A",
                        fillBlankQuestion("I would like to reserve a ticket today.", "I want a ticket.")),
                module(QuizType.SITUATIONAL_CHOICE, 0, "Module B",
                        situationalQuestion("Which sentence is polite?")));

        assertContentUnavailable(() -> mapper.sequence(version), "iv1:quiz:0:0");
    }

    private static void assertContentUnavailable(ThrowableAssert.ThrowingCallable call,
                                                 String expectedQuizId) {
        TopicRequestException error = catchThrowableOfType(call, TopicRequestException.class);
        assertContentUnavailable(error, expectedQuizId);
    }

    private static void assertContentUnavailable(TopicRequestException error, String expectedQuizId) {
        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getCode()).isEqualTo("CONTENT_UNAVAILABLE");
        assertThat(error.getMessage()).contains(expectedQuizId);
    }

    private static List<String> ids(List<QuizEntry> sequence) {
        return sequence.stream().map(QuizEntry::quizId).toList();
    }

    private static QuizOptionVo option(String label, String text, boolean correct) {
        return QuizOptionVo.builder().label(label).textEn(text).isCorrect(correct).build();
    }

    private static QuizQuestionVo fillBlankQuestion(String sentenceText, String meaningVi,
                                                    QuizOptionVo... options) {
        QuizOptionVo[] stored = options.length == 0
                ? new QuizOptionVo[]{
                        option("d", "buy", false),
                        option("c", "reserve", true),
                        option("b", "take", false),
                        option("a", "make", false)}
                : options;
        QuizQuestionVo.QuizQuestionVoBuilder builder = QuizQuestionVo.builder()
                .prompt("Fill in the blank")
                .audioUrl("question.mp3")
                .tips("Read the whole sentence")
                .explanation("Choose the verb that fits")
                .options(List.of(stored));
        if (sentenceText != null) {
            builder.sentence(QuizSentenceVo.builder()
                    .textEn(sentenceText)
                    .meaningVi(meaningVi)
                    .audioUrl("sentence.mp3")
                    .build());
        }
        return builder.build();
    }

    private static QuizQuestionVo situationalQuestion(String prompt, QuizOptionVo... options) {
        QuizOptionVo[] stored = options.length == 0
                ? new QuizOptionVo[]{
                        option("A", "Could I have a map", false),
                        option("B", "How much is the ticket", false),
                        option("C", "Where is the departure gate", true),
                        option("D", "May I sit here", false)}
                : options;
        return QuizQuestionVo.builder()
                .prompt(prompt)
                .explanation("Choose the polite request")
                .quizData(Map.of("scenario_vi", "At the airport counter"))
                .options(List.of(stored))
                .build();
    }

    private static QuizModuleVo module(QuizType type, Integer orderIndex, String title,
                                       QuizQuestionVo... questions) {
        return QuizModuleVo.builder()
                .quizType(type)
                .orderIndex(orderIndex)
                .title(title)
                .questions(List.of(questions))
                .build();
    }

    private static TopicItemVersion version(QuizModuleVo... modules) {
        return TopicItemVersion.builder().id("iv1").quizzes(List.of(modules)).build();
    }
}
