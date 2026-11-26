package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.model.enums.MemorySource;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.service.CompanionMemoryOperationCursor;
import fit.iuh.engolearn.companion.service.CompanionMemoryOperationEvent;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.CompanionMemoryToolContext;
import fit.iuh.engolearn.companion.service.CompanionToolDefinition;
import fit.iuh.engolearn.companion.service.MemoryMutation;
import fit.iuh.engolearn.companion.service.MemoryOperationResult;
import fit.iuh.engolearn.companion.service.MemoryRetrievalResult;
import fit.iuh.engolearn.companion.service.MemoryRetrievalService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
public class CompanionMemoryToolHandler {
    private static final Logger log = LoggerFactory.getLogger(CompanionMemoryToolHandler.class);
    private static final String SEARCH = "searchMemory";
    private static final String CREATE = "createMemory";
    private static final String UPDATE = "updateMemory";
    private static final String DELETE = "deleteMemory";
    private static final List<CompanionToolDefinition> DEFINITIONS = List.of(
            new CompanionToolDefinition(SEARCH, "Search this learner's existing memories. Use for memory context the learner asks about.",
                    "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":500},\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10}},\"required\":[\"query\"],\"additionalProperties\":false}"),
            new CompanionToolDefinition(CREATE, "Save one useful memory grounded in the current user message. Include an exact evidence excerpt; content may be a faithful paraphrase. Classify trigger as USER_REQUESTED only for a direct request, otherwise AUTOMATIC.",
                    "{\"type\":\"object\",\"properties\":{\"category\":{\"type\":\"string\",\"enum\":[\"FACT\",\"GOAL\",\"PREFERENCE\",\"OBSERVATION\",\"EXPERIENCE\"]},\"content\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":2000},\"source\":{\"type\":\"string\",\"enum\":[\"EXPLICIT\",\"INFERRED\"]},\"trigger\":{\"type\":\"string\",\"enum\":[\"USER_REQUESTED\",\"AUTOMATIC\"]},\"retrievalMode\":{\"type\":\"string\",\"enum\":[\"ALWAYS_ON\",\"RELEVANT\"]},\"evidence\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"category\",\"content\",\"source\",\"trigger\",\"evidence\"],\"additionalProperties\":false}"),
            new CompanionToolDefinition(UPDATE, "Update an owned memory using an exact evidence excerpt from the current user message. Preserve its category and source. Classify trigger as USER_REQUESTED only for a direct request, otherwise AUTOMATIC.",
                    "{\"type\":\"object\",\"properties\":{\"memoryId\":{\"type\":\"string\"},\"content\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":2000},\"expectedVersion\":{\"type\":\"integer\",\"minimum\":1},\"trigger\":{\"type\":\"string\",\"enum\":[\"USER_REQUESTED\",\"AUTOMATIC\"]},\"evidence\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"memoryId\",\"content\",\"expectedVersion\",\"trigger\",\"evidence\"],\"additionalProperties\":false}"),
            new CompanionToolDefinition(DELETE, "Forget an owned memory only when the learner directly asks to forget it in the current message.",
                    "{\"type\":\"object\",\"properties\":{\"memoryId\":{\"type\":\"string\"},\"expectedVersion\":{\"type\":\"integer\",\"minimum\":1},\"evidence\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}},\"required\":[\"memoryId\",\"expectedVersion\",\"evidence\"],\"additionalProperties\":false}"));

    private final CompanionMemoryService memories;
    private final MemoryRetrievalService retrieval;
    private final ObjectMapper mapper;
    private final byte[] fingerprintKey;

    public CompanionMemoryToolHandler(CompanionMemoryService memories, MemoryRetrievalService retrieval,
            ObjectMapper mapper, @Value("${engolearn.security.jwt.secret}") String signingSecret) {
        this.memories = Objects.requireNonNull(memories);
        this.retrieval = Objects.requireNonNull(retrieval);
        this.mapper = Objects.requireNonNull(mapper);
        this.fingerprintKey = hmac(signingSecret.getBytes(StandardCharsets.UTF_8),
                "engolearn/companion-memory-fingerprint/v1".getBytes(StandardCharsets.UTF_8));
    }

    public List<CompanionToolDefinition> definitions() { return DEFINITIONS; }

    public boolean supports(String name) {
        return SEARCH.equals(name) || CREATE.equals(name) || UPDATE.equals(name) || DELETE.equals(name);
    }

    public String execute(String name, Map<String, Object> arguments, String learnerId,
            CompanionMemoryToolContext context) {
        if (context == null) return loggedResult(name, "error", "context", "ERROR", "Memory tools are unavailable in this context.");
        if (learnerId == null || learnerId.isBlank()) return loggedResult(name, "reject", "identity", "FORBIDDEN", "Memory context is unavailable.");
        try {
            String response = switch (name) {
                case SEARCH -> search(arguments, learnerId, context);
                case CREATE -> create(arguments, learnerId, context);
                case UPDATE -> update(arguments, learnerId, context);
                case DELETE -> delete(arguments, learnerId, context);
                default -> result("ERROR", "The requested tool is unavailable.");
            };
            logDecision(name, "return", "complete", resultCode(response));
            return response;
        } catch (CompanionException failure) {
            if ("TURN_CONFLICT".equals(failure.getCode())) throw failure;
            if ("MEMORY_OPERATION_CONFLICT".equals(failure.getCode())) {
                context.cursor().suppressMutations();
                logDecision(name, "reject", "receipt", failure.getCode());
                return result("CONFLICT", failure.getSafeDetail());
            }
            if (failure.getStatus() == 404) return loggedResult(name, "reject", "canonical_owner", "NO_DATA", "No matching memory was found.");
            if (failure.getStatus() == 409) return loggedResult(name, "reject", "canonical_mutation", "CONFLICT", failure.getSafeDetail());
            if (failure.getStatus() == 400) return loggedResult(name, "reject", "canonical_validation", "INVALID_ARGUMENTS", failure.getSafeDetail());
            logDecision(name, "error", "canonical_mutation", failure.getCode());
            return result("ERROR", "Memory context is temporarily unavailable.");
        } catch (RuntimeException failure) {
            logDecision(name, "error", "handler", "MEMORY_TOOL_ERROR");
            return result("ERROR", "Memory context is temporarily unavailable.");
        }
    }

    private String search(Map<String, Object> args, String learnerId, CompanionMemoryToolContext context) {
        if (args == null || !keys(args, "query", "limit") || !(args.get("query") instanceof String query)
                || query.isBlank() || query.length() > 500) return invalid(SEARCH, "arguments");
        int limit = integer(args.getOrDefault("limit", 5), 1, 10);
        if (limit < 1) return invalid(SEARCH, "arguments");
        MemoryRetrievalResult found = retrieval.retrieve(learnerId, query.trim(), context.deadline());
        List<String> ids = found.references().stream().limit(limit).map(MemoryRetrievalResult.MemoryReference::id).toList();
        var current = memories.findCurrentByIds(learnerId, ids);
        if (current.isEmpty()) return result("NO_DATA", null);
        return json(Map.of("status", "SUCCESS", "items", current.stream().map(item -> Map.of(
                "id", item.id(), "category", item.category().name(), "content", item.content(),
                "version", item.version())).toList()));
    }

    private String create(Map<String, Object> args, String learnerId, CompanionMemoryToolContext context) {
        if (args == null || !keys(args, "category", "content", "source", "trigger", "retrievalMode", "evidence")
                || !(args.get("content") instanceof String content) || !(args.get("evidence") instanceof String evidence)) return invalid(CREATE, "arguments");
        MemoryCategory category = enumValue(MemoryCategory.class, args.get("category"));
        if (category == null || enumValue(MemorySource.class, args.get("source")) == null) return invalid(CREATE, "arguments");
        if (!validExcerpt(context.currentInput(), evidence)) return invalid(CREATE, "evidence");
        if (!validProposedContent(content)) return invalid(CREATE, "content");
        MemoryRetrievalMode mode = args.containsKey("retrievalMode")
                ? enumValue(MemoryRetrievalMode.class, args.get("retrievalMode")) : MemoryRetrievalMode.RELEVANT;
        if (mode == null) return invalid(CREATE, "arguments");
        MemoryTrigger trigger = enumValue(MemoryTrigger.class, args.get("trigger"));
        if (trigger == null) return invalid(CREATE, "trigger");
        boolean automatic = trigger == MemoryTrigger.AUTOMATIC;
        if (automatic && !context.admissionAutomaticMemoryEnabled()) {
            logDecision(CREATE, "block", "automatic_policy", "AUTOMATIC_MEMORY_DISABLED");
            return result("FORBIDDEN", "The current message does not authorize this memory change.");
        }
        MemorySource source = enumValue(MemorySource.class, args.get("source"));
        return mutate(MemoryMutation.Action.CREATED, args, learnerId, context, null, category,
                content, source, mode, null, automatic);
    }

    private String update(Map<String, Object> args, String learnerId, CompanionMemoryToolContext context) {
        if (args == null || !keys(args, "memoryId", "content", "expectedVersion", "trigger", "evidence")
                || !(args.get("memoryId") instanceof String memoryId) || memoryId.isBlank()
                || !(args.get("content") instanceof String content) || !(args.get("evidence") instanceof String evidence)) return invalid(UPDATE, "arguments");
        long version = integer(args.get("expectedVersion"), 1, Integer.MAX_VALUE);
        if (version < 1) return invalid(UPDATE, "version");
        if (!validExcerpt(context.currentInput(), evidence)) return invalid(UPDATE, "evidence");
        if (!validProposedContent(content)) return invalid(UPDATE, "content");
        MemoryTrigger trigger = enumValue(MemoryTrigger.class, args.get("trigger"));
        if (trigger == null) return invalid(UPDATE, "trigger");
        boolean automatic = trigger == MemoryTrigger.AUTOMATIC;
        if (automatic && !context.admissionAutomaticMemoryEnabled()) {
            logDecision(UPDATE, "block", "automatic_policy", "AUTOMATIC_MEMORY_DISABLED");
            return result("FORBIDDEN", "The current message does not authorize this memory change.");
        }
        return mutate(MemoryMutation.Action.UPDATED, args, learnerId, context, memoryId, null,
                content, null, null, version, automatic);
    }

    private String delete(Map<String, Object> args, String learnerId, CompanionMemoryToolContext context) {
        if (args == null || !keys(args, "memoryId", "expectedVersion", "evidence")
                || !(args.get("memoryId") instanceof String memoryId) || memoryId.isBlank()
                || !(args.get("evidence") instanceof String evidence)) return invalid(DELETE, "arguments");
        if (!validExcerpt(context.currentInput(), evidence)) return invalid(DELETE, "evidence");
        long version = integer(args.get("expectedVersion"), 1, Integer.MAX_VALUE);
        if (version < 1 || !directForgetRequest(context.currentInput()))
        {
            logDecision(DELETE, "block", "direct_intent", "DIRECT_FORGET_REQUIRED");
            return result("FORBIDDEN", "A direct forget request in the current message is required.");
        }
        return mutate(MemoryMutation.Action.DELETED, args, learnerId, context, memoryId, null,
                null, null, null, version, false);
    }

    private String mutate(MemoryMutation.Action action, Map<String, Object> args, String learnerId,
            CompanionMemoryToolContext context, String memoryId, MemoryCategory category, String content,
            MemorySource source, MemoryRetrievalMode mode, Long version, boolean automatic) {
        CompanionMemoryOperationCursor cursor = context.cursor();
        if (cursor.mutationsSuppressed()) return result("CONFLICT", "Earlier memory operations in this turn conflicted.");
        String normalized = action + "|" + Objects.toString(memoryId, "") + "|" + Objects.toString(category, "")
                + "|" + normalize(Objects.toString(content, "")) + "|" + Objects.toString(source, "") + "|"
                + Objects.toString(mode, "") + "|" + Objects.toString(version, "") + "|"
                + normalize(Objects.toString(args.get("evidence"), ""))
                + (action == MemoryMutation.Action.DELETED ? "" : "|" + args.get("trigger"));
        String fingerprint = HexFormat.of().formatHex(hmac(fingerprintKey, normalized.getBytes(StandardCharsets.UTF_8)));
        MemoryOperationResult operation = memories.mutate(new MemoryMutation(learnerId, context.conversationId(),
                context.turnId(), cursor.ordinal(), fingerprint, action, memoryId, category,
                content == null ? null : content.trim(), source, mode, version, automatic,
                context.admissionAutomaticMemoryEnabled(), context.forgetRevision()), context.conversationFence());
        cursor.advance();
        logDecision(switch (action) {
            case CREATED -> CREATE;
            case UPDATED -> UPDATE;
            case DELETED -> DELETE;
        }, operation.effectCurrent() ? "commit_or_replay" : "replay_stale",
                "receipt", operation.effectCurrent() ? "SUCCESS" : "NO_DATA");
        if (operation.effectCurrent()) context.observer().onMemoryOperation(new CompanionMemoryOperationEvent(
                operation.operationId(), operation.action().name(), operation.memoryId(), operation.version()));
        return json(Map.of("status", operation.effectCurrent() ? "SUCCESS" : "NO_DATA",
                "operationId", operation.operationId(), "action", operation.action().name(),
                "memoryId", operation.memoryId(), "version", operation.version()));
    }

    private static boolean validProposedContent(String content) {
        return content != null && !content.isBlank() && content.codePointCount(0, content.length()) <= 2000;
    }

    private static boolean validExcerpt(String input, String evidence) {
        return input != null && evidence != null && !evidence.isBlank() && evidence.length() <= 1000
                && input.contains(evidence.trim());
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    // Preserve the existing chat-forget guard until its authorization design is resolved.
    private static boolean directForgetRequest(String input) {
        String text = normalizeIntent(input).strip();
        return !containsAny(text, "dont forget", "do not forget", "never forget", "dung quen",
                "dung xoa", "khong xoa", "khong quen")
                && startsWithAny(text, "please forget ", "can you forget ", "i want you to forget ",
                "forget this ", "forget that ", "forget my memory ",
                "delete my memory ", "remove this from memory ", "hay quen ", "hay xoa ky uc ", "xoa ky uc ");
    }

    private static boolean startsWithAny(String text, String... phrases) {
        for (String phrase : phrases) if (text.startsWith(phrase)) return true;
        return false;
    }

    private static String normalizeIntent(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
        return normalize(decomposed);
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }

    private static boolean keys(Map<String, Object> map, String... allowed) {
        return map.keySet().stream().allMatch(key -> java.util.Arrays.asList(allowed).contains(key));
    }

    private static int integer(Object value, int min, int max) {
        if (!(value instanceof Number number) || number.doubleValue() != number.longValue()) return -1;
        long parsed = number.longValue();
        return parsed < min || parsed > max ? -1 : (int) parsed;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
        if (!(value instanceof String text)) return null;
        try { return Enum.valueOf(type, text); } catch (IllegalArgumentException invalid) { return null; }
    }

    private String invalid(String tool, String stage) {
        logDecision(tool, "reject", stage, "INVALID_ARGUMENTS");
        return result("INVALID_ARGUMENTS", "Tool arguments are invalid.");
    }
    private String loggedResult(String tool, String decision, String stage, String code, String message) {
        logDecision(tool, decision, stage, code);
        return result(code, message);
    }
    private void logDecision(String tool, String decision, String stage, String code) {
        log.info("Companion memory tool: tool={}, decision={}, stage={}, code={}", safeToolName(tool), decision, stage, safeCode(code));
    }
    private String resultCode(String response) {
        for (String code : List.of("SUCCESS", "NO_DATA", "FORBIDDEN", "INVALID_ARGUMENTS", "CONFLICT", "ERROR")) {
            if (response.contains("\"status\":\"" + code + "\"")) return code;
        }
        return "UNKNOWN_RESULT";
    }
    private static String safeToolName(String name) {
        return name != null && List.of(SEARCH, CREATE, UPDATE, DELETE).contains(name) ? name : "UNKNOWN_TOOL";
    }
    private static String safeCode(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "UNKNOWN_CODE";
    }
    private String result(String status, String message) {
        return message == null ? json(Map.of("status", status)) : json(Map.of("status", status, "message", message));
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Could not serialize companion memory result", failure); }
    }
    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("HMAC is unavailable", impossible); }
    }
    private enum MemoryTrigger { USER_REQUESTED, AUTOMATIC }
}
