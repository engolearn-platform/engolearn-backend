package fit.iuh.engolearn.learningpath.service.impl;

import com.mongodb.MongoException;
import fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads;
import fit.iuh.engolearn.learningpath.contract.dto.*;
import fit.iuh.engolearn.learningpath.contract.enums.*;
import fit.iuh.engolearn.learningpath.contract.exception.LearningPathCompanionReadException;
import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;
import fit.iuh.engolearn.learningpath.model.vo.*;
import fit.iuh.engolearn.learningpath.repository.LearningPathCompanionReadRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static fit.iuh.engolearn.learningpath.contract.exception.LearningPathCompanionReadException.Reason.*;

@Service
public class LearningPathCompanionReadsImpl implements LearningPathCompanionReads {
    private static final Comparator<LearningPathActivityPageDto.Row> ACTIVITY_ORDER =
            Comparator.comparing(LearningPathActivityPageDto.Row::occurredAt).reversed()
                    .thenComparing(LearningPathActivityPageDto.Row::pathId, Comparator.reverseOrder())
                    .thenComparingInt(LearningPathActivityPageDto.Row::weekNo)
                    .thenComparingInt(LearningPathActivityPageDto.Row::dayNo)
                    .thenComparing(row -> row.eventKind().name());
    private final LearningPathCompanionReadRepository reads;
    private final Clock clock;

    public LearningPathCompanionReadsImpl(LearningPathCompanionReadRepository reads, Clock clock) {
        this.reads = reads;
        this.clock = clock;
    }

    @Override
    public LearningPathActivePageDto active(String learnerId, Instant asOf, LearningPathPageQueryDto query) {
        validate(learnerId, query);
        if (asOf == null) throw invalid();
        String scope = binding(learnerId, "ACTIVE", asOf.toString());
        String[] key = decode(query.cursor(), scope, 2);
        Instant time = null;
        String id = null;
        if (key != null) {
            try {
                time = Instant.parse(key[0]);
                id = key[1];
                if (!ObjectId.isValid(id)) throw invalid();
            } catch (RuntimeException ex) { throw invalid(); }
        }
        Instant anchorTime = time;
        String anchorId = id;
        return read(() -> {
            if (anchorId != null && !reads.activeAnchor(learnerId, anchorTime, anchorId)) throw invalid();
            List<LearningPath> paths = reads.active(learnerId, anchorTime, anchorId, query.limit() + 1);
            boolean more = paths.size() > query.limit();
            List<LearningPath> page = paths.stream().limit(query.limit()).toList();
            List<LearningPathActivePageDto.Row> rows = page.stream().map(path -> activeRow(path, asOf)).toList();
            LearningPath last = page.isEmpty() ? null : page.getLast();
            String cursor = more ? encode(scope, last.getCreatedAt().toString(), last.getId()) : null;
            return new LearningPathActivePageDto(rows, more, cursor, LearningPathCoverage.COMPLETE, asOf);
        });
    }

