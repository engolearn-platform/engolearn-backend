package fit.iuh.engolearn.speaking.repository;

import com.mongodb.ExplainVerbosity;
import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.config.SpeakingMongoIndexes;
import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.dto.MediaDeliveryDto;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.shared.security.impl.JwtTokenService;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingLimitsVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingSnapshotVo;
import fit.iuh.engolearn.speaking.service.SpeakingHistory;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.Group;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicLearnerState;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.UserTopicProgress;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import fit.iuh.engolearn.topic.model.vo.TopicMediaReferenceVo;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.service.TopicGenerationService;
import fit.iuh.engolearn.user.model.RefreshSession;
import fit.iuh.engolearn.user.model.User;
import jakarta.servlet.http.Cookie;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.time.Instant;
import java.net.URI;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfDockerAvailable
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"engolearn.speaking.recovery.enabled=false",
        "engolearn.security.jwt.secret=speaking-history-db-only-integration-secret"})
class SpeakingHistoryMongoIntegrationTest extends AbstractMongoIntegrationTest {
    private static final String GROUPS = "/api/v1/speaking/history/topics";
    private static final String SESSIONS = "/api/v1/speaking/sessions";
    private static final String LEARNER = "history-learner";
    private static final String OTHER = "history-other";
    private static final String ACTIVE = "674300000000000000000001";
    private static final String COMPLETE = "674300000000000000000002";
    private static final String HIDDEN = "674300000000000000000003";
    private static final String NOT_STARTED = "674300000000000000000004";
    private static final String FOREIGN = "674300000000000000000005";
    private static final String DRAFT_ITEM = "674300000000000000000006";
    private static final Instant TIE = Instant.parse("2026-11-25T07:30:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;
    @Autowired private SpeakingHistory history;
    @Autowired private SpeakingHistoryQueries queries;
    @Autowired private TopicGenerationService generations;
    @MockitoSpyBean private TopicRepository topicRepository;
    @MockitoBean private MediaManagement media;

    @BeforeEach
    void fixture() {
        SpeakingMongoIndexes.initialize(mongoTemplate);
        account(LEARNER, UserRole.USER);
        account(OTHER, UserRole.ADMIN);
    }

    @Test
    void ownerGroupsUseCurrentStatusAndFullTotalsBeyondMetadataBatchAndPage() throws Exception {
        topic(ACTIVE, ContentStatus.PUBLISHED);
        topic(COMPLETE, ContentStatus.PUBLISHED);
        topic(HIDDEN, ContentStatus.HIDDEN);
        topic(NOT_STARTED, ContentStatus.PUBLISHED);
        started(ACTIVE);
        started(HIDDEN);
        complete(COMPLETE);
        session(1, LEARNER, ACTIVE, true, "Older title", CefrLevel.A1, TIE.minusSeconds(1), false);
        session(2, LEARNER, ACTIVE, false, "Pinned title", CefrLevel.A2, TIE, false);
        session(3, LEARNER, COMPLETE, false, "Pinned title", CefrLevel.A2, TIE, false);
        session(4, LEARNER, HIDDEN, true, "Saved fallback", CefrLevel.A2, TIE, false);
        session(5, LEARNER, HIDDEN, false, null, null, TIE, false);
        session(6, LEARNER, NOT_STARTED, false, "Pinned title", CefrLevel.A1, TIE, false);
        session(7, OTHER, ACTIVE, false, "Private", CefrLevel.C2, TIE.plusSeconds(1), false);
        session(8, LEARNER, ACTIVE, false, "Deleted", CefrLevel.C2, TIE.plusSeconds(2), true);
        for (int index = 0; index < 102; index++) {
            session(1000 + index, LEARNER, "unknown-%03d".formatted(index), false,
                    "Unknown", CefrLevel.A1, TIE.plusSeconds(10), false);
        }

        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "IN_PROGRESS").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.inProgress").value(2))
                .andExpect(jsonPath("$.data.totals.completed").value(1))
                .andExpect(jsonPath("$.data.totals.other").value(103))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].topicId").value(ACTIVE))
                .andExpect(jsonPath("$.data.items[0].title").value("Shared current title"))
                .andExpect(jsonPath("$.data.items[0].level").value("B2"))
                .andExpect(jsonPath("$.data.items[0].learningProgress.totalItemCount").value(1))
                .andExpect(jsonPath("$.data.items[0].sessionCount").value(2))
                .andExpect(jsonPath("$.data.items[0].latestSessionCreatedAt").value(TIE.toString()));
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "IN_PROGRESS")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].topicId").value(HIDDEN))
                .andExpect(jsonPath("$.data.items[0].title").value("Saved fallback"))
                .andExpect(jsonPath("$.data.items[0].level").value("A2"))
                .andExpect(jsonPath("$.data.items[0].coverUrl").isEmpty())
                .andExpect(jsonPath("$.data.items[0].learningProgress").isEmpty());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER")
                        .param("page", "102").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].topicId").value(NOT_STARTED))
                .andExpect(jsonPath("$.data.items[0].learningStatus").value("NOT_STARTED"));
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].topicId").value("unknown-000"))
                .andExpect(jsonPath("$.data.items[0].learningStatus").value("UNKNOWN"));

        complete(ACTIVE);
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.inProgress").value(1))
                .andExpect(jsonPath("$.data.totals.completed").value(2))
                .andExpect(jsonPath("$.data.items[0].topicId").value(ACTIVE))
                .andExpect(jsonPath("$.data.items[0].learningProgress.percent").value(100))
                .andExpect(jsonPath("$.data.items[1].topicId").value(COMPLETE));
        assertThat(mongoTemplate.findById(id(2), SpeakingSession.class).getSnapshot().getTopicTitle())
                .isEqualTo("Pinned title");
        verifyNoInteractions(media);
    }

    @Test
    void parentUnionExactAndRecentHaveDistinctIntentAndStablePaging() throws Exception {
        topic(ACTIVE, ContentStatus.PUBLISHED);
        session(1, LEARNER, ACTIVE, false, "Pinned parent", CefrLevel.A2, TIE, false);
        session(2, LEARNER, ACTIVE, true, "Pinned parent", CefrLevel.A2, TIE, false);
        session(3, LEARNER, NOT_STARTED, false, "Other parent", CefrLevel.A1, TIE.plusSeconds(1), false);
        session(4, OTHER, FOREIGN, true, "Private", CefrLevel.C2, TIE, false);
        session(5, LEARNER, ACTIVE, true, "Deleted", CefrLevel.C2, TIE.plusSeconds(2), true);
        String parent = GROUPS + "/" + ACTIVE + "/sessions";

        mvc.perform(get(parent).cookie(cookie(LEARNER)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionCount").value(2))
                .andExpect(jsonPath("$.data.title").value("Shared current title"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(2)))
                .andExpect(jsonPath("$.data.items[0].scope.type").value("TOPIC_ITEM"))
                .andExpect(jsonPath("$.data.items[0].scope.topicItemId").value(itemId(ACTIVE)))
                .andExpect(jsonPath("$.data.items[0].context.topicTitle").value("Pinned parent"))
                .andExpect(jsonPath("$.data.items[0].context.topicItemTitle").value("Historic item"))
                .andExpect(jsonPath("$.data.items[0].committedTurnCount").value(2))
                .andExpect(jsonPath("$.data.items[0].speechDurationMs").value(18000))
                .andExpect(jsonPath("$.data.items[0].status").value("ENDED"))
                .andExpect(jsonPath("$.data.items[0].assessmentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.items[0].context.level").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.items[0].assessment").doesNotHaveJsonPath());
        mvc.perform(get(parent).cookie(cookie(LEARNER)).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(1)));
        mvc.perform(get(GROUPS + "/" + FOREIGN + "/sessions").cookie(cookie(LEARNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.title").isEmpty()).andExpect(jsonPath("$.data.sessionCount").value(0));
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("scopeType", "TOPIC").param("topicId", ACTIVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(1)));
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("scopeType", "TOPIC_ITEM")
                        .param("topicId", ACTIVE).param("topicItemId", itemId(ACTIVE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(2)));
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(3)))
                .andExpect(jsonPath("$.data.items[1].sessionId").value(id(2)));
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(1)));
    }

    @Test
    void farPastLastParentPageReturnsAnEmptyPageWithTheFullCountAndTitle() throws Exception {
        session(1, LEARNER, ACTIVE, false, "Saved title", CefrLevel.A1, TIE, false);
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions").cookie(cookie(LEARNER))
                        .param("page", Integer.toString(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.title").value("Saved title"))
                .andExpect(jsonPath("$.data.sessionCount").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void historyRoutesRejectMixedAndDuplicateQueryIntent() throws Exception {
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions").cookie(cookie(LEARNER)).param("scopeType", "TOPIC"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions").cookie(cookie(LEARNER)).param("topicItemId", itemId(ACTIVE)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "COMPLETED", "OTHER"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER").param("topicId", ACTIVE))
                .andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER").param("size", ""))
                .andExpect(status().isBadRequest());
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("group", "OTHER"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("parentTopicId", ACTIVE))
                .andExpect(status().isBadRequest());
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("scopeType", "TOPIC")
                        .param("topicId", ACTIVE, HIDDEN))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCOPE"));
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("unrelatedTrackingParameter", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void validationAndAuthenticationKeepInvalidFiltersFromWidening() throws Exception {
        mvc.perform(get(GROUPS).param("group", "OTHER")).andExpect(status().isUnauthorized());
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get(GROUPS).cookie(cookie(OTHER)).param("group", "OTHER")).andExpect(status().isOk());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER))).andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER").param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions").cookie(cookie(LEARNER)).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("scopeType", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCOPE"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("insufficient-role", null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(() -> history.topics(Group.OTHER, 0, 20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> history.parent(ACTIVE, 0, 20)).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER)).param("scopeType", "TOPIC").param("topicId", ACTIVE)
                        .param("topicItemId", itemId(ACTIVE)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCOPE"));
    }

    @Test
    void deletionRecomputesCountsAndNewestAcrossEveryPath() throws Exception {
        session(1, LEARNER, ACTIVE, false, "Older", CefrLevel.A1, TIE.minusSeconds(1), false);
        session(2, LEARNER, ACTIVE, true, "Newest", CefrLevel.A2, TIE, false);
        session(3, LEARNER, FOREIGN, true, "Another parent", CefrLevel.A2, TIE, false);
        session(4, OTHER, ACTIVE, true, "Private", CefrLevel.C2, TIE, false);

        mvc.perform(delete(SESSIONS + "/" + id(4)).cookie(cookie(LEARNER))).andExpect(status().isForbidden());
        mvc.perform(delete(SESSIONS + "/" + id(2)).cookie(cookie(LEARNER))).andExpect(status().isNoContent());
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions").cookie(cookie(LEARNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Older"))
                .andExpect(jsonPath("$.data.sessionCount").value(1));
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[1].topicId").value(ACTIVE))
                .andExpect(jsonPath("$.data.items[1].latestSessionCreatedAt").value(TIE.minusSeconds(1).toString()));
        mvc.perform(delete(SESSIONS + "/" + id(1)).cookie(cookie(LEARNER))).andExpect(status().isNoContent());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totals.other").value(1));
        mvc.perform(delete(SESSIONS).cookie(cookie(LEARNER))).andExpect(status().isNoContent());
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totals.other").value(0));
        mvc.perform(get(SESSIONS).cookie(cookie(OTHER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(4)));
    }

    @Test
    void metadataFailureKeepsParentAndRecentReadableButGroupsReturnRetryableError() throws Exception {
        session(1, LEARNER, ACTIVE, false, "Pinned title", CefrLevel.A1, TIE, false);
        doThrow(new DataAccessResourceFailureException("Injected Topic lookup failure"))
                .when(topicRepository).findAllById(any());

        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("HISTORY_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true)).andExpect(jsonPath("$.data").doesNotHaveJsonPath());
        mvc.perform(get(GROUPS + "/" + ACTIVE + "/sessions").cookie(cookie(LEARNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Pinned title"));
        mvc.perform(get(SESSIONS).cookie(cookie(LEARNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value(id(1)));
    }

    @Test
    void coverResolutionIsLimitedToPublishedPageAndStatusUsesCurrentGeneration() throws Exception {
        topic(ACTIVE, ContentStatus.PUBLISHED);
        topic(COMPLETE, ContentStatus.PUBLISHED);
        topic(HIDDEN, ContentStatus.HIDDEN);
        started(ACTIVE);
        complete(ACTIVE);
        mongoTemplate.insert(TopicLearnerState.builder().id(generations.id(LEARNER, ACTIVE))
                .topicId(ACTIVE).userId(LEARNER).generation(1).build());
        String visibleAsset = id(201);
        String offPageAsset = id(202);
        String hiddenAsset = id(203);
        cover(ACTIVE, visibleAsset);
        cover(COMPLETE, offPageAsset);
        cover(HIDDEN, hiddenAsset);
        when(media.resolve(visibleAsset)).thenReturn(new MediaDeliveryDto(
                URI.create("https://media.example.test/covered"), TIE.plusSeconds(60), "image/png"));
        session(1, LEARNER, ACTIVE, false, "Former active", CefrLevel.A1, TIE, false);
        session(2, LEARNER, COMPLETE, false, "Former completed", CefrLevel.A1, TIE, false);
        session(3, LEARNER, HIDDEN, false, "Former hidden", CefrLevel.A2, TIE.plusSeconds(1), false);

        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "OTHER").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totals.completed").value(0))
                .andExpect(jsonPath("$.data.items[0].learningStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.items[0].coverUrl").isEmpty())
                .andExpect(jsonPath("$.data.items[1].learningStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.items[1].coverUrl").value("https://media.example.test/covered"));
        verify(media).resolve(visibleAsset);
        verifyNoMoreInteractions(media);
    }

    @Test
    void historyUsesVietnamesePublishedTitleAndExcludesNonPublishedItemsFromProgress() throws Exception {
        topic(DRAFT_ITEM, ContentStatus.PUBLISHED, "Tiêu đề hiện tại", "Current English title", ContentStatus.DRAFT);
        started(DRAFT_ITEM);
        complete(DRAFT_ITEM);
        session(1, LEARNER, DRAFT_ITEM, true, "Saved topic title", CefrLevel.A1, TIE, false);

        mvc.perform(get(GROUPS).cookie(cookie(LEARNER)).param("group", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.completed").value(0))
                .andExpect(jsonPath("$.data.totals.inProgress").value(1))
                .andExpect(jsonPath("$.data.items[0].topicId").value(DRAFT_ITEM))
                .andExpect(jsonPath("$.data.items[0].title").value("Tiêu đề hiện tại"))
                .andExpect(jsonPath("$.data.items[0].learningStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items[0].learningProgress.completedItemCount").value(0))
                .andExpect(jsonPath("$.data.items[0].learningProgress.totalItemCount").value(0));

        mvc.perform(get(GROUPS + "/" + DRAFT_ITEM + "/sessions").cookie(cookie(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tiêu đề hiện tại"))
                .andExpect(jsonPath("$.data.items[0].context.topicTitle").value("Saved topic title"))
                .andExpect(jsonPath("$.data.items[0].context.topicItemTitle").value("Historic item"));
    }

    @Test
    void explainReadsOwnerGroupAndParentPageThroughHistoryIndexes() {
        for (int index = 0; index < 70; index++) {
            session(100 + index, LEARNER, "another-" + index, false, "Saved", CefrLevel.A1, TIE, false);
            session(200 + index, OTHER, ACTIVE, false, "Private", CefrLevel.A2, TIE, false);
            session(300 + index, LEARNER, ACTIVE, false, "Deleted", CefrLevel.A2, TIE, true);
        }
        session(1, LEARNER, ACTIVE, false, "Saved", CefrLevel.A1, TIE, false);
        session(2, LEARNER, ACTIVE, true, "Saved", CefrLevel.A1, TIE, false);

        var aggregate = queries.groups(LEARNER).explain(ExplainVerbosity.EXECUTION_STATS);
        var groupCursor = aggregate.getList("stages", Document.class).getFirst().get("$cursor", Document.class);
        var groupPlan = groupCursor.getEmbedded(List.of("queryPlanner", "winningPlan"), Document.class);
        var groupStats = groupCursor.get("executionStats", Document.class);
        assertThat(groupPlan.toJson()).contains("IXSCAN", "owner_id", "deleted_at");
        assertThat(groupStats.getInteger("totalDocsExamined")).isEqualTo(72);

        var page = queries.parentPageQuery(LEARNER, ACTIVE, 0, 20);
        var parent = mongoTemplate.getCollection("speaking_v2_sessions").find(page.getQueryObject())
                .projection(page.getFieldsObject()).sort(page.getSortObject()).limit(page.getLimit())
                .explain(ExplainVerbosity.EXECUTION_STATS);
        var parentPlan = parent.getEmbedded(List.of("queryPlanner", "winningPlan"), Document.class);
        var parentStats = parent.get("executionStats", Document.class);
        assertThat(parentPlan.toJson()).contains("IXSCAN", "speaking_v2_parent_history").doesNotContain("\"SORT\"");
        assertThat(parentStats.getInteger("totalDocsExamined")).isEqualTo(2);
        System.out.println("H-A11 group winning plan " + groupPlan.toJson());
        System.out.println("H-A11 group docs/keys examined " + groupStats.get("totalDocsExamined")
                + "/" + groupStats.get("totalKeysExamined"));
        System.out.println("H-A11 parent winning plan " + parentPlan.toJson());
        System.out.println("H-A11 parent docs/keys examined " + parentStats.get("totalDocsExamined")
                + "/" + parentStats.get("totalKeysExamined"));
    }

    private void topic(String id, ContentStatus status) {
        topic(id, status, null, "Shared current title", ContentStatus.PUBLISHED);
    }

    private void topic(String id, ContentStatus status, String titleVi, String titleEn, ContentStatus itemStatus) {
        String version = "release-" + id;
        String itemVersion = "version-" + id;
        mongoTemplate.insert(Topic.builder().id(id).latestPublishedVersion(version)
                .titleVi(titleVi).titleEn(titleEn).cefrLevel(CefrLevel.B2).build());
        mongoTemplate.insert(TopicVersion.builder().id(version).topicId(id).status(status)
                .itemVersionIds(List.of(itemVersion)).build());
        mongoTemplate.insert(TopicItemVersion.builder().id(itemVersion).topicId(id).topicItemId(itemId(id))
                .status(itemStatus).title("Current item").order(1).build());
    }

    private void started(String topicId) {
        mongoTemplate.insert(UserTopicProgress.builder().userId(LEARNER).topicId(topicId).build());
    }

    private void complete(String topicId) {
        mongoTemplate.insert(UserTopicItemProgress.builder().userId(LEARNER).topicId(topicId)
                .topicItemId(itemId(topicId)).status(ItemProgressStatus.COMPLETED).build());
    }

    private void cover(String topicId, String mediaId) {
        Topic topic = mongoTemplate.findById(topicId, Topic.class);
        topic.setCoverUrl("topic-media:" + topicId + ":" + mediaId);
        topic.setMediaReferences(List.of(TopicMediaReferenceVo.builder().mediaId(mediaId).kind(TopicMediaKind.IMAGE).build()));
        mongoTemplate.save(topic);
    }

    private void session(int number, String ownerId, String topicId, boolean item, String savedTitle,
                         CefrLevel level, Instant created, boolean deleted) {
        String sessionId = id(number);
        var snapshot = SpeakingSnapshotVo.builder().scopeType(item ? SpeakingScopeType.TOPIC_ITEM : SpeakingScopeType.TOPIC)
                .topicId(topicId).topicItemId(item ? itemId(topicId) : null)
                .title(item ? "Historic item" : savedTitle).topicTitle(savedTitle)
                .topicItemTitle(item ? "Historic item" : null).level(level).build();
        mongoTemplate.insert(SpeakingSession.builder().id(sessionId).ownerId(ownerId)
                .clientRequestId(sessionId).snapshot(snapshot).committedTurnCount(2).speechDurationMs(18000)
                .limits(SpeakingLimitsVo.builder().maxTurns(30).build())
                .status(SpeakingSessionStatus.ENDED).assessmentStatus(SpeakingAssessmentStatus.PARTIAL)
                .deletedAt(deleted ? created : null).build());
        mongoTemplate.getCollection("speaking_v2_sessions").updateOne(new Document("_id", sessionId),
                new Document("$set", new Document("created_at", Date.from(created))));
    }

    private void account(String id, UserRole role) {
        mongoTemplate.insert(new User(id, id + "@example.test", "unused", role, false));
        mongoTemplate.insert(RefreshSession.builder().sessionId("auth-" + id).accountId(id)
                .expiresAt(Instant.now().plusSeconds(3600)).build());
    }

    private Cookie cookie(String id) {
        return new Cookie("ACCESS_TOKEN", tokens.create(id, "auth-" + id, "access", 900));
    }

    private static String itemId(String topicId) {
        return topicId.startsWith("6743") ? "6744" + topicId.substring(4) : "item-" + topicId;
    }

    private static String id(int number) {
        return "00000000-0000-4000-8000-%012d".formatted(number);
    }
}
