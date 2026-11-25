package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest;
import fit.iuh.engolearn.companion.dto.response.MessageResponse;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.model.CompanionConversation;
import fit.iuh.engolearn.companion.model.CompanionMessage;
import fit.iuh.engolearn.companion.repository.CompanionConversationRepository;
import fit.iuh.engolearn.companion.repository.CompanionMessageRepository;
import fit.iuh.engolearn.companion.service.CompanionHistoryMessage;
import fit.iuh.engolearn.companion.service.CompanionModel;
import fit.iuh.engolearn.companion.service.CompanionTurnResult;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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
    private final ConcurrentHashMap<String, Object> activeLearners = new ConcurrentHashMap<>();

    @Override public PreparedTurn prepareTurn(String conversationId, CompanionTurnRequest request) {
        if (request == null || request.turnId() == null || request.content() == null || request.content().isBlank()) {
            throw new CompanionException(400, "INVALID_TURN", "Turn ID and non-empty content are required", false);
        }
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
                    if (messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT).isPresent()) {
                        throw conflict("This turn is already complete");
                    }
                    CompanionConversation claimed = conversations.claimRetry(conversationId, learnerId, turnId, now, leaseUntil,
                                    properties.getMaxMessages())
                            .orElseThrow(() -> conflict("Another companion turn is already active"));
                    return new PreparedTurn(conversationId, learnerId, turnId, request.content(),
                            claimed.getActiveTurnGeneration(), userMessage.getSequence(), guardToken);
                }
                if (messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER).isPresent()
                        || messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT).isPresent()) {
                    throw conflict("Turn ID has already been used");
                }
                int maxMessages = properties.getMaxMessages();
                if (conversation.getMessageCount() + 2 > maxMessages) {
                    throw new CompanionException(409, "MESSAGE_LIMIT_REACHED", "Conversation message limit reached", false);
                }
                CompanionConversation admitted = conversations.admitNew(conversationId, learnerId, turnId,
                        conversation.getNextSequence(), now, leaseUntil, maxMessages)
                        .orElseThrow(() -> conflict("Another companion turn is active or the message limit was reached"));
                long userSequence = admitted.getPendingUserSequence();
                messages.insert(CompanionMessage.builder().conversationId(conversationId).turnId(turnId)
                        .sequence(userSequence).role(CompanionMessage.Role.USER).content(request.content()).createdAt(now).build());
                return new PreparedTurn(conversationId, learnerId, turnId, request.content(),
                        admitted.getActiveTurnGeneration(), userSequence, guardToken);
            });
        } catch (RuntimeException ex) {
            activeLearners.remove(learnerId, guardToken);
            throw ex;
        }
    }

    @Override public CompanionTurnResult streamPreparedTurn(PreparedTurn prepared, TurnObserver observer) {
        Instant turnDeadline = Instant.now().plus(properties.getTurnTimeout());
        AtomicReference<StringBuilder> output = new AtomicReference<>(new StringBuilder());
        Sinks.Empty<Void> cancellation = Sinks.empty();
        try {
            observer.setCancelAction(() -> cancellation.tryEmitEmpty());
            List<CompanionMessage> prior = new java.util.ArrayList<>(messages.newest(prepared.conversationId(), prepared.userSequence(),
                    Math.max(0, properties.getHistoryMessageLimit())));
            Collections.reverse(prior);
            List<CompanionHistoryMessage> history = prior.stream()
                    .map(message -> new CompanionHistoryMessage(message.getRole().name(), message.getContent())).toList();
            var prompt = contextBuilder.build(prepared.learnerId(), history, prepared.content());
            RuntimeException lastFailure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                if (observer.isCancelled()) throw new TurnCancelledException();
                java.time.Duration remaining = java.time.Duration.between(Instant.now(), turnDeadline);
                if (remaining.isZero() || remaining.isNegative()) {
                    throw new CompanionException(504, "COMPANION_TURN_TIMEOUT", "The companion response timed out", true);
                }
                java.time.Duration attemptLimit = remaining.compareTo(properties.getInferenceTimeout()) < 0
                        ? remaining : properties.getInferenceTimeout();
                StringBuilder attemptOutput = new StringBuilder();
                AtomicBoolean attemptDelivered = new AtomicBoolean(false);
                try {
                    Flux<String> stream = model.stream(prompt)
                            .takeUntilOther(cancellation.asMono())
                            .doOnNext(delta -> {
                                if (observer.isCancelled()) throw new TurnCancelledException();
                                if (delta == null || delta.isEmpty()) return;
                                attemptOutput.append(delta);
                                attemptDelivered.set(true);
                                observer.onDelta(delta);
                            });
                    stream.then().block(attemptLimit);
                    if (observer.isCancelled()) throw new TurnCancelledException();
                    if (attemptOutput.isEmpty()) throw providerUnavailable();
                    output.set(attemptOutput);
                    break;
                } catch (RuntimeException failure) {
                    if (failure instanceof TurnCancelledException) throw failure;
                    log.debug("Companion inference attempt failed; causeType={}", rootCauseType(failure));
                    RuntimeException safe = safeFailure(failure);
                    if (attemptDelivered.get() || attempt == 1 || !isRetryable(safe)) throw safe;
                    lastFailure = failure;
                }
            }
            if (output.get().isEmpty()) throw safeFailure(lastFailure == null ? providerUnavailable() : lastFailure);
            if (observer.isCancelled()) throw new TurnCancelledException();
            return persistCompleted(prepared, output.get().toString());
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

    private CompanionTurnResult persistCompleted(PreparedTurn prepared, String content) {
        Instant now = Instant.now();
        return transactions.required(() -> {
            boolean committed = conversations.finish(prepared.conversationId(), prepared.learnerId(), prepared.turnId(),
                    prepared.generation(), prepared.userSequence() + 1, now);
            if (!committed) throw conflict("Turn lease expired before completion");
            CompanionMessage assistant = messages.insert(CompanionMessage.builder()
                    .conversationId(prepared.conversationId()).turnId(prepared.turnId())
                    .sequence(prepared.userSequence() + 1).role(CompanionMessage.Role.ASSISTANT)
                    .content(content).createdAt(now).build());
            CompanionConversation conversation = conversations.findOwned(prepared.conversationId(), prepared.learnerId())
                    .orElseThrow(CompanionTurnServiceImpl::notFound);
            int threshold = (int) Math.ceil(properties.getMaxMessages() * properties.getWarningPercent() / 100.0);
            return new CompanionTurnResult(prepared.turnId(), new MessageResponse(assistant.getId(), assistant.getSequence(),
                    assistant.getRole().name(), assistant.getContent(), assistant.getCreatedAt()), conversation.getMessageCount(),
                    properties.getMaxMessages(), conversation.getMessageCount() >= threshold);
        });
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
    private static final class TurnCancelledException extends RuntimeException {}
}
