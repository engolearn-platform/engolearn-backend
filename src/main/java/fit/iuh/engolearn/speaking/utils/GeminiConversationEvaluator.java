package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import fit.iuh.engolearn.speaking.model.SpeakingContentSnapshot;
import fit.iuh.engolearn.speaking.utils.port.ConversationEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GeminiConversationEvaluator implements ConversationEvaluator {
    private final ObjectMapper mapper;
    private volatile HttpClient client;
    private final String apiKey;
    private final String model;
    private final String endpoint;

    public GeminiConversationEvaluator(ObjectMapper mapper,
            @Value("${engolearn.speaking.gemini.api-key:}") String apiKey,
            @Value("${engolearn.speaking.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${engolearn.speaking.gemini.endpoint:https://generativelanguage.googleapis.com/v1beta/models}") String endpoint) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
    }

    @Override
    public Result evaluate(SpeakingContentSnapshot content, String transcript,
                           List<Exchange> history, List<String> achievedGoalIds) {
        return evaluate(content, transcript, history, achievedGoalIds, null);
    }

    @Override
    public Result evaluate(SpeakingContentSnapshot content, String transcript,
                           List<Exchange> history, List<String> achievedGoalIds, String openingReply) {
        if (apiKey.isBlank()) throw new IllegalStateException("Gemini API key is not configured");
        String roleplay = content.context() == null ? content.description()
                : content.context() + "\nYour role: " + content.aiRole() + "\nLearner role: " + content.learnerRole();
        String opening = openingReply == null ? "" : "\nYour opening line: " + openingReply;
        String prompt = "You are an English conversation partner for a Vietnamese learner. "
                + "Assess only the finalized learner utterance. A valid turn must be intelligible and meaningful. "
                + "Award a goal only when its evidence is an exact quote from this utterance. "
                + "Correct only errors that change meaning. Keep dialogue and example phrases in English; "
                + "feedback, correction explanations and hints in Vietnamese. Give at most one short hint. "
                + "Prioritize missing REQUIRED goals in the next reply. Return only JSON with fields: "
                + "valid (boolean), reply (string), goalEvidence [{goalId,quote}], "
                + "resourceEvidence [{resourceId,quote,explanation}], "
                + "corrections [{original,corrected,explanation}], hint (string or null), feedback (string array).\n"
                + "Item: " + content.title() + "\nSituation: " + roleplay + opening
                + "\nGoals: " + content.goals() + "\nAlready achieved: " + achievedGoalIds
                + "\nVocabulary: " + content.vocabulary() + "\nExpressions: " + content.expressions()
                + "\nRecent dialogue: " + history.stream().skip(Math.max(0, history.size() - 5)).toList()
                + "\nFinal utterance: " + transcript;
        try {
            String body = mapper.writeValueAsString(Map.of("contents", List.of(Map.of("parts",
                    List.of(Map.of("text", prompt)))), "generationConfig",
                    Map.of("responseMimeType", "application/json")));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini returned HTTP " + response.statusCode());
            }
            JsonNode wrapper = mapper.readTree(response.body());
            String text = wrapper.path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText("");
            JsonNode result = mapper.readTree(text);
            List<SpeakingAssessmentRecord.GoalEvidence> goals = new ArrayList<>();
            for (JsonNode item : result.path("goalEvidence")) {
                String id = item.path("goalId").asText("");
                String quote = item.path("quote").asText("");
                if (content.goals().stream().anyMatch(goal -> goal.id().equals(id))
                        && !quote.isBlank() && transcript.toLowerCase().contains(quote.toLowerCase())) {
                    goals.add(new SpeakingAssessmentRecord.GoalEvidence(id, quote));
                }
            }
            List<SpeakingAssessmentRecord.ResourceEvidence> resources = new ArrayList<>();
            for (JsonNode item : result.path("resourceEvidence")) {
                String quote = item.path("quote").asText("");
                if (!quote.isBlank() && transcript.toLowerCase().contains(quote.toLowerCase())) {
                    resources.add(new SpeakingAssessmentRecord.ResourceEvidence(
                            item.path("resourceId").asText(""), quote,
                            item.path("explanation").asText("")));
                }
            }
            List<SpeakingAssessmentRecord.Correction> corrections = new ArrayList<>();
            for (JsonNode item : result.path("corrections")) {
                corrections.add(new SpeakingAssessmentRecord.Correction(item.path("original").asText(""),
                        item.path("corrected").asText(""), item.path("explanation").asText("")));
            }
            List<String> feedback = new ArrayList<>();
            for (JsonNode item : result.path("feedback")) {
                if (!item.asText("").isBlank()) feedback.add(item.asText());
            }
            String hint = result.path("hint").isNull() ? null : result.path("hint").asText(null);
            return new Result(result.path("valid").asBoolean(false), result.path("reply").asText(""),
                    goals, resources, corrections, hint, feedback);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Gemini evaluation failed", ex);
        }
    }

    private HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = HttpClient.newHttpClient();
                current = client;
            }
        }
        return current;
    }
}
