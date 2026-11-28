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

    @Test void attachedReferencePersistsAndRetryUsesCanonicalReference() {
        var now = Instant.now();
        String learner = "context-owner";
        var conversation = conversations.insert(CompanionConversation.builder()
                .learnerId(learner).messageCount(0).nextSequence(1).activeTurnGeneration(0)
                .createdAt(now).updatedAt(now).build());
        CurrentUserProvider users = Mockito.mock(CurrentUserProvider.class);
        Mockito.when(users.requireCurrentUserId()).thenReturn(learner);
        var resolver = Mockito.mock(fit.iuh.engolearn.companion.service.impl.ScreenContextResolverRegistry.class);
        var reference = new fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest(
                fit.iuh.engolearn.companion.model.enums.ScreenContextType.TOPIC_OVERVIEW, "topic-one", null, null, null);
        var other = new fit.iuh.engolearn.companion.dto.request.ScreenContextReferenceRequest(
                fit.iuh.engolearn.companion.model.enums.ScreenContextType.TOPIC_OVERVIEW, "topic-two", null, null, null);
        Mockito.when(resolver.resolve(learner, reference)).thenThrow(new CompanionException(503, "SCREEN_CONTEXT_TEMPORARILY_UNAVAILABLE", "Retry", true))
                .thenReturn("{\"topic\":\"Travel\"}");
        CompanionModel model = Mockito.mock(CompanionModel.class);
        Mockito.when(model.stream(Mockito.any())).thenReturn(Flux.just(new CompanionModelEvent.TextDelta("Travel"),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(19))));
        QuotaAvailability availability = (userId, type) -> new AvailabilityResultDto(type, 0, 100, 100,
                "TOKEN", "DAILY", now, now.plusSeconds(86400), now, "AVAILABLE");
        QuotaUsageRecording usage = command -> new UsageResultDto(true, "ledger", command.amount(), now, "allowance", null);
        CompanionToolRegistry tools = Mockito.mock(CompanionToolRegistry.class);
        Mockito.when(tools.definitions(Mockito.anyBoolean())).thenReturn(List.of());
        CompanionMemoryService memories = Mockito.mock(CompanionMemoryService.class);
        Mockito.when(memories.settingsSnapshot(learner)).thenReturn(new MemorySettingsSnapshot(false, 0));
        MemoryRetrievalService retrieval = Mockito.mock(MemoryRetrievalService.class);
        Mockito.when(retrieval.retrieve(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(new MemoryRetrievalResult("", List.of()));
        var service = new CompanionTurnServiceImpl(users, conversations, messages, model, contextBuilder, properties,
                transactions, availability, usage, tools, memories, retrieval, resolver, toolExecutor);
        var turnId = UUID.randomUUID();
        var prepared = service.prepareTurn(conversation.getId(), new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(turnId, "Explain this", false, reference));
        var observer = new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return false; }
        };
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.streamPreparedTurn(prepared, observer))
                .isInstanceOf(CompanionException.class);
        Mockito.verifyNoInteractions(model);
        var persisted = messages.findByTurnAndRole(conversation.getId(), turnId.toString(), CompanionMessage.Role.USER).orElseThrow();
        assertThat(persisted.getScreenContext().getTopicId()).isEqualTo("topic-one");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.prepareTurn(conversation.getId(),
                new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(turnId, "Explain this", true, other)))
                .isInstanceOf(CompanionException.class);
        var retry = service.prepareTurn(conversation.getId(), new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(turnId, "Explain this", true));
        assertThat(retry.screenContext()).isEqualTo(reference);
        assertThat(service.streamPreparedTurn(retry, observer).assistantMessage().content()).isEqualTo("Travel");
        Mockito.verify(model).stream(Mockito.argThat(request -> request.messages().stream().anyMatch(message ->
                message.text().contains("untrusted contextual data") && message.text().contains("Travel"))));
        assertThat(messages.newest(conversation.getId(), null, 10)).hasSize(2);
    }

    @Test void firstAdmissionIsAtomicAndTitleAndOwnerLookupSurviveCompletionAndDeletion() {
        var model = Mockito.mock(CompanionModel.class);
        Mockito.when(model.stream(Mockito.any())).thenReturn(Flux.just(new CompanionModelEvent.TextDelta("Answer"),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(19))));
        var usage = new java.util.concurrent.atomic.AtomicInteger();
        var service = firstService("first-owner", available(true), model, usage, messages);
        UUID id = UUID.randomUUID();
        String text = "  " + "a".repeat(64) + "😀extra  ";
        var request = new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, text, false);
        var first = service.prepareFirstTurn(request);
        assertThat(first.admission().conversation().title()).isEqualTo("a".repeat(64) + "😀");
        assertThat(first.admission().userMessage().content()).isEqualTo(text);
        assertThat(first.admission().userMessage().sequence()).isEqualTo(1);
        assertThat(service.getFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.ACTIVE);
        assertThat(conversations.listOwned("first-owner", null, null, 10)).hasSize(1);
        assertThat(messages.newest(first.prepared().conversationId(), null, 10)).hasSize(1);
        assertFirstCode(() -> service.prepareFirstTurn(request), "FIRST_TURN_ALREADY_ADMITTED");
        assertFirstCode(() -> service.prepareFirstTurn(new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, "different", false)),
                "FIRST_TURN_PAYLOAD_CONFLICT");
        assertFirstCode(() -> firstService("other-owner", available(true), model, usage, messages).getFirstTurn(id), "FIRST_TURN_NOT_FOUND");
        service.streamPreparedTurn(first.prepared(), firstObserver());
        var completed = service.getFirstTurn(id);
        assertThat(completed.state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.COMPLETED);
        assertThat(completed.assistantMessage().content()).isEqualTo("Answer");
        assertThat(completed.conversation().title()).isEqualTo(first.admission().conversation().title());
        assertThat(usage.get()).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("companion_first_test_usage").countDocuments()).isEqualTo(1);
        Mockito.verify(model, Mockito.times(1)).stream(Mockito.any());
        assertFirstCode(() -> service.prepareFirstTurn(request), "FIRST_TURN_ALREADY_ADMITTED");
        transactions.required(() -> {
            conversations.deleteOwned(first.prepared().conversationId(), "first-owner");
            messages.deleteConversationMessages(first.prepared().conversationId());
            return null;
        });
        assertThat(service.getFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.DELETED);
        assertFirstCode(() -> service.prepareFirstTurn(request), "FIRST_TURN_REMOVED");
        assertThat(conversations.listOwned("first-owner", null, null, 10)).isEmpty();
    }

    @Test void rejectedCancelledAndRolledBackFirstRequestsHaveNoConversationOrUser() {
        var model = Mockito.mock(CompanionModel.class);
        var usage = new java.util.concurrent.atomic.AtomicInteger();
        var rejected = firstService("reject-owner", available(false), model, usage, messages);
        var id = UUID.randomUUID();
        assertFirstCode(() -> rejected.prepareFirstTurn(new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, " ", false)), "INVALID_TURN");
        assertFirstCode(() -> rejected.prepareFirstTurn(new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, "hello", false)), "COMPANION_QUOTA_EXHAUSTED");
        assertFirstCode(() -> rejected.getFirstTurn(id), "FIRST_TURN_NOT_FOUND");
        var cancelled = firstService("reject-owner", available(true), model, usage, messages);
        assertThat(cancelled.cancelFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.CANCELLED);
        assertThat(cancelled.cancelFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.CANCELLED);
        assertFirstCode(() -> cancelled.prepareFirstTurn(new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, "hello", false)), "FIRST_TURN_CANCELLED");
        var failingMessages = Mockito.mock(CompanionMessageRepository.class);
        Mockito.when(failingMessages.insert(Mockito.any())).thenAnswer(invocation -> {
            messages.insert(invocation.getArgument(0));
            throw new IllegalStateException("injected rollback after USER insert");
        });
        var broken = firstService("rollback-owner", available(true), model, usage, failingMessages);
        var failedId = UUID.randomUUID();
        assertFirstCode(() -> broken.prepareFirstTurn(new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(failedId, "hello", false)), "FIRST_TURN_ADMISSION_UNKNOWN");
        assertFirstCode(() -> broken.getFirstTurn(failedId), "FIRST_TURN_NOT_FOUND");
        assertThat(mongoTemplate.findAll(CompanionConversation.class)).isEmpty();
        assertThat(mongoTemplate.findAll(CompanionMessage.class)).isEmpty();
        assertThat(mongoTemplate.findAll(fit.iuh.engolearn.companion.model.CompanionFirstTurnReceipt.class)).hasSize(1);
        assertThat(usage.get()).isZero();
        Mockito.verifyNoInteractions(model);
    }

    @Test void concurrentFirstAdmissionAcrossInstancesHasOneWinnerAndCancellationFencesDelayedAdmission() throws Exception {
        var model = Mockito.mock(CompanionModel.class);
        Mockito.when(model.stream(Mockito.any())).thenReturn(Flux.just(new CompanionModelEvent.TextDelta("Answer"),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(19))));
        var usage = new java.util.concurrent.atomic.AtomicInteger();
        // Precreate only disposable test collections so this tests the unique receipt race, not collection DDL.
        mongoTemplate.createCollection(CompanionConversation.class);
        mongoTemplate.createCollection(CompanionMessage.class);
        mongoTemplate.createCollection(fit.iuh.engolearn.companion.model.CompanionFirstTurnReceipt.class);
        for (int i = 0; i < 2; i++) {
            String owner = "race-owner-" + i;
            var barrier = new CountDownLatch(2);
            QuotaAvailability gated = (user, type) -> {
                barrier.countDown();
                awaitBarrier(barrier);
                return available(true).checkAvailability(user, type);
            };
            var one = firstService(owner, gated, model, usage, messages);
            var two = firstService(owner, gated, model, usage, messages);
            var id = UUID.randomUUID();
            var request = new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, "same payload", false);
            var outcomes = race(() -> one.prepareFirstTurn(request), () -> two.prepareFirstTurn(request));
            assertThat(outcomes.stream().filter(CompanionTurnService.FirstPreparedTurn.class::isInstance)).hasSize(1);
            var winner = outcomes.get(0) instanceof CompanionTurnService.FirstPreparedTurn ? one : two;
            var prepared = (CompanionTurnService.FirstPreparedTurn) outcomes.stream()
                    .filter(CompanionTurnService.FirstPreparedTurn.class::isInstance).findFirst().orElseThrow();
            assertThat(conversations.listOwned(owner, null, null, 10)).hasSize(1);
            assertThat(messages.newest(prepared.prepared().conversationId(), null, 10)).hasSize(1);
            assertThat(two.getFirstTurn(id).conversation().id()).isEqualTo(prepared.prepared().conversationId());
            winner.streamPreparedTurn(prepared.prepared(), firstObserver());
            assertThat(one.getFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.COMPLETED);
            assertFirstCode(() -> two.prepareFirstTurn(request), "FIRST_TURN_ALREADY_ADMITTED");
        }
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        QuotaAvailability delayed = (user, type) -> {
            entered.countDown();
            awaitBarrier(release);
            return available(true).checkAvailability(user, type);
        };
        var admission = firstService("cancel-race-owner", delayed, model, usage, messages);
        var cancel = firstService("cancel-race-owner", available(true), model, usage, messages);
        var id = UUID.randomUUID();
        var request = new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, "late", false);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var future = pool.submit(() -> admission.prepareFirstTurn(request));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cancel.cancelFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.CANCELLED);
            release.countDown();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> future.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(cancel.getFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.CANCELLED);
            assertThat(conversations.listOwned("cancel-race-owner", null, null, 10)).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(usage.get()).isEqualTo(2);
        Mockito.verify(model, Mockito.times(2)).stream(Mockito.any());
        assertThat(mongoTemplate.findAll(CompanionMessage.class)).hasSize(4);
    }

    @Test void admittedStopKeepsCanonicalUserAndDoesNotClaimCancellationBeforeAdmission() {
        var model = Mockito.mock(CompanionModel.class);
        var usage = new java.util.concurrent.atomic.AtomicInteger();
        var service = firstService("stop-owner", available(true), model, usage, messages);
        var id = UUID.randomUUID();
        var prepared = service.prepareFirstTurn(new fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest(id, "recorded", false));
        assertThat(service.cancelFirstTurn(id).state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.ACTIVE);
        service.cancelPreparedTurn(prepared.prepared());
        var status = service.getFirstTurn(id);
        assertThat(status.state()).isEqualTo(fit.iuh.engolearn.companion.dto.response.FirstTurnStatusResponse.State.INCOMPLETE);
        assertThat(status.userMessage().content()).isEqualTo("recorded");
        assertThat(status.retryEligible()).isTrue();
        assertThat(usage.get()).isZero();
        Mockito.verifyNoInteractions(model);
    }

    private static QuotaAvailability available(boolean allowed) {
        Instant now = Instant.now();
        return (user, type) -> new AvailabilityResultDto(type, allowed ? 0 : 100, 100, allowed ? 100 : 0,
                "TOKEN", "DAILY", now, now.plusSeconds(86400), now, allowed ? "AVAILABLE" : "EXHAUSTED");
    }

    private CompanionTurnService firstService(String learner, QuotaAvailability availability, CompanionModel model,
            java.util.concurrent.atomic.AtomicInteger usageCount, CompanionMessageRepository messageStore) {
        CurrentUserProvider users = Mockito.mock(CurrentUserProvider.class);
        Mockito.when(users.requireCurrentUserId()).thenReturn(learner);
        QuotaUsageRecording usage = command -> {
            usageCount.incrementAndGet();
            mongoTemplate.insert(new Document("_id", command.idempotencyKey()).append("amount", command.amount()),
                    "companion_first_test_usage");
            return new UsageResultDto(true, "ledger", command.amount(), Instant.now(), "allowance", null);
        };
        CompanionToolRegistry tools = Mockito.mock(CompanionToolRegistry.class);
        Mockito.when(tools.definitions(Mockito.anyBoolean())).thenReturn(List.of());
        CompanionMemoryService memories = Mockito.mock(CompanionMemoryService.class);
        Mockito.when(memories.settingsSnapshot(learner)).thenReturn(new MemorySettingsSnapshot(false, 0));
        MemoryRetrievalService retrieval = Mockito.mock(MemoryRetrievalService.class);
        Mockito.when(retrieval.retrieve(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(new MemoryRetrievalResult("", List.of()));
        return new CompanionTurnServiceImpl(users, conversations, messageStore, model, contextBuilder, properties,
                transactions, availability, usage, tools, memories, retrieval,
                Mockito.mock(fit.iuh.engolearn.companion.service.impl.ScreenContextResolverRegistry.class), toolExecutor);
    }

    private static CompanionTurnService.TurnObserver firstObserver() {
        return new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return false; }
        };
    }

    private static void assertFirstCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        org.assertj.core.api.Assertions.assertThatThrownBy(call).isInstanceOf(CompanionException.class)
                .extracting(failure -> ((CompanionException) failure).getCode()).isEqualTo(code);
    }

    private static void awaitBarrier(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test barrier timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
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
                properties, transactions, availability, usage, tools, memories, memoryRetrieval,
                Mockito.mock(fit.iuh.engolearn.companion.service.impl.ScreenContextResolverRegistry.class), toolExecutor);
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
