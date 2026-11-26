package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest;
import fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest;
import fit.iuh.engolearn.companion.model.vo.ScreenContextReferenceVo;
import fit.iuh.engolearn.companion.dto.response.MessageResponse;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.model.CompanionConversation;
import fit.iuh.engolearn.companion.model.CompanionMessage;
import fit.iuh.engolearn.companion.repository.CompanionConversationRepository;
import fit.iuh.engolearn.companion.repository.CompanionMessageRepository;
import fit.iuh.engolearn.companion.service.CompanionHistoryMessage;
import fit.iuh.engolearn.companion.service.CompanionModel;
import fit.iuh.engolearn.companion.service.CompanionModelContinuation;
import fit.iuh.engolearn.companion.service.CompanionModelEvent;
import fit.iuh.engolearn.companion.service.CompanionModelRequest;
import fit.iuh.engolearn.companion.service.CompanionPromptMessage;
import fit.iuh.engolearn.companion.service.CompanionToolCall;
import fit.iuh.engolearn.companion.service.CompanionToolResult;
import fit.iuh.engolearn.companion.service.CompanionUsage;
import fit.iuh.engolearn.companion.service.CompanionTurnResult;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.companion.service.CompanionMemoryOperationCursor;
import fit.iuh.engolearn.companion.service.CompanionMemoryToolContext;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.MemoryRetrievalResult;
import fit.iuh.engolearn.companion.service.MemoryRetrievalService;
import fit.iuh.engolearn.companion.service.MemorySettingsSnapshot;
import fit.iuh.engolearn.quota.contract.QuotaAvailability;
import fit.iuh.engolearn.quota.contract.QuotaUsageRecording;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
public class CompanionTurnServiceImpl implements CompanionTurnService {
    private static final Logger log = LoggerFactory.getLogger(CompanionTurnServiceImpl.class);
    private final CurrentUserProvider users;
    private final CompanionConversationRepository conversations;
    private final CompanionMessageRepository messages;
    private final CompanionModel model;
    private final CompanionContextBuilder contextBuilder;
    private final CompanionProperties properties;
    private final MongoMessageTransactions transactions;
    private final QuotaAvailability quotaAvailability;
    private final QuotaUsageRecording quotaUsage;
    private final CompanionToolRegistry toolRegistry;
    private final CompanionMemoryService memories;
    private final MemoryRetrievalService memoryRetrieval;
    private final ScreenContextResolverRegistry screenContexts;
    @Qualifier("companionToolExecutor")
    private final ExecutorService companionToolExecutor;
    private final ConcurrentHashMap<String, Object> activeLearners = new ConcurrentHashMap<>();

