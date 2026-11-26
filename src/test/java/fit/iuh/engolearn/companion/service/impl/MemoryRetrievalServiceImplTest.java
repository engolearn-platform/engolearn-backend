package fit.iuh.engolearn.companion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.MemoryEmbeddingService;
import fit.iuh.engolearn.companion.service.MemoryRetrievalResult.MemoryReference;
import fit.iuh.engolearn.companion.service.MemoryVectorDocument;
import fit.iuh.engolearn.companion.service.MemoryVectorStore;
import fit.iuh.engolearn.config.CompanionMemoryProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryRetrievalServiceImplTest {
    private final CompanionMemoryService memories = mock(CompanionMemoryService.class);
    private final MemoryEmbeddingService embeddings = mock(MemoryEmbeddingService.class);
    private final MemoryVectorStore vectors = mock(MemoryVectorStore.class);
    private final CompanionMemoryProperties properties = new CompanionMemoryProperties();
    private final MemoryRetrievalServiceImpl retrieval = new MemoryRetrievalServiceImpl(memories, embeddings, vectors, properties);
    private final Instant deadline = Instant.now().plusSeconds(10);
    private final MemoryVectorDocument always = memory("always-1", 1, MemoryRetrievalMode.ALWAYS_ON, "I live in Hanoi.");

    @BeforeEach
    void setUp() {
        when(memories.listCurrent("learner-a", MemoryRetrievalMode.ALWAYS_ON, properties.getAlwaysOnLimit()))
                .thenReturn(List.of(always));
        when(embeddings.embedQuery(eq("hello"), any())).thenReturn(List.of(0.1f, 0.2f));
    }

    @Test
    void rechecksOwnerVersionAndRetrievalModeBeforePromptAssembly() {
        MemoryVectorDocument relevant = memory("relevant-1", 4, MemoryRetrievalMode.RELEVANT, "I am learning English.");
        MemoryVectorDocument stale = memory("stale-1", 8, MemoryRetrievalMode.RELEVANT, "Old version.");
        MemoryVectorDocument alwaysMode = memory("wrong-mode", 2, MemoryRetrievalMode.ALWAYS_ON, "Not relevant mode.");
        when(vectors.search(any(), eq("learner-a"), anyInt(), any())).thenReturn(List.of(
                new MemoryVectorStore.VectorMatch("relevant-1", "learner-a", 4),
                new MemoryVectorStore.VectorMatch("stale-1", "learner-a", 7),
                new MemoryVectorStore.VectorMatch("wrong-mode", "learner-a", 2),
                new MemoryVectorStore.VectorMatch("foreign", "learner-b", 3)));
        when(memories.findCurrentByIds("learner-a", List.of("relevant-1", "stale-1", "wrong-mode")))
                .thenReturn(List.of(relevant, stale, alwaysMode));

        var result = retrieval.retrieve("learner-a", "hello", deadline);

        assertThat(result.references()).containsExactly(new MemoryReference("always-1", 1), new MemoryReference("relevant-1", 4));
        assertThat(result.promptContext()).contains("I live in Hanoi.", "I am learning English.")
                .doesNotContain("Old version.", "Not relevant mode.");
        verify(vectors).search(any(), eq("learner-a"), eq(properties.getTopK()), any());
    }

    @Test
    void vectorFailureFallsBackToAlwaysOnCanonicalMemories() {
        when(vectors.search(any(), eq("learner-a"), anyInt(), any())).thenThrow(new IllegalStateException("qdrant unavailable"));

        var result = retrieval.retrieve("learner-a", "hello", deadline);

        assertThat(result.references()).containsExactly(new MemoryReference("always-1", 1));
        assertThat(result.promptContext()).contains("I live in Hanoi.").doesNotContain("relevant");
    }

    @Test
    void expiredDeadlineSkipsEmbeddingAndStillReturnsAlwaysOnMemories() {
        var result = retrieval.retrieve("learner-a", "hello", Instant.now().minusSeconds(1));

        assertThat(result.references()).containsExactly(new MemoryReference("always-1", 1));
        org.mockito.Mockito.verifyNoInteractions(embeddings, vectors);
    }

    private static MemoryVectorDocument memory(String id, long version, MemoryRetrievalMode mode, String content) {
        return new MemoryVectorDocument(id, "learner-a", MemoryCategory.FACT, content, mode, version);
    }
}
