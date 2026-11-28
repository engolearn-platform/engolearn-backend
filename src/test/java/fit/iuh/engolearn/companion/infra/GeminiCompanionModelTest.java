package fit.iuh.engolearn.companion.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import fit.iuh.engolearn.companion.service.CompanionModelContinuation;
import fit.iuh.engolearn.companion.service.CompanionModelEvent;
import fit.iuh.engolearn.companion.service.CompanionModelRequest;
import fit.iuh.engolearn.companion.service.CompanionPromptMessage;
import fit.iuh.engolearn.companion.service.CompanionToolDefinition;
import fit.iuh.engolearn.companion.service.CompanionToolResult;
import fit.iuh.engolearn.config.CompanionProperties;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiCompanionModelTest {
    @Test
    void streamedToolRoundPreservesNativeCallIdAndSignatureAndUsesOneCumulativeTotal() {
        var transport = new FixtureTransport(List.of(
                responseWithPart(Part.builder()
                        .functionCall(FunctionCall.builder().id("call-9").name("lookup").args(Map.of()).build())
                        .thoughtSignature(new byte[]{3, 1, 4}).build(), 89),
                responseWithoutCandidate(89)));
        var model = new GeminiCompanionModel(new CompanionProperties(), transport);
        var request = new CompanionModelRequest(
                List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "Look this up")),
                List.of(new CompanionToolDefinition("lookup", "Look something up", "{\"type\":\"OBJECT\"}")),
                null, List.of());

        List<CompanionModelEvent> events = model.stream(request).collectList().block();

        assertThat(events).hasSize(2);
        var calls = (CompanionModelEvent.ToolCalls) events.get(0);
        assertThat(calls.calls()).containsExactly(new fit.iuh.engolearn.companion.service.CompanionToolCall(
                "call-9", "lookup", Map.of()));
        assertThat(events.get(1)).isEqualTo(new CompanionModelEvent.RoundCompleted(
                new fit.iuh.engolearn.companion.service.CompanionUsage(89)));
        assertThat(transport.lastConfig.automaticFunctionCalling().orElseThrow().disable()).contains(true);
        assertThat(transport.lastConfig.tools()).isPresent();
        assertThat(transport.lastConfig.toolConfig().orElseThrow().functionCallingConfig().orElseThrow()
                .streamFunctionCallArguments()).isEmpty();
    }

    @Test
    void laterToolRoundTripsRetainEarlierExchangesAndExactFunctionResponseIds() {
        var transport = FixtureTransport.forRounds(List.of(
                List.of(toolCallResponse("call-9", new byte[]{3, 1, 4}, 89)),
                List.of(toolCallResponse("call-10", new byte[]{1, 5, 9}, 93)),
                List.of(responseWithoutCandidate(101))));
        var model = new GeminiCompanionModel(new CompanionProperties(), transport);
        var prompt = List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "Look this up"));
        var first = new CompanionModelRequest(
                prompt,
                List.of(new CompanionToolDefinition("lookup", "Look something up", "{\"type\":\"OBJECT\"}")),
                null, List.of());
        var firstEvents = model.stream(first).collectList().block();
        var firstCalls = (CompanionModelEvent.ToolCalls) firstEvents.get(0);
        var second = new CompanionModelRequest(
                prompt,
                List.of(new CompanionToolDefinition("lookup", "Look something up", "{\"type\":\"OBJECT\"}")),
                firstCalls.continuation(),
                List.of(new CompanionToolResult("call-9", "lookup", "{\"step\":1}")));
        var secondEvents = model.stream(second).collectList().block();
        var secondCalls = (CompanionModelEvent.ToolCalls) secondEvents.get(0);
        var third = new CompanionModelRequest(
                prompt, List.of(), secondCalls.continuation(),
                List.of(new CompanionToolResult("call-10", "lookup", "{\"step\":2}")));

        List<CompanionModelEvent> events = model.stream(third).collectList().block();

        assertThat(events).containsExactly(new CompanionModelEvent.RoundCompleted(
                new fit.iuh.engolearn.companion.service.CompanionUsage(101)));
        List<Content> sent = transport.lastContents;
        assertThat(sent).hasSize(5);
        List<Part> nativeCalls = sent.stream().filter(content -> "model".equals(content.role().orElse(null)))
                .flatMap(content -> content.parts().orElse(List.of()).stream()).toList();
        assertThat(nativeCalls).hasSize(2);
        assertThat(nativeCalls.get(0).functionCall().orElseThrow().id()).contains("call-9");
        assertThat(nativeCalls.get(0).thoughtSignature().orElseThrow()).containsExactly((byte) 3, (byte) 1, (byte) 4);
        assertThat(nativeCalls.get(1).functionCall().orElseThrow().id()).contains("call-10");
        assertThat(nativeCalls.get(1).thoughtSignature().orElseThrow()).containsExactly((byte) 1, (byte) 5, (byte) 9);
        List<String> resultIds = sent.stream().flatMap(content -> content.parts().orElse(List.of()).stream())
                .map(part -> part.functionResponse().map(response -> response.id().orElse(null)).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        assertThat(resultIds).containsExactly("call-9", "call-10");
        assertThat(transport.lastConfig.tools()).isEmpty();
    }

    @Test
    void toolResultRoundTripRestoresNativeSignatureAndCorrelatesFunctionResponseId() {
        var transport = new FixtureTransport(List.of(responseWithoutCandidate(93)));
        var model = new GeminiCompanionModel(new CompanionProperties(), transport);
        var continuation = continuationFromToolRound();
        var request = new CompanionModelRequest(
                List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "Look this up")),
                List.of(), continuation,
                List.of(new CompanionToolResult("call-9", "lookup", "{\"found\":true}")));

        List<CompanionModelEvent> events = model.stream(request).collectList().block();

        assertThat(events).containsExactly(new CompanionModelEvent.RoundCompleted(
                new fit.iuh.engolearn.companion.service.CompanionUsage(93)));
        List<Content> sent = transport.lastContents;
        Content nativeAssistant = sent.stream().filter(content -> "model".equals(content.role().orElse(null)))
                .findFirst().orElseThrow();
        Part nativeCall = nativeAssistant.parts().orElseThrow().get(0);
        assertThat(nativeCall.functionCall().orElseThrow().id()).contains("call-9");
        assertThat(nativeCall.thoughtSignature().orElseThrow()).containsExactly((byte) 3, (byte) 1, (byte) 4);
        var functionResponse = sent.stream().flatMap(content -> content.parts().orElse(List.of()).stream())
                .map(part -> part.functionResponse().orElse(null)).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
        assertThat(functionResponse.id()).contains("call-9");
        assertThat(functionResponse.name()).contains("lookup");
        assertThat(transport.lastConfig.tools()).isEmpty();
    }

    @Test
    void ordinaryTextStreamsBeforeTerminalUsageAndMissingAuthoritativeTotalFails() {
        var textTransport = new FixtureTransport(List.of(
                responseWithPart(Part.fromText("Hello"), null), responseWithoutCandidate(4)));
        var model = new GeminiCompanionModel(new CompanionProperties(), textTransport);
        var request = new CompanionModelRequest(
                List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "Hi")), List.of(), null, List.of());

        assertThat(model.stream(request).collectList().block()).containsExactly(
                new CompanionModelEvent.TextDelta("Hello"),
                new CompanionModelEvent.RoundCompleted(new fit.iuh.engolearn.companion.service.CompanionUsage(4)));

        var missingUsage = new GeminiCompanionModel(new CompanionProperties(),
                new FixtureTransport(List.of(responseWithoutCandidate(null))));
        assertThatThrownBy(() -> missingUsage.stream(request).collectList().block())
                .isInstanceOf(fit.iuh.engolearn.companion.exception.CompanionException.class);
    }

    @Test
    void actualRegistryDeclarationsAndNativeMultiDomainContinuationAreOffline() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var assessments = org.mockito.Mockito.mock(fit.iuh.engolearn.assessment.contract.AssessmentCompanionResults.class);
        var registry = new fit.iuh.engolearn.companion.service.impl.CompanionToolRegistry(List.of(
                new fit.iuh.engolearn.companion.service.impl.CompanionAssessmentToolHandler(assessments,mapper),
                new fit.iuh.engolearn.companion.service.impl.CompanionMemoryToolHandler(
                        org.mockito.Mockito.mock(fit.iuh.engolearn.companion.service.CompanionMemoryService.class),
                        org.mockito.Mockito.mock(fit.iuh.engolearn.companion.service.MemoryRetrievalService.class),
                        mapper,"synthetic-offline-test-only"),
                new fit.iuh.engolearn.companion.service.impl.CompanionTopicToolHandler(
                        org.mockito.Mockito.mock(fit.iuh.engolearn.topic.contract.TopicCompanionReads.class),mapper),
                new fit.iuh.engolearn.companion.service.impl.CompanionLearningPathToolHandler(
                        org.mockito.Mockito.mock(fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads.class),mapper),
                new fit.iuh.engolearn.companion.service.impl.CompanionAssessmentHistoryToolHandler(assessments,mapper)));
        var firstParts = List.of(nativeCall("topic-1","findLearningTopics",Map.of("level","B1"),new byte[]{1,2}),
                nativeCall("path-1","getLearningPathProgress",Map.of("mode","ACTIVE"),new byte[]{3,4}));
        var secondParts = List.of(nativeCall("history-1","getAssessmentHistory",Map.of("detail","SKILLS"),new byte[]{5,6}),
                nativeCall("latest-1","getLatestAssessmentResult",Map.of("detail","SKILLS"),new byte[]{7,8}));
        var transport = FixtureTransport.forRounds(List.of(
                List.of(responseWithParts(firstParts,4)),List.of(responseWithParts(secondParts,5)),
                List.of(responseWithoutCandidate(3))));
        var model = new GeminiCompanionModel(new CompanionProperties(),transport);
        var prompt = List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER,"Guide me"));
        var declarations = registry.definitions(true);
        var first = (CompanionModelEvent.ToolCalls)model.stream(new CompanionModelRequest(prompt,declarations,null,List.of()))
                .collectList().block().getFirst();
        var nativeDeclarations = transport.lastConfig.tools().orElseThrow().getFirst().functionDeclarations().orElseThrow();
        assertThat(nativeDeclarations).hasSize(9);
        var schemas = new java.util.HashMap<String,com.google.genai.types.Schema>();
        for (var declaration : nativeDeclarations)
            schemas.put(declaration.name().orElseThrow(),declaration.parameters().orElseThrow());
        assertThat(schemas.keySet()).containsExactlyInAnyOrder("getLatestAssessmentResult","searchMemory","createMemory",
                "updateMemory","deleteMemory","findLearningTopics","getTopicLearningProgress","getLearningPathProgress","getAssessmentHistory");
        for (var declaration : declarations) {
            var expected = mapper.readTree(declaration.inputSchemaJson());
            var schema = schemas.get(declaration.name());
            assertThat(schema.type()).isPresent();
            var props = schema.properties().orElseThrow();
            assertThat(props.keySet()).containsExactlyInAnyOrderElementsOf(expected.get("properties").propertyNames());
            var expectedRequired = new ArrayList<String>();
            if (expected.has("required")) expected.get("required").forEach(node -> expectedRequired.add(node.asText()));
            assertThat(schema.required().orElse(List.of())).containsExactlyElementsOf(expectedRequired);
            for (var entry : props.entrySet()) {
                var wanted = expected.get("properties").get(entry.getKey());
                var actual = entry.getValue();
                assertThat(actual.type()).isPresent();
                if (wanted.has("enum")) {
                    var values = new ArrayList<String>();
                    wanted.get("enum").forEach(node -> values.add(node.asText()));
                    assertThat(actual.enum_().orElseThrow()).containsExactlyElementsOf(values);
                }
                if (wanted.has("minimum")) assertThat(actual.minimum()).contains(wanted.get("minimum").asDouble());
                if (wanted.has("maximum")) assertThat(actual.maximum()).contains(wanted.get("maximum").asDouble());
                if (wanted.has("maxLength")) assertThat(actual.maxLength()).contains(wanted.get("maxLength").asLong());
                if (wanted.has("minLength")) assertThat(actual.minLength()).contains(wanted.get("minLength").asLong());
                if (wanted.has("maxItems")) assertThat(actual.maxItems()).contains(wanted.get("maxItems").asLong());
            }
        }
        assertThat(first.calls().stream().map(fit.iuh.engolearn.companion.service.CompanionToolCall::callId).toList())
                .containsExactly("topic-1","path-1");
        var second = (CompanionModelEvent.ToolCalls)model.stream(new CompanionModelRequest(prompt,declarations,first.continuation(),
                List.of(new CompanionToolResult("topic-1","findLearningTopics","{\"status\":\"SUCCESS\"}"),
                        new CompanionToolResult("path-1","getLearningPathProgress","{\"status\":\"ERROR\"}"))))
                .collectList().block().getFirst();
        assertThat(second.calls().stream().map(fit.iuh.engolearn.companion.service.CompanionToolCall::callId).toList())
                .containsExactly("history-1","latest-1");
        model.stream(new CompanionModelRequest(prompt,List.of(),second.continuation(),
                List.of(new CompanionToolResult("history-1","getAssessmentHistory","{\"status\":\"NO_DATA\"}"),
                        new CompanionToolResult("latest-1","getLatestAssessmentResult","{\"status\":\"SUCCESS\"}"))))
                .collectList().block();
        var sent = transport.lastContents.stream().flatMap(content -> content.parts().orElse(List.of()).stream()).toList();
        var nativeCalls = sent.stream().filter(part -> part.functionCall().isPresent()).toList();
        assertThat(nativeCalls.stream().map(part -> part.functionCall().orElseThrow().id().orElseThrow()).toList())
                .containsExactly("topic-1","path-1","history-1","latest-1");
        for (int i=0;i<nativeCalls.size();i++)
            assertThat(nativeCalls.get(i).thoughtSignature().orElseThrow()).containsExactly((byte)(i*2+1),(byte)(i*2+2));
        var responses = sent.stream().flatMap(part -> part.functionResponse().stream()).toList();
        assertThat(responses.stream().map(response -> response.id().orElseThrow()).toList())
                .containsExactly("topic-1","path-1","history-1","latest-1");
        assertThat(responses.stream().map(response -> response.name().orElseThrow()).toList())
                .containsExactly("findLearningTopics","getLearningPathProgress","getAssessmentHistory","getLatestAssessmentResult");
        assertThat(responses.stream().map(response -> response.response().orElseThrow().get("status")).toList())
                .containsExactly("SUCCESS","ERROR","NO_DATA","SUCCESS");
    }

    private static Part nativeCall(String id,String name,Map<String,Object> args,byte[] signature) {
        return Part.builder().functionCall(FunctionCall.builder().id(id).name(name).args(args).build())
                .thoughtSignature(signature).build();
    }

    private static GenerateContentResponse responseWithParts(List<Part> parts,int tokens) {
        return GenerateContentResponse.builder().candidates(Candidate.builder()
                .content(Content.builder().role("model").parts(parts).build()).build())
                .usageMetadata(GenerateContentResponseUsageMetadata.builder().totalTokenCount(tokens).build()).build();
    }


    private static CompanionModelContinuation continuationFromToolRound() {
        var transport = new FixtureTransport(List.of(toolCallResponse("call-9", new byte[]{3, 1, 4}, 89)));
        var model = new GeminiCompanionModel(new CompanionProperties(), transport);
        var request = new CompanionModelRequest(
                List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "Look this up")),
                List.of(new CompanionToolDefinition("lookup", "Look something up", "{\"type\":\"OBJECT\"}")),
                null, List.of());
        return ((CompanionModelEvent.ToolCalls) model.stream(request).collectList().block().get(0)).continuation();
    }

    private static GenerateContentResponse toolCallResponse(String callId, byte[] signature, Integer totalTokens) {
        return responseWithPart(Part.builder()
                .functionCall(FunctionCall.builder().id(callId).name("lookup").args(Map.of()).build())
                .thoughtSignature(signature).build(), totalTokens);
    }

    private static GenerateContentResponse responseWithPart(Part part, Integer totalTokens) {
        var candidate = Candidate.builder().content(Content.builder().role("model").parts(part).build()).build();
        var response = GenerateContentResponse.builder().candidates(candidate);
        if (totalTokens != null) response.usageMetadata(GenerateContentResponseUsageMetadata.builder()
                .totalTokenCount(totalTokens).build());
        return response.build();
    }

    private static GenerateContentResponse responseWithoutCandidate(Integer totalTokens) {
        var response = GenerateContentResponse.builder();
        if (totalTokens != null) response.usageMetadata(GenerateContentResponseUsageMetadata.builder()
                .totalTokenCount(totalTokens).build());
        return response.build();
    }

    private static final class FixtureTransport implements GeminiContentTransport {
        private final List<List<GenerateContentResponse>> responseRounds;
        private List<Content> lastContents;
        private GenerateContentConfig lastConfig;
        private int roundIndex;

        private FixtureTransport(List<GenerateContentResponse> responses) {
            this.responseRounds = List.of(responses);
        }

        private FixtureTransport(List<List<GenerateContentResponse>> responseRounds, boolean multipleRounds) {
            this.responseRounds = responseRounds;
        }

        private static FixtureTransport forRounds(List<List<GenerateContentResponse>> responseRounds) {
            return new FixtureTransport(responseRounds, true);
        }

        @Override
        public GeminiContentStream stream(String model, List<Content> contents, GenerateContentConfig config) {
            this.lastContents = List.copyOf(contents);
            this.lastConfig = config;
            List<GenerateContentResponse> responses = responseRounds.get(Math.min(roundIndex++, responseRounds.size() - 1));
            return new FixtureStream(responses);
        }
    }

    private static final class FixtureStream implements GeminiContentTransport.GeminiContentStream {
        private final List<GenerateContentResponse> responses;

        private FixtureStream(List<GenerateContentResponse> responses) {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public Iterator<GenerateContentResponse> iterator() {
            return responses.iterator();
        }

        @Override
        public void close() {}
    }
}