    @Override public PreparedTurn prepareTurn(String conversationId, CompanionTurnRequest request) {
        if (request == null || request.turnId() == null || request.content() == null || request.content().isBlank()) {
            throw new CompanionException(400, "INVALID_TURN", "Turn ID and non-empty content are required", false);
        }
        if (request.screenContext() != null) screenContexts.validate(request.screenContext());
        String learnerId = users.requireCurrentUserId();
        Object guardToken = new Object();
        if (activeLearners.putIfAbsent(learnerId, guardToken) != null) throw conflict("Another companion turn is already active");
        try {
            String turnId = request.turnId().toString();
            Instant now = Instant.now();
            Duration leaseDuration = properties.getTurnTimeout().plusSeconds(30);
            if (leaseDuration.compareTo(properties.getInferenceTimeout().multipliedBy(2).plusSeconds(30)) < 0)
                leaseDuration = properties.getInferenceTimeout().multipliedBy(2).plusSeconds(30);
            Instant leaseUntil = now.plus(leaseDuration);
            return transactions.required(() -> {
                CompanionConversation conversation = conversations.findOwned(conversationId, learnerId)
                        .orElseThrow(CompanionTurnServiceImpl::notFound);
                if (request.retry()) {
                    if (!turnId.equals(conversation.getPendingTurnId())) throw conflict("This turn cannot be retried");
                    CompanionMessage userMessage = messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER)
                            .orElseThrow(() -> conflict("This turn cannot be retried"));
                    if (!userMessage.getContent().equals(request.content())) throw conflict("Retry content must match the original turn");
                    ScreenContextReferenceRequest originalContext = fromStoredReference(userMessage.getScreenContext());
                    if (request.screenContext() != null && !request.screenContext().equals(originalContext))
                        throw conflict("Retry context must match the original turn");
                    if (messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT).isPresent()) {
                        throw conflict("This turn is already complete");
                    }
                    requireQuotaAvailable(learnerId);
                    CompanionConversation claimed = conversations.claimRetry(conversationId, learnerId, turnId, now, leaseUntil,
                                    properties.getMaxMessages())
                            .orElseThrow(() -> conflict("Another companion turn is already active"));
                    return new PreparedTurn(conversationId, learnerId, turnId, request.content(),
                            claimed.getActiveTurnGeneration(), userMessage.getSequence(), guardToken,
                            Boolean.TRUE.equals(userMessage.getMemoryAutomaticEnabledSnapshot()),
                            userMessage.getMemoryForgetRevisionSnapshot() == null ? 0L
                                    : userMessage.getMemoryForgetRevisionSnapshot(), originalContext);
                }
                if (messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER).isPresent()
                        || messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT).isPresent()) {
                    throw conflict("Turn ID has already been used");
                }
                int maxMessages = properties.getMaxMessages();
                if (conversation.getMessageCount() + 2 > maxMessages) {
                    throw new CompanionException(409, "MESSAGE_LIMIT_REACHED", "Conversation message limit reached", false);
                }
                requireQuotaAvailable(learnerId);
                MemorySettingsSnapshot memorySettings = memories.settingsSnapshot(learnerId);
                CompanionConversation admitted = conversations.admitNew(conversationId, learnerId, turnId,
                        conversation.getNextSequence(), now, leaseUntil, maxMessages)
                        .orElseThrow(() -> conflict("Another companion turn is active or the message limit was reached"));
                long userSequence = admitted.getPendingUserSequence();
                messages.insert(CompanionMessage.builder().conversationId(conversationId).turnId(turnId)
                        .sequence(userSequence).role(CompanionMessage.Role.USER).content(request.content())
                        .screenContext(toStoredReference(request.screenContext()))
                        .memoryAutomaticEnabledSnapshot(memorySettings.automaticMemoryEnabled())
                        .memoryForgetRevisionSnapshot(memorySettings.forgetRevision()).createdAt(now).build());
                return new PreparedTurn(conversationId, learnerId, turnId, request.content(),
                        admitted.getActiveTurnGeneration(), userSequence, guardToken,
                        memorySettings.automaticMemoryEnabled(), memorySettings.forgetRevision(), request.screenContext());
            });
        } catch (RuntimeException ex) {
            activeLearners.remove(learnerId, guardToken);
            throw ex;
        }
    }

    @Override public CompanionTurnResult streamPreparedTurn(PreparedTurn prepared, TurnObserver observer) {
        Instant turnDeadline = Instant.now().plus(properties.getTurnTimeout());
        StringBuilder output = new StringBuilder();
        Sinks.Empty<Void> cancellation = Sinks.empty();
        try {
            observer.setCancelAction(() -> cancellation.tryEmitEmpty());
            List<CompanionMessage> prior = new java.util.ArrayList<>(messages.newest(prepared.conversationId(), prepared.userSequence(),
                    Math.max(0, properties.getHistoryMessageLimit())));
            Collections.reverse(prior);
            List<CompanionHistoryMessage> history = prior.stream()
                    .map(message -> new CompanionHistoryMessage(message.getRole().name(), message.getContent())).toList();
            String screenContext = prepared.screenContext() == null ? null : resolveScreenContext(prepared, turnDeadline, observer);
            MemoryRetrievalResult memoryContext = retrieveMemoryContext(prepared, turnDeadline, observer);
            List<CompanionPromptMessage> messagesForModel = contextBuilder.build(prepared.learnerId(), history,
                    prepared.content(), memoryContext == null ? "" : memoryContext.promptContext(),
                    prepared.admissionAutomaticMemoryEnabled());
            if (screenContext != null) messagesForModel = contextBuilder.withScreenContext(messagesForModel, screenContext);
            CompanionModelContinuation continuation = null;
            List<CompanionToolResult> toolResults = List.of();
            Set<String> seenCallIds = new HashSet<>();
            long totalTokens = 0;
            int toolCallsExecuted = 0;
            int toolRounds = 0;
            boolean toolsDisabled = false;
            boolean retryUsed = false;
            CompanionMemoryOperationCursor memoryCursor = new CompanionMemoryOperationCursor();
            RuntimeException lastFailure = null;
            while (true) {
                if (observer.isCancelled()) throw new TurnCancelledException();
                java.time.Duration remaining = java.time.Duration.between(Instant.now(), turnDeadline);
                if (remaining.isZero() || remaining.isNegative()) {
                    throw new CompanionException(504, "COMPANION_TURN_TIMEOUT", "The companion response timed out", true);
                }
                java.time.Duration attemptLimit = remaining.compareTo(properties.getInferenceTimeout()) < 0
                        ? remaining : properties.getInferenceTimeout();
                boolean toolsEnabled = !toolsDisabled && toolRounds < properties.getMaxToolRounds()
                        && toolCallsExecuted < properties.getMaxToolCalls();
                if (!toolsEnabled) toolsDisabled = true;
                if (toolsEnabled) toolRounds++;
                TurnRound round = new TurnRound();
                AtomicBoolean attemptDelivered = new AtomicBoolean(false);
                while (true) {
                    TurnRound currentRound = new TurnRound();
                    try {
                        model.stream(new CompanionModelRequest(messagesForModel, toolRegistry.definitions(toolsEnabled),
                                continuation, toolResults)).takeUntilOther(cancellation.asMono()).doOnNext(event -> {
                            if (observer.isCancelled()) throw new TurnCancelledException();
                            if (currentRound.completed) throw invalidProviderResponse();
                            if (event instanceof CompanionModelEvent.TextDelta text) {
                                if (text.text().isEmpty() || currentRound.toolCalls != null) return;
                                output.append(text.text());
                                attemptDelivered.set(true);
                                observer.onDelta(text.text());
                            } else if (event instanceof CompanionModelEvent.ToolCalls calls) {
                                if (!toolsEnabled || currentRound.toolCalls != null || output.length() > 0) throw invalidToolOrder();
                                currentRound.toolCalls = calls;
                            } else if (event instanceof CompanionModelEvent.RoundCompleted completed) {
                                currentRound.usage = completed.usage();
                                currentRound.completed = true;
                            }
                        }).then().block(attemptLimit);
                        if (observer.isCancelled()) throw new TurnCancelledException();
                        if (!currentRound.completed || currentRound.usage == null || currentRound.usage.totalTokenCount() <= 0) throw missingUsage();
                        totalTokens = addUsage(totalTokens, currentRound.usage);
                        round = currentRound;
                        log.info("Companion provider tool call returned: present={}, count={}",
                                round.toolCalls != null, round.toolCalls == null ? 0 : round.toolCalls.calls().size());
                        break;
                    } catch (RuntimeException failure) {
                        if (failure instanceof TurnCancelledException) throw failure;
                        if (failure instanceof CompanionException companion
                                && ("COMPANION_USAGE_UNAVAILABLE".equals(companion.getCode())
                                || "COMPANION_TOOL_ORDER_INVALID".equals(companion.getCode())
                                || "COMPANION_PROVIDER_RESPONSE_INVALID".equals(companion.getCode()))) throw companion;
                        log.debug("Companion inference attempt failed; causeType={}", rootCauseType(failure));
                        RuntimeException safe = safeFailure(failure);
                        if (attemptDelivered.get() || retryUsed || !isRetryable(safe)) throw safe;
                        retryUsed = true;
                        lastFailure = failure;
                    }
                }

                if (round.toolCalls == null) {
                    if (output.isEmpty()) throw safeFailure(lastFailure == null ? providerUnavailable() : lastFailure);
                    break;
                }

                continuation = round.toolCalls.continuation();
                List<CompanionToolResult> results = new ArrayList<>();
                for (CompanionToolCall call : round.toolCalls.calls()) {
                    if (!seenCallIds.add(call.callId())) throw invalidProviderResponse();
                    String json;
                    if (toolCallsExecuted >= properties.getMaxToolCalls()) {
                        log.info("Companion tool call: tool={}, decision=skip, stage=tool_budget, code=CALL_LIMIT_REACHED",
                                safeToolName(call.name()));
                        json = toolError("The tool call budget is exhausted.");
                        toolsDisabled = true;
                    } else {
                        toolCallsExecuted++;
                        log.info("Companion tool call: tool={}, decision=invoke, stage=provider_response, code=TOOL_CALL_RECEIVED",
                                safeToolName(call.name()));
                        json = executeTool(call, prepared, turnDeadline, observer, memoryCursor);
                    }
                    results.add(new CompanionToolResult(call.callId(), call.name(), json));
                }
                toolResults = List.copyOf(results);
                if (toolRounds >= properties.getMaxToolRounds() || toolCallsExecuted >= properties.getMaxToolCalls()) {
                    toolsDisabled = true;
                }
            }
            if (observer.isCancelled()) throw new TurnCancelledException();
            if (output.isEmpty() || totalTokens <= 0) throw missingUsage();
            return persistCompleted(prepared, output.toString(), totalTokens);
        } catch (RuntimeException failure) {
            release(prepared);
            if (failure instanceof TurnCancelledException) {
                throw new CompanionException(504, "TURN_CANCELLED", "Turn was cancelled", true);
            }
            throw safeFailure(failure);
        } finally {
            observer.setCancelAction(null);
            activeLearners.remove(prepared.learnerId(), prepared.guardToken());
        }
    }

    @Override public void abandonPreparedTurn(PreparedTurn prepared) {
        try { release(prepared); }
        finally { activeLearners.remove(prepared.learnerId(), prepared.guardToken()); }
    }

    private CompanionTurnResult persistCompleted(PreparedTurn prepared, String content, long totalTokens) {
        Instant now = Instant.now();
        return transactions.required(() -> {
            boolean committed = conversations.finish(prepared.conversationId(), prepared.learnerId(), prepared.turnId(),
                    prepared.generation(), prepared.userSequence() + 1, now);
            if (!committed) throw conflict("Turn lease expired before completion");
            CompanionMessage assistant = messages.insert(CompanionMessage.builder()
                    .conversationId(prepared.conversationId()).turnId(prepared.turnId())
                    .sequence(prepared.userSequence() + 1).role(CompanionMessage.Role.ASSISTANT)
                    .content(content).createdAt(now).build());
            fit.iuh.engolearn.quota.contract.dto.UsageResultDto usage;
            try {
                usage = quotaUsage.recordUsage(new UsageCommandDto(prepared.learnerId(), "AI_COMPANION_TOKEN_USAGE", totalTokens,
                        "AI_COMPANION", "turn:" + prepared.conversationId() + ":" + prepared.turnId(),
                        prepared.conversationId() + "/" + prepared.turnId(), null));
            } catch (RuntimeException failure) {
                throw quotaUnavailable();
            }
            if (usage == null || !usage.accepted() || usage.amount() != totalTokens) throw quotaUnavailable();
            CompanionConversation conversation = conversations.findOwned(prepared.conversationId(), prepared.learnerId())
                    .orElseThrow(CompanionTurnServiceImpl::notFound);
            int threshold = (int) Math.ceil(properties.getMaxMessages() * properties.getWarningPercent() / 100.0);
            return new CompanionTurnResult(prepared.turnId(), new MessageResponse(assistant.getId(), assistant.getSequence(),
                    assistant.getRole().name(), assistant.getContent(), assistant.getCreatedAt()), conversation.getMessageCount(),
                    properties.getMaxMessages(), conversation.getMessageCount() >= threshold);
        });
    }

    private void requireQuotaAvailable(String learnerId) {
        AvailabilityResultDto availability;
        try {
            availability = quotaAvailability.checkAvailability(learnerId, "AI_COMPANION_TOKEN_USAGE");
        } catch (RuntimeException failure) {
            throw quotaUnavailable();
        }
        if (availability == null || !"AI_COMPANION_TOKEN_USAGE".equals(availability.quotaType())
                || !"TOKEN".equalsIgnoreCase(availability.unit()) || !"DAILY".equalsIgnoreCase(availability.period())
                || availability.limit() < 0 || availability.used() < 0 || availability.remaining() < 0
                || availability.remaining() > availability.limit() || availability.status() == null) {
            throw quotaUnavailable();
        }
        if (availability.remaining() == 0 || "EXHAUSTED".equals(availability.status())
                || "OVER_LIMIT".equals(availability.status())) {
            throw new CompanionException(429, "COMPANION_QUOTA_EXHAUSTED",
                    "The companion token allowance is exhausted. Try again after it resets.", false);
        }
        if (!"AVAILABLE".equals(availability.status())) throw quotaUnavailable();
    }

    private static CompanionException quotaUnavailable() {
        return new CompanionException(503, "COMPANION_QUOTA_UNAVAILABLE",
                "The companion service is temporarily unavailable. Retry the request.", true);
    }

    private ScreenContextReferenceVo toStoredReference(ScreenContextReferenceRequest ref) {
        return ref == null ? null : ScreenContextReferenceVo.builder().type(ref.type()).topicId(ref.topicId())
                .attemptId(ref.attemptId()).stage(ref.stage()).itemIndex(ref.itemIndex()).build();
    }

    private ScreenContextReferenceRequest fromStoredReference(ScreenContextReferenceVo ref) {
        return ref == null ? null : new ScreenContextReferenceRequest(ref.getType(), ref.getTopicId(),
                ref.getAttemptId(), ref.getStage(), ref.getItemIndex());
    }

    private String resolveScreenContext(PreparedTurn prepared, Instant deadline, TurnObserver observer) {
        if (observer.isCancelled()) throw new TurnCancelledException();
        Instant timeout = Instant.now().plus(properties.getToolTimeout());
        Instant resolveDeadline = timeout.isBefore(deadline) ? timeout : deadline;
        Future<String> future;
        try {
            future = companionToolExecutor.submit(() -> screenContexts.resolve(prepared.learnerId(), prepared.screenContext()));
        } catch (RejectedExecutionException busy) {
            throw screenContextUnavailable();
        }
        try {
            while (true) {
                if (observer.isCancelled()) throw new TurnCancelledException();
                long wait = Duration.between(Instant.now(), resolveDeadline).toMillis();
                if (wait <= 0) throw screenContextUnavailable();
                try { return future.get(Math.min(100, wait), TimeUnit.MILLISECONDS); }
                catch (TimeoutException waiting) { /* Check cancellation and deadline again. */ }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw screenContextUnavailable();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof CompanionException safe) throw safe;
            throw screenContextUnavailable();
        } finally {
            future.cancel(true);
        }
    }

    private CompanionException screenContextUnavailable() {
        return new CompanionException(503, "SCREEN_CONTEXT_TEMPORARILY_UNAVAILABLE",
                "The selected context could not be loaded. Retry this turn.", true);
    }

    private MemoryRetrievalResult retrieveMemoryContext(PreparedTurn prepared, Instant turnDeadline, TurnObserver observer) {
        if (observer.isCancelled()) throw new TurnCancelledException();
        Duration remaining = Duration.between(Instant.now(), turnDeadline);
        if (remaining.isNegative() || remaining.isZero()) return null;
        Duration timeout = remaining.compareTo(properties.getToolTimeout()) < 0 ? remaining : properties.getToolTimeout();
        Future<MemoryRetrievalResult> future;
        try {
            future = companionToolExecutor.submit(() -> memoryRetrieval.retrieve(prepared.learnerId(),
                    prepared.content(), turnDeadline));
        } catch (RejectedExecutionException saturated) {
            return null;
        }
        Instant retrievalDeadline = Instant.now().plus(timeout);
        while (true) {
            if (observer.isCancelled()) {
                future.cancel(true);
                throw new TurnCancelledException();
            }
            Duration wait = Duration.between(Instant.now(), retrievalDeadline);
            if (wait.isNegative() || wait.isZero()) {
                future.cancel(true);
                return null;
            }
            try {
                return future.get(Math.min(100, Math.max(1, wait.toMillis())), TimeUnit.MILLISECONDS);
            } catch (TimeoutException waiting) {
                // Keep cancellation responsive while retrieval uses the same bounded tool executor.
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException failure) {
                log.debug("Companion memory retrieval unavailable; causeType={}", rootCauseType((failure.getCause() instanceof RuntimeException runtime)
                        ? runtime : new RuntimeException(failure.getCause())));
                return null;
            }
        }
    }

    private String executeTool(CompanionToolCall call, PreparedTurn prepared, Instant turnDeadline,
            TurnObserver observer, CompanionMemoryOperationCursor memoryCursor) {
        Duration remaining = Duration.between(Instant.now(), turnDeadline);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new CompanionException(504, "COMPANION_TURN_TIMEOUT", "The companion response timed out", true);
        }
        Duration timeout = remaining.compareTo(properties.getToolTimeout()) < 0 ? remaining : properties.getToolTimeout();
        CompanionMemoryToolContext memoryContext = new CompanionMemoryToolContext(prepared.conversationId(),
                prepared.turnId(), prepared.content(), prepared.admissionAutomaticMemoryEnabled(),
                prepared.forgetRevision(), turnDeadline, () -> fenceMemoryWrite(prepared, turnDeadline),
                memoryCursor, observer);
        Future<String> future;
        try {
            future = companionToolExecutor.submit(() -> toolRegistry.execute(call.name(), call.arguments(),
                    prepared.learnerId(), () -> notifyAssessmentLookup(observer), memoryContext));
        } catch (RejectedExecutionException saturated) {
            log.info("Companion tool call: tool={}, decision=error, stage=tool_executor, code=EXECUTOR_SATURATED",
                    safeToolName(call.name()));
            return toolError("Assessment context is temporarily unavailable.");
        }
        Instant toolDeadline = Instant.now().plus(timeout);
        try {
            while (true) {
                if (observer.isCancelled()) {
                    future.cancel(true);
                    throw new TurnCancelledException();
                }
                remaining = Duration.between(Instant.now(), toolDeadline);
                if (remaining.isNegative() || remaining.isZero()) {
                    future.cancel(true);
                    log.info("Companion tool call: tool={}, decision=error, stage=tool_timeout, code=TOOL_TIMEOUT",
                            safeToolName(call.name()));
                    if (!Instant.now().isBefore(turnDeadline)) {
                        throw new CompanionException(504, "COMPANION_TURN_TIMEOUT", "The companion response timed out", true);
                    }
                    return toolError("Assessment context took too long to load.");
                }
                try {
                    String response = future.get(Math.min(100, Math.max(1, remaining.toMillis())), TimeUnit.MILLISECONDS);
                    log.info("Companion tool call: tool={}, decision=result, stage=tool_handler, code={}",
                            safeToolName(call.name()), toolResultStatus(response));
                    return response;
                } catch (TimeoutException waiting) {
                    // Polling keeps cancellation responsive while the bounded read runs separately.
                }
            }
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new TurnCancelledException();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof ObserverCallbackException callbackFailure) {
                if (observer.isCancelled()) throw new TurnCancelledException();
                throw callbackFailure;
            }
            if (failure.getCause() instanceof CompanionException turnFailure
                    && "TURN_CONFLICT".equals(turnFailure.getCode())) throw turnFailure;
            log.info("Companion tool call: tool={}, decision=error, stage=tool_execution, code=TOOL_EXECUTION_FAILED",
                    safeToolName(call.name()));
            return toolError("Assessment context is temporarily unavailable.");
        }
    }

    private static String safeToolName(String name) {
        return switch (name == null ? "" : name) {
            case "getLatestAssessmentResult", "searchMemory", "createMemory", "updateMemory", "deleteMemory" -> name;
            default -> "UNKNOWN_TOOL";
        };
    }

    private static String toolResultStatus(String response) {
        if (response == null) return "EMPTY_RESULT";
        for (String status : List.of("SUCCESS", "NO_DATA", "FORBIDDEN", "INVALID_ARGUMENTS", "CONFLICT", "ERROR")) {
            if (response.contains("\"status\":\"" + status + "\"")) return status;
        }
        return "UNKNOWN_RESULT";
    }

    private void fenceMemoryWrite(PreparedTurn prepared, Instant turnDeadline) {
        Instant now = Instant.now();
        if (!now.isBefore(turnDeadline)) {
            throw new CompanionException(504, "COMPANION_TURN_TIMEOUT", "The companion response timed out", true);
        }
        if (!conversations.fenceActiveTurn(prepared.conversationId(), prepared.learnerId(), prepared.turnId(),
                prepared.generation(), now, turnDeadline.plusSeconds(30))) {
            throw conflict("Turn lease expired before the memory change could commit");
        }
    }

    private static void notifyAssessmentLookup(TurnObserver observer) {
        try {
            observer.onStatus(TurnStatus.ASSESSMENT_LOOKUP);
        } catch (RuntimeException failure) {
            throw new ObserverCallbackException(failure);
        }
    }

    private static String toolError(String message) {
        return "{\"status\":\"ERROR\",\"message\":\"" + message + "\"}";
    }

    private static long addUsage(long accumulated, CompanionUsage usage) {
        try {
            long total = Math.addExact(accumulated, usage.totalTokenCount());
            if (usage.totalTokenCount() <= 0 || total > 9_007_199_254_740_991L) throw missingUsage();
            return total;
        } catch (ArithmeticException invalid) {
            throw missingUsage();
        }
    }

    private static CompanionException missingUsage() {
        return new CompanionException(503, "COMPANION_USAGE_UNAVAILABLE",
                "The companion service is temporarily unavailable. Retry this turn.", true);
    }

    private static CompanionException invalidProviderResponse() {
        return new CompanionException(502, "COMPANION_PROVIDER_RESPONSE_INVALID",
                "The companion service returned an invalid response. Retry this turn.", false);
    }

    private static CompanionException invalidToolOrder() {
        return new CompanionException(502, "COMPANION_TOOL_ORDER_INVALID",
                "The companion service returned an invalid response. Retry this turn.", false);
    }

    private void release(PreparedTurn prepared) {
        try {
            transactions.required(() -> conversations.release(prepared.conversationId(), prepared.learnerId(),
                    prepared.turnId(), prepared.generation(), Instant.now()));
        } catch (RuntimeException ignored) {
            // Expiry and fencing still prevent this worker from committing.
            log.warn("Companion turn reservation cleanup failed; lease expiry will release it");
        }
    }

    private RuntimeException safeFailure(RuntimeException failure) {
        if (failure instanceof CompanionException exception) return exception;
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException || current.getClass().getSimpleName().contains("Timeout")) {
                return new CompanionException(504, "COMPANION_TURN_TIMEOUT", "The companion response timed out", true);
            }
            current = current.getCause();
        }
        return providerUnavailable();
    }

    private static String rootCauseType(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }

    private static CompanionException providerUnavailable() {
        return new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE", "The companion service is temporarily unavailable", true);
    }
    private static boolean isRetryable(RuntimeException exception) {
        return exception instanceof CompanionException companionException
                && (companionException.getStatus() == 503 || companionException.getStatus() == 504)
                && companionException.isRetryable();
    }
    private static CompanionException conflict(String detail) { return new CompanionException(409, "TURN_CONFLICT", detail, false); }
    private static CompanionException notFound() { return new CompanionException(404, "CONVERSATION_NOT_FOUND", "Conversation was not found", false); }
    private static final class TurnRound {
        private CompanionModelEvent.ToolCalls toolCalls;
        private CompanionUsage usage;
        private boolean completed;
    }
    private static final class TurnCancelledException extends RuntimeException {}
    private static final class ObserverCallbackException extends RuntimeException {
        private ObserverCallbackException(RuntimeException cause) { super(cause); }
    }
}
