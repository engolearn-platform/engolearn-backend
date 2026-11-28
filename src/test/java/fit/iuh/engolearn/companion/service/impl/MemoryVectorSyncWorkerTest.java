package fit.iuh.engolearn.companion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fit.iuh.engolearn.companion.model.CompanionMemory;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.repository.CompanionMemoryRepository;
import fit.iuh.engolearn.companion.service.CompanionMemoryCommitted;
import fit.iuh.engolearn.companion.service.MemoryEmbeddingService;
import fit.iuh.engolearn.companion.service.MemoryVectorStore;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemoryVectorSyncWorkerTest {
    @Test
    void serializesConcurrentPostCommitSignalsOnOneWorker() throws Exception {
        CompanionMemoryRepository memories = mock(CompanionMemoryRepository.class);
        MemoryEmbeddingService embeddings = mock(MemoryEmbeddingService.class);
        MemoryVectorStore vectors = mock(MemoryVectorStore.class);
        CompanionMemoryProperties properties = new CompanionMemoryProperties();
        CompanionMemory first = memory("memory-1");
        CompanionMemory second = memory("memory-2");
        when(memories.findCurrentByIdAndVersion("memory-1", 1)).thenReturn(Optional.of(first));
        when(memories.findCurrentByIdAndVersion("memory-2", 1)).thenReturn(Optional.of(second));
        CountDownLatch firstEmbeddingStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstEmbedding = new CountDownLatch(1);
        CountDownLatch bothUpsertsCompleted = new CountDownLatch(2);
        AtomicInteger embeddingCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = embeddingCalls.incrementAndGet();
            if (call == 1) {
                firstEmbeddingStarted.countDown();
                assertThat(releaseFirstEmbedding.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return List.of(0.1f, 0.2f);
        }).when(embeddings).embedDocument(any(), any());
        doAnswer(invocation -> {
            bothUpsertsCompleted.countDown();
            return null;
        }).when(vectors).upsert(any(), anyList(), anyMap(), any());
        MemoryVectorSyncWorker worker = new MemoryVectorSyncWorker(memories, embeddings, vectors, properties);

        try {
            worker.onMemoryCommitted(new CompanionMemoryCommitted("memory-1", 1));
            assertThat(firstEmbeddingStarted.await(2, TimeUnit.SECONDS)).isTrue();
            worker.onMemoryCommitted(new CompanionMemoryCommitted("memory-2", 1));
            assertThat(embeddingCalls.get()).isEqualTo(1);
            releaseFirstEmbedding.countDown();
            assertThat(bothUpsertsCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstEmbedding.countDown();
            worker.stop();
        }
    }

    @Test
    void failedForgetCleanupRemainsRecoverableAndReconcileDeletesIt() {
        CompanionMemoryRepository memories = mock(CompanionMemoryRepository.class);
        MemoryEmbeddingService embeddings = mock(MemoryEmbeddingService.class);
        MemoryVectorStore vectors = mock(MemoryVectorStore.class);
        CompanionMemoryProperties properties = new CompanionMemoryProperties();
        var cleanup = fit.iuh.engolearn.companion.model.MemoryVectorCleanup.builder()
                .pointId("forgotten-point").learnerId("learner-a").deletionVersion(2).retryState("FAILED").build();
        when(memories.listVectorCleanupsForReconciliation(null, properties.getReconcilePageLimit()))
                .thenReturn(List.of(cleanup));
        when(vectors.scroll(null, properties.getReconcilePageLimit(), properties.getRequestTimeout()))
                .thenReturn(new MemoryVectorStore.VectorPage(List.of(), null));
        org.mockito.Mockito.doThrow(new IllegalStateException("offline")).doNothing()
                .when(vectors).delete("forgotten-point", properties.getRequestTimeout());
        MemoryVectorSyncWorker worker = new MemoryVectorSyncWorker(memories, embeddings, vectors, properties);
        try {
            worker.reconcile();
            org.mockito.Mockito.verify(memories).updateVectorCleanupState("forgotten-point", "FAILED");
            org.mockito.Mockito.verify(memories, org.mockito.Mockito.never()).deleteVectorCleanup("forgotten-point");
            worker.reconcile();
            org.mockito.Mockito.verify(memories).deleteVectorCleanup("forgotten-point");
        } finally {
            worker.stop();
        }
    }

    private static CompanionMemory memory(String id) {
        return CompanionMemory.builder().id(id).learnerId("learner-a").category(MemoryCategory.FACT)
                .content("I live in Hanoi.").retrievalMode(MemoryRetrievalMode.RELEVANT)
                .version(1).vectorSyncState("PENDING").build();
    }
}
