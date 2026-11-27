package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.assessment.contract.AssessmentCompanionResults;
import fit.iuh.engolearn.assessment.contract.dto.*;
import fit.iuh.engolearn.assessment.contract.enums.*;
import fit.iuh.engolearn.companion.service.impl.*;
import fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads;
import fit.iuh.engolearn.learningpath.contract.dto.*;
import fit.iuh.engolearn.learningpath.contract.enums.*;
import fit.iuh.engolearn.learningpath.contract.exception.LearningPathCompanionReadException;
import fit.iuh.engolearn.topic.contract.TopicCompanionReads;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.contract.exception.TopicCompanionInvalidQueryException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanionBusinessToolTest {
    private static final Instant NOW = Instant.parse("2026-11-22T17:00:01Z");
    private static final Instant WEEK = Instant.parse("2026-11-22T17:00:00Z");
    private final TopicCompanionReads topics = mock(TopicCompanionReads.class);
    private final LearningPathCompanionReads paths = mock(LearningPathCompanionReads.class);
    private final AssessmentCompanionResults assessments = mock(AssessmentCompanionResults.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final CompanionToolRegistry registry = new CompanionToolRegistry(List.of(
            new CompanionTopicToolHandler(topics,json), new CompanionLearningPathToolHandler(paths,json),
            new CompanionAssessmentHistoryToolHandler(assessments,json)));
    private final CompanionBusinessTurnContext scope = new CompanionBusinessTurnContext(NOW);

    private String call(String name, Map<String,Object> args) {
        return registry.execute(name,args,"owner",()->{},null,scope);
    }
    private String status(String value) { return json.readTree(value).get("status").asText(); }

    @Test void strictArgumentsDoNotReachProvidersAndUnsupportedTopicModeNotAdvertised() {
        assertEquals(4,registry.definitions(true).size());
        assertEquals("UNKNOWN_TOOL",registry.safeName("secret-owner"));
        assertFalse(registry.definitions(true).stream().filter(d -> d.name().equals("getTopicLearningProgress"))
                .findFirst().orElseThrow().inputSchemaJson().contains("RECENT"));
        for (var invalid : List.<Map<String,Object>>of(Map.of("mode","RECENT"),
                Map.of("mode","COUNTS","limit",1), Map.of("mode","RESUME","limit",1.5),
                Map.of("mode","RESUME","owner","foreign"), Map.of("mode","RESUME","cursor","x".repeat(513))))
            assertEquals("INVALID_ARGUMENTS",status(call("getTopicLearningProgress",invalid)));
        var nullArgs = new HashMap<String,Object>();
        nullArgs.put("level",null);
        assertEquals("INVALID_ARGUMENTS",status(call("findLearningTopics",nullArgs)));
        assertEquals("INVALID_ARGUMENTS",status(call("getLearningPathProgress",Map.of("mode","RECENT","asOf",NOW.toString()))));
        assertEquals("INVALID_ARGUMENTS",status(call("getAssessmentHistory",Map.of("limit",1))));
        verifyNoInteractions(topics,paths,assessments);
    }

    @Test void typedTopicInvalidVersusUnavailableAndKnownZeroCounts() {
        when(topics.resume(eq("owner"),any())).thenThrow(new TopicCompanionInvalidQueryException("private cursor"));
        assertEquals("INVALID_ARGUMENTS",status(call("getTopicLearningProgress",Map.of("mode","RESUME","cursor","foreign"))));
        when(topics.discover(eq("owner"),any())).thenThrow(new IllegalArgumentException("private provider error"));
        String result = call("findLearningTopics",Map.of("keyword","  interview  "));
        assertEquals("ERROR",status(result));
        assertFalse(result.contains("private"));
        verify(topics).discover("owner",new TopicDiscoveryQueryDto("interview",null,3,null));
        when(topics.counts("owner")).thenReturn(new TopicCompletionCountsDto(NOW,"COMPLETE",0,0,"CURRENT_VISIBLE_CURRENT_GENERATION"));
        assertEquals("SUCCESS",status(call("getTopicLearningProgress",Map.of("mode","COUNTS"))));
    }

    @Test void pathFrozenTimeAndHalfOpenWeekLimitedAndForeignCursor() {
        var first = new LearningPathPageQueryDto(3,null);
        var second = new LearningPathPageQueryDto(3,"next");
        var row = new LearningPathActivePageDto.Row("path","B1","IN_PROGRESS",1,List.of("Reading"),
                5,2,1,LearningPathAvailability.UNKNOWN,List.of(new LearningPathActivePageDto.NextAction(
                1,1,"IN_PROGRESS",LearningPathAction.RESUME,NOW,null,LearningPathAvailability.UNKNOWN,true)));
        when(paths.active("owner",NOW,first)).thenReturn(new LearningPathActivePageDto(
                List.of(row),true,"next",LearningPathCoverage.COMPLETE,NOW));
        String active = call("getLearningPathProgress",Map.of("mode","ACTIVE"));
        assertEquals("SUCCESS",status(active));
        assertTrue(active.contains("UNKNOWN"));
        assertTrue(active.contains("\"requiresAppRecheck\":true"));
        when(paths.active("owner",NOW,second)).thenReturn(new LearningPathActivePageDto(
                List.of(),false,null,LearningPathCoverage.COMPLETE,NOW));
        assertEquals("NO_DATA",status(call("getLearningPathProgress",Map.of("mode","ACTIVE","cursor","next"))));
        verify(paths).active("owner",NOW,second);
        when(paths.active("owner",NOW.plusSeconds(1),second)).thenThrow(new LearningPathCompanionReadException(
                LearningPathCompanionReadException.Reason.INVALID_QUERY));
        assertEquals("INVALID_ARGUMENTS",status(registry.execute("getLearningPathProgress",
                Map.of("mode","ACTIVE","cursor","next"),"owner",()->{},null,
                new CompanionBusinessTurnContext(NOW.plusSeconds(1)))));
        when(paths.active("foreign",NOW,second)).thenThrow(new LearningPathCompanionReadException(
                LearningPathCompanionReadException.Reason.INVALID_QUERY));
        assertEquals("INVALID_ARGUMENTS",status(registry.execute("getLearningPathProgress",
                Map.of("mode","ACTIVE","cursor","next"),"foreign",()->{},null,scope)));
        verify(paths,never()).active("owner",NOW.plusSeconds(1),second);
        verify(paths,never()).active("foreign",NOW,second);
        assertEquals("INVALID_ARGUMENTS",status(registry.execute("getLearningPathProgress",
                Map.of("mode","ACTIVE","cursor","next"),"owner",()->{},null,new CompanionBusinessTurnContext(NOW))));
        when(paths.recent("owner",WEEK,NOW,first)).thenReturn(new LearningPathActivityPageDto(
                List.of(),true,null,LearningPathCoverage.LIMITED,NOW));
        var limited = json.readTree(call("getLearningPathProgress",Map.of("mode","RECENT")));
        assertEquals("SUCCESS",limited.get("status").asText());
        assertEquals("LIMITED",limited.get("data").get("coverage").asText());
        assertTrue(limited.get("data").get("nextCursor").isNull());
        assertEquals(WEEK,scope.weekStart());
    }

    @Test void historyRedactsPaperAndOnlyComparesMatchingVisibleSkills() {
        var a = new AssessmentCompanionSkillDto("READ","Reading","hidden-example","READING",8,10,80,null);
        var b = new AssessmentCompanionSkillDto("READ","Reading","hidden-example","READING",6,10,60,null);
        var hidden = new AssessmentCompanionSkillDto("OTHER","Other","hidden-example","READING",4,5,80,null);
        var rows = List.of(
                new AssessmentCompanionHistoryRowDto(new AssessmentCompanionSummaryDto("new",NOW,8,"B1",
                        AssessmentCompanionScope.PREVIEW,List.of(a)),"private-paper",1,1,false),
                new AssessmentCompanionHistoryRowDto(new AssessmentCompanionSummaryDto("old",NOW.minusSeconds(100),6,"B1",
                        AssessmentCompanionScope.FULL,List.of(b,hidden)),"private-paper",1,1,false));
        when(assessments.historyForCompanion("owner",new AssessmentCompanionHistoryQueryDto(2,null)))
                .thenReturn(new AssessmentCompanionHistoryPageDto(rows,false,null,AssessmentCompanionHistoryCoverage.COMPLETE,NOW));
        String summary = call("getAssessmentHistory",Map.of());
        assertFalse(summary.contains("skills"));
        assertFalse(summary.contains("private-paper"));
        String detail = call("getAssessmentHistory",Map.of("detail","SKILLS"));
        assertFalse(detail.contains("private-paper"));
        assertFalse(detail.contains("hidden-example"));
        var observed = json.readTree(detail).get("data").get("observations");
        assertEquals(1,observed.size());
        assertEquals("READ",observed.get(0).get("code").asText());
        assertEquals(80,observed.get(0).get("newerPercentage").asInt());
    }

    @Test void oversizedUtf8ResultFailsClosedWithoutSkippingRowsOrLeakingCursor() {
        var row = new LearningPathActivePageDto.Row("path","B1","IN_PROGRESS",1,
                java.util.Collections.nCopies(5,"\u6f22".repeat(160)),5,2,1,LearningPathAvailability.UNKNOWN,List.of());
        when(paths.active(eq("owner"),eq(NOW),any())).thenReturn(new LearningPathActivePageDto(
                java.util.Collections.nCopies(5,row),true,"private-cursor",LearningPathCoverage.COMPLETE,NOW));
        String result = call("getLearningPathProgress",Map.of("mode","ACTIVE","limit",5));
        assertEquals("ERROR",status(result));
        assertTrue(result.contains("RESULT_LIMIT"));
        assertFalse(result.contains("private-cursor"));
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= 12000);
    }
}
