package fit.iuh.engolearn.companion.service.impl;

import fit.iuh.engolearn.companion.model.CompanionMemory;
import fit.iuh.engolearn.companion.model.MemoryVectorCleanup;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.repository.CompanionMemoryRepository;
import fit.iuh.engolearn.companion.service.CompanionMemoryCommitted;
import fit.iuh.engolearn.companion.service.MemoryEmbeddingService;
import fit.iuh.engolearn.companion.service.MemoryVectorStore;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryVectorSyncWorker {
    private final CompanionMemoryRepository memories;
    private final MemoryEmbeddingService embeddings;
    private final MemoryVectorStore vectors;
    private final CompanionMemoryProperties properties;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "companion-memory-vector-sync");
        thread.setDaemon(true);
        return thread;
    });

    @EventListener
    public void onMemoryCommitted(CompanionMemoryCommitted event) {
        if (properties.isEnabled()) worker.execute(() -> syncCommitted(event.memoryId(), event.version()));
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (properties.isEnabled() && properties.isReconcileOnStart()) worker.execute(this::reconcile);
    }

    @Scheduled(fixedDelayString = "${engolearn.ai-companion.memory.worker-delay-ms:30000}")
    public void drainPending() {
        if (properties.isEnabled()) worker.execute(this::drain);
    }

    public void reconcile() {
        if (!properties.isEnabled()) return;
        reindexCurrentRecords();
        reconcileCleanupReceipts();
        removeStalePoints();
    }

    private void drain() {
        try {
            memories.listCurrentForSync(properties.getSyncBatchSize()).forEach(memory -> sync(memory, false));
        } catch (RuntimeException failure) {
            log.warn("Memory vector pending scan failed: {}", failure.getClass().getSimpleName());
        }
        try {
            memories.listPendingVectorCleanups(properties.getSyncBatchSize()).forEach(cleanup -> removeForgottenPoint(cleanup, false));
        } catch (RuntimeException failure) {
            log.warn("Memory vector cleanup scan failed: {}", failure.getClass().getSimpleName());
        }
    }

    private void syncCommitted(String memoryId, long version) {
        try {
            memories.findCurrentByIdAndVersion(memoryId, version).ifPresent(memory -> sync(memory, true));
        } catch (RuntimeException failure) {
            log.warn("Committed memory vector sync lookup failed: {}", failure.getClass().getSimpleName());
        }
    }

    private void sync(CompanionMemory memory, boolean force) {
        if (!force && "SYNCED".equals(memory.getVectorSyncState())) return;
        try {
            var vector = embeddings.embedDocument(memory.getContent(), properties.getRequestTimeout());
            vectors.upsert(memory.getId(), vector, Map.of("learner_id", memory.getLearnerId(),
                    "version", memory.getVersion(), "category", memory.getCategory().name(),
                    "retrieval_mode", memory.getRetrievalMode().name()), properties.getRequestTimeout());
            memories.updateVectorSyncState(memory.getId(), memory.getVersion(), "SYNCED");
        } catch (RuntimeException failure) {
            try {
                memories.updateVectorSyncState(memory.getId(), memory.getVersion(), "FAILED");
            } catch (RuntimeException stateFailure) {
                log.warn("Memory vector sync state update failed: {}", stateFailure.getClass().getSimpleName());
            }
            log.warn("Memory vector sync failed: {}", failure.getClass().getSimpleName());
        }
    }

    private void removeForgottenPoint(MemoryVectorCleanup cleanup, boolean retryFailed) {
        if (!retryFailed && "FAILED".equals(cleanup.getRetryState())) return;
        try {
            vectors.delete(cleanup.getPointId(), properties.getRequestTimeout());
            memories.deleteVectorCleanup(cleanup.getPointId());
        } catch (RuntimeException failure) {
            try {
                memories.updateVectorCleanupState(cleanup.getPointId(), "FAILED");
            } catch (RuntimeException stateFailure) {
                log.warn("Memory vector cleanup state update failed: {}", stateFailure.getClass().getSimpleName());
            }
            log.warn("Forgotten memory vector cleanup failed: {}", failure.getClass().getSimpleName());
        }
    }

    private void reindexCurrentRecords() {
        String afterId = null;
        for (int pageNumber = 0; pageNumber < properties.getReconcileMaxPages(); pageNumber++) {
            List<CompanionMemory> page = memories.listCurrentForReconciliation(afterId, properties.getReconcilePageLimit());
            if (page.isEmpty()) return;
            page.forEach(memory -> sync(memory, true));
            afterId = page.getLast().getId();
            if (page.size() < properties.getReconcilePageLimit()) return;
        }
    }

    private void reconcileCleanupReceipts() {
        String afterPointId = null;
        for (int pageNumber = 0; pageNumber < properties.getReconcileMaxPages(); pageNumber++) {
            List<MemoryVectorCleanup> page = memories.listVectorCleanupsForReconciliation(
                    afterPointId, properties.getReconcilePageLimit());
            if (page.isEmpty()) return;
            page.forEach(cleanup -> removeForgottenPoint(cleanup, true));
            afterPointId = page.getLast().getPointId();
            if (page.size() < properties.getReconcilePageLimit()) return;
        }
    }

    private void removeStalePoints() {
        String offset = null;
        for (int pageNumber = 0; pageNumber < properties.getReconcileMaxPages(); pageNumber++) {
            MemoryVectorStore.VectorPage page = vectors.scroll(offset, properties.getReconcilePageLimit(), properties.getRequestTimeout());
            if (page.points().isEmpty()) return;
            Map<String, List<MemoryVectorStore.VectorPoint>> byOwner = new LinkedHashMap<>();
            for (MemoryVectorStore.VectorPoint point : page.points()) {
                if (point.learnerId() != null) byOwner.computeIfAbsent(point.learnerId(), ignored -> new ArrayList<>()).add(point);
                else vectors.delete(point.pointId(), properties.getRequestTimeout());
            }
            byOwner.forEach((owner, points) -> {
                Map<String, CompanionMemory> current = new LinkedHashMap<>();
                memories.findCurrentByLearnerIdAndIds(owner, points.stream().map(MemoryVectorStore.VectorPoint::pointId).toList())
                        .forEach(memory -> current.put(memory.getId(), memory));
                for (MemoryVectorStore.VectorPoint point : points) {
                    CompanionMemory record = current.get(point.pointId());
                    if (record == null || record.getVersion() != point.version()) vectors.delete(point.pointId(), properties.getRequestTimeout());
                }
            });
            offset = page.nextOffset();
            if (offset == null || page.points().size() < properties.getReconcilePageLimit()) return;
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdown();
    }
}