    private LearningPathActivePageDto.Row activeRow(LearningPath path, Instant asOf) {
        if (path.getId() == null || !ObjectId.isValid(path.getId()) || path.getCreatedAt() == null
                || path.getStatus() == null) throw inconsistent();
        List<LearningPathActivePageDto.NextAction> actions = new ArrayList<>();
        boolean unknown = path.getWeeks() == null;
        Set<String> dayKeys = new HashSet<>();
        for (LearningPathWeek week : safe(path.getWeeks())) {
            if (week == null || week.getWeekNo() == null || week.getDays() == null) {
                unknown = true;
                continue;
            }
            for (LearningPathDay day : week.getDays()) {
                if (day == null || day.getDayNo() == null || day.getStatus() == null) {
                    unknown = true;
                    continue;
                }
                if (!dayKeys.add(week.getWeekNo() + ":" + day.getDayNo())) throw inconsistent();
                if (!LearningPathDayAvailability.available(path, week, day, asOf)) continue;
                if (!configured(path, day)) {
                    unknown = true;
                    continue;
                }
                // Snapshot readiness is intentionally unverified; the app must recheck before starting.
                actions.add(new LearningPathActivePageDto.NextAction(week.getWeekNo(), day.getDayNo(),
                        day.getStatus().name(), day.getStatus() == LearningPathDayStatus.IN_PROGRESS
                        ? LearningPathAction.RESUME : LearningPathAction.START,
                        instant(day.getStartedAt()), instant(day.getAutoCloseAt()),
                        LearningPathAvailability.UNKNOWN, true));
            }
        }
        actions.sort(Comparator.comparing((LearningPathActivePageDto.NextAction a) ->
                        a.action() != LearningPathAction.RESUME)
                .thenComparingInt(LearningPathActivePageDto.NextAction::weekNo)
                .thenComparingInt(LearningPathActivePageDto.NextAction::dayNo));
        LearningPathProgress progress = path.getProgress();
        List<String> labels = safe(path.getMicroSkills()).stream().filter(Objects::nonNull)
                .map(LearningPathMicroSkillSnapshot::getMicroSkill).filter(Objects::nonNull).distinct().limit(5).toList();
        LearningPathAvailability availability = unknown || !actions.isEmpty()
                ? LearningPathAvailability.UNKNOWN : LearningPathAvailability.NOT_AVAILABLE;
        return new LearningPathActivePageDto.Row(path.getId(), path.getLevel(), path.getStatus().name(),
                path.getCurrentWeekNo(), labels, progress == null ? null : progress.getAssigned(),
                progress == null ? null : progress.getAnswered(), progress == null ? null : progress.getCorrect(),
                availability, actions.stream().limit(5).toList());
    }

    private boolean configured(LearningPath path, LearningPathDay day) {
        if (path.getConfig() == null || path.getConfig().getQuestionsPerDay() == null
                || day.getItems() == null || day.getItems().isEmpty()
                || day.getItems().size() != path.getConfig().getQuestionsPerDay()) return false;
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (LearningPathDayItem item : day.getItems()) {
            if (item == null || item.getQuestionId() == null || item.getQuestionId().isBlank()
                    || item.getMicroSkill() == null || item.getOrder() == null
                    || item.getOrder() < 1 || item.getOrder() > day.getItems().size()
                    || !ids.add(item.getQuestionId()) || !orders.add(item.getOrder())) return false;
        }
        return true;
    }

    @Override
    public LearningPathCompletionCountsDto counts(String learnerId) {
        validateOwner(learnerId);
        return read(() -> new LearningPathCompletionCountsDto(clock.instant(), LearningPathCoverage.COMPLETE,
                reads.completed(learnerId), "CURRENT_RECORDED_PATHS"));
    }

