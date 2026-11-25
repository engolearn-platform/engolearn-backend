package fit.iuh.engolearn.companion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import fit.iuh.engolearn.companion.model.CompanionConversation;
import fit.iuh.engolearn.companion.model.CompanionMessage;
import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

@EnabledIfDockerAvailable
class CompanionTurnReservationIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired CompanionConversationRepository conversations;
    @Autowired CompanionMessageRepository messages;
    @Autowired MongoMessageTransactions transactions;

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
