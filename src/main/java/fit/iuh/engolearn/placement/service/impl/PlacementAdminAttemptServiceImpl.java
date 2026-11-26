package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminAttemptSearchRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptDetailResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptPageResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptSummaryResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.mapper.PlacementAdminAttemptMapper;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementAttemptSearch;
import fit.iuh.engolearn.placement.repository.PlacementAttemptTestSetRepository;
import fit.iuh.engolearn.placement.repository.PlacementAttemptTestSetVersion;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.PlacementAdminAttemptService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import fit.iuh.engolearn.placement.service.PlacementAudioDelivery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PlacementAdminAttemptServiceImpl implements PlacementAdminAttemptService {
    private static final ZoneId SEARCH_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PlacementAttemptRepository attempts;
    private final PlacementTestSetRepository testSets;
    private final PlacementAttemptTestSetRepository testSetHistory;
    private final Clock placementClock;

    private final PlacementAudioDelivery audioDelivery;

    @Override
    public PlacementAdminAttemptPageResponse search(PlacementAdminAttemptSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 100 : request.size();
        LocalDate from = request.from();
        LocalDate to = request.to();
        if ((from != null && (from.getYear() < 1 || from.getYear() > 9999))
                || (to != null && (to.getYear() < 1 || to.getYear() > 9999))
                || (from != null && to != null && from.isAfter(to))) {
            throw new PlacementRequestException(HttpStatus.BAD_REQUEST, "PLACEMENT_INVALID_DATE_RANGE",
                    "Enter a valid start-date range");
        }
        String testSetCode = optionalText(request.testSetCode());
        List<PlacementAttemptTestSetVersion> matchingVersions = testSetCode == null ? List.of()
                : testSetHistory.findVersionsByCode(testSetCode, request.version());
        if (testSetCode != null && matchingVersions.isEmpty()) {
            return new PlacementAdminAttemptPageResponse(List.of(), 0, page, size);
        }
        PlacementAttemptSearch search = new PlacementAttemptSearch(optionalText(request.search()),
                optionalText(request.userId()), matchingVersions, request.version(), request.status(), request.level(),
                from == null ? null : from.atStartOfDay(SEARCH_ZONE).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(SEARCH_ZONE).toInstant());
        Page<PlacementAttempt> rows = attempts.findForAdmin(search, PageRequest.of(page, size));
        Map<String, PlacementAttemptTestSetVersion> versions = findVersions(rows.getContent(), matchingVersions);
        Instant now = placementClock.instant();
        List<PlacementAdminAttemptSummaryResponse> items = rows.stream().map(attempt -> {
            PlacementAttemptTestSetVersion set = versions.get(attempt.getTestSetId());
            if (set != null && set.version() != attempt.getTestSetVersion()) set = null;
            boolean hasResult = hasResult(attempt);
            return PlacementAdminAttemptMapper.toSummary(attempt, set == null ? null : set.code(),
                    set == null ? null : set.totalQuestions(), hasResult, claimStatus(attempt, now, hasResult));
        }).toList();
        return new PlacementAdminAttemptPageResponse(items, rows.getTotalElements(), page, size);
    }

    @Override
    public PlacementAdminAttemptDetailResponse getDetail(String id) {
        PlacementAttempt attempt = attempts.findById(id).orElseThrow(() ->
                new PlacementRequestException(HttpStatus.NOT_FOUND, "PLACEMENT_ATTEMPT_NOT_FOUND",
                        "Placement attempt not found"));
        PlacementTestSet testSet = testSets.findVersion(attempt.getTestSetId(), attempt.getTestSetVersion())
                .orElse(null);
        boolean hasResult = hasResult(attempt);
        return PlacementAdminAttemptMapper.toDetail(attempt, testSet == null ? null : audioDelivery.prepare(testSet), hasResult,
                claimStatus(attempt, placementClock.instant(), hasResult));
    }

    private Map<String, PlacementAttemptTestSetVersion> findVersions(List<PlacementAttempt> rows,
            List<PlacementAttemptTestSetVersion> matchingVersions) {
        if (rows.isEmpty()) return Map.of();
        List<PlacementAttemptTestSetVersion> versions = matchingVersions;
        if (versions.isEmpty()) {
            versions = testSetHistory.findVersionsById(rows.stream().map(PlacementAttempt::getTestSetId)
                    .filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return versions.stream().collect(Collectors.toMap(PlacementAttemptTestSetVersion::id, Function.identity()));
    }

    private static String optionalText(String text) {
        return StringUtils.hasText(text) ? text.strip() : null;
    }

    private static boolean hasResult(PlacementAttempt attempt) {
        return (attempt.getStatus() == PlacementAttemptStatus.SUBMITTED
                || attempt.getStatus() == PlacementAttemptStatus.AUTO_SUBMITTED)
                && attempt.getCorrectCount() != null && attempt.getPlacementLevel() != null;
    }

    private static PlacementClaimStatus claimStatus(PlacementAttempt attempt, Instant now, boolean hasResult) {
        if (attempt.getGuestSessionId() == null) return null;
        if (attempt.getStatus() == PlacementAttemptStatus.EXPIRED) return PlacementClaimStatus.NO_RESULT;
        if (!hasResult) return null;
        if (attempt.getUserId() != null) return PlacementClaimStatus.ALREADY_CLAIMED;
        Instant expiresAt = attempt.getClaimExpiresAt();
        if (expiresAt == null) return null;
        return now.isBefore(expiresAt) ? PlacementClaimStatus.CLAIMABLE : PlacementClaimStatus.CLAIM_EXPIRED;
    }
}
