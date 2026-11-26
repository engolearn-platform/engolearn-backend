package fit.iuh.engolearn.companion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import fit.iuh.engolearn.companion.model.CompanionConversation;
import fit.iuh.engolearn.companion.model.CompanionMessage;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.service.CompanionModel;
import fit.iuh.engolearn.companion.service.CompanionModelEvent;
import fit.iuh.engolearn.companion.service.CompanionPromptMessage;
import fit.iuh.engolearn.companion.service.CompanionUsage;
import fit.iuh.engolearn.companion.service.CompanionTurnService;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.MemoryRetrievalService;
import fit.iuh.engolearn.companion.service.MemoryRetrievalResult;
import fit.iuh.engolearn.companion.service.MemorySettingsSnapshot;
import fit.iuh.engolearn.companion.service.impl.CompanionContextBuilder;
import fit.iuh.engolearn.companion.service.impl.CompanionToolRegistry;
import fit.iuh.engolearn.companion.service.impl.CompanionTurnServiceImpl;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.quota.contract.QuotaAvailability;
import fit.iuh.engolearn.quota.contract.QuotaUsageRecording;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.contract.dto.UsageResultDto;
import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

@EnabledIfDockerAvailable
class CompanionTurnReservationIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired CompanionConversationRepository conversations;
    @Autowired CompanionMessageRepository messages;
    @Autowired MongoMessageTransactions transactions;
    @Autowired CompanionContextBuilder contextBuilder;
    @Autowired CompanionProperties properties;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("companionToolExecutor") ExecutorService toolExecutor;

    @Test void simultaneousAdmissionAtCapHasOneWinnerAndNoOverflow() throws Exception {
        Instant now = Instant.now();
        CompanionConversation conversation = transactions.required(() -> conversations.insert(CompanionConversation.builder()
                .learnerId("learner").messageCount(98).nextSequence(99).activeTurnGeneration(0)
                .createdAt(now).updatedAt(now).build()));
        List<String> ids = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var results = race(() -> admit(conversation.getId(), ids.get(0), 99, now),
                () -> admit(conversation.getId(), ids.get(1), 99, now));
        assertThat(results.stream().filter(Boolean.TRUE::equals)).hasSize(1);
        CompanionConversation saved = conversations.findOwned(conversation.getId(), "learner").orElseThrow();
        assertThat(saved.getMessageCount()).isEqualTo(99);
        assertThat(mongoTemplate.findAll(CompanionMessage.class)).hasSize(1);
    }

    @Test void expiredWorkerIsFencedAndReleasedReservationAllowsANewQuestion() {
        Instant now = Instant.now();
        CompanionConversation conversation = transactions.required(() -> conversations.insert(CompanionConversation.builder()
                .learnerId("learner").messageCount(0).nextSequence(1).activeTurnGeneration(0)
                .createdAt(now).updatedAt(now).build()));
        String failedTurn = UUID.randomUUID().toString();
        var first = transactions.required(() -> reserve(conversation.getId(), failedTurn, 1, now,
                now.minusMillis(1)));
        assertThat(first).isNotNull();
        String nextTurn = UUID.randomUUID().toString();
        var second = transactions.required(() -> conversations.claimRetry(conversation.getId(), "learner", failedTurn,
                now, now.plusSeconds(120), 100).orElseThrow());
        assertThat(second.getActiveTurnGeneration()).isGreaterThan(first.getActiveTurnGeneration());
        assertThat(transactions.required(() -> conversations.finish(conversation.getId(), "learner", failedTurn,
                first.getActiveTurnGeneration(), 2, now))).isFalse();
        transactions.required(() -> conversations.release(conversation.getId(), "learner", failedTurn,
                second.getActiveTurnGeneration(), now));
        CompanionConversation afterRelease = conversations.findOwned(conversation.getId(), "learner").orElseThrow();
        var admittedNext = transactions.required(() -> conversations.admitNew(conversation.getId(), "learner", nextTurn,
                afterRelease.getNextSequence(), now, now.plusSeconds(120), 100).orElseThrow());
        assertThat(admittedNext.getPendingTurnId()).isEqualTo(nextTurn);
        assertThat(admittedNext.getMessageCount()).isEqualTo(2);
    }

    @Test void memoryFenceRequiresTheCurrentUnexpiredTurnGeneration() {
        Instant admittedAt = Instant.now();
        CompanionConversation conversation = transactions.required(() -> conversations.insert(CompanionConversation.builder()
                .learnerId("learner").messageCount(0).nextSequence(1).activeTurnGeneration(0)
                .createdAt(admittedAt).updatedAt(admittedAt).build()));
        String turnId = UUID.randomUUID().toString();
        CompanionConversation first = transactions.required(() -> conversations.admitNew(conversation.getId(), "learner",
                turnId, 1, admittedAt, admittedAt.plusSeconds(10), 100).orElseThrow());
        Instant writeAt = admittedAt.plusSeconds(1);
        assertThat(transactions.required(() -> conversations.fenceActiveTurn(conversation.getId(), "learner", turnId,
                first.getActiveTurnGeneration(), writeAt, admittedAt.plusSeconds(20)))).isTrue();

        Instant takeoverAt = admittedAt.plusSeconds(21);
        CompanionConversation retry = transactions.required(() -> conversations.claimRetry(conversation.getId(), "learner",
                turnId, takeoverAt, takeoverAt.plusSeconds(30), 100).orElseThrow());
        assertThat(retry.getActiveTurnGeneration()).isGreaterThan(first.getActiveTurnGeneration());
        assertThat(transactions.required(() -> conversations.fenceActiveTurn(conversation.getId(), "learner", turnId,
                first.getActiveTurnGeneration(), takeoverAt.plusSeconds(1), takeoverAt.plusSeconds(31)))).isFalse();
        assertThat(transactions.required(() -> conversations.fenceActiveTurn(conversation.getId(), "learner", turnId,
                retry.getActiveTurnGeneration(), takeoverAt.plusSeconds(1), takeoverAt.plusSeconds(31)))).isTrue();
    }

    @Test void assistantAndActualUsageCommitTogetherAndBothRollBackOnQuotaFailure() {
        assertAtomicCompletion(false, true);
        assertAtomicCompletion(true, false);
    }

    private void assertAtomicCompletion(boolean failQuota, boolean succeeds) {
        mongoTemplate.dropCollection("companion_test_usage");
        Instant now = Instant.now();
        String learner = "accounting-learner";
        CompanionConversation conversation = transactions.required(() -> conversations.insert(CompanionConversation.builder()
                .learnerId(learner).messageCount(0).nextSequence(1).activeTurnGeneration(0)
                .createdAt(now).updatedAt(now).build()));
        CurrentUserProvider users = Mockito.mock(CurrentUserProvider.class);
        Mockito.when(users.requireCurrentUserId()).thenReturn(learner);
        CompanionModel model = request -> Flux.just(new CompanionModelEvent.TextDelta("Completed response"),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(19)));
        QuotaAvailability availability = (userId, quotaType) -> new AvailabilityResultDto(quotaType, 0, 100, 100,
                "TOKEN", "DAILY", now, now.plusSeconds(86400), now, "AVAILABLE");
        QuotaUsageRecording usage = command -> {
            mongoTemplate.insert(new Document("_id", command.idempotencyKey()).append("amount", command.amount()),
                    "companion_test_usage");
            if (failQuota) throw new IllegalStateException("quota store unavailable");
            return new UsageResultDto(true, "ledger-" + command.idempotencyKey(), command.amount(), now,
                    "allowance-1", null);
        };
        CompanionToolRegistry tools = Mockito.mock(CompanionToolRegistry.class);
        Mockito.when(tools.definitions(Mockito.anyBoolean())).thenReturn(List.of());
        CompanionMemoryService memories = Mockito.mock(CompanionMemoryService.class);
        Mockito.when(memories.settingsSnapshot(Mockito.anyString())).thenReturn(new MemorySettingsSnapshot(true, 0));
        MemoryRetrievalService memoryRetrieval = Mockito.mock(MemoryRetrievalService.class);
        Mockito.when(memoryRetrieval.retrieve(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new MemoryRetrievalResult("", List.of()));
        CompanionTurnService service = new CompanionTurnServiceImpl(users, conversations, messages, model, contextBuilder,
                properties, transactions, availability, usage, tools, memories, memoryRetrieval, toolExecutor);
        String turnId = UUID.randomUUID().toString();
        var prepared = service.prepareTurn(conversation.getId(),
                new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(UUID.fromString(turnId), "Question", false));

        if (succeeds) {
            service.streamPreparedTurn(prepared, new CompanionTurnService.TurnObserver() {
                @Override public void onDelta(String text) {}
                @Override public boolean isCancelled() { return false; }
            });
        } else {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.streamPreparedTurn(prepared,
                    new CompanionTurnService.TurnObserver() {
                        @Override public void onDelta(String text) {}
                        @Override public boolean isCancelled() { return false; }
                    })).isInstanceOf(CompanionException.class)
                    .extracting(failure -> ((CompanionException) failure).getCode())
                    .isEqualTo("COMPANION_QUOTA_UNAVAILABLE");
        }
        assertThat(messages.findByTurnAndRole(conversation.getId(), turnId, CompanionMessage.Role.USER)).isPresent();
        assertThat(messages.findByTurnAndRole(conversation.getId(), turnId, CompanionMessage.Role.ASSISTANT).isPresent())
                .isEqualTo(succeeds);
        assertThat(mongoTemplate.getCollection("companion_test_usage").countDocuments()).isEqualTo(succeeds ? 1 : 0);
    }

    private boolean admit(String conversationId, String turnId, long sequence, Instant now) {
        return Boolean.TRUE.equals(transactions.required(() -> {
            CompanionConversation row = conversations.admitNew(conversationId, "learner", turnId, sequence, now,
                    now.plusSeconds(120), 100).orElse(null);
            if (row == null) return false;
            messages.insert(CompanionMessage.builder().conversationId(conversationId).turnId(turnId)
                    .sequence(sequence).role(CompanionMessage.Role.USER).content("hi").createdAt(now).build());
            return true;
        }));
    }

    private CompanionConversation reserve(String id, String turnId, long sequence, Instant now, Instant lease) {
        CompanionConversation row = conversations.admitNew(id, "learner", turnId, sequence, now, lease, 100).orElseThrow();
        messages.insert(CompanionMessage.builder().conversationId(id).turnId(turnId).sequence(sequence)
                .role(CompanionMessage.Role.USER).content("failed").createdAt(now).build());
        return row;
    }

    private List<Object> race(java.util.concurrent.Callable<Object> one, java.util.concurrent.Callable<Object> two) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (var call : List.of(one, two)) futures.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { return call.call(); } catch (RuntimeException failure) { return failure; }
            }));
            ready.await(10, TimeUnit.SECONDS); start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
