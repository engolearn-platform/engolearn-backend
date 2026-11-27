package fit.iuh.engolearn.companion.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Trusted time and issued Path cursor scope, discarded with each turn. */
public final class CompanionBusinessTurnContext {
    private final Instant asOf;
    private final Set<CursorScope> issued = ConcurrentHashMap.newKeySet();

    public CompanionBusinessTurnContext(Instant asOf) {
        this.asOf = Objects.requireNonNull(asOf);
    }

    public Instant asOf() { return asOf; }

    public Instant weekStart() {
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return asOf.atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant();
    }

    public boolean canContinue(String owner, String mode, String cursor) {
        return cursor == null || issued.contains(new CursorScope(owner,mode,cursor));
    }

    public void rememberCursor(String owner, String mode, String cursor) {
        if (cursor != null) issued.add(new CursorScope(owner,mode,cursor));
    }

    private record CursorScope(String owner, String mode, String cursor) {}
}
