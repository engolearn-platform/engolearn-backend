package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.TopicCreateRequest;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.request.TopicUpdateRequest;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListItem;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.service.TopicManagementService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 — integration tests against a real MongoDB (design record F05 §9.1 step 10):
 * regex-escaped case-insensitive search, AND-combined filters, fixed
 * {@code updatedAt DESC} sort, paging, the ARCHIVED-excluding item aggregation
 * (null duration coalesced to 0), derived {@code durationMinutes}/{@code itemCount}
 * through the service, and the two management indexes' key patterns.
 *
 * <p>Requires the docker-compose Mongo stack ({@code localhost:27017},
 * {@code engolearn_db}, {@code admin}/{@code admin123}) as configured in
 * {@code application.yaml}. Uses {@code @SpringBootTest} rather than
 * {@code @DataMongoTest} because {@code spring-boot-starter-mongodb-test} 4.1.1
 * does not bring in {@code spring-boot-data-mongodb-test} (the module owning the
 * {@code @DataMongoTest} annotation) and no dependency may be added; the full
 * context additionally proves auditing and {@code auto-index-creation} work
 * end-to-end. Timestamps for sort assertions are inserted raw so they stay
 * deterministic.</p>
 */
@SpringBootTest
class TopicManagementMongoIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final String VERSIONS_COLLECTION = "topics_version";

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TopicItemRepository topicItemRepository;

    @Autowired
    private TopicManagementService service;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanCollections() {
        topicRepository.deleteAll();
        topicItemRepository.deleteAll();
    }

    // --------------------------------------------------------------- search

    @Test
    void search_isCaseInsensitiveSubstringOnEitherTitle_andEscapesRegexMetacharacters() {
        topicRepository.save(topic("t1", "Ordering Food at a Restaurant", "Gọi món tại nhà hàng",
                null, null, null, BASE.plusSeconds(10)));
        topicRepository.save(topic("t2", "Travel Plans", "Kế hoạch du lịch",
                null, null, null, BASE.plusSeconds(20)));

        assertThat(ids(search(criteria("food", null, null, null)))).containsExactly("t1");
        assertThat(ids(search(criteria("MÓN", null, null, null)))).containsExactly("t1");
        assertThat(ids(search(criteria("restaurant", null, null, null)))).containsExactly("t1");
        assertThat(ids(search(criteria("Kế hoạch", null, null, null)))).containsExactly("t2");
        assertThat(ids(search(criteria("zzz", null, null, null)))).isEmpty();

        // Pattern.quote: regex metacharacters are matched literally, never interpreted
        assertThat(ids(search(criteria(".*", null, null, null)))).isEmpty();
        assertThat(ids(search(criteria("(", null, null, null)))).isEmpty();
        assertThat(ids(search(criteria("o.*g", null, null, null)))).isEmpty();
    }

    @Test
    void search_combinesAllFiltersWithAnd() {
        topicRepository.save(topic("t1", "Topic One", "Chủ đề một", CefrLevel.B1, TopicCategory.DINING, 3, BASE.plusSeconds(10)));
        topicRepository.save(topic("t2", "Topic Two", "Chủ đề hai", CefrLevel.B1, TopicCategory.TRAVEL, 3, BASE.plusSeconds(20)));
        topicRepository.save(topic("t3", "Topic Three", "Chủ đề ba", CefrLevel.A2, TopicCategory.DINING, 3, BASE.plusSeconds(30)));

        assertThat(ids(search(criteria("topic", CefrLevel.B1, TopicCategory.DINING, true)))).containsExactly("t1");
        assertThat(ids(search(criteria("topic", CefrLevel.B1, null, null)))).containsExactlyInAnyOrder("t1", "t2");
        assertThat(ids(search(criteria("topic", null, TopicCategory.DINING, null)))).containsExactlyInAnyOrder("t1", "t3");
        assertThat(ids(search(criteria("topic", CefrLevel.A2, TopicCategory.TRAVEL, null)))).isEmpty();
        assertThat(ids(search(criteria("nope", null, null, null)))).isEmpty();
    }

    @Test
    void search_publishedFilter_matchesBothPresentValueAndMissingField() {
        topicRepository.save(topic("published", "Published Topic", "Đã phát hành", null, null, 2, BASE.plusSeconds(10)));
        topicRepository.save(topic("draft", "Draft Topic", "Bản nháp", null, null, null, BASE.plusSeconds(20)));
        // Raw insert with no latest_published_version field at all — must count as draft
        mongoTemplate.getCollection("topics").insertOne(new Document("_id", "raw-draft")
                .append("title_en", "Raw Draft Topic")
                .append("title_vi", "Nháp thô")
                .append("updated_at", Date.from(BASE.plusSeconds(30))));

        assertThat(ids(search(criteria(null, null, null, true)))).containsExactly("published");
        assertThat(ids(search(criteria(null, null, null, false)))).containsExactlyInAnyOrder("draft", "raw-draft");
        assertThat(ids(search(criteria(null, null, null, null)))).hasSize(3);
    }

    @Test
    void search_sortsByUpdatedAtDescending() {
        insertRawTopic("old", "Old Topic", BASE.plusSeconds(1));
        insertRawTopic("newest", "Newest Topic", BASE.plusSeconds(300));
        insertRawTopic("middle", "Middle Topic", BASE.plusSeconds(150));

        assertThat(ids(search(criteria(null, null, null, null)))).containsExactly("newest", "middle", "old");
    }

    @Test
    void search_paginatesWithTotals() {
        for (int i = 1; i <= 5; i++) {
            insertRawTopic("t" + i, "Topic " + i, BASE.plusSeconds(i));
        }

        Page<Topic> firstPage = topicRepository.search(criteria(null, null, null, null),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "updatedAt")));

        assertThat(ids(firstPage)).containsExactly("t5", "t4");
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);

        Page<Topic> lastPage = topicRepository.search(criteria(null, null, null, null),
                PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "updatedAt")));
        assertThat(ids(lastPage)).containsExactly("t1");
        assertThat(lastPage.getTotalElements()).isEqualTo(5);
    }

    @Test
    void search_emptyResultReturnsEmptyPage() {
        Page<Topic> page = topicRepository.search(criteria("nothing", null, null, null),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updatedAt")));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ---------------------------------------------------------- aggregation

    @Test
    void statsByTopicIds_excludesArchivedItems_andCoalescesMissingDurationToZero() {
        topicItemRepository.saveAll(List.of(
                item("t1", ContentStatus.DRAFT, 30),
                item("t1", ContentStatus.PUBLISHED, 40),
                item("t1", ContentStatus.BETA, null),
                item("t1", ContentStatus.ARCHIVED, 600),
                item("t2", ContentStatus.DRAFT, 5)));

        var stats = topicItemRepository.statsByTopicIds(List.of("t1", "t2", "t3"));

        assertThat(stats.get("t1").itemCount()).isEqualTo(3);
        assertThat(stats.get("t1").totalDurationSec()).isEqualTo(70);
        assertThat(stats.get("t2").itemCount()).isEqualTo(1);
        assertThat(stats.get("t2").totalDurationSec()).isEqualTo(5);
        assertThat(stats).doesNotContainKey("t3");
    }

    @Test
    void statsByTopicId_returnsEmptyWhenTopicHasNoItems_orOnlyArchivedItems() {
        topicItemRepository.save(item("t1", ContentStatus.ARCHIVED, 300));

        assertThat(topicItemRepository.statsByTopicId("t1")).isEqualTo(TopicItemStats.EMPTY);
        assertThat(topicItemRepository.statsByTopicId("unknown")).isEqualTo(TopicItemStats.EMPTY);
    }

    // ------------------------------------------------------ service + Mongo

    @Test
    void service_list_derivesStatusItemCountAndCeiledDurationMinutesFromStoredItems() {
        topicRepository.save(topic("published", "Ordering Food", "Gọi món", CefrLevel.B1, TopicCategory.DINING, 3, BASE.plusSeconds(10)));
        topicRepository.save(topic("draft", "Travel Plans", "Du lịch", CefrLevel.A2, TopicCategory.TRAVEL, null, BASE.plusSeconds(20)));
        topicItemRepository.saveAll(List.of(
                item("published", ContentStatus.PUBLISHED, 30),
                item("published", ContentStatus.DRAFT, 40),
                item("published", ContentStatus.ARCHIVED, 900),
                item("draft", ContentStatus.DRAFT, null)));

        var response = service.list(new TopicManagementListQuery(null, null, null, null, 0, 20));

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.counters().total()).isEqualTo(2);
        assertThat(response.counters().published()).isEqualTo(1);
        assertThat(response.counters().draft()).isEqualTo(1);

        var publishedItem = response.items().stream().filter(i -> i.id().equals("published")).findFirst().orElseThrow();
        assertThat(publishedItem.status()).isEqualTo("published");
        assertThat(publishedItem.itemCount()).isEqualTo(2);
        assertThat(publishedItem.cefr()).isEqualTo(CefrLevel.B1);
        assertThat(publishedItem.category()).isEqualTo(TopicCategory.DINING);

        var draftItem = response.items().stream().filter(i -> i.id().equals("draft")).findFirst().orElseThrow();
        assertThat(draftItem.status()).isEqualTo("draft");
        assertThat(draftItem.itemCount()).isEqualTo(1);

        // durationMinutes is not exposed by the list item; verify through the detail read (ceil(70/60) = 2)
        assertThat(service.get("published").durationMinutes()).isEqualTo(2);
        assertThat(service.get("published").itemCount()).isEqualTo(2);
        assertThat(service.get("draft").durationMinutes()).isZero();
    }

    @Test
    void service_create_writesNoVersionDocumentsAndNoItems() {
        long versionsBefore = mongoTemplate.getCollection(VERSIONS_COLLECTION).countDocuments();

        var created = service.create(new TopicCreateRequest("Food", "Ẩm thực", "B1", "DINING", null, null));

        assertThat(created.status()).isEqualTo("draft");
        assertThat(created.itemCount()).isZero();
        assertThat(created.durationMinutes()).isZero();
        assertThat(mongoTemplate.getCollection(VERSIONS_COLLECTION).countDocuments()).isEqualTo(versionsBefore);
        assertThat(topicItemRepository.count()).isZero();

        assertThat(created.id()).as("created topic id must be returned for the Location header").isNotNull();

        // Mongo stores the generated id as ObjectId; the entity exposes its hex string
        Document stored = mongoTemplate.getCollection("topics")
                .find(new Document("_id", new ObjectId(created.id()))).first();
        assertThat(stored).as("stored topic document for id " + created.id()).isNotNull();
        assertThat(stored).doesNotContainKeys("latest_published_version", "latest_beta_version",
                "duration_minutes", "tags", "order_index");
        assertThat(stored.getString("title_en")).isEqualTo("Food");
    }

    @Test
    void service_create_thenGetUpdateAndList_roundTripByReturnedId() {
        var created = service.create(new TopicCreateRequest("Round Trip", "Vòng tròn", null, null, null, null));

        // E1 → E2: the id handed back in the 201 body/Location must resolve
        assertThat(service.get(created.id()).titleEn()).isEqualTo("Round Trip");

        // E1 → E3: partial update by the same id
        var updated = service.update(created.id(), new TopicUpdateRequest(
                null, null, null, null, "edited description", null,
                Set.of(TopicUpdateRequest.DESCRIPTION)));
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.description()).isEqualTo("edited description");

        // E1 → E4: the created topic appears in the management list
        assertThat(service.list(new TopicManagementListQuery(null, null, null, null, 0, 20)).items())
                .extracting(TopicManagementListItem::id)
                .contains(created.id());
    }

    // -------------------------------------------------------------- indexes

    @Test
    void indexes_createdForManagementHotPaths() {
        List<Document> topicIndexes = mongoTemplate.getCollection("topics").listIndexes().into(new ArrayList<>());
        assertThat(topicIndexes).anySatisfy(index -> {
            assertThat(index.getString("name")).isEqualTo("updated_at");
            assertThat(index.get("key")).isEqualTo(new Document("updated_at", 1));
        });

        List<Document> itemIndexes = mongoTemplate.getCollection("topic_items").listIndexes().into(new ArrayList<>());
        assertThat(itemIndexes).anySatisfy(index -> {
            assertThat(index.getString("name")).isEqualTo("topic_status");
            assertThat(index.get("key")).isEqualTo(new Document("topic_id", 1).append("status", 1));
        });
    }

    // -------------------------------------------------------------- helpers

    private Page<Topic> search(TopicManagementSearchCriteria criteria) {
        return topicRepository.search(criteria, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    private static TopicManagementSearchCriteria criteria(String search, CefrLevel cefr, TopicCategory category, Boolean published) {
        return new TopicManagementSearchCriteria(search, cefr, category, published);
    }

    private static List<String> ids(Page<Topic> page) {
        return page.getContent().stream().map(Topic::getId).toList();
    }

    private static Topic topic(String id, String titleEn, String titleVi,
                               CefrLevel cefr, TopicCategory category, Integer latestPublishedVersion, Instant updatedAt) {
        return Topic.builder()
                .id(id)
                .titleEn(titleEn)
                .titleVi(titleVi)
                .cefrLevel(cefr)
                .category(category)
                .latestPublishedVersion(latestPublishedVersion)
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
    }

    private static TopicItem item(String topicId, ContentStatus status, Integer durationSec) {
        return TopicItem.builder()
                .topicId(topicId)
                .status(status)
                .context(TopicContextVo.builder().durationSec(durationSec).build())
                .build();
    }

    /** Bypasses the mapping layer so {@code updated_at} is deterministic for sort assertions. */
    private void insertRawTopic(String id, String titleEn, Instant updatedAt) {
        mongoTemplate.getCollection("topics").insertOne(new Document("_id", id)
                .append("title_en", titleEn)
                .append("title_vi", titleEn)
                .append("updated_at", Date.from(updatedAt)));
    }
}
