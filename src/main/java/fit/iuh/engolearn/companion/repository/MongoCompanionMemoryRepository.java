package fit.iuh.engolearn.companion.repository;

import fit.iuh.engolearn.companion.model.CompanionMemory;
import fit.iuh.engolearn.companion.model.CompanionMemorySettings;
import fit.iuh.engolearn.companion.model.MemoryVectorCleanup;
import fit.iuh.engolearn.companion.model.MemoryOperationReceipt;
import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoCompanionMemoryRepository implements CompanionMemoryRepository {
    private final MongoTemplate mongo;

    @Override
    public MemoryPage list(String learnerId, MemoryCategory category, Instant beforeUpdatedAt, String beforeId, int limit) {
        Criteria criteria = Criteria.where("learner_id").is(learnerId);
        if (category != null) criteria.and("category").is(category);
        if (beforeUpdatedAt != null && beforeId != null) criteria.andOperator(new Criteria().orOperator(
                Criteria.where("updated_at").lt(beforeUpdatedAt),
                new Criteria().andOperator(Criteria.where("updated_at").is(beforeUpdatedAt), Criteria.where("_id").lt(beforeId))));
        List<CompanionMemory> rows = mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "updated_at")
                .and(Sort.by(Sort.Direction.DESC, "_id"))).limit(limit + 1), CompanionMemory.class);
        String cursor = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            CompanionMemory last = rows.get(rows.size() - 1);
            cursor = last.getUpdatedAt().toString() + "|" + last.getId();
        }
        return new MemoryPage(List.copyOf(rows), cursor);
    }

    @Override public Optional<CompanionMemory> findCurrent(String learnerId, String memoryId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("_id").is(memoryId)
                .and("learner_id").is(learnerId)), CompanionMemory.class));
    }
    @Override public Optional<CompanionMemory> findCurrentByIdAndVersion(String memoryId, long version) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("_id").is(memoryId).and("version").is(version)), CompanionMemory.class));
    }

    @Override public Optional<CompanionMemory> findDuplicate(String learnerId, MemoryCategory category, String normalizedContent) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("learner_id").is(learnerId)
                .and("category").is(category).and("content").is(normalizedContent)), CompanionMemory.class));
    }

    @Override public List<CompanionMemory> findCurrentByLearnerIdAndIds(String learnerId, Collection<String> memoryIds) {
        if (memoryIds.isEmpty()) return List.of();
        return mongo.find(Query.query(Criteria.where("learner_id").is(learnerId).and("_id").in(memoryIds)), CompanionMemory.class);
    }

    @Override public List<CompanionMemory> listCurrentByLearnerId(String learnerId, MemoryRetrievalMode retrievalMode) {
        return listCurrentByLearnerId(learnerId, retrievalMode, 100);
    }
    @Override public List<CompanionMemory> listCurrentByLearnerId(String learnerId, MemoryRetrievalMode retrievalMode, int limit) {
        Criteria criteria = Criteria.where("learner_id").is(learnerId);
        if (retrievalMode != null) criteria.and("retrieval_mode").is(retrievalMode);
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "updated_at").and(Sort.by(Sort.Direction.DESC, "_id")))
                .limit(Math.max(1, Math.min(limit, 100))), CompanionMemory.class);
    }

    @Override public List<CompanionMemory> listCurrentForSync(int limit) {
        Criteria pending = new Criteria().orOperator(Criteria.where("vector_sync_state").is("PENDING"),
                Criteria.where("vector_sync_state").exists(false), Criteria.where("vector_sync_state").is(null));
        return mongo.find(Query.query(pending)
                .with(Sort.by(Sort.Direction.ASC, "_id")).limit(limit), CompanionMemory.class);
    }

    @Override public List<CompanionMemory> listCurrentForReconciliation(String afterId, int limit) {
        Criteria criteria = afterId == null ? new Criteria() : Criteria.where("_id").gt(afterId);
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.ASC, "_id")).limit(limit), CompanionMemory.class);
    }

    @Override public CompanionMemory insert(CompanionMemory memory) { return mongo.insert(memory); }

    @Override public Optional<CompanionMemory> update(String learnerId, String memoryId, long expectedVersion, String content, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(memoryId).and("learner_id").is(learnerId).and("version").is(expectedVersion));
        var result = mongo.updateFirst(query, new Update().set("content", content).set("vector_sync_state", "PENDING")
                .set("updated_at", now).inc("version", 1), CompanionMemory.class);
        return result.getModifiedCount() == 1 ? findCurrent(learnerId, memoryId) : Optional.empty();
    }

    @Override public Optional<CompanionMemory> incrementVersion(String learnerId, String memoryId, long expectedVersion, Instant now) {
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(memoryId).and("learner_id").is(learnerId)
                        .and("version").is(expectedVersion)), new Update().set("updated_at", now).inc("version", 1), CompanionMemory.class);
        return result.getModifiedCount() == 1 ? findCurrent(learnerId, memoryId) : Optional.empty();
    }

    @Override public boolean deleteCurrent(String learnerId, String memoryId, long expectedVersion) {
        return mongo.remove(Query.query(Criteria.where("_id").is(memoryId).and("learner_id").is(learnerId)
                .and("version").is(expectedVersion)), CompanionMemory.class).getDeletedCount() == 1;
    }

    @Override public Optional<CompanionMemorySettings> findSettings(String learnerId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("learner_id").is(learnerId)), CompanionMemorySettings.class));
    }

    @Override public CompanionMemorySettings ensureSettings(String learnerId) {
        CompanionMemorySettings existing = mongo.findAndModify(Query.query(Criteria.where("_id").is(learnerId)),
                new Update().setOnInsert("learner_id", learnerId).setOnInsert("forget_revision", 0L),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().upsert(true).returnNew(true),
                CompanionMemorySettings.class);
        return existing;
    }

    @Override public CompanionMemorySettings setAutomaticMemoryEnabled(String learnerId, boolean enabled, Instant now) {
        ensureSettings(learnerId);
        mongo.updateFirst(Query.query(Criteria.where("learner_id").is(learnerId)),
                new Update().set("automatic_memory_enabled", enabled).inc("mutation_fence", 1).set("updated_at", now), CompanionMemorySettings.class);
        return findSettings(learnerId).orElseThrow();
    }

    @Override public boolean fenceAutomaticWrite(String learnerId, long expectedForgetRevision, Instant now) {
        ensureSettings(learnerId);
        return mongo.updateFirst(Query.query(Criteria.where("learner_id").is(learnerId)
                        .and("forget_revision").is(expectedForgetRevision)
                        .orOperator(Criteria.where("automatic_memory_enabled").exists(false), Criteria.where("automatic_memory_enabled").is(null), Criteria.where("automatic_memory_enabled").is(true))),
                new Update().inc("mutation_fence", 1).set("updated_at", now), CompanionMemorySettings.class).getModifiedCount() == 1;
    }

    @Override public boolean fenceExplicitWrite(String learnerId, Instant now) {
        ensureSettings(learnerId);
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(learnerId)),
                new Update().inc("mutation_fence", 1).set("updated_at", now), CompanionMemorySettings.class).getModifiedCount() == 1;
    }

    @Override public long advanceForgetRevision(String learnerId, Instant now) {
        ensureSettings(learnerId);
        CompanionMemorySettings changed = mongo.findAndModify(Query.query(Criteria.where("learner_id").is(learnerId)),
                new Update().inc("forget_revision", 1).inc("mutation_fence", 1).set("updated_at", now),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), CompanionMemorySettings.class);
        return changed.getForgetRevision();
    }

    @Override public void insertCleanup(MemoryVectorCleanup cleanup) { mongo.insert(cleanup); }
    @Override public List<MemoryVectorCleanup> listVectorCleanups(int limit) {
        return mongo.find(Query.query(new Criteria()).with(Sort.by(Sort.Direction.ASC, "created_at")).limit(limit), MemoryVectorCleanup.class);
    }
    @Override public List<MemoryVectorCleanup> listPendingVectorCleanups(int limit) {
        Criteria pending = new Criteria().orOperator(Criteria.where("retry_state").is("PENDING"),
                Criteria.where("retry_state").exists(false), Criteria.where("retry_state").is(null));
        return mongo.find(Query.query(pending).with(Sort.by(Sort.Direction.ASC, "point_id")).limit(limit), MemoryVectorCleanup.class);
    }
    @Override public List<MemoryVectorCleanup> listVectorCleanupsForReconciliation(String afterPointId, int limit) {
        Criteria criteria = afterPointId == null ? new Criteria() : Criteria.where("point_id").gt(afterPointId);
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.ASC, "point_id")).limit(limit), MemoryVectorCleanup.class);
    }
    @Override public void deleteVectorCleanup(String pointId) {
        mongo.remove(Query.query(Criteria.where("point_id").is(pointId)), MemoryVectorCleanup.class);
    }
    @Override public boolean updateVectorCleanupState(String pointId, String state) {
        if (!List.of("PENDING", "FAILED").contains(state)) throw new IllegalArgumentException("Invalid vector cleanup state");
        return mongo.updateFirst(Query.query(Criteria.where("point_id").is(pointId)),
                new Update().set("retry_state", state), MemoryVectorCleanup.class).getModifiedCount() == 1;
    }
    @Override public boolean updateVectorSyncState(String memoryId, long expectedVersion, String state) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(memoryId).and("version").is(expectedVersion)),
                new Update().set("vector_sync_state", state), CompanionMemory.class).getModifiedCount() == 1;
    }
    @Override public Optional<MemoryOperationReceipt> findReceipt(String conversationId, String turnId, int ordinal) {
        String id = MemoryOperationReceipt.idFor(conversationId, turnId, ordinal);
        return Optional.ofNullable(mongo.findById(id, MemoryOperationReceipt.class));
    }
    @Override public void insertReceipt(MemoryOperationReceipt receipt) { mongo.insert(receipt); }
}
