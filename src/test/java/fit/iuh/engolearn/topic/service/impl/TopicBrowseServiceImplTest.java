package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.model.enums.TopicSortOrder;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemSummaryResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;
import fit.iuh.engolearn.topic.dto.response.TopicSummaryResponse;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.utils.TopicCursorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the browse service with mocked repositories.
 *
 * <p>What these tests prove: what the service sends to the store, how it turns a fetched
 * batch into a page, and how a cursor is produced, rejected and re-used. What they do not
 * prove: that MongoDB itself filters, orders and compares the way the query asks it to. That
 * part was checked against a real MongoDB outside the build.
 */
@ExtendWith(MockitoExtension.class)
class TopicBrowseServiceImplTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private TopicVersionRepository topicVersionRepository;

    @Mock
    private TopicItemVersionRepository topicItemVersionRepository;

    private TopicBrowseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopicBrowseServiceImpl(topicRepository, topicVersionRepository,
                topicItemVersionRepository);
    }

    // --- list ---------------------------------------------------------------------------

    @Test
    void listReturnsOnlyPublishedTopicsAndAsksTheStoreForOneRowMoreThanThePage() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(null, null, TopicRepository.SortKey.ORDER_INDEX,
                null, null, 21)).thenReturn(List.of(published("t1", 1, 10), published("t2", 2, 20)));

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, null, null));

        // The published-only filter lives in the store query; the service asks for the page
        // plus one row and reports the result as it comes.
        verify(topicRepository).findPublishedPage(null, null, TopicRepository.SortKey.ORDER_INDEX,
                null, null, 21);
        assertThat(page.getItems()).extracting(TopicSummaryResponse::getTopicId).containsExactly("t1", "t2");
        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void listFiltersByExactlyOneLevelAndByNoLevelWhenNoneIsGiven() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(eq(CefrLevel.A1), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(published("t1", 1, 10)));
        service.getAvailableTopics(request("a1", null, null, null, null));
        verify(topicRepository).findPublishedPage(eq(CefrLevel.A1), any(), any(), any(), any(), anyInt());

        when(topicRepository.findPublishedPage(eq(null), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        service.getAvailableTopics(request("  ", null, null, null, null));
        verify(topicRepository).findPublishedPage(eq(null), any(), any(), any(), any(), anyInt());
    }

    @Test
    void listPassesTheKeywordToTheStoreTrimmedAndTreatsABlankKeywordAsNoFilter() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        service.getAvailableTopics(request(null, "  airport ", null, null, null));
        verify(topicRepository).findPublishedPage(any(), eq("airport"), any(), any(), any(), anyInt());

        service.getAvailableTopics(request(null, "   ", null, null, null));
        verify(topicRepository).findPublishedPage(any(), eq(null), any(), any(), any(), anyInt());
    }

    @Test
    void listSortsByRecommendedOrderByDefaultAndByDurationWhenAsked() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        service.getAvailableTopics(request(null, null, null, null, null));
        verify(topicRepository).findPublishedPage(any(), any(), eq(TopicRepository.SortKey.ORDER_INDEX),
                any(), any(), anyInt());

        service.getAvailableTopics(request(null, null, "DURATION", null, null));
        verify(topicRepository).findPublishedPage(any(), any(), eq(TopicRepository.SortKey.DURATION_MINUTES),
                any(), any(), anyInt());
    }

    @Test
    void listUsesTwentyTopicsPerPageByDefault() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(IntStream.rangeClosed(1, 21).mapToObj(n -> published("t" + n, n, n)).toList());

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, null, null));

        assertThat(page.getItems()).hasSize(20);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isNotNull();
    }

    @Test
    void listAcceptsAPageSizeInsideTheAllowedRangeAndRejectsAnythingElse() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        service.getAvailableTopics(request(null, null, null, 5, null));
        verify(topicRepository).findPublishedPage(any(), any(), any(), any(), any(), eq(6));

        service.getAvailableTopics(request(null, null, null, 100, null));
        verify(topicRepository).findPublishedPage(any(), any(), any(), any(), any(), eq(101));

        assertThatThrownBy(() -> service.getAvailableTopics(request(null, null, null, 0, null)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        assertThatThrownBy(() -> service.getAvailableTopics(request(null, null, null, -1, null)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        assertThatThrownBy(() -> service.getAvailableTopics(request(null, null, null, 101, null)))
                .isInstanceOf(InvalidBrowseRequestException.class);
    }

    @Test
    void listRejectsAnUnknownLevelAndAnUnknownSortOrder() {
        assertThatThrownBy(() -> service.getAvailableTopics(request("C9", null, null, null, null)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        assertThatThrownBy(() -> service.getAvailableTopics(request(null, null, "title", null, null)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        verifyNoInteractions(topicRepository, topicVersionRepository, topicItemVersionRepository);
    }

    @Test
    void listRejectsACursorThatCannotBeDecoded() {
        assertThatThrownBy(() -> service.getAvailableTopics(request(null, null, null, null, "not-base64!!")))
                .isInstanceOf(InvalidBrowseRequestException.class);
        verifyNoInteractions(topicRepository, topicVersionRepository, topicItemVersionRepository);
    }

    @Test
    void listRejectsACursorProducedByAnotherSortOrderOrAnotherFilter() {
        String cursor = TopicCursorCodec.encode(TopicSortOrder.RECOMMENDED,
                                                TopicCursorCodec.fingerprint(CefrLevel.A1, null), 1, "t1");

        assertThatThrownBy(() -> service.getAvailableTopics(request(null, null, "duration", null, cursor)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        assertThatThrownBy(() -> service.getAvailableTopics(request("A2", null, null, null, cursor)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        assertThatThrownBy(() -> service.getAvailableTopics(request(null, "coffee", null, null, cursor)))
                .isInstanceOf(InvalidBrowseRequestException.class);
        // Same filters, same sort order: the cursor is accepted.
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        service.getAvailableTopics(request("a1", null, null, null, cursor));
        verify(topicRepository).findPublishedPage(eq(CefrLevel.A1), eq(null),
                eq(TopicRepository.SortKey.ORDER_INDEX), eq(1), eq("t1"), anyInt());
    }

    @Test
    void scrollingPagesNeverRepeatsOrSkipsATopicEvenWhenSortKeysTie() {
        // Four topics share order_index 1 and two of them share duration 10 as well, so the
        // cursor has to carry the pair (sort key, id).
        List<Topic> stored = List.of(
                published("t3", 1, 10),
                published("t1", 1, 10),
                published("t2", 1, 10),
                published("t4", 1, 20),
                published("t5", 2, 5));
        stubPageQuery(stored);

        List<String> seen = scrollAll(null, null, null, 2);

        assertThat(seen).containsExactly("t1", "t2", "t3", "t4", "t5");
        assertThat(new LinkedHashSet<>(seen)).hasSize(stored.size());
    }

    @Test
    void theLastPageCarriesNoCursor() {
        stubPageQuery(List.of(published("t1", 1, 10), published("t2", 2, 20)));

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, 5, null));

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void aPageKeepsTheOrderTheStoreReturnedItIn() {
        stubEveryReleaseAsResolvable();
        when(topicRepository.findPublishedPage(null, null, TopicRepository.SortKey.ORDER_INDEX,
                null, null, 21)).thenReturn(List.of(published("t2", 1, 10), published("t1", 1, 10)));

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, null, null));

        assertThat(page.getItems()).extracting(TopicSummaryResponse::getTopicId).containsExactly("t2", "t1");
    }

    @Test
    void theCardShowsTheMaintainedTopicDurationAndCountsOnlyVisibleItems() {
        Topic topic = published("t1", 1, 30);
        when(topicRepository.findPublishedPage(null, null, TopicRepository.SortKey.ORDER_INDEX,
                null, null, 21)).thenReturn(List.of(topic));
        when(topicVersionRepository.findAllById(List.of("r1"))).thenReturn(List.of(release("r1", "i1", "i2")));
        when(topicItemVersionRepository.findAllById(List.of("i1", "i2"))).thenReturn(List.of(
                itemVersion("i1", "item-1", 1, ContentStatus.PUBLISHED, 12, 2, 1),
                itemVersion("i2", "item-2", 2, ContentStatus.HIDDEN, 40, 5, 5)));

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, null, null));
        TopicSummaryResponse card = page.getItems().get(0);

        // The hidden item contributes nothing, and the duration is the maintained value on
        // the topic document (30), not the sum of the visible item durations (12).
        assertThat(card.getVocabularyCount()).isEqualTo(2);
        assertThat(card.getQuizCount()).isEqualTo(1);
        assertThat(card.getDurationMinutes()).isEqualTo(30);
        assertThat(card.getTitleEn()).isEqualTo("Topic t1");
    }

    @Test
    void aTopicWhoseReleasePointerIsGoneIsNotListed() {
        Topic dangling = published("t1", 1, 10);
        dangling.setLatestPublishedVersion("r-missing");
        when(topicRepository.findPublishedPage(null, null, TopicRepository.SortKey.ORDER_INDEX,
                null, null, 21)).thenReturn(List.of(dangling));
        when(topicVersionRepository.findAllById(List.of("r-missing"))).thenReturn(List.of());

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, null, null));

        assertThat(page.getItems()).isEmpty();
    }

    // --- list: releases that are not published ---------------------------------------

    @Test
    void aTopicWhoseReleaseIsNotPublishedIsNotListed() {
        Topic draft = published("t1", 1, 10);
        Topic archived = published("t2", 2, 20);
        archived.setLatestPublishedVersion("r2");
        Topic published = published("t3", 3, 30);
        published.setLatestPublishedVersion("r3");
        lenient().when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of());
        when(topicRepository.findPublishedPage(null, null, TopicRepository.SortKey.ORDER_INDEX,
                null, null, 21)).thenReturn(List.of(draft, archived, published));
        when(topicVersionRepository.findAllById(List.of("r1", "r2", "r3"))).thenReturn(List.of(
                releaseWithStatus("r1", ContentStatus.DRAFT),
                releaseWithStatus("r2", ContentStatus.ARCHIVED),
                releaseWithStatus("r3", ContentStatus.PUBLISHED)));

        TopicPageResponse page = service.getAvailableTopics(request(null, null, null, null, null));

        assertThat(page.getItems()).extracting(TopicSummaryResponse::getTopicId).containsExactly("t3");
    }

    @Test
    void aPageFilteredDownToNothingStillAdvertisesTheNextPage() {
        // Every fetched row of the first page points at a draft release while the store has
        // published topics behind them. The empty page must still advertise a next page, and
        // the cursor must lead to those topics instead of ending the list.
        List<Topic> stored = List.of(
                publishedWithRelease("t1", "r1", 1, 10),
                publishedWithRelease("t2", "r2", 2, 20),
                publishedWithRelease("t3", "r3", 3, 30),
                publishedWithRelease("t4", "r4", 4, 40));
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    Integer cursorSortKey = invocation.getArgument(3);
                    String cursorId = invocation.getArgument(4);
                    int limit = invocation.getArgument(5);
                    return stored.stream()
                            .filter(topic -> cursorId == null
                                    || topic.getOrderIndex() > cursorSortKey
                                    || (topic.getOrderIndex().equals(cursorSortKey)
                                    && topic.getId().compareTo(cursorId) > 0))
                            .limit(limit)
                            .toList();
                });
        when(topicVersionRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> releaseWithStatus(id, id.equals("r4")
                            ? ContentStatus.PUBLISHED
                            : ContentStatus.DRAFT))
                    .toList();
        });
        lenient().when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of());

        TopicPageResponse first = service.getAvailableTopics(request(null, null, null, 2, null));

        assertThat(first.getItems()).isEmpty();
        assertThat(first.isHasMore()).isTrue();
        assertThat(first.getNextCursor()).isNotNull();

        TopicPageResponse second = service.getAvailableTopics(
                request(null, null, null, 2, first.getNextCursor()));

        assertThat(second.getItems()).extracting(TopicSummaryResponse::getTopicId).containsExactly("t4");
        assertThat(second.isHasMore()).isFalse();
        assertThat(second.getNextCursor()).isNull();
    }

    @Test
    void scrollingPastTopicsWithAnUnpublishedReleaseKeepsMovingForward() {
        // t1 and t3 point at a draft release. With a page size of two the first page is
        // filtered down to t2, and t4 must still arrive on the next page.
        List<Topic> stored = List.of(
                publishedWithRelease("t1", "r1", 1, 10),
                publishedWithRelease("t2", "r2", 2, 20),
                publishedWithRelease("t3", "r3", 3, 30),
                publishedWithRelease("t4", "r4", 4, 40));
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    Integer cursorSortKey = invocation.getArgument(3);
                    String cursorId = invocation.getArgument(4);
                    int limit = invocation.getArgument(5);
                    return stored.stream()
                            .filter(topic -> cursorId == null
                                    || topic.getOrderIndex() > cursorSortKey
                                    || (topic.getOrderIndex().equals(cursorSortKey)
                                    && topic.getId().compareTo(cursorId) > 0))
                            .limit(limit)
                            .toList();
                });
        when(topicVersionRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> id.equals("r1") || id.equals("r3")
                            ? releaseWithStatus(id, ContentStatus.DRAFT)
                            : releaseWithStatus(id, ContentStatus.PUBLISHED))
                    .toList();
        });
        lenient().when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of());

        List<String> seen = scrollAll(null, null, null, 2);

        assertThat(seen).containsExactly("t2", "t4");
        assertThat(new LinkedHashSet<>(seen)).hasSize(seen.size());
    }

    // --- detail -------------------------------------------------------------------------

    @Test
    void detailRefusesATopicWhoseReleaseIsNotPublished() {
        Topic topic = published("t1", 1, 30);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicVersionRepository.findById("r1"))
                .thenReturn(Optional.of(releaseWithStatus("r1", ContentStatus.DRAFT)));

        assertThatThrownBy(() -> service.getTopicDetail(GetTopicDetailRequest.builder().topicId("t1").build()))
                .isInstanceOf(TopicNotFoundException.class);
        verify(topicItemVersionRepository, never()).findAllById(anyList());
    }

    @Test
    void detailRefusesATopicWhoseReleaseIsArchived() {
        Topic topic = published("t1", 1, 30);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicVersionRepository.findById("r1"))
                .thenReturn(Optional.of(releaseWithStatus("r1", ContentStatus.ARCHIVED)));

        assertThatThrownBy(() -> service.getTopicDetail(GetTopicDetailRequest.builder().topicId("t1").build()))
                .isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void detailReturnsTheVisibleItemsOfTheCurrentReleaseInTheirFixedOrder() {
        Topic topic = published("t1", 1, 30);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicVersionRepository.findById("r1")).thenReturn(Optional.of(release("r1", "i3", "i1", "i2", "i4")));
        when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of(
                itemVersion("i1", "item-1", 1, ContentStatus.PUBLISHED, 12, 3, 2),
                itemVersion("i2", "item-2", 1, ContentStatus.PUBLISHED, 15, 1, 1),
                itemVersion("i3", "item-3", 3, ContentStatus.PUBLISHED, 20, 2, 1),
                itemVersion("i4", "item-4", 2, ContentStatus.HIDDEN, 30, 9, 9)));

        var detail = service.getTopicDetail(GetTopicDetailRequest.builder().topicId("t1").build());

        // The manifest order was i3, i1, i2, i4; the display order comes from the item
        // versions: order 1 with i1 before i2, then order 3. The hidden item is gone.
        assertThat(detail.getItems()).extracting(TopicItemSummaryResponse::getTopicItemId)
                .containsExactly("item-1", "item-2", "item-3");
        assertThat(detail.getItems().get(0).getDurationMinutes()).isEqualTo(12);
        assertThat(detail.getItems().get(0).getVocabularyCount()).isEqualTo(3);
        assertThat(detail.getItems().get(0).getQuizCount()).isEqualTo(2);
        assertThat(detail.getItems().get(0).getExpressionCount()).isZero();
        assertThat(detail.getTopicId()).isEqualTo("t1");
        assertThat(detail.getDescription()).isEqualTo("Description of t1");
    }

    @Test
    void detailCountsEverySampleSentenceOfEveryExpressionGroup() {
        Topic topic = published("t1", 1, 30);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicVersionRepository.findById("r1")).thenReturn(Optional.of(release("r1", "i1")));
        TopicItemVersion item = itemVersion("i1", "item-1", 1, ContentStatus.PUBLISHED, 5, 1, 1);
        item.setExpressions(List.of(
                block(2, 3), // two groups with three samples each
                block(1, 1)));
        when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of(item));

        var detail = service.getTopicDetail(GetTopicDetailRequest.builder().topicId("t1").build());

        assertThat(detail.getItems().get(0).getExpressionCount()).isEqualTo(7);
    }

    @Test
    void detailRefusesAnUnknownTopic() {
        when(topicRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTopicDetail(GetTopicDetailRequest.builder().topicId("nope").build()))
                .isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void detailRefusesATopicThatWasNeverPublished() {
        Topic topic = published("t1", 1, 10);
        topic.setLatestPublishedVersion(null);
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.getTopicDetail(GetTopicDetailRequest.builder().topicId("t1").build()))
                .isInstanceOf(TopicNotFoundException.class);
        verify(topicVersionRepository, never()).findById(anyString());
    }

    @Test
    void detailRefusesATopicWhoseReleasePointerIsGone() {
        Topic topic = published("t1", 1, 10);
        topic.setLatestPublishedVersion("r-missing");
        when(topicRepository.findById("t1")).thenReturn(Optional.of(topic));
        when(topicVersionRepository.findById("r-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTopicDetail(GetTopicDetailRequest.builder().topicId("t1").build()))
                .isInstanceOf(TopicNotFoundException.class);
    }

    @Test
    void detailRefusesARequestWithoutATopicId() {
        assertThatThrownBy(() -> service.getTopicDetail(GetTopicDetailRequest.builder().topicId(" ").build()))
                .isInstanceOf(InvalidBrowseRequestException.class);
    }

    // --- helpers -----------------------------------------------------------------------

    /**
     * Makes every release pointer of a returned topic resolve to an empty release, so a test
     * only has to stub the item versions it cares about.
     */
    private void stubEveryReleaseAsResolvable() {
        lenient().when(topicVersionRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            return ids.stream().map(TopicBrowseServiceImplTest::release).toList();
        });
        lenient().when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of());
    }

    /**
     * Answers the page query from an in-memory list, applying the same rule the store query
     * applies: rows that follow the cursor row on the pair (sort key, id), ordered by it.
     */
    private void stubPageQuery(List<Topic> stored) {
        when(topicRepository.findPublishedPage(any(), any(), any(), any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    TopicRepository.SortKey sortKey = invocation.getArgument(2);
                    Integer cursorSortKey = invocation.getArgument(3);
                    String cursorId = invocation.getArgument(4);
                    int limit = invocation.getArgument(5);
                    Function<Topic, Integer> key = sortKey == TopicRepository.SortKey.DURATION_MINUTES
                            ? Topic::getDurationMinutes
                            : Topic::getOrderIndex;
                    Comparator<Topic> order = Comparator
                            .comparing(key, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(Topic::getId);
                    return stored.stream()
                            .filter(topic -> topic.getLatestPublishedVersion() != null)
                            .filter(topic -> followsCursor(topic, key, cursorSortKey, cursorId))
                            .sorted(order)
                            .limit(limit)
                            .toList();
                });
        stubEveryReleaseAsResolvable();
    }

    private static boolean followsCursor(Topic topic, Function<Topic, Integer> key,
                                         Integer cursorSortKey, String cursorId) {
        if (cursorId == null) {
            return true;
        }
        Integer value = key.apply(topic);
        boolean idAfter = topic.getId().compareTo(cursorId) > 0;
        if (cursorSortKey == null) {
            // A missing sort key orders first, so only the remaining null-key rows follow.
            return value != null || idAfter;
        }
        return value > cursorSortKey || (value.equals(cursorSortKey) && idAfter);
    }

    private List<String> scrollAll(String level, String keyword, String sort, int limit) {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        TopicPageResponse page;
        int guard = 0;
        do {
            page = service.getAvailableTopics(request(level, keyword, sort, limit, cursor));
            page.getItems().forEach(item -> seen.add(item.getTopicId()));
            cursor = page.getNextCursor();
            guard++;
        } while (cursor != null && guard < 20);
        return seen;
    }

    private static GetAvailableTopicRequest request(String level, String keyword, String sort,
                                                    Integer limit, String cursor) {
        return GetAvailableTopicRequest.builder()
                .level(level).keyword(keyword).sort(sort).limit(limit).cursor(cursor).build();
    }

    private static Topic published(String id, Integer orderIndex, Integer durationMinutes) {
        return Topic.builder()
                .id(id)
                .titleEn("Topic " + id)
                .titleVi("Chủ đề " + id)
                .description("Description of " + id)
                .cefrLevel(CefrLevel.A1)
                .orderIndex(orderIndex)
                .durationMinutes(durationMinutes)
                .coverUrl("https://cdn.example.test/" + id + ".jpg")
                .latestPublishedVersion("r1")
                .build();
    }

    private static Topic publishedWithRelease(String id, String releaseId, Integer orderIndex, Integer durationMinutes) {
        Topic topic = published(id, orderIndex, durationMinutes);
        topic.setLatestPublishedVersion(releaseId);
        return topic;
    }

    private static TopicVersion release(String id, String... itemVersionIds) {
        return releaseWithStatus(id, ContentStatus.PUBLISHED, itemVersionIds);
    }

    private static TopicVersion releaseWithStatus(String id, ContentStatus status, String... itemVersionIds) {
        return TopicVersion.builder()
                .id(id)
                .itemVersionIds(List.of(itemVersionIds))
                .status(status)
                .build();
    }

    private static TopicItemVersion itemVersion(String id, String topicItemId, Integer order,
                                                ContentStatus status, int durationMinutes,
                                                int vocabularies, int quizzes) {
        return TopicItemVersion.builder()
                .id(id)
                .topicItemId(topicItemId)
                .title("Scenario " + topicItemId)
                .description("Short description " + topicItemId)
                .order(order)
                .status(status)
                .durationMinutes(durationMinutes)
                .vocabularies(words(vocabularies))
                .quizzes(quizModules(quizzes))
                .build();
    }

    private static List<ItemWordVo> words(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> ItemWordVo.builder().vocabId("v" + i).build())
                .toList();
    }

    private static List<QuizModuleVo> quizModules(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> QuizModuleVo.builder().quizId("q" + i).build())
                .toList();
    }

    private static ExpressionBlockVo block(int groups, int samplesPerGroup) {
        List<ExpressionPurposeVo> purposes = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            List<ExpressionExampleVo> examples = new ArrayList<>();
            for (int s = 0; s < samplesPerGroup; s++) {
                examples.add(ExpressionExampleVo.builder().textEn("sample " + s).build());
            }
            purposes.add(ExpressionPurposeVo.builder().examples(examples).build());
        }
        return ExpressionBlockVo.builder().purposes(purposes).build();
    }
}