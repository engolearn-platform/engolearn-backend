package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.model.SpeakingGoal;
import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingDraftValidatorContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<JsonNode> fixtures;

    @BeforeAll
    static void loadFixtures() throws IOException {
        Path path = findWorkspaceFixture("03-speaking-ai-contract-fixtures.json");
        var loaded = new ArrayList<JsonNode>();
        MAPPER.readTree(Files.readString(path)).withArray("fixtures").forEach(loaded::add);
        fixtures = List.copyOf(loaded);
    }

    private static Path findWorkspaceFixture(String fileName) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(Path.of("Note", "speaking-with-AI", "redesign-inside", fileName));
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace fixture: " + fileName);
    }

    @ParameterizedTest(name = "{0} follows the reviewed schema expectation")
    @MethodSource("reviewedFixtureIds")
    void validatesReviewedFixtureExpectation(String id) {
        JsonNode fixture = fixture(id);
        var validator = new SpeakingDraftValidator(MAPPER);

        if (fixture.path("expectedSchemaValid").asBoolean()) {
            assertDoesNotThrow(() -> validator.validateSchema(fixture.path("response").toString()));
        } else {
            assertThrows(SpeakingDraftValidationException.class,
                    () -> validator.validateSchema(fixture.path("response").toString()));
        }
    }

    @Test
    void bindsOnlyAfterStrictSchemaValidation() {
        var validator = new SpeakingDraftValidator(MAPPER);
        String valid = fixture("ACT-01").path("response").toString();

        var draft = assertDoesNotThrow(() -> validator.validateSchema(valid));

        assertEquals("speaking.ai.draft.v1", draft.contractVersion());
        assertEquals("REACT_AND_ASK", draft.dialogue().proposedAct().name());
    }

    @Test
    void semanticValidationKeepsOnlyEvidenceFromCurrentFinalizedTranscript() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I want the large coffee.",
                List.of(new SpeakingGoal("coffee", "Coffee", "coffee", SpeakingGoal.Priority.REQUIRED)),
                Map.of("coffee", new SpeakingDraftValidator.PinnedResource("coffee", "VOCABULARY", "coffee")));
        String response = fixture("ACT-01").path("response").toString()
                .replace("\"goalEvidence\":[]", "\"goalEvidence\":[{\"goalId\":\"coffee\",\"quote\":\"I want the large coffee\"}]")
                .replace("What would you like to drink?", "What size would you like?");

        var proposal = assertDoesNotThrow(() -> validator.validate(response, context));

        assertEquals(List.of("coffee"), proposal.goalEvidence().stream().map(e -> e.goalId()).toList());
        assertTrue(proposal.valid());
    }

    @Test
    void rejectsUnknownFieldsBeforeTypedBinding() {
        var validator = new SpeakingDraftValidator(MAPPER);
        String response = fixture("ACT-01").path("response").toString()
                .replace("\"dialogue\":", "\"modelControlledStatus\":\"COMPLETED\",\"dialogue\":");

        assertThrows(SpeakingDraftValidationException.class, () -> validator.validateSchema(response));
    }

    @Test
    void adapterDoesNotInvokePersistenceOrLifecycle() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I want coffee.",
                List.of(new SpeakingGoal("coffee", "Order coffee", "order coffee", SpeakingGoal.Priority.REQUIRED)),
                Map.of());
        var proposal = validator.validate(fixture("ACT-01").path("response").toString(), context);

        var record = SpeakingAssessmentRecordAdapter.toRecord("session", "learner", "I want coffee.", proposal);

        assertEquals("session", record.getSessionId());
        assertEquals("learner", record.getLearnerId());
        assertNull(record.getId());
        assertEquals("speaking-v1", record.getCriteriaVersion());
    }

    @Test
    void omitsNegatedQuotedAndQuestionEvidence() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I do not want coffee. Did you say 'I want tea'?",
                List.of(new SpeakingGoal("coffee", "Coffee", "coffee", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("tea", "Tea", "tea", SpeakingGoal.Priority.REQUIRED)), Map.of());
        String response = fixture("ACT-01").path("response").toString()
                .replace("\"goalEvidence\":[]", "\"goalEvidence\":[{\"goalId\":\"coffee\",\"quote\":\"I do not want coffee\"},{\"goalId\":\"tea\",\"quote\":\"I want tea\"}]");

        var proposal = validator.validate(response, context);

        assertTrue(proposal.goalEvidence().isEmpty());
    }

    @Test
    void requiresAllExplicitConditionsOfAMultiConditionGoal() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I want coffee.",
                List.of(new SpeakingGoal("order", "Order", "coffee and large", SpeakingGoal.Priority.REQUIRED)), Map.of());
        String response = fixture("ACT-01").path("response").toString()
                .replace("\"goalEvidence\":[]", "\"goalEvidence\":[{\"goalId\":\"order\",\"quote\":\"I want coffee\"}]");

        var proposal = validator.validate(response, context);

        assertTrue(proposal.goalEvidence().isEmpty());
    }

    @Test
    void rejectsResourceEvidenceFromWrongPinnedContextAndMeaningBreakingCorrection() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I do not like tea.", List.of(),
                Map.of("coffee", new SpeakingDraftValidator.PinnedResource("coffee", "VOCABULARY", "coffee")));
        String response = fixture("ACT-01").path("response").toString()
                .replace("\"resourceEvidence\":[]", "\"resourceEvidence\":[{\"resourceId\":\"coffee\",\"resourceKind\":\"VOCABULARY\",\"quote\":\"tea\"}]")
                .replace("\"corrections\":[]", "\"corrections\":[{\"original\":\"I do not like tea\",\"suggestedEnglish\":\"I like tea\",\"explanationVi\":\"Meaning\"}]");

        var proposal = validator.validate(response, context);

        assertTrue(proposal.resourceEvidence().isEmpty());
        assertTrue(proposal.corrections().isEmpty());
    }

    @Test
    void acceptsAtMostOneConservativeCorrectionAndRejectsLikelySttSubstitution() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I need two tickets.", List.of(), Map.of());
        String response = fixture("ACT-01").path("response").toString()
                .replace("\"corrections\":[]", "\"corrections\":["
                        + "{\"original\":\"tickets\",\"suggestedEnglish\":\"ticket\",\"explanationVi\":\"Số nhiều\"},"
                        + "{\"original\":\"two\",\"suggestedEnglish\":\"too\",\"explanationVi\":\"Word\"},"
                        + "{\"original\":\"hello\",\"suggestedEnglish\":\"hallo\",\"explanationVi\":\"Sound\"}]");

        var proposal = validator.validate(response, context);

        assertTrue(proposal.corrections().size() <= 1);
        assertTrue(proposal.corrections().stream().noneMatch(item -> item.original().equals("hello")));
    }

    @Test
    void omitsDialogueTargetOutsidePinnedSnapshotOrAlreadyAchieved() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = new SpeakingDraftValidator.ValidationContext("I can pay by card.",
                new SpeakingContentSnapshot("item", "version", "title", "description",
                        List.of(new SpeakingGoal("price", "Ask price", "price", SpeakingGoal.Priority.REQUIRED)),
                        null, 3, List.of(), List.of(), 1L, "REQUIRED_GOALS_V2"), Map.of(), List.of("price"));
        String response = fixture("ACT-05").path("response").toString();

        var proposal = validator.validate(response, context);

        assertNull(proposal.dialogue());
    }

    @Test
    void keepsReviewedWrapUpSchemaButDoesNotAuthorizeItAsNormalDialogue() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var proposal = validator.validate(fixture("ACT-07").path("response").toString(),
                context("I am done.", List.of(), Map.of()));

        assertTrue(proposal.valid());
        assertNull(proposal.dialogue());
    }

    @Test
    void validatesV2EnglishHintAndVietnameseLeadAsSeparateNullableFields() {
        var validator = new SpeakingDraftValidator(MAPPER);
        String response = """
                {"contractVersion":"speaking.ai.draft.v2","assessment":{"turnValidityProposal":"VALID","userIntent":"STATEMENT","goalEvidence":[],"resourceEvidence":[],"corrections":[],"hintExampleEn":"I would like to check my gate.","hintLeadVi":"Bạn có thể nói:"},"dialogue":{"proposedAct":"REACT_AND_INVITE","spokenReply":"I can help with that.","continuationType":"INVITATION","primaryQuestion":null,"targetGoalId":null}}
                """;

        var proposal = assertDoesNotThrow(() -> validator.validateV2(response,
                context("I need help.", List.of(), Map.of())));

        assertEquals("I would like to check my gate.", proposal.hintExampleEn());
        assertEquals("Bạn có thể nói:", proposal.hintLeadVi());
        assertEquals("speaking-v2", proposal.criteriaVersion());

        var record = SpeakingAssessmentRecordAdapter.toRecord("session", "learner", "I need help.", proposal);
        assertEquals("I would like to check my gate.", record.getHintExampleEn());
        assertEquals("Bạn có thể nói:", record.getHintLeadVi());
        assertEquals("speaking-v2", record.getCriteriaVersion());
    }

    @Test
    void v2ClosingTurnSuppressesHintFieldsWithoutRejectingAssessment() {
        var validator = new SpeakingDraftValidator(MAPPER);
        String response = """
                {"contractVersion":"speaking.ai.draft.v2","assessment":{"turnValidityProposal":"VALID","userIntent":"CLOSING_UTTERANCE","goalEvidence":[],"resourceEvidence":[],"corrections":[],"hintExampleEn":"Please ask about the gate.","hintLeadVi":"Bạn có thể nói:"},"dialogue":{"proposedAct":"REACT_AND_INVITE","spokenReply":"Goodbye.","continuationType":"INVITATION","primaryQuestion":null,"targetGoalId":null}}
                """;

        var proposal = validator.validateV2(response, context("That's all.", List.of(), Map.of()));

        assertTrue(proposal.valid());
        assertNull(proposal.hintExampleEn());
        assertNull(proposal.hintLeadVi());
    }

    @Test
    void v2AcceptsPinnedGoalIdsWithoutRecheckingNaturalLanguageEvidence() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("Can I have a small iced coffee, please? I don't want milk.", List.of(
                new SpeakingGoal("choose-small-size", "Choose the small size",
                        "Specify that you want a small drink.", SpeakingGoal.Priority.REQUIRED),
                new SpeakingGoal("answer-about-milk", "Answer the milk question",
                        "Tell the barista whether you want milk.", SpeakingGoal.Priority.OPTIONAL),
                new SpeakingGoal("ask-price", "Ask for the price",
                        "Ask the barista how much the drink costs.", SpeakingGoal.Priority.REQUIRED)), Map.of());
        String response = v2("STATEMENT", "[{\"goalId\":\"choose-small-size\",\"quote\":\"Can I have a small iced coffee, please?\"},"
                + "{\"goalId\":\"answer-about-milk\",\"quote\":\"I don't want milk.\"},"
                + "{\"goalId\":\"ask-price\",\"quote\":\"'How much is the drink?'\"},"
                + "{\"goalId\":\"unknown\",\"quote\":\"Anything\"}]");

        var proposal = validator.validateV2(response, context);

        assertEquals(List.of("choose-small-size", "answer-about-milk", "ask-price"), proposal.goalEvidence().stream()
                .map(SpeakingAssessmentRecord.GoalEvidence::goalId).toList());
        assertEquals("Can I have a small iced coffee, please?", proposal.goalEvidence().get(0).quote());
        assertEquals("I don't want milk.", proposal.goalEvidence().get(1).quote());
        assertEquals("'How much is the drink?'", proposal.goalEvidence().get(2).quote());
    }

    @Test
    void v2IgnoresAlreadyAchievedAndDuplicateGoalIdsAndKeepsV1Filtering() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = new SpeakingDraftValidator.ValidationContext("I want coffee.",
                new SpeakingContentSnapshot("item", "version", "title", "description", List.of(
                        new SpeakingGoal("coffee", "Coffee", "large coffee", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("tea", "Tea", "tea", SpeakingGoal.Priority.REQUIRED)),
                        null, 2, List.of(), List.of(), 1L, "REQUIRED_GOALS_V2"), Map.of(), List.of("tea"));
        String response = v2("STATEMENT", "[{\"goalId\":\"coffee\",\"quote\":\"not in transcript\"},"
                + "{\"goalId\":\"coffee\",\"quote\":\"duplicate\"},{\"goalId\":\"tea\",\"quote\":\"achieved\"}]");

        var v2Proposal = validator.validateV2(response, context);
        var noEvidence = validator.validateV2(v2("STATEMENT", "[]"), context);
        String v1 = fixture("ACT-01").path("response").toString()
                .replace("\"goalEvidence\":[]", "\"goalEvidence\":[{\"goalId\":\"coffee\",\"quote\":\"not in transcript\"}]");

        assertEquals(List.of("coffee"), v2Proposal.goalEvidence().stream()
                .map(SpeakingAssessmentRecord.GoalEvidence::goalId).toList());
        assertTrue(noEvidence.goalEvidence().isEmpty());
        assertTrue(validator.validate(v1, context).goalEvidence().isEmpty());
    }

    @Test
    void v2StillRejectsBlankGoalEvidenceFieldsAndKeepsResourceValidation() {
        var validator = new SpeakingDraftValidator(MAPPER);
        var context = context("I want coffee.", List.of(
                new SpeakingGoal("coffee", "Coffee", "coffee", SpeakingGoal.Priority.REQUIRED)),
                Map.of("coffee", new SpeakingDraftValidator.PinnedResource("coffee", "VOCABULARY", "coffee"),
                        "tea", new SpeakingDraftValidator.PinnedResource("tea", "EXPRESSION", "tea")));
        assertThrows(SpeakingDraftValidationException.class,
                () -> validator.validateV2(v2("STATEMENT", "[{\"goalId\":\"coffee\",\"quote\":\" \"}]"), context));
        assertThrows(SpeakingDraftValidationException.class,
                () -> validator.validateV2(v2("STATEMENT", "[{\"goalId\":\" \",\"quote\":\"coffee\"}]"), context));
        assertThrows(SpeakingDraftValidationException.class,
                () -> validator.validateV2(v2("STATEMENT", "[{\"quote\":\"coffee\"}]"), context));
        assertThrows(SpeakingDraftValidationException.class,
                () -> validator.validateV2(v2("STATEMENT", "[{\"goalId\":\"coffee\"}]"), context));

        var proposal = validator.validateV2(v2WithResource("[]",
                "[{\"resourceId\":\"coffee\",\"resourceKind\":\"VOCABULARY\",\"quote\":\"coffee\"},"
                        + "{\"resourceId\":\"tea\",\"resourceKind\":\"EXPRESSION\",\"quote\":\"tea\"}]"), context);
        assertEquals(1, proposal.resourceEvidence().size());
        assertEquals("coffee", proposal.resourceEvidence().get(0).quote());
    }

    private static String v2(String intent, String goals) {
        return v2WithResource(goals, "[]").replace("\"userIntent\":\"STATEMENT\"",
                "\"userIntent\":\"" + intent + "\"");
    }

    private static String v2WithResource(String goals, String resources) {
        return "{\"contractVersion\":\"speaking.ai.draft.v2\",\"assessment\":{"
                + "\"turnValidityProposal\":\"VALID\",\"userIntent\":\"STATEMENT\","
                + "\"goalEvidence\":" + goals + ",\"resourceEvidence\":" + resources
                + ",\"corrections\":[],\"hintExampleEn\":null,\"hintLeadVi\":null},"
                + "\"dialogue\":{\"proposedAct\":\"REACT_AND_INVITE\",\"spokenReply\":\"Okay.\","
                + "\"continuationType\":\"INVITATION\",\"primaryQuestion\":null,\"targetGoalId\":null}}";
    }

    private static Stream<String> reviewedFixtureIds() {
        return Stream.concat(Stream.of("ACT-01", "ACT-02", "ACT-03", "ACT-04", "ACT-05", "ACT-06", "ACT-07",
                        "REC-01", "REC-02"), Stream.of("BAD-01", "BAD-02", "BAD-03", "BAD-04", "BAD-05", "BAD-06",
                        "BAD-07", "BAD-08", "BAD-09", "BAD-10"));
    }

    private JsonNode fixture(String id) {
        return fixtures.stream().filter(fixture -> id.equals(fixture.path("id").asText())).findFirst().orElseThrow();
    }

    private static SpeakingDraftValidator.ValidationContext context(String transcript, List<SpeakingGoal> goals,
            Map<String, SpeakingDraftValidator.PinnedResource> resources) {
        return new SpeakingDraftValidator.ValidationContext(transcript,
                new SpeakingContentSnapshot("item", "version", "title", "description", goals, null, 3,
                        List.of("coffee"), List.of(), 1L, "REQUIRED_GOALS_V2"), resources, List.of());
    }
}