    @Override
    public LearningPathActivityPageDto recent(String learnerId, Instant fromInclusive, Instant toExclusive,
                                              LearningPathPageQueryDto query) {
        validate(learnerId, query);
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) throw invalid();
        String scope = binding(learnerId, "RECENT", fromInclusive + "|" + toExclusive);
        String[] anchor = decode(query.cursor(), scope, 5);
        return read(() -> {
            var source = reads.recent(learnerId, ceilMillis(fromInclusive), ceilMillis(toExclusive));
            if (source.limited()) {
                if (anchor != null) throw invalid();
                return new LearningPathActivityPageDto(List.of(), true, null, LearningPathCoverage.LIMITED, toExclusive);
            }
            List<LearningPathActivityPageDto.Row> rows = new ArrayList<>();
            Set<String> days = new HashSet<>();
            for (Document raw : source.days()) {
                ObjectId pathId = raw.getObjectId("_id");
                Integer week = raw.getInteger("week_no");
                Document day = raw.get("day", Document.class);
                if (pathId == null || week == null || day == null || day.getInteger("day_no") == null
                        || day.getString("status") == null) throw inconsistent();
                if (!days.add(pathId.toHexString() + ":" + week + ":" + day.getInteger("day_no"))) throw inconsistent();
                addEvent(rows, pathId.toHexString(), week, day, LearningPathEventKind.DAY_STARTED,
                        instant(day.getDate("started_at")), fromInclusive, toExclusive);
                addEvent(rows, pathId.toHexString(), week, day, LearningPathEventKind.DAY_CLOSED,
                        instant(day.getDate("closed_at")), fromInclusive, toExclusive);
            }
            rows.sort(ACTIVITY_ORDER);
            int start = 0;
            if (anchor != null) {
                while (start < rows.size() && !Arrays.equals(anchor, eventKey(rows.get(start)))) start++;
                if (start == rows.size()) throw invalid();
                start++;
            }
            int end = Math.min(rows.size(), start + query.limit());
            List<LearningPathActivityPageDto.Row> page = rows.subList(start, end);
            boolean more = end < rows.size();
            return new LearningPathActivityPageDto(page, more,
                    more ? encode(scope, eventKey(page.getLast())) : null, LearningPathCoverage.COMPLETE, toExclusive);
        });
    }

    private void addEvent(List<LearningPathActivityPageDto.Row> rows, String pathId, int week, Document day,
                          LearningPathEventKind kind, Instant at, Instant from, Instant to) {
        if (at == null || at.isBefore(from) || !at.isBefore(to)) return;
        boolean closed = kind == LearningPathEventKind.DAY_CLOSED;
        Document result = closed ? day.get("result", Document.class) : null;
        rows.add(new LearningPathActivityPageDto.Row(pathId, week, day.getInteger("day_no"),
                day.getString("status"), kind, at, closed ? day.getString("close_reason") : null,
                number(result, "completion_rate"), number(result, "practice_score")));
    }

    private Double number(Document document, String key) {
        if (document == null || document.get(key) == null) return null;
        Object value = document.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw inconsistent();
        return n.doubleValue();
    }

    private static String[] eventKey(LearningPathActivityPageDto.Row row) {
        return new String[]{row.occurredAt().toString(), row.pathId(), String.valueOf(row.weekNo()),
                String.valueOf(row.dayNo()), row.eventKind().name()};
    }

    private static Instant ceilMillis(Instant value) {
        Instant truncated = Instant.ofEpochMilli(value.toEpochMilli());
        return truncated.equals(value) ? truncated : truncated.plusMillis(1);
    }

    private static Instant instant(Date date) { return date == null ? null : date.toInstant(); }
    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }

    private static void validateOwner(String owner) {
        if (owner == null || owner.isBlank()) throw invalid();
    }

    private static void validate(String owner, LearningPathPageQueryDto query) {
        validateOwner(owner);
        if (query == null || query.limit() < 1 || query.limit() > 5
                || (query.cursor() != null && (query.cursor().isBlank() || query.cursor().length() > 512))) throw invalid();
    }

    private static String binding(String owner, String mode, String scope) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((owner + "\u0000" + mode + "\u0000" + scope).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static String encode(String scope, String... key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("1|" + scope + "|" + String.join("|", key)).getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decode(String cursor, String scope, int keySize) {
        if (cursor == null) return null;
        try {
            String[] values = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != keySize + 2 || !"1".equals(values[0]) || !scope.equals(values[1])) throw invalid();
            return Arrays.copyOfRange(values, 2, values.length);
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }

    private static <T> T read(Supplier<T> operation) {
        try { return operation.get(); }
        catch (LearningPathCompanionReadException ex) { throw ex; }
        catch (MongoException | DataAccessException ex) { throw new LearningPathCompanionReadException(UNAVAILABLE); }
        catch (RuntimeException ex) { throw inconsistent(); }
    }

    private static LearningPathCompanionReadException invalid() {
        return new LearningPathCompanionReadException(INVALID_QUERY);
    }

    private static LearningPathCompanionReadException inconsistent() {
        return new LearningPathCompanionReadException(INCONSISTENT_STATE);
    }
}
